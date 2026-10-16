package dev.solotilt.personal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Depth for video wallpapers: a depth map (MlDepth, single pass) for a keyframe about every second of the clip,
 * built once in the background and cached beside the photo maps (same "depth-<key>-" prefix, so pruning treats
 * them alike). The renderer swaps in the map of the keyframe nearest the playing position. Until the set is
 * ready the poster's map is used for the whole clip, as before.
 */
final class VideoDepth {
    static final long STEP_MS=1000;static final int MAX=24,LONG_SIDE=1024;
    static final ExecutorService BUILD=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"ParallaxiaVideoDepth");t.setPriority(Thread.MIN_PRIORITY);return t;});
    static final Set<String> building=new HashSet<>();

    static File index(Context c,String key){return new File(c.getFilesDir(),"depth-"+key+"-video-v1.txt");}
    static File map(Context c,String key,int i){return new File(c.getFilesDir(),"depth-"+key+"-video-v1-"+i+".png");}

    /** Keyframe times for a clip: about one a second, at least the first and the last frame, at most MAX. Pure, unit-tested. */
    static long[] times(long durationMs){
        if(durationMs<=0)return new long[]{0};
        int n=(int)Math.max(2,Math.min(MAX,durationMs/STEP_MS+1));long last=Math.max(0,durationMs-50);
        long[] t=new long[n];for(int i=0;i<n;i++)t[i]=Math.round(last*(double)i/(n-1));return t;
    }
    /** Index of the keyframe nearest ms. Pure, unit-tested. */
    static int nearest(long[] times,long ms){
        int best=0;for(int i=1;i<times.length;i++)if(Math.abs(times[i]-ms)<Math.abs(times[best]-ms))best=i;return best;
    }
    /** The keyframe times of a finished set, or null while there is none. */
    static long[] ready(Context c,String key){
        File f=index(c,key);if(!f.isFile())return null;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
            String[] parts=r.readLine().trim().split(",");long[] t=new long[parts.length];
            for(int i=0;i<t.length;i++){t[i]=Long.parseLong(parts[i]);if(!map(c,key,i).isFile())return null;}
            return t;
        }catch(IOException|RuntimeException e){return null;}
    }
    static Bitmap load(Context c,String key,int i){return BitmapFactory.decodeFile(map(c,key,i).getPath());}

    /** Starts building the set for the slot's clip unless it exists or is being built; done runs afterwards (worker thread). */
    static void ensure(Context c,String slot,String key,Runnable done){
        if(ready(c,key)!=null)return;
        synchronized(building){if(!building.add(key))return;}
        Context app=c.getApplicationContext();
        BUILD.execute(()->{
            try{build(app,ImageStore.videoFile(app,slot),key);if(done!=null)done.run();}
            catch(Exception|OutOfMemoryError e){Log.w("SoloDepth","Video depth failed",e);}
            finally{synchronized(building){building.remove(key);}}
        });
    }
    static void build(Context c,File video,String key)throws IOException{
        long started=android.os.SystemClock.elapsedRealtime();
        try(MediaMetadataRetriever r=new MediaMetadataRetriever()){
            r.setDataSource(video.getPath());
            long duration=ImageStore.number(r,MediaMetadataRetriever.METADATA_KEY_DURATION);
            int w=(int)ImageStore.number(r,MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),h=(int)ImageStore.number(r,MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if(ImageStore.number(r,MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)%180!=0){int t=w;w=h;h=t;}
            float s=w>0&&h>0?Math.min(1,LONG_SIDE/(float)Math.max(w,h)):1;
            long[] times=times(duration);StringBuilder line=new StringBuilder();
            for(int i=0;i<times.length;i++){
                Bitmap frame=w>0&&h>0?r.getScaledFrameAtTime(times[i]*1000,MediaMetadataRetriever.OPTION_CLOSEST,Math.max(1,Math.round(w*s)),Math.max(1,Math.round(h*s))):r.getFrameAtTime(times[i]*1000,MediaMetadataRetriever.OPTION_CLOSEST);
                if(frame==null)throw new IOException("No frame at "+times[i]+" ms");
                Bitmap depth;try{depth=MlDepth.estimate(c,frame,true);}finally{frame.recycle();}
                try{DepthMapManager.write(map(c,key,i),depth);}finally{depth.recycle();}
                line.append(i==0?"":",").append(times[i]);
            }
            // The index goes last, so a half-built set is never taken for a finished one.
            File tmp=new File(index(c,key).getPath()+".part");
            try(Writer out=new OutputStreamWriter(new FileOutputStream(tmp),StandardCharsets.UTF_8)){out.write(line.toString());}
            if(!tmp.renameTo(index(c,key)))throw new IOException("Cannot save the video depth index");
            CrashLog.note("Video depth: "+times.length+" keyframes in "+(android.os.SystemClock.elapsedRealtime()-started)+" ms");
        }catch(RuntimeException e){throw new IOException("This video cannot be read for depth",e);}
    }
    private VideoDepth(){}
}
