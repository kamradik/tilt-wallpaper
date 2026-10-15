package dev.solotilt.personal;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import static dev.solotilt.personal.WallpaperConfig.Preset;

/**
 * Media and file side of presets. A preset owns a copy of its picture in files/presets/<id>.png (at most 2048 px,
 * as every slot photo is), so applying it restores the picture even after the slot moved on. A video slot keeps
 * its clip as files/presets/<id>.mp4 only when the clip is at most 50 MB; larger clips are saved as their poster
 * frame (a photo preset), because preset copies are never cleaned up behind the user's back.
 *
 * A .tilt file is a ZIP with preset.json and, optionally, photo.jpg. Videos are never exported, only their poster.
 * Import trusts nothing: entry count, sizes and names are bounded, nothing is written by entry name, the JSON is
 * clamped by the same parser as the config, unknown effects become Classic, and the photo is decoded at most 2048 px.
 */
final class Presets {
    static final long MAX_VIDEO_BYTES=50L*1024*1024;
    static final int MAX_ENTRIES=16,MAX_JSON=64*1024,MAX_PHOTO=16*1024*1024,MAX_TOTAL=24*1024*1024;
    static final String FORMAT="tilt-preset",JSON_ENTRY="preset.json",PHOTO_ENTRY="photo.jpg";
    static final String HOME=WallpaperConfig.HOME,LOCK=WallpaperConfig.LOCK,BOTH="both";

    static File dir(Context c){File d=new File(c.getFilesDir(),"presets");if(!d.isDirectory()&&!d.mkdirs())android.util.Log.w("TiltPresets","Cannot create "+d);return d;}
    static File photo(Context c,String id){return new File(dir(c),id+".png");}
    static File video(Context c,String id){return new File(dir(c),id+".mp4");}

    /** Snapshot of what a screen shows now: profile plus a private copy of its media. Off the main thread. */
    static Preset capture(Context c,WallpaperConfig config,String screen,String name)throws IOException{
        String slot=config.separateLock&&LOCK.equals(screen)?LOCK:HOME;
        Preset p=new Preset();p.id=WallpaperConfig.newId();p.name=WallpaperConfig.cleanName(name);p.created=System.currentTimeMillis();
        p.profile.copyFrom(config.profile(screen));
        if(ImageStore.isCustom(c,slot)){
            byte[] data;synchronized(ImageStore.LOCK){data=ImageStore.file(c,slot).readFully();}
            write(photo(c,p.id),data);p.media=Preset.PHOTO;
            File clip=ImageStore.videoFile(c,slot);
            if(ImageStore.isVideo(c,slot)&&clip.length()<=MAX_VIDEO_BYTES){
                File temp=new File(dir(c),p.id+".mp4.part");
                try{synchronized(ImageStore.LOCK){Files.copy(clip.toPath(),temp.toPath(),StandardCopyOption.REPLACE_EXISTING);}
                    if(!temp.renameTo(video(c,p.id)))throw new IOException("The video cannot be saved");p.media=Preset.VIDEO;}
                finally{if(temp.exists()&&!temp.delete())android.util.Log.w("TiltPresets","Cannot delete "+temp);}
            }
        }else{p.media=Preset.BUILTIN;p.builtin=ImageStore.presetIndex(c,slot);}
        return p;
    }
    /** Puts the preset's media on a slot (photo, clip or built-in background). Off the main thread. */
    static void applyMedia(Context c,Preset p,String slot)throws IOException{
        if(Preset.VIDEO.equals(p.media)&&video(c,p.id).isFile())ImageStore.importVideo(c,Uri.fromFile(video(c,p.id)),slot);
        else if(!Preset.BUILTIN.equals(p.media)&&photo(c,p.id).isFile())ImageStore.importImage(c,Uri.fromFile(photo(c,p.id)),slot);
        else ImageStore.usePreset(c,slot,p.builtin);
        DepthMapManager.prune(c);
    }
    /** Applies to HOME, LOCK or BOTH and saves the config. Applying to the lock screen alone gives it its own wallpaper. */
    static void apply(Context c,WallpaperConfig config,Preset p,String target)throws IOException{
        if(LOCK.equals(target)&&!config.separateLock){config.separateLock=true;config.lock.copyFrom(config.home);}
        List<String> slots=new ArrayList<>();
        if(!LOCK.equals(target))slots.add(HOME);
        if(LOCK.equals(target)||BOTH.equals(target)&&config.separateLock)slots.add(LOCK);
        for(String s:slots)config.profile(s).copyFrom(p.profile);
        config.save(c);
        for(String s:slots)applyMedia(c,p,s);
    }
    static void delete(Context c,Preset p){for(File f:new File[]{photo(c,p.id),video(c,p.id)})if(f.exists()&&!f.delete())android.util.Log.w("TiltPresets","Cannot delete "+f);}
    static Preset duplicate(Context c,Preset p,String name)throws IOException{
        Preset copy=p.copy();copy.id=WallpaperConfig.newId();copy.name=WallpaperConfig.cleanName(name);copy.created=System.currentTimeMillis();
        if(photo(c,p.id).isFile())Files.copy(photo(c,p.id).toPath(),photo(c,copy.id).toPath(),StandardCopyOption.REPLACE_EXISTING);
        if(video(c,p.id).isFile())Files.copy(video(c,p.id).toPath(),video(c,copy.id).toPath(),StandardCopyOption.REPLACE_EXISTING);
        return copy;
    }
    /** Deletes preset files no saved preset refers to (after an interrupted save or a config reset). */
    static void prune(Context c,WallpaperConfig config){
        File[] files=dir(c).listFiles();if(files==null)return;
        for(File f:files){String id=f.getName().replaceAll("\\..*$","");if(config.preset(id)==null&&!f.delete())android.util.Log.w("TiltPresets","Cannot delete "+f);}
    }
    static Bitmap thumbnail(Context c,Preset p,int side){
        try{
            if(!Preset.BUILTIN.equals(p.media)&&photo(c,p.id).isFile())return ImageDecoder.decodeBitmap(ImageDecoder.createSource(photo(c,p.id)),(d,info,s)->{
                int w=info.getSize().getWidth(),h=info.getSize().getHeight();float k=Math.min(1,side/(float)Math.max(1,Math.min(w,h)));
                d.setTargetSize(Math.max(1,Math.round(w*k)),Math.max(1,Math.round(h*k)));d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});
            return ImageStore.preset(p.builtin,side/720f);
        }catch(IOException|RuntimeException|OutOfMemoryError e){return null;}
    }
    static void write(File f,byte[] data)throws IOException{
        AtomicFile a=new AtomicFile(f);FileOutputStream out=a.startWrite();
        try{out.write(data);a.finishWrite(out);}catch(IOException e){a.failWrite(out);throw e;}
    }

    // ---------------------------------------------------------------- .tilt files
    /** The preset as stored in a .tilt file: no id or file references, only what describes the look. */
    static JSONObject exportJson(Preset p)throws JSONException{
        boolean photo=!Preset.BUILTIN.equals(p.media);
        return new JSONObject().put("format",FORMAT).put("version",1).put("name",p.name).put("media",photo?Preset.PHOTO:Preset.BUILTIN).put("builtin",p.builtin)
            .put("profile",WallpaperConfig.writeProfile(p.profile));
    }
    static void export(Context c,Preset p,OutputStream target)throws IOException{
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(target))){
            zip.putNextEntry(new ZipEntry(JSON_ENTRY));zip.write(exportJson(p).toString(2).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            if(!Preset.BUILTIN.equals(p.media)&&photo(c,p.id).isFile()){
                Bitmap b=ImageStore.decode(ImageDecoder.createSource(photo(c,p.id)));
                try{zip.putNextEntry(new ZipEntry(PHOTO_ENTRY));if(!b.compress(Bitmap.CompressFormat.JPEG,92,zip))throw new IOException("Image encoding failed");zip.closeEntry();}
                finally{b.recycle();}
            }
        }catch(JSONException e){throw new IOException(e);}
    }

    /** The two parts of a .tilt file, read with every limit enforced. Pure Java, unit-tested. */
    static final class Tilt{byte[] json,photo;}
    static Tilt read(InputStream in)throws IOException{
        Tilt t=new Tilt();int entries=0;long total=0;Set<String> names=new HashSet<>();
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(in))){
            for(ZipEntry e;(e=zip.getNextEntry())!=null;){
                if(++entries>MAX_ENTRIES)throw new IOException("Too many entries");
                String name=e.getName();
                // Zip-slip: nothing is ever written by entry name, but a crafted path marks a crafted file.
                if(name==null||name.isEmpty()||name.contains("..")||name.startsWith("/")||name.contains("\\")||name.contains(":")||name.indexOf('\0')>=0)throw new IOException("Unsafe entry name");
                if(!names.add(name))throw new IOException("Duplicate entry");
                int limit=JSON_ENTRY.equals(name)?MAX_JSON:PHOTO_ENTRY.equals(name)?MAX_PHOTO:MAX_TOTAL;
                if(e.getSize()>limit)throw new IOException("Entry too large");
                byte[] data=bounded(zip,Math.min(limit,MAX_TOTAL-total));total+=data.length;
                if(JSON_ENTRY.equals(name))t.json=data;else if(PHOTO_ENTRY.equals(name))t.photo=data;  // other entries are read (bounded) and ignored
            }
        }catch(ZipException e){throw new IOException("Not a .tilt file",e);}
        if(t.json==null)throw new IOException("Not a .tilt file");
        return t;
    }
    /** Reads one entry, failing as soon as it exceeds the limit (sizes in the header can lie). */
    static byte[] bounded(InputStream in,long limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[16384];long n=0;
        for(int r;(r=in.read(buffer))!=-1;){n+=r;if(n>limit)throw new IOException("Entry too large");out.write(buffer,0,r);}
        return out.toByteArray();
    }
    /** preset.json to a preset with a fresh id; media says PHOTO only if the file brought a photo. */
    static Preset parse(byte[] json,boolean hasPhoto)throws IOException{
        try{
            JSONObject o=new JSONObject(new String(json,StandardCharsets.UTF_8));
            if(!FORMAT.equals(o.optString("format"))||o.optJSONObject("profile")==null)throw new IOException("Not a .tilt file");
            o.remove("id");Preset p=Preset.parse(o);if(p==null)throw new IOException("Not a .tilt file");
            p.media=hasPhoto&&Preset.PHOTO.equals(o.optString("media"))?Preset.PHOTO:Preset.BUILTIN;p.created=System.currentTimeMillis();return p;
        }catch(JSONException e){throw new IOException("Not a .tilt file",e);}
    }
    /** Imports a .tilt file as a new preset (not yet added to the config). Off the main thread. */
    static Preset importTilt(Context c,Uri uri)throws IOException{
        Tilt t;try(InputStream in=c.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot open");t=read(in);}
        Preset p=parse(t.json,t.photo!=null);
        if(Preset.PHOTO.equals(p.media)){
            Bitmap b;try{b=ImageStore.decode(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(t.photo)));}catch(IOException|RuntimeException e){throw new IOException("The photo cannot be read",e);}
            try{ByteArrayOutputStream png=new ByteArrayOutputStream();if(!b.compress(Bitmap.CompressFormat.PNG,100,png))throw new IOException("Image encoding failed");write(photo(c,p.id),png.toByteArray());}
            finally{b.recycle();}
        }
        return p;
    }
    /** A file name for export: the preset name without characters file systems reject. */
    static String fileName(Preset p){String s=p.name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();return (s.isEmpty()?"preset":s)+".tilt";}
    private Presets(){}
}
