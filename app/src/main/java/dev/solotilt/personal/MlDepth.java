package dev.solotilt.personal;

import ai.onnxruntime.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import java.io.*;
import java.nio.FloatBuffer;
import java.util.Collections;

/**
 * Spatial Scene depth: Depth Anything V2 Small (int8 ONNX export by onnx-community, Apache-2.0) run on the CPU with
 * ONNX Runtime. One inference per photo, about a second on a current phone; DepthMapManager caches the result, so the
 * session is opened for the call and closed again rather than holding ~100 MB in the wallpaper process.
 * The model returns relative inverse depth (larger = nearer), which is exactly the map's convention: white is near.
 */
final class MlDepth {
    static final String MODEL="models/depth_anything_v2_small_int8.onnx";
    /** Long side of the network input; both sides must be multiples of the ViT patch size. */
    static final int LONG_SIDE=518,PATCH=14;
    static final float[] MEAN={.485f,.456f,.406f},STD={.229f,.224f,.225f};

    /** Network input size for a photo: the long side LONG_SIDE, aspect kept, both sides rounded to PATCH. */
    static int[] inputSize(int width,int height){
        float s=LONG_SIDE/(float)Math.max(width,height);
        return new int[]{Math.max(PATCH,Math.round(width*s/PATCH)*PATCH),Math.max(PATCH,Math.round(height*s/PATCH)*PATCH)};
    }
    /** Writes the photo as a normalized CHW float tensor of the given size. */
    static float[] tensor(int[] argb,int w,int h){
        float[] t=new float[3*w*h];int plane=w*h;
        for(int i=0;i<plane;i++){int p=argb[i];
            t[i]=(((p>>16)&255)/255f-MEAN[0])/STD[0];t[plane+i]=(((p>>8)&255)/255f-MEAN[1])/STD[1];t[2*plane+i]=((p&255)/255f-MEAN[2])/STD[2];}
        return t;
    }
    /** Relative depth to 0..255 grey (white near), stretched between the 1st and 99th percentile so a few extreme
     *  pixels (sky, a lens flare) cannot flatten the rest. */
    static int[] grey(float[] depth){
        float[] sorted=depth.clone();java.util.Arrays.sort(sorted);
        float lo=sorted[(int)(sorted.length*.01f)],hi=sorted[Math.min(sorted.length-1,(int)(sorted.length*.99f))],range=Math.max(1e-6f,hi-lo);
        int[] out=new int[depth.length];
        for(int i=0;i<depth.length;i++){int v=Math.round(255*Math.max(0,Math.min(1,(depth[i]-lo)/range)));out[i]=0xff000000|v<<16|v<<8|v;}
        return out;
    }

    static Bitmap estimate(Context c,Bitmap image)throws IOException{
        int[] size=inputSize(image.getWidth(),image.getHeight());int w=size[0],h=size[1];
        Bitmap small=Bitmap.createScaledBitmap(image,w,h,true);int[] pixels=new int[w*h];small.getPixels(pixels,0,w,0,0,w,h);if(small!=image)small.recycle();
        long started=android.os.SystemClock.elapsedRealtime();
        try{
            OrtEnvironment env=OrtEnvironment.getEnvironment();
            try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){
                options.setIntraOpNumThreads(Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors())));
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                try(OrtSession session=env.createSession(model(c),options);
                    OnnxTensor input=OnnxTensor.createTensor(env,FloatBuffer.wrap(tensor(pixels,w,h)),new long[]{1,3,h,w});
                    OrtSession.Result result=session.run(Collections.singletonMap(session.getInputNames().iterator().next(),input))){
                    OnnxTensor out=(OnnxTensor)result.get(0);long[] shape=out.getInfo().getShape();
                    int oh=(int)shape[shape.length-2],ow=(int)shape[shape.length-1];
                    FloatBuffer fb=out.getFloatBuffer();float[] depth=new float[fb.remaining()];fb.get(depth);
                    if(depth.length!=ow*oh)throw new IOException("Unexpected depth shape");
                    Log.i("SoloDepth","Depth Anything "+w+"x"+h+" in "+(android.os.SystemClock.elapsedRealtime()-started)+" ms");
                    return Bitmap.createBitmap(grey(depth),ow,oh,Bitmap.Config.ARGB_8888);
                }
            }
        }catch(OrtException|RuntimeException|UnsatisfiedLinkError e){throw new IOException("Depth model failed",e);}
    }
    static byte[] model(Context c)throws IOException{
        try(InputStream in=c.getAssets().open(MODEL);ByteArrayOutputStream out=new ByteArrayOutputStream(28<<20)){
            byte[] b=new byte[1<<16];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();
        }
    }
    private MlDepth(){}
}
