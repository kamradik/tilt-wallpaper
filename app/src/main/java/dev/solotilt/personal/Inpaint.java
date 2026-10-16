package dev.solotilt.personal;

import ai.onnxruntime.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * The background plate: the photo with its main subject removed and the background painted in behind it, by
 * MI-GAN (Picsart AI Research, MIT; the authors' ONNX pipeline, which crops around the hole, runs the network at
 * 512 px and blends the result back itself). Depth effects show it where the subject moved away, so what appears
 * behind the subject is a plausible wall or sky rather than smeared edge pixels. Made once per photo, next to
 * its depth map; MlDepth's subject mask (G channel, B = 255) says what to remove.
 */
final class Inpaint {
    static final String MODEL="models/migan_pipeline_v2.onnx";
    /** How far beyond the subject's outline the hole reaches: the largest shift a depth effect gives it, plus a
     *  margin so no rim of the subject survives on the plate. In pixels of the map's long side (1024). */
    static final int GROW=56;

    /** The hole: mask pixels above half, grown by r with a square-then-round dilation. 255 = keep, 0 = paint.
     *  Pure, unit-tested. */
    static byte[] hole(float[] mask,int w,int h,int r){
        // Separable max filter (rows, then columns), then the corners trimmed to a disc.
        float[] row=new float[w*h],col=new float[w*h];
        for(int y=0;y<h;y++){int base=y*w;for(int x=0;x<w;x++){float m=0;for(int k=Math.max(0,x-r);k<=Math.min(w-1,x+r);k++)m=Math.max(m,mask[base+k]);row[base+x]=m;}}
        for(int x=0;x<w;x++)for(int y=0;y<h;y++){float m=0;for(int k=Math.max(0,y-r);k<=Math.min(h-1,y+r);k++)m=Math.max(m,row[k*w+x]);col[y*w+x]=m;}
        byte[] out=new byte[w*h];
        for(int i=0;i<out.length;i++)out[i]=(byte)(col[i]>.3f?0:255);
        return out;
    }

    /** The plate for a photo and its masked depth map (same size as the map). Null when the map has no mask.
     *  Blocking, a second or so on a phone: call off the main thread. */
    static Bitmap plate(Context c,Bitmap photo,Bitmap map)throws IOException{
        int w=map.getWidth(),h=map.getHeight();int[] px=new int[w*h];map.getPixels(px,0,w,0,0,w,h);
        if((px[0]&255)!=255)return null;                                   // B = 255 marks a subject mask
        float[] mask=new float[w*h];for(int i=0;i<px.length;i++)mask[i]=((px[i]>>8)&255)/255f;
        byte[] keep=hole(mask,w,h,Math.max(4,Math.round(GROW*Math.max(w,h)/1024f)));
        Bitmap small=Bitmap.createScaledBitmap(photo,w,h,true);int[] rgb=new int[w*h];small.getPixels(rgb,0,w,0,0,w,h);if(small!=photo)small.recycle();
        ByteBuffer image=ByteBuffer.allocateDirect(3*w*h),hole=ByteBuffer.allocateDirect(w*h);
        int plane=w*h;
        for(int i=0;i<plane;i++){int p=rgb[i];image.put(i,(byte)(p>>16));image.put(plane+i,(byte)(p>>8));image.put(2*plane+i,(byte)p);}
        hole.put(keep);hole.rewind();
        long started=android.os.SystemClock.elapsedRealtime();
        try{
            OrtEnvironment env=OrtEnvironment.getEnvironment();
            try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){
                options.setIntraOpNumThreads(Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors())));
                try(OrtSession session=env.createSession(MlDepth.asset(c,MODEL),options);
                    OnnxTensor img=OnnxTensor.createTensor(env,image,new long[]{1,3,h,w},OnnxJavaType.UINT8);
                    OnnxTensor msk=OnnxTensor.createTensor(env,hole,new long[]{1,1,h,w},OnnxJavaType.UINT8)){
                    Map<String,OnnxTensor> in=new HashMap<>();
                    java.util.Iterator<String> names=session.getInputNames().iterator();
                    String first=names.next(),second=names.next();
                    // The pipeline's inputs are named "image" and "mask"; match by name, fall back to order.
                    in.put(first.contains("mask")?first:second.contains("mask")?second:second,msk);
                    in.put(first.contains("mask")?second:first,img);
                    try(OrtSession.Result result=session.run(in)){
                        OnnxTensor out=(OnnxTensor)result.get(0);ByteBuffer o=out.getByteBuffer();
                        int[] painted=new int[plane];
                        for(int i=0;i<plane;i++)painted[i]=0xff000000|(o.get(i)&255)<<16|(o.get(plane+i)&255)<<8|(o.get(2*plane+i)&255);
                        String timing="MI-GAN plate "+w+"x"+h+" in "+(android.os.SystemClock.elapsedRealtime()-started)+" ms";
                        Log.i("SoloDepth",timing);CrashLog.note(timing);
                        return Bitmap.createBitmap(painted,w,h,Bitmap.Config.ARGB_8888);
                    }
                }
            }
        }catch(OrtException|RuntimeException|UnsatisfiedLinkError e){throw new IOException("Inpainting failed",e);}
    }
    private Inpaint(){}
}
