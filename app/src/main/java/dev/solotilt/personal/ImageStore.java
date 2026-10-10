package dev.solotilt.personal;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;

final class ImageStore {
    static final Object LOCK=new Object();
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("wallpaper",0);}
    static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"image.png"));}
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
    static void importImage(Context c,Uri uri)throws IOException{
        Bitmap image=decode(ImageDecoder.createSource(c.getContentResolver(),uri));
        try{
            synchronized(LOCK){
                AtomicFile target=file(c); FileOutputStream stream=null;
                try{stream=target.startWrite();if(!image.compress(Bitmap.CompressFormat.PNG,100,stream))throw new IOException("Image encoding failed");target.finishWrite(stream);}
                catch(Exception e){if(stream!=null)target.failWrite(stream);throw new IOException(e);}
            }
            prefs(c).edit().putBoolean("custom",true).putLong("revision",System.nanoTime()).apply();
        }finally{image.recycle();}
    }
    static Bitmap load(Context c)throws IOException{
        if(prefs(c).getBoolean("custom",false)){
            synchronized(LOCK){return decode(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(file(c).readFully())));}
        }
        return preset(prefs(c).getInt("preset",0));
    }
    static boolean hasCustom(Context c){return file(c).getBaseFile().exists();}
    /** Small copy of the saved photo for the picker tile; null when none is saved. */
    static Bitmap thumbnail(Context c,int side)throws IOException{
        if(!hasCustom(c))return null;
        synchronized(LOCK){return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file(c).getBaseFile()),(decoder,info,s)->{
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
