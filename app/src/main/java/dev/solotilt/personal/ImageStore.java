package dev.solotilt.personal;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;

final class ImageStore {
    static final Object LOCK=new Object();
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
    static boolean isRevisionKey(String key){return key!=null&&(key.startsWith("revision")||key.startsWith("depth_revision"));}
    static void importImage(Context c,Uri uri,String slot)throws IOException{
        Bitmap image=decode(ImageDecoder.createSource(c.getContentResolver(),uri));
        try{
            synchronized(LOCK){
                AtomicFile target=file(c,slot); FileOutputStream stream=null;
                try{stream=target.startWrite();if(!image.compress(Bitmap.CompressFormat.PNG,100,stream))throw new IOException("Image encoding failed");target.finishWrite(stream);}
                catch(Exception e){if(stream!=null)target.failWrite(stream);throw new IOException(e);}
            }
            prefs(c).edit().putBoolean(key("custom",slot),true).putLong(key("revision",slot),System.nanoTime()).apply();
        }finally{image.recycle();}
    }
    static void usePreset(Context c,String slot,int index){prefs(c).edit().putBoolean(key("custom",slot),false).putInt(key("preset",slot),index).putLong(key("revision",slot),System.nanoTime()).apply();}
    static void useCustom(Context c,String slot){prefs(c).edit().putBoolean(key("custom",slot),true).putLong(key("revision",slot),System.nanoTime()).apply();}
    /** Starts a slot from another slot's media (used when the lock screen gets its own wallpaper). */
    static void copyMedia(Context c,String from,String to)throws IOException{
        synchronized(LOCK){
            if(hasCustom(c,from)){byte[] data=file(c,from).readFully();AtomicFile target=file(c,to);FileOutputStream out=target.startWrite();try{out.write(data);target.finishWrite(out);}catch(IOException e){target.failWrite(out);throw e;}}
        }
        prefs(c).edit().putBoolean(key("custom",to),isCustom(c,from)).putInt(key("preset",to),presetIndex(c,from)).putLong(key("revision",to),System.nanoTime()).apply();
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
