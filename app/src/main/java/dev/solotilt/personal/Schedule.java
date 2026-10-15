package dev.solotilt.personal;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/**
 * Time-of-day presets and "photo of the day", applied without a background service: the wallpaper engine pokes
 * check() whenever it becomes visible (and on unlock). The check compares a key for the active window and for today's
 * photo with the keys last applied, so most pokes cost one config parse on a worker thread and write nothing.
 * Changes go through the normal config and media writes, so the engines' existing listeners reload the renderer.
 *
 * When a screen has both a time window and photo rotation, the window's preset sets the effect and the rotation
 * owns the photo.
 */
final class Schedule {
    static final int MAX_WINDOWS=4,DAY=24*60;
    static final String STATE_WINDOW="sched_window_",STATE_PHOTO="sched_photo_",STATE_INDEX="sched_index_",UNLOCKS="sched_unlocks";

    /** One time window: from this minute of the day until the next window starts (wrapping past midnight). */
    static final class Window {
        int minute;String preset;
        Window(int minute,String preset){this.minute=Math.floorMod(minute,DAY);this.preset=preset;}
    }
    /** One screen's plan. */
    static final class Plan {
        boolean enabled,rotate,perUnlock;String album;final List<Window> windows=new ArrayList<>();
        JSONObject json()throws JSONException{
            JSONArray list=new JSONArray();for(Window w:windows)list.put(new JSONObject().put("at",w.minute).put("preset",w.preset));
            JSONObject o=new JSONObject().put("enabled",enabled).put("windows",list).put("rotate",rotate).put("perUnlock",perUnlock);
            if(album!=null)o.put("album",album);return o;
        }
        static void read(JSONObject o,Plan p){
            if(o==null)return;
            p.enabled=o.optBoolean("enabled",false);p.rotate=o.optBoolean("rotate",false);p.perUnlock=o.optBoolean("perUnlock",false);
            String album=o.optString("album","");p.album=album.startsWith("content://")&&album.length()<=2048?album:null;
            JSONArray list=o.optJSONArray("windows");Set<Integer> seen=new HashSet<>();
            if(list!=null)for(int i=0;i<list.length()&&p.windows.size()<MAX_WINDOWS;i++){
                JSONObject w=list.optJSONObject(i);if(w==null)continue;
                int at=w.optInt("at",-1);String preset=w.optString("preset","");
                if(at<0||at>=DAY||!WallpaperConfig.validId(preset)||!seen.add(at))continue;
                p.windows.add(new Window(at,preset));
            }
            p.sort();
        }
        void sort(){windows.sort(Comparator.comparingInt(w->w.minute));}
        /** A rotation without an album or a schedule without windows does nothing. */
        boolean timed(){return enabled&&!windows.isEmpty();}
        boolean rotating(){return rotate&&album!=null;}
    }

    // ---------------------------------------------------------------- pure selection (unit-tested)
    /** Index of the window active at the minute of the day: the last one that started at or before it, else the
     *  last of the day still running from yesterday. -1 without windows. Windows must be sorted. */
    static int active(List<Window> windows,int minute){
        if(windows.isEmpty())return -1;int found=windows.size()-1;
        for(int i=0;i<windows.size();i++)if(windows.get(i).minute<=minute)found=i;
        return found;
    }
    /** Identifies one occurrence of a window: the date it started on (yesterday when it wraps past midnight), its
     *  start and preset. A change of key means a new window began since the last apply. */
    static String windowKey(List<Window> windows,LocalDateTime now){
        int minute=now.getHour()*60+now.getMinute(),i=active(windows,minute);if(i<0)return null;
        Window w=windows.get(i);LocalDate started=w.minute<=minute?now.toLocalDate():now.toLocalDate().minusDays(1);
        return started+"@"+w.minute+":"+w.preset;
    }
    static String rotationKey(Plan p,LocalDate today,long unlocks){return p.perUnlock?"u"+unlocks:"d"+today;}
    static String hhmm(int minute){return String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60);}

    // ---------------------------------------------------------------- applying
    static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"TiltSchedule");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;});
    static volatile long lastPoke,lastUnlock;

    /** Engine hook (main thread): cheap throttle here, the real check on the worker. unlock=true counts an unlock. */
    static void poke(Context context,boolean unlock){
        Context c=context.getApplicationContext();long now=android.os.SystemClock.elapsedRealtime();
        if(unlock){
            // Every engine (home and lock) receives the same broadcast; count it once.
            if(now-lastUnlock<3000)return;lastUnlock=now;
            SharedPreferences p=ImageStore.prefs(c);p.edit().putLong(UNLOCKS,p.getLong(UNLOCKS,0)+1).apply();
        }else if(now-lastPoke<10_000)return;
        lastPoke=now;
        WORKER.execute(()->{try{check(c);}catch(Exception|OutOfMemoryError e){Log.w("TiltSchedule","Schedule check failed",e);}});
    }

    /** Applies whatever became due since the last check. Worker thread only. */
    static void check(Context c){
        synchronized(Schedule.class){
            WallpaperConfig config=WallpaperConfig.load(c);SharedPreferences prefs=ImageStore.prefs(c);SharedPreferences.Editor state=prefs.edit();
            boolean configChanged=false;LocalDateTime now=LocalDateTime.now();long unlocks=prefs.getLong(UNLOCKS,0);
            for(String slot:new String[]{WallpaperConfig.HOME,WallpaperConfig.LOCK}){
                // Without its own wallpaper the lock screen mirrors home, so only the home plan runs.
                Plan plan=config.plan(slot);boolean live=WallpaperConfig.HOME.equals(slot)||config.separateLock;
                String windowKey=live&&plan.timed()?windowKey(plan.windows,now):null;
                if(windowKey==null)state.remove(STATE_WINDOW+slot);
                else if(!windowKey.equals(prefs.getString(STATE_WINDOW+slot,null))){
                    WallpaperConfig.Preset preset=config.preset(plan.windows.get(active(plan.windows,now.getHour()*60+now.getMinute())).preset);
                    if(preset!=null){
                        config.profile(slot).copyFrom(preset.profile);configChanged=true;
                        if(!plan.rotating())try{Presets.applyMedia(c,preset,slot);}catch(Exception e){Log.w("TiltSchedule","Preset media failed",e);}
                    }
                    state.putString(STATE_WINDOW+slot,windowKey);
                }
                String photoKey=live&&plan.rotating()?rotationKey(plan,now.toLocalDate(),unlocks)+"|"+plan.album:null;
                if(photoKey==null)state.remove(STATE_PHOTO+slot);
                else if(!photoKey.equals(prefs.getString(STATE_PHOTO+slot,null))){
                    int next=nextPhoto(c,Uri.parse(plan.album),slot,prefs.getInt(STATE_INDEX+slot,-1));
                    if(next>=0)state.putInt(STATE_INDEX+slot,next);
                    // An unreadable album (permission revoked, folder gone) is not retried until the next day or unlock.
                    state.putString(STATE_PHOTO+slot,photoKey);
                }
            }
            if(configChanged)config.save(c);
            state.apply();
        }
    }
    /** Imports the album image after lastIndex (by name order) as the slot photo; returns its index or -1. */
    static int nextPhoto(Context c,Uri tree,String slot,int lastIndex){
        List<Uri> images=albumImages(c,tree);if(images.isEmpty())return -1;
        int next=Math.floorMod(lastIndex+1,images.size());
        // A broken file is skipped instead of stalling the rotation on it.
        for(int tries=0;tries<Math.min(3,images.size());tries++,next=(next+1)%images.size()){
            try{ImageStore.importImage(c,images.get(next),slot);DepthMapManager.prune(c);return next;}
            catch(Exception|OutOfMemoryError e){Log.w("TiltSchedule","Skipping album image",e);}
        }
        return -1;
    }
    /** Images directly inside the picked folder, sorted by name; empty when the folder cannot be read. */
    static List<Uri> albumImages(Context c,Uri tree){
        List<String[]> found=new ArrayList<>();
        try{
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
            try(Cursor cursor=c.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
                while(cursor!=null&&cursor.moveToNext()&&found.size()<5000){
                    String type=cursor.getString(1);
                    if(type!=null&&type.startsWith("image/"))found.add(new String[]{cursor.getString(2)==null?"":cursor.getString(2),cursor.getString(0)});
                }
            }
        }catch(RuntimeException e){Log.w("TiltSchedule","Album unreadable",e);return Collections.emptyList();}
        found.sort((a,b)->a[0].compareToIgnoreCase(b[0]));
        List<Uri> out=new ArrayList<>();for(String[] f:found)out.add(DocumentsContract.buildDocumentUriUsingTree(tree,f[1]));return out;
    }
    /** After the user edits a plan: forget what was applied so the change shows on the next check. */
    static void reset(Context c,String slot){ImageStore.prefs(c).edit().remove(STATE_WINDOW+slot).remove(STATE_PHOTO+slot).apply();lastPoke=0;}
    private Schedule(){}
}
