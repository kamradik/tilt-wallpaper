package dev.solotilt.personal;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.*;
import android.net.Uri;
import android.opengl.*;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Re-encodes a picked clip into the wallpaper file: decode (MediaCodec into a SurfaceTexture), draw each frame with
 * OpenGL onto the encoder's input surface, encode H.264, mux. Three things the plain remux in VideoImport cannot do:
 * - the cut is frame exact: frames before the start are decoded but not encoded, so the clip begins on the very frame
 *   picked, not on the keyframe before it;
 * - the frame is scaled to VideoPlayer.MAX_SIDE, the size the wallpaper samples anyway, so 4K clips shrink to a
 *   fraction of their size and decode cheaply;
 * - keyframes come every KEY_INTERVAL_S, so a seek (Tilt Scrub) decodes at most a handful of frames: scrubbing stays
 *   smooth whatever the source's keyframe interval was.
 * Only the video track is written (a wallpaper is silent), with the source's rotation as the orientation hint.
 * Codec or GL trouble throws Unsupported, after which the caller falls back to the remux; problems the user must fix
 * (no video, too long, too big) are plain IOExceptions with a readable message. Blocking: call off the main thread.
 */
final class VideoTranscode {
    static final String MIME="video/avc";
    static final float KEY_INTERVAL_S=.5f;
    static final long FRAME_TIMEOUT_MS=2500,DEQUEUE_US=10_000;
    /** The device's codecs or GL could not do it; the caller should remux instead. */
    static final class Unsupported extends IOException{Unsupported(String m,Throwable cause){super(m,cause);}}
    interface Progress{void at(float fraction);}

    static File transcode(Context c,Uri source,long startUs,long endUs,File target,Progress progress)throws IOException{
        MediaExtractor extractor=new MediaExtractor();
        try{
            try{extractor.setDataSource(c,source,null);}catch(IOException|RuntimeException e){throw new Unsupported("Extractor cannot open the clip",e);}
            int track=-1;MediaFormat format=null;
            for(int i=0;i<extractor.getTrackCount();i++){MediaFormat f=extractor.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("video/")){track=i;format=f;break;}}
            if(format==null)throw new IOException(ImageStore.videoProblem(false,1,1));
            long duration=format.containsKey(MediaFormat.KEY_DURATION)?format.getLong(MediaFormat.KEY_DURATION):0;
            if(duration<=0)duration=VideoImport.metadataDurationUs(c,source);
            long[] window=VideoImport.window(startUs,endUs,duration);
            String problem=VideoImport.lengthProblem(window,duration);if(problem!=null)throw new IOException(problem);
            int rotation=VideoImport.rotation(c,source,format);
            try{return new VideoTranscode(extractor,track,format,rotation,window,duration,target,progress).run();}
            catch(IllegalArgumentException|IllegalStateException|UnsupportedOperationException e){throw new Unsupported("Transcoding failed",e);}
        }finally{extractor.release();}
    }

    // ---- pure helpers (unit-tested) ----
    /** Output size for a coded frame: the longer side capped at maxSide, aspect kept, both sides even and at least 2. */
    static int[] outputSize(int width,int height,int maxSide){
        float s=Math.min(1,maxSide/(float)Math.max(width,height));
        return new int[]{Math.max(2,Math.round(width*s/2)*2),Math.max(2,Math.round(height*s/2)*2)};
    }
    /** Bit rate for the output: about 0.2 bits per pixel per frame, 2..12 Mbit/s, so 3 minutes stay well under the size cap. */
    static int bitRate(int width,int height,float fps){return (int)Math.max(2_000_000,Math.min(12_000_000,width*(long)height*fps*.2f));}

    final MediaExtractor extractor;final int track,rotation;final MediaFormat input;final long[] window;final long duration;final File target;final Progress progress;
    MediaCodec decoder,encoder;MediaMuxer muxer;int outTrack=-1;boolean muxing;
    EGLDisplay display=EGL14.EGL_NO_DISPLAY;EGLContext context=EGL14.EGL_NO_CONTEXT;EGLSurface surface=EGL14.EGL_NO_SURFACE;
    Surface encoderInput,decoderOutput;SurfaceTexture texture;HandlerThread frames;
    final Object frameLock=new Object();boolean frameReady;
    int program,oes;final float[] st=new float[16];
    long bytes;int written;

    VideoTranscode(MediaExtractor extractor,int track,MediaFormat input,int rotation,long[] window,long duration,File target,Progress progress){
        this.extractor=extractor;this.track=track;this.input=input;this.rotation=rotation;this.window=window;this.duration=duration;this.target=target;this.progress=progress;
    }

    File run()throws IOException{
        try{
            int width=input.getInteger(MediaFormat.KEY_WIDTH),height=input.getInteger(MediaFormat.KEY_HEIGHT);
            float fps=input.containsKey(MediaFormat.KEY_FRAME_RATE)?number(input,MediaFormat.KEY_FRAME_RATE):30;if(!(fps>1&&fps<=240))fps=30;
            int[] size=outputSize(width,height,VideoPlayer.MAX_SIDE);
            try{encoder=MediaCodec.createEncoderByType(MIME);}catch(IOException e){throw new Unsupported("No H.264 encoder",e);}
            // Some hardware encoders take only multiples of 16; round there when the exact size is refused.
            MediaCodecInfo.VideoCapabilities caps=encoder.getCodecInfo().getCapabilitiesForType(MIME).getVideoCapabilities();
            if(caps!=null&&!caps.isSizeSupported(size[0],size[1])){size[0]=Math.max(16,Math.round(size[0]/16f)*16);size[1]=Math.max(16,Math.round(size[1]/16f)*16);}
            MediaFormat out=MediaFormat.createVideoFormat(MIME,size[0],size[1]);
            out.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            out.setInteger(MediaFormat.KEY_BIT_RATE,bitRate(size[0],size[1],fps));
            out.setInteger(MediaFormat.KEY_FRAME_RATE,Math.round(fps));
            out.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL,KEY_INTERVAL_S);
            encoder.configure(out,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoderInput=encoder.createInputSurface();
            setUpGl();
            frames=new HandlerThread("ParallaxiaFrames");frames.start();
            texture=new SurfaceTexture(oes);
            texture.setOnFrameAvailableListener(t->{synchronized(frameLock){frameReady=true;frameLock.notifyAll();}},new Handler(frames.getLooper()));
            decoderOutput=new Surface(texture);
            String mime=input.getString(MediaFormat.KEY_MIME);
            try{decoder=MediaCodec.createDecoderByType(mime);}catch(IOException e){throw new Unsupported("No decoder for "+mime,e);}
            input.removeKey(MediaFormat.KEY_ROTATION); // frames are encoded as coded; the orientation hint rotates them
            decoder.configure(input,decoderOutput,null,0);
            muxer=new MediaMuxer(target.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);if(rotation!=0)muxer.setOrientationHint(rotation);
            encoder.start();decoder.start();
            extractor.selectTrack(track);extractor.seekTo(window[0],MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            loop(size[0],size[1]);
            if(written==0)throw new IOException("The video cannot be read");
            muxer.stop();muxing=false;
            Log.i("SoloVideo","Transcoded "+written+" frames "+width+"x"+height+" -> "+size[0]+"x"+size[1]+", "+(bytes>>10)+" KiB from "+(window[0]/1000)+" ms");
            return target;
        }finally{release();}
    }

    void loop(int width,int height)throws IOException{
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo(),encoded=new MediaCodec.BufferInfo();
        boolean inputDone=false,decoderDone=false,encoderDone=false;
        long end=window[1]==Long.MAX_VALUE?duration:window[1],length=Math.max(1,end-window[0]);
        while(!encoderDone){
            if(!inputDone&&!decoderDone){
                int i=decoder.dequeueInputBuffer(DEQUEUE_US);
                if(i>=0){
                    ByteBuffer buffer=decoder.getInputBuffer(i);int size=buffer==null?-1:extractor.readSampleData(buffer,0);
                    long time=extractor.getSampleTime();
                    // Samples well past the end are not needed; a little slack lets reordered frames before the end decode.
                    if(size<0||time<0||window[1]!=Long.MAX_VALUE&&time>window[1]+500_000){decoder.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}
                    else{decoder.queueInputBuffer(i,0,size,time,0);extractor.advance();}
                }
            }
            if(!decoderDone){
                int o=decoder.dequeueOutputBuffer(info,DEQUEUE_US);
                if(o>=0){
                    boolean eos=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    long time=info.presentationTimeUs;
                    boolean past=time>=window[1];
                    boolean render=info.size>0&&time>=window[0]&&!past;
                    decoder.releaseOutputBuffer(o,render);
                    if(render){
                        awaitFrame();texture.updateTexImage();texture.getTransformMatrix(st);
                        draw(width,height);
                        EGLExt.eglPresentationTimeANDROID(display,surface,(time-window[0])*1000);
                        if(!EGL14.eglSwapBuffers(display,surface))throw new IllegalStateException("eglSwapBuffers "+EGL14.eglGetError());
                        if(progress!=null)progress.at(Math.min(1,(time-window[0])/(float)length));
                    }
                    if(eos||past){encoder.signalEndOfInputStream();decoderDone=true;}
                }
            }
            encoderDone=drain(encoded);
        }
    }
    /** Moves encoded output to the muxer; true once the encoder has delivered end of stream. */
    boolean drain(MediaCodec.BufferInfo info)throws IOException{
        while(true){
            int o=encoder.dequeueOutputBuffer(info,0);
            if(o==MediaCodec.INFO_TRY_AGAIN_LATER)return false;
            if(o==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(outTrack>=0)throw new IllegalStateException("Format changed twice");outTrack=muxer.addTrack(encoder.getOutputFormat());muxer.start();muxing=true;continue;}
            if(o<0)continue;
            ByteBuffer data=encoder.getOutputBuffer(o);
            if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;
            if(info.size>0&&data!=null&&muxing){
                data.position(info.offset).limit(info.offset+info.size);muxer.writeSampleData(outTrack,data,info);written++;
                bytes+=info.size;if(bytes>ImageStore.MAX_VIDEO_BYTES)throw new IOException(ImageStore.videoProblem(true,1,bytes));
            }
            encoder.releaseOutputBuffer(o,false);
            if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)return true;
        }
    }
    void awaitFrame(){
        synchronized(frameLock){
            long until=System.currentTimeMillis()+FRAME_TIMEOUT_MS;
            while(!frameReady){long left=until-System.currentTimeMillis();if(left<=0)throw new IllegalStateException("Decoder frame timed out");try{frameLock.wait(left);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
            frameReady=false;
        }
    }

    // ---- GL: one textured quad, the SurfaceTexture transform applied ----
    static final String VERTEX="attribute vec2 p;uniform mat4 st;varying vec2 uv;void main(){gl_Position=vec4(p*2.0-1.0,0.0,1.0);uv=(st*vec4(p,0.0,1.0)).xy;}";
    static final String FRAGMENT="#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES t;varying vec2 uv;void main(){gl_FragColor=texture2D(t,uv);}";
    static final FloatBuffer QUAD=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().put(new float[]{0,0,1,0,0,1,1,1});

    void setUpGl(){
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] v=new int[2];
        if(display==EGL14.EGL_NO_DISPLAY||!EGL14.eglInitialize(display,v,0,v,1))throw new IllegalStateException("EGL init");
        int[] attributes={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,0x3142/*EGL_RECORDABLE_ANDROID*/,1,EGL14.EGL_NONE};
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        if(!EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0)||count[0]==0)throw new IllegalStateException("No recordable EGL config");
        context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        if(context==EGL14.EGL_NO_CONTEXT)throw new IllegalStateException("EGL context");
        surface=EGL14.eglCreateWindowSurface(display,configs[0],encoderInput,new int[]{EGL14.EGL_NONE},0);
        if(surface==EGL14.EGL_NO_SURFACE||!EGL14.eglMakeCurrent(display,surface,surface,context))throw new IllegalStateException("EGL surface");
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,shader(GLES20.GL_VERTEX_SHADER,VERTEX));GLES20.glAttachShader(program,shader(GLES20.GL_FRAGMENT_SHADER,FRAGMENT));
        GLES20.glBindAttribLocation(program,0,"p");GLES20.glLinkProgram(program);
        int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException("Link: "+GLES20.glGetProgramInfoLog(program));
        int[] t=new int[1];GLES20.glGenTextures(1,t,0);oes=t[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,oes);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
    }
    static int shader(int type,String source){
        int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);
        int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException("Shader: "+GLES20.glGetShaderInfoLog(s));
        return s;
    }
    void draw(int width,int height){
        GLES20.glViewport(0,0,width,height);GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,oes);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"t"),0);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"st"),1,false,st,0);
        QUAD.position(0);GLES20.glEnableVertexAttribArray(0);GLES20.glVertexAttribPointer(0,2,GLES20.GL_FLOAT,false,0,QUAD);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
    }

    static float number(MediaFormat f,String key){
        try{return f.getFloat(key);}catch(ClassCastException e){return f.getInteger(key);}
    }

    void release(){
        if(decoder!=null){try{decoder.stop();}catch(RuntimeException ignored){}decoder.release();}
        if(encoder!=null){try{encoder.stop();}catch(RuntimeException ignored){}encoder.release();}
        if(muxer!=null){if(muxing)try{muxer.stop();}catch(RuntimeException ignored){}try{muxer.release();}catch(RuntimeException ignored){}}
        if(display!=EGL14.EGL_NO_DISPLAY){
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
            EGL14.eglReleaseThread(); // no eglTerminate: the display is shared with the app's other GL users
        }
        if(decoderOutput!=null)decoderOutput.release();if(texture!=null)texture.release();if(encoderInput!=null)encoderInput.release();
        if(frames!=null)frames.quitSafely();
    }
}
