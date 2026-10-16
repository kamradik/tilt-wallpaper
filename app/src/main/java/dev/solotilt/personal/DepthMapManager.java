package dev.solotilt.personal;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;

/** Shared preparation/cache for Depth and Focus. Never called from doFrame. */
final class DepthMapManager {
    interface DepthProvider { Bitmap estimate(Bitmap image)throws IOException; }
    // Fallback when the depth model cannot run (no native library for the ABI, out of memory): a spatial/luminance
    // prior, approximate rather than semantic depth. Normally MlDepth (Depth Anything V2) provides the map.
    static final DepthProvider PROVIDER=image->{
        int w=image.getWidth(),h=image.getHeight();float ratio=Math.min(1,320f/Math.max(w,h));
        w=Math.max(1,Math.round(w*ratio));h=Math.max(1,Math.round(h*ratio));
        Bitmap small=Bitmap.createScaledBitmap(image,w,h,true);int[] pixels=new int[w*h];small.getPixels(pixels,0,w,0,0,w,h);if(small!=image)small.recycle();
        float[] luma=new float[w*h],depth=new float[w*h],smoothed=new float[w*h];
        for(int i=0;i<pixels.length;i++)luma[i]=(((pixels[i]>>16)&255)*.2126f+((pixels[i]>>8)&255)*.7152f+(pixels[i]&255)*.0722f)/255;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=y*w+x;float dx=(x+.5f)/w-.5f,dy=(y+.5f)/h-.52f;
            float center=(float)Math.exp(-(dx*dx*7+dy*dy*4));float detail=Math.abs(luma[i]-luma[y*w+Math.max(0,x-2)]);
            depth[i]=.65f*center+.2f*(y+.5f)/h+.12f*luma[i]+.03f*detail;
        }
        float min=1,max=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=y*w+x;float sum=0,total=0;
            for(int oy=-1;oy<=1;oy++)for(int ox=-1;ox<=1;ox++){int j=Math.max(0,Math.min(h-1,y+oy))*w+Math.max(0,Math.min(w-1,x+ox));float weight=1/(1+30*Math.abs(luma[i]-luma[j]));sum+=depth[j]*weight;total+=weight;}
            smoothed[i]=sum/total;min=Math.min(min,smoothed[i]);max=Math.max(max,smoothed[i]);
        }
        for(int i=0;i<pixels.length;i++){int v=Math.round(255*(smoothed[i]-min)/Math.max(.001f,max-min));pixels[i]=0xff000000|v<<16|v<<8|v;}
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    };
    static String key(Context c,String slot){return ImageStore.isCustom(c,slot)?ImageStore.key("custom",slot)+"-"+ImageStore.revision(c,slot):"preset-"+ImageStore.presetIndex(c,slot);}
    static File file(Context c,String key,boolean manual){return new File(c.getFilesDir(),"depth-"+key+(manual?"-manual":"-approx-v1")+".png");}
    static File plateFile(Context c,String key){return new File(c.getFilesDir(),"depth-"+key+"-plate-v1.jpg");}
    /** The photo's background plate (Inpaint), when one was made; null otherwise. */
    static Bitmap plate(Context c,String key){File f=plateFile(c,key);return f.isFile()?BitmapFactory.decodeFile(f.getPath()):null;}
    static File mlFile(Context c,String key){return new File(c.getFilesDir(),"depth-"+key+"-ml-v4.png");}
    static boolean manual(Context c,String key){return file(c,key,true).isFile();}
    /** For previews: the map already prepared for this photo, or the quick approximation; never runs the network,
     *  which takes seconds and belongs to the wallpaper itself. */
    static synchronized Bitmap cached(Context c,Bitmap image,String key)throws IOException{
        if(manual(c,key)){Bitmap b=BitmapFactory.decodeFile(file(c,key,true).getPath());if(b!=null)return depthOnly(b);}
        File ml=mlFile(c,key);if(ml.isFile()){Bitmap b=BitmapFactory.decodeFile(ml.getPath());if(b!=null)return b;}
        return depthOnly(PROVIDER.estimate(image));
    }
    /** The photo's depth map: an imported one, else the model's, else (model unavailable) the approximation. Cached. */
    static synchronized Bitmap load(Context c,Bitmap image,String key)throws IOException{
        if(manual(c,key)){Bitmap cached=BitmapFactory.decodeFile(file(c,key,true).getPath());if(cached!=null)return depthOnly(cached);}
        File ml=mlFile(c,key);
        if(ml.isFile()){Bitmap cached=BitmapFactory.decodeFile(ml.getPath());if(cached!=null)return cached;}
        try{Bitmap result=MlDepth.estimate(c,image);try{write(ml,result);}catch(IOException e){result.recycle();throw e;}
            // With a subject, paint the background behind it once, for the depth effects to show when it moves.
            try{Bitmap plate=Inpaint.plate(c,image,result);if(plate!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(plateFile(c,key))){plate.compress(Bitmap.CompressFormat.JPEG,92,out);}finally{plate.recycle();}}}
            catch(IOException|OutOfMemoryError e){android.util.Log.w("SoloDepth","No background plate",e);}
            return result;}
        catch(IOException|OutOfMemoryError e){android.util.Log.w("SoloDepth","Depth model unavailable, using the approximation",e);}
        File f=file(c,key,false);if(f.isFile()){Bitmap cached=BitmapFactory.decodeFile(f.getPath());if(cached!=null)return depthOnly(cached);}
        Bitmap result=PROVIDER.estimate(image);try{write(f,result);}catch(IOException e){result.recycle();throw e;}return depthOnly(result);
    }
    /** Grey maps (imported, approximate) carry no subject mask: depth stays in R, G and B are cleared so the shaders
     *  never read their grey as a mask (MlDepth marks a real mask with B = 255). */
    static Bitmap depthOnly(Bitmap grey){
        int w=grey.getWidth(),h=grey.getHeight();int[] px=new int[w*h];grey.getPixels(px,0,w,0,0,w,h);
        for(int i=0;i<px.length;i++)px[i]=0xff000000|(px[i]&0xff0000);
        Bitmap out=Bitmap.createBitmap(px,w,h,Bitmap.Config.ARGB_8888);if(out!=grey)grey.recycle();return out;
    }
    static void importDepth(Context c,Uri uri,String slot)throws IOException{
        String key=key(c,slot);Bitmap decoded=ImageStore.decode(ImageDecoder.createSource(c.getContentResolver(),uri));
        Bitmap scaled=Bitmap.createScaledBitmap(decoded,Math.max(1,Math.round(decoded.getWidth()*Math.min(1,512f/Math.max(decoded.getWidth(),decoded.getHeight())))),Math.max(1,Math.round(decoded.getHeight()*Math.min(1,512f/Math.max(decoded.getWidth(),decoded.getHeight())))),true);
        try{write(file(c,key,true),scaled);}finally{if(scaled!=decoded)scaled.recycle();decoded.recycle();}
        ImageStore.prefs(c).edit().putLong(ImageStore.key("depth_revision",slot),System.nanoTime()).apply();
    }
    static void write(File f,Bitmap bitmap)throws IOException{AtomicFile a=new AtomicFile(f);FileOutputStream out=null;try{out=a.startWrite();if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Depth encode");a.finishWrite(out);}catch(IOException e){if(out!=null)a.failWrite(out);throw e;}}
    /** Deletes depth maps of photos no slot shows any more. */
    static void prune(Context c){String home="depth-"+key(c,WallpaperConfig.HOME)+"-",lock="depth-"+key(c,WallpaperConfig.LOCK)+"-";File[] files=c.getFilesDir().listFiles();if(files!=null)for(File f:files)if(f.getName().startsWith("depth-")&&!f.getName().startsWith(home)&&!f.getName().startsWith(lock))f.delete();}
}
