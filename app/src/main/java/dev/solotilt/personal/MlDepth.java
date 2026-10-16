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
 *
 * Three steps make the map better than one plain inference:
 * - the input is LONG_SIDE (588) rather than the training size 518, for a little more detail without leaving the
 *   range the network was trained on;
 * - the photo also runs mirrored and the two answers are averaged (test-time augmentation), cancelling much of the
 *   one-sided noise;
 * - the map is enlarged to REFINED and its edges are snapped to the photo's with a guided filter, so a subject's
 *   outline moves with the subject instead of carrying a halo of background when the phone tilts.
 */
final class MlDepth {
    static final String MODEL="models/depth_anything_v2_small_int8.onnx";
    /** Long side of the network input; both sides must be multiples of the ViT patch size. */
    static final int LONG_SIDE=588,PATCH=14,REFINED=1024;
    /** Guided filter window (pixels of the refined map) and regularization: only strong photo edges move the depth. */
    static final int RADIUS=8;static final float EPS=.01f;
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
    /** Relative depth to 0..1, stretched between the 1st and 99th percentile so a few extreme pixels (sky, a lens
     *  flare) cannot flatten the rest. */
    static float[] normalize(float[] depth){
        float[] sorted=depth.clone();java.util.Arrays.sort(sorted);
        float lo=sorted[(int)(sorted.length*.01f)],hi=sorted[Math.min(sorted.length-1,(int)(sorted.length*.99f))],range=Math.max(1e-6f,hi-lo);
        float[] out=new float[depth.length];for(int i=0;i<depth.length;i++)out[i]=Math.max(0,Math.min(1,(depth[i]-lo)/range));
        return out;
    }
    /** Relative depth to 0..255 grey, white near. */
    static int[] grey(float[] depth){return greyOf(normalize(depth));}
    /** 0..1 values to 0..255 grey. */
    static int[] greyOf(float[] unit){int[] out=new int[unit.length];for(int i=0;i<unit.length;i++){int v=Math.round(255*Math.max(0,Math.min(1,unit[i])));out[i]=0xff000000|v<<16|v<<8|v;}return out;}
    /** Mirrors a w x h image left to right (ARGB pixels or values). */
    static int[] mirror(int[] a,int w,int h){int[] o=new int[a.length];for(int y=0;y<h;y++)for(int x=0;x<w;x++)o[y*w+x]=a[y*w+w-1-x];return o;}
    static float[] mirror(float[] a,int w,int h){float[] o=new float[a.length];for(int y=0;y<h;y++)for(int x=0;x<w;x++)o[y*w+x]=a[y*w+w-1-x];return o;}
    /** Bilinear resize of a w x h value grid to tw x th (pixel centres aligned). */
    static float[] resize(float[] a,int w,int h,int tw,int th){
        float[] o=new float[tw*th];
        for(int y=0;y<th;y++){float fy=Math.max(0,Math.min(h-1,(y+.5f)*h/th-.5f));int y0=(int)fy,y1=Math.min(h-1,y0+1);float ty=fy-y0;
            for(int x=0;x<tw;x++){float fx=Math.max(0,Math.min(w-1,(x+.5f)*w/tw-.5f));int x0=(int)fx,x1=Math.min(w-1,x0+1);float tx=fx-x0;
                o[y*tw+x]=(a[y0*w+x0]*(1-tx)+a[y0*w+x1]*tx)*(1-ty)+(a[y1*w+x0]*(1-tx)+a[y1*w+x1]*tx)*ty;}}
        return o;
    }
    /** Mean over the (2r+1)^2 window around each pixel, clipped at the borders; summed-area table, O(pixels). */
    static float[] box(float[] a,int w,int h,int r){
        double[] s=new double[(w+1)*(h+1)];
        for(int y=0;y<h;y++){double row=0;for(int x=0;x<w;x++){row+=a[y*w+x];s[(y+1)*(w+1)+x+1]=s[y*(w+1)+x+1]+row;}}
        float[] o=new float[w*h];
        for(int y=0;y<h;y++){int y0=Math.max(0,y-r),y1=Math.min(h,y+r+1);for(int x=0;x<w;x++){int x0=Math.max(0,x-r),x1=Math.min(w,x+r+1);
            o[y*w+x]=(float)((s[y1*(w+1)+x1]-s[y0*(w+1)+x1]-s[y1*(w+1)+x0]+s[y0*(w+1)+x0])/((x1-x0)*(y1-y0)));}}
        return o;
    }
    /** Guided filter (He, Sun, Tang 2010): p smoothed while following the edges of guide I. Both 0..1, w x h. */
    static float[] guided(float[] guide,float[] p,int w,int h,int r,float eps){
        int n=w*h;float[] ip=new float[n],ii=new float[n];for(int i=0;i<n;i++){ip[i]=guide[i]*p[i];ii[i]=guide[i]*guide[i];}
        float[] mI=box(guide,w,h,r),mP=box(p,w,h,r),mIP=box(ip,w,h,r),mII=box(ii,w,h,r),a=new float[n],b=new float[n];
        for(int i=0;i<n;i++){float var=mII[i]-mI[i]*mI[i],cov=mIP[i]-mI[i]*mP[i];a[i]=cov/(var+eps);b[i]=mP[i]-a[i]*mI[i];}
        float[] mA=box(a,w,h,r),mB=box(b,w,h,r),q=new float[n];
        for(int i=0;i<n;i++)q[i]=Math.max(0,Math.min(1,mA[i]*guide[i]+mB[i]));
        return q;
    }
    /** Luminance 0..1 of ARGB pixels. */
    static float[] luma(int[] argb){float[] o=new float[argb.length];for(int i=0;i<argb.length;i++){int p=argb[i];o[i]=(((p>>16)&255)*.2126f+((p>>8)&255)*.7152f+(p&255)*.0722f)/255f;}return o;}
    /** Size of the refined map: the photo's aspect, the long side REFINED (never larger than the photo). */
    static int[] refinedSize(int width,int height){
        float s=Math.min(1,REFINED/(float)Math.max(width,height));return new int[]{Math.max(1,Math.round(width*s)),Math.max(1,Math.round(height*s))};
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
                try(OrtSession session=env.createSession(model(c),options)){
                    String name=session.getInputNames().iterator().next();int[] shape=new int[2];
                    float[] plain=infer(env,session,name,pixels,w,h,shape);
                    float[] mirrored=mirror(infer(env,session,name,mirror(pixels,w,h),w,h,shape),shape[0],shape[1]);
                    long inferred=android.os.SystemClock.elapsedRealtime()-started;
                    // Each pass is stretched on its own before averaging, so a different offset or scale cannot bias one side.
                    float[] a=normalize(plain),b=normalize(mirrored),depth=new float[a.length];for(int i=0;i<a.length;i++)depth[i]=(a[i]+b[i])*.5f;
                    int[] out=refinedSize(image.getWidth(),image.getHeight());int rw=out[0],rh=out[1];
                    Bitmap guideImage=Bitmap.createScaledBitmap(image,rw,rh,true);int[] guidePixels=new int[rw*rh];guideImage.getPixels(guidePixels,0,rw,0,0,rw,rh);if(guideImage!=image)guideImage.recycle();
                    float[] refined=guided(luma(guidePixels),resize(depth,shape[0],shape[1],rw,rh),rw,rh,RADIUS,EPS);
                    String timing="Depth Anything "+w+"x"+h+" x2 in "+inferred+" ms, refined to "+rw+"x"+rh+" in "+(android.os.SystemClock.elapsedRealtime()-started-inferred)+" ms";
                    Log.i("SoloDepth",timing);CrashLog.note(timing); // shown in Diagnostics, so the real speed on a phone is visible
                    return Bitmap.createBitmap(greyOf(refined),rw,rh,Bitmap.Config.ARGB_8888);
                }
            }
        }catch(OrtException|RuntimeException|UnsatisfiedLinkError e){throw new IOException("Depth model failed",e);}
    }
    /** One inference; shape receives {width, height} of the depth output. */
    static float[] infer(OrtEnvironment env,OrtSession session,String name,int[] pixels,int w,int h,int[] shape)throws OrtException,IOException{
        try(OnnxTensor input=OnnxTensor.createTensor(env,FloatBuffer.wrap(tensor(pixels,w,h)),new long[]{1,3,h,w});
            OrtSession.Result result=session.run(Collections.singletonMap(name,input))){
            OnnxTensor out=(OnnxTensor)result.get(0);long[] s=out.getInfo().getShape();
            shape[0]=(int)s[s.length-1];shape[1]=(int)s[s.length-2];
            FloatBuffer fb=out.getFloatBuffer();float[] depth=new float[fb.remaining()];fb.get(depth);
            if(depth.length!=shape[0]*shape[1])throw new IOException("Unexpected depth shape");
            return depth;
        }
    }
    static byte[] model(Context c)throws IOException{
        try(InputStream in=c.getAssets().open(MODEL);ByteArrayOutputStream out=new ByteArrayOutputStream(28<<20)){
            byte[] b=new byte[1<<16];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();
        }
    }
    private MlDepth(){}
}
