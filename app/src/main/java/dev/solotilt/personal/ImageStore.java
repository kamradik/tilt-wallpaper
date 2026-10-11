package dev.solotilt.personal;
import android.content.*;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;

final class ImageStore {
    static final Object LOCK=new Object();
    static final long MAX_VIDEO_MS=3*60_000L,MAX_VIDEO_BYTES=300L*1024*1024;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("wallpaper",0);}
    static Bitmap decode(ImageDecoder.Source source) throws IOException {
        return ImageDecoder.decodeBitmap(source,(decoder,info,s)->{
            int w=info.getSize().getWidth(),h=info.getSize().getHeight();
            float scale=Math.min(1,2048f/Math.max(w,h));
            decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
            decoder.setOnPartialImageListener(e->false);
        });
    }
    /** Home keeps the pre-slot names (image.png, custom, preset, revision) so existing installs carry over unchanged. */
    static String key(String base,String slot){return WallpaperConfig.LOCK.equals(slot)?base+"_lock":base;}
    static AtomicFile file(Context c,String slot){return new AtomicFile(new File(c.getFilesDir(),WallpaperConfig.LOCK.equals(slot)?"image_lock.png":"image.png"));}
    static boolean isCustom(Context c,String slot){return prefs(c).getBoolean(key("custom",slot),false)&&hasCustom(c,slot);}
    static int presetIndex(Context c,String slot){return Math.floorMod(prefs(c).getInt(key("preset",slot),WallpaperConfig.LOCK.equals(slot)?1:0),3);}
    static long revision(Context c,String slot){return prefs(c).getLong(key("revision",slot),0);}
    // Every media change (photo, preset, video) bumps "revision"; the "video" kind flag is written in the same edit,
    // so it is deliberately not a reload key: listening to both would prepare twice per import.
    static boolean isRevisionKey(String key){return key!=null&&(key.startsWith("revision")||key.startsWith("depth_revision"));}
    /** A video slot plays videoFile and keeps its poster frame as the slot photo, so stills, thumbnails and depth need no video path. */
    static boolean isVideo(Context c,String slot){return prefs(c).getBoolean(key("video",slot),false)&&isCustom(c,slot)&&videoFile(c,slot).isFile();}
    static String videoName(String slot){return WallpaperConfig.LOCK.equals(slot)?"video_lock.mp4":"video.mp4";}
    static File videoFile(Context c,String slot){return new File(c.getFilesDir(),videoName(slot));}
    /** Why a clip cannot be a wallpaper, or null when it can; pure so the limits are unit-tested. */
    static String videoProblem(boolean hasVideo,long durationMs,long bytes){
        if(bytes>MAX_VIDEO_BYTES)return "The video is larger than 300 MB";
        if(!hasVideo)return "The file has no video track";
        if(durationMs<=0)return "The video length cannot be read";
        if(durationMs>MAX_VIDEO_MS)return "The video is longer than 3 minutes";
        return null;
    }
    /**
     * Copies a clip into private storage for the slot; call off the main thread. The copy is validated, its first sync
     * frame becomes the slot photo (poster), then the clip replaces the old one by rename, so a running player keeps
     * reading the previous file until the revision bump makes engines reload. Throws IOException with a readable message.
     */
    static void importVideo(Context c,Uri uri,String slot)throws IOException{
        File target=videoFile(c,slot),temp=new File(c.getFilesDir(),target.getName()+".part");Bitmap poster=null;
        try{
            long bytes=0;
            try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){
                if(in==null)throw new IOException("The video cannot be opened");byte[] buffer=new byte[65536];int n;
                while((n=in.read(buffer))!=-1){bytes+=n;if(bytes>MAX_VIDEO_BYTES)throw new IOException(videoProblem(true,1,bytes));out.write(buffer,0,n);}
            }
            try(MediaMetadataRetriever r=new MediaMetadataRetriever()){
                r.setDataSource(temp.getPath());
                String problem=videoProblem("yes".equals(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)),number(r,MediaMetadataRetriever.METADATA_KEY_DURATION),bytes);
                if(problem!=null)throw new IOException(problem);
                int w=(int)number(r,MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),h=(int)number(r,MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                if(number(r,MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)%180!=0){int t=w;w=h;h=t;} // the retriever returns upright frames
                float scale=w>0&&h>0?Math.min(1,2048f/Math.max(w,h)):1;
                poster=w>0&&h>0?r.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)))
                    :r.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }catch(RuntimeException e){throw new IOException("This video format is not supported",e);}
            if(poster==null)throw new IOException("This video format is not supported");
            synchronized(LOCK){
                writePhoto(file(c,slot),poster);
                if(!temp.renameTo(target))throw new IOException("The video cannot be saved");
            }
            prefs(c).edit().putBoolean(key("video",slot),true).putBoolean(key("custom",slot),true).putLong(key("revision",slot),System.nanoTime()).apply();
        }finally{if(poster!=null)poster.recycle();if(temp.exists()&&!temp.delete())android.util.Log.w("SoloVideo","Cannot delete "+temp);}
    }
    static long number(MediaMetadataRetriever r,int key){try{String v=r.extractMetadata(key);return v==null?0:Long.parseLong(v.trim());}catch(NumberFormatException e){return 0;}}
    static void writePhoto(AtomicFile target,Bitmap image)throws IOException{
        FileOutputStream stream=null;
        try{stream=target.startWrite();if(!image.compress(Bitmap.CompressFormat.PNG,100,stream))throw new IOException("Image encoding failed");target.finishWrite(stream);}
        catch(Exception e){if(stream!=null)target.failWrite(stream);throw e instanceof IOException?(IOException)e:new IOException(e);}
    }
    /** A slot switched back to a photo drops its clip: nothing can reach it again (the custom photo is now a still). */
    static void dropVideo(Context c,String slot){synchronized(LOCK){File f=videoFile(c,slot);if(f.exists()&&!f.delete())android.util.Log.w("SoloVideo","Cannot delete "+f);}}
    static void importImage(Context c,Uri uri,String slot)throws IOException{
        Bitmap image=decode(ImageDecoder.createSource(c.getContentResolver(),uri));
        try{
            synchronized(LOCK){writePhoto(file(c,slot),image);}
            prefs(c).edit().putBoolean(key("custom",slot),true).putBoolean(key("video",slot),false).putLong(key("revision",slot),System.nanoTime()).apply();dropVideo(c,slot);
        }finally{image.recycle();}
    }
    static void usePreset(Context c,String slot,int index){prefs(c).edit().putBoolean(key("custom",slot),false).putBoolean(key("video",slot),false).putInt(key("preset",slot),index).putLong(key("revision",slot),System.nanoTime()).apply();dropVideo(c,slot);}
    static void useCustom(Context c,String slot){prefs(c).edit().putBoolean(key("custom",slot),true).putBoolean(key("video",slot),false).putLong(key("revision",slot),System.nanoTime()).apply();dropVideo(c,slot);}
    /** Starts a slot from another slot's media (used when the lock screen gets its own wallpaper). */
    static void copyMedia(Context c,String from,String to)throws IOException{
        boolean video=isVideo(c,from);
        synchronized(LOCK){
            if(hasCustom(c,from)){byte[] data=file(c,from).readFully();AtomicFile target=file(c,to);FileOutputStream out=target.startWrite();try{out.write(data);target.finishWrite(out);}catch(IOException e){target.failWrite(out);throw e;}}
            File clip=videoFile(c,to);
            if(video){File temp=new File(c.getFilesDir(),clip.getName()+".part");
                try{java.nio.file.Files.copy(videoFile(c,from).toPath(),temp.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);if(!temp.renameTo(clip))throw new IOException("The video cannot be copied");}
                finally{if(temp.exists()&&!temp.delete())android.util.Log.w("SoloVideo","Cannot delete "+temp);}
            }else if(clip.exists()&&!clip.delete())android.util.Log.w("SoloVideo","Cannot delete "+clip);
        }
        prefs(c).edit().putBoolean(key("custom",to),isCustom(c,from)).putBoolean(key("video",to),video).putInt(key("preset",to),presetIndex(c,from)).putLong(key("revision",to),System.nanoTime()).apply();
    }
    static Bitmap load(Context c,String slot)throws IOException{
        if(isCustom(c,slot)){
            synchronized(LOCK){return decode(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(file(c,slot).readFully())));}
        }
        return preset(presetIndex(c,slot));
    }
    static boolean hasCustom(Context c,String slot){return file(c,slot).getBaseFile().exists();}
    /** Small copy of the slot's saved photo for picker tiles; null when none is saved. */
    static Bitmap thumbnail(Context c,String slot,int side)throws IOException{
        if(!hasCustom(c,slot))return null;
        synchronized(LOCK){return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file(c,slot).getBaseFile()),(decoder,info,s)->{
            int w=info.getSize().getWidth(),h=info.getSize().getHeight();float scale=Math.min(1,side/(float)Math.min(w,h));
            decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });}
    }
    static Bitmap preset(int index){return preset(index,1);}
    /** Draws the 720x1560 preset at the given scale, so thumbnails never allocate the full-size bitmap. */
    static Bitmap preset(int index,float scale){
        int w=720,h=1560;
        Bitmap b=Bitmap.createBitmap(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.scale(scale,scale);Paint p=new Paint(3);
        int[][] palettes={{0xff260b27,0xffff6f93,0xffffddd5},{0xff07163a,0xff377dcb,0xff7ff1e0},{0xff4b0c0a,0xffff6427,0xffffd29c}};
        int[] colors=palettes[Math.floorMod(index,3)];
        p.setShader(new LinearGradient(0,0,w,h,colors,null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
        for(int i=0;i<6;i++){
            Path path=new Path();float y=-600+i*350;
            path.moveTo(-100,y);path.cubicTo(1100,y+70,-300,y+950,950,y+650);path.lineTo(950,y+1040);path.cubicTo(-340,y+1240,1230,y+280,-100,y+180);path.close();
            p.setShader(new LinearGradient(0,y,w,y+850,new int[]{colors[0],colors[1],colors[2],colors[1]},new float[]{0,.4f,.58f,1},Shader.TileMode.CLAMP));
            c.drawPath(path,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0x77fff2eb);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        }
        return b;
    }
}
