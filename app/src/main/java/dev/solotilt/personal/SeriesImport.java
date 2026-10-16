package dev.solotilt.personal;

import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.opengl.*;
import android.util.Log;
import android.view.Surface;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

/**
 * A wallpaper from a series of photos taken while stepping sideways: the photos become a short video in which
 * each one dissolves smoothly into the next, and Tilt Scrub then picks the moment by the tilt, so turning the
 * phone walks through the series like an old lenticular postcard. Built on the video path the app already has
 * (encoder surface + OpenGL, as VideoTranscode); keyframes are frequent so scrubbing stays smooth.
 */
final class SeriesImport {
    static final int MIN=2,MAX=6,WIDTH=720,HEIGHT=1280,FPS=30,HOLD=6,BLEND=18;
    interface Progress{void at(float fraction);}

    /** Frames the video holds for n photos: a short hold on each, a dissolve between neighbours. Pure, unit-tested. */
    static int frames(int n){return n*HOLD+(n-1)*BLEND;}
    /** Which two photos frame f shows and how far it has dissolved from the first to the second. Pure, unit-tested. */
    static float[] mix(int f,int n){
        int span=HOLD+BLEND;int i=Math.min(n-1,f/span),local=f-i*span;
        if(i>=n-1||local<HOLD)return new float[]{i,i,0};
        float t=(local-HOLD+1)/(float)(BLEND+1);t=t*t*(3-2*t);
        return new float[]{i,i+1,t};
    }

    /** Writes the series video to target (overwritten). Blocking: call off the main thread. */
    static void encode(Context c,List<Uri> photos,File target,Progress progress)throws IOException{
        if(photos.size()<MIN||photos.size()>MAX)throw new IOException("Pick from "+MIN+" to "+MAX+" photos");
        Bitmap[] frames=new Bitmap[photos.size()];
        MediaCodec encoder=null;MediaMuxer muxer=null;Surface input=null;boolean muxing=false;
        EGLDisplay display=EGL14.EGL_NO_DISPLAY;EGLContext context=EGL14.EGL_NO_CONTEXT;EGLSurface surface=EGL14.EGL_NO_SURFACE;
        try{
            for(int i=0;i<frames.length;i++)frames[i]=cover(ImageStore.decode(ImageDecoder.createSource(c.getContentResolver(),photos.get(i))));
            MediaFormat format=MediaFormat.createVideoFormat("video/avc",WIDTH,HEIGHT);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE,6_000_000);format.setInteger(MediaFormat.KEY_FRAME_RATE,FPS);format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL,.25f);
            encoder=MediaCodec.createEncoderByType("video/avc");encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);input=encoder.createInputSurface();
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] v=new int[2];if(!EGL14.eglInitialize(display,v,0,v,1))throw new IOException("EGL");
            EGLConfig[] cfg=new EGLConfig[1];int[] n=new int[1];
            if(!EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,0x3142,1,EGL14.EGL_NONE},0,cfg,0,1,n,0)||n[0]==0)throw new IOException("No recordable EGL config");
            context=EGL14.eglCreateContext(display,cfg[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreateWindowSurface(display,cfg[0],input,new int[]{EGL14.EGL_NONE},0);
            if(!EGL14.eglMakeCurrent(display,surface,surface,context))throw new IOException("EGL surface");
            int program=program();int[] tex=new int[frames.length];GLES20.glGenTextures(tex.length,tex,0);
            for(int i=0;i<tex.length;i++){GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,tex[i]);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,frames[i],0);frames[i].recycle();frames[i]=null;}
            muxer=new MediaMuxer(target.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            encoder.start();MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();int track=-1,total=frames(tex.length);
            GLES20.glUseProgram(program);int a=GLES20.glGetUniformLocation(program,"a"),b=GLES20.glGetUniformLocation(program,"b"),t=GLES20.glGetUniformLocation(program,"t");
            GLES20.glUniform1i(a,0);GLES20.glUniform1i(b,1);
            FloatBuffer quad=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().put(new float[]{0,0,1,0,0,1,1,1});
            for(int f=0;f<=total;f++){
                if(f<total){
                    float[] m=mix(f,tex.length);
                    GLES20.glViewport(0,0,WIDTH,HEIGHT);
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,tex[(int)m[0]]);
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,tex[(int)m[1]]);GLES20.glUniform1f(t,m[2]);
                    quad.position(0);GLES20.glEnableVertexAttribArray(0);GLES20.glVertexAttribPointer(0,2,GLES20.GL_FLOAT,false,0,quad);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
                    EGLExt.eglPresentationTimeANDROID(display,surface,f*1_000_000_000L/FPS);EGL14.eglSwapBuffers(display,surface);
                    if(progress!=null)progress.at(f/(float)total);
                }else encoder.signalEndOfInputStream();
                // Drain what the encoder has ready (all of it once the input has ended).
                while(true){
                    int o=encoder.dequeueOutputBuffer(info,f<total?0:10_000);
                    if(o==MediaCodec.INFO_TRY_AGAIN_LATER){if(f<total)break;else continue;}
                    if(o==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){track=muxer.addTrack(encoder.getOutputFormat());muxer.start();muxing=true;continue;}
                    if(o<0)continue;
                    ByteBuffer data=encoder.getOutputBuffer(o);
                    if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0&&info.size>0&&muxing&&data!=null){data.position(info.offset).limit(info.offset+info.size);muxer.writeSampleData(track,data,info);}
                    encoder.releaseOutputBuffer(o,false);
                    if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)break;
                }
            }
            GLES20.glDeleteTextures(tex.length,tex,0);GLES20.glDeleteProgram(program);
            muxer.stop();muxing=false;
            Log.i("SoloVideo","Series of "+photos.size()+" photos encoded, "+total+" frames");
        }catch(IllegalStateException|IllegalArgumentException e){throw new IOException("This phone cannot encode the series",e);}
        finally{
            for(Bitmap f:frames)if(f!=null)f.recycle();
            if(encoder!=null){try{encoder.stop();}catch(RuntimeException ignored){}encoder.release();}
            if(muxer!=null){if(muxing)try{muxer.stop();}catch(RuntimeException ignored){}try{muxer.release();}catch(RuntimeException ignored){}}
            if(display!=EGL14.EGL_NO_DISPLAY){EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);EGL14.eglReleaseThread();}
            if(input!=null)input.release();
        }
    }
    /** The photo scaled and centre-cropped to the video frame. */
    static Bitmap cover(Bitmap src){
        Bitmap out=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);
        float s=Math.max(WIDTH/(float)src.getWidth(),HEIGHT/(float)src.getHeight());float w=src.getWidth()*s,h=src.getHeight()*s;
        c.drawBitmap(src,null,new RectF((WIDTH-w)/2,(HEIGHT-h)/2,(WIDTH+w)/2,(HEIGHT+h)/2),new Paint(Paint.FILTER_BITMAP_FLAG));src.recycle();return out;
    }
    static int program(){
        String vs="attribute vec2 p;varying vec2 uv;void main(){gl_Position=vec4(p*2.0-1.0,0.0,1.0);uv=vec2(p.x,1.0-p.y);}";
        String fs="precision mediump float;uniform sampler2D a,b;uniform float t;varying vec2 uv;void main(){gl_FragColor=mix(texture2D(a,uv),texture2D(b,uv),t);}";
        int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,VideoTranscode.shader(GLES20.GL_VERTEX_SHADER,vs));GLES20.glAttachShader(p,VideoTranscode.shader(GLES20.GL_FRAGMENT_SHADER,fs));
        GLES20.glBindAttribLocation(p,0,"p");GLES20.glLinkProgram(p);return p;
    }
    private SeriesImport(){}
}
