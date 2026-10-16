package dev.solotilt.personal;

import android.content.*;
import java.util.*;
import org.json.*;

/**
 * Versioned persistent state, shared by the preview and every wallpaper engine.
 * Global motion/performance settings plus one Profile per screen; the lock profile is used only with separateLock.
 */
final class WallpaperConfig {
    static final String KEY="config_v2";static final int VERSION=4;
    /** Saved presets are capped so their media copies cannot fill the device. */
    static final int MAX_PRESETS=30;
    static final String HOME="home",LOCK="lock";
    /** Frame-rate cap: follow the display, or a fixed 30/60. */
    static final int FPS_AUTO=0,FPS_30=1,FPS_60=2;

    int quality,fps;float sensitivity=1,smoothing=.45f,neutralX,neutralY;
    boolean reduceMotion,touch=true,haptics=true,separateLock;String language="ru";
    final Set<String> favorites=new LinkedHashSet<>();final LinkedList<String> recents=new LinkedList<>();
    final Profile home=new Profile(),lock=new Profile();
    /** v4: saved looks (newest first) and one time-of-day / photo-of-the-day plan per screen. */
    final List<Preset> presets=new ArrayList<>();final Schedule.Plan homePlan=new Schedule.Plan(),lockPlan=new Schedule.Plan();

    /** A saved look: a profile snapshot plus a reference to the preset's own media copy (see Presets). */
    static final class Preset {
        static final String PHOTO="photo",VIDEO="video",BUILTIN="builtin";
        String id,name,media=BUILTIN;int builtin;long created;final Profile profile=new Profile();
        Preset copy(){Preset p=new Preset();p.id=id;p.name=name;p.media=media;p.builtin=builtin;p.created=created;p.profile.copyFrom(profile);return p;}
        JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("name",name).put("created",created).put("media",media).put("builtin",builtin).put("profile",writeProfile(profile));}
        /** Clamped like the rest of the config; null when the entry is unusable. An invalid id is replaced, so it can never reach a file path. */
        static Preset parse(JSONObject o){
            if(o==null)return null;Preset p=new Preset();
            String id=o.optString("id","");p.id=validId(id)?id:newId();
            p.name=cleanName(o.optString("name",""));p.created=Math.max(0,o.optLong("created",0));
            String media=o.optString("media",BUILTIN);p.media=PHOTO.equals(media)||VIDEO.equals(media)?media:BUILTIN;
            p.builtin=Math.floorMod(o.optInt("builtin",0),3);
            readProfile(o.optJSONObject("profile"),p.profile);return p;
        }
    }
    static boolean validId(String id){return id!=null&&id.matches("[a-z0-9]{4,24}");}
    static final Random IDS=new Random();
    static String newId(){return Long.toString(System.currentTimeMillis(),36)+Integer.toString(1296+IDS.nextInt(45360),36);}
    /** Trimmed, control characters removed, at most 40 characters; empty becomes "Пресет". */
    static String cleanName(String raw){
        String s=raw==null?"":raw.replaceAll("\\p{Cntrl}"," ").trim().replaceAll("\\s+"," ");
        if(s.length()>40)s=s.substring(0,40).trim();return s.isEmpty()?"Пресет":s;
    }
    Preset preset(String id){if(id!=null)for(Preset p:presets)if(p.id.equals(id))return p;return null;}
    Schedule.Plan plan(String slot){return LOCK.equals(slot)?lockPlan:homePlan;}
    /** Drops schedule windows whose preset is gone. */
    void prunePlans(){for(Schedule.Plan plan:new Schedule.Plan[]{homePlan,lockPlan})plan.windows.removeIf(w->preset(w.preset)==null);}

    // ---------------------------------------------------------------- curated collections and "surprise me"
    static final String[] COLLECTION_RU={"Спокойные","Яркие","Экономные","Объёмные"},COLLECTION_EN={"Calm","Vivid","Battery-friendly","3D"};
    static final Set<String> CALM=Set.of("classic","focus","glass","fluted","ice","depth","shadow","window","golden","bokeh","pond");
    static final Set<String> VIVID=Set.of("holo","prism","oil","mercury","caustic","aurora","nebula","lenticular","film","light");
    /** Calm and Vivid are curated lists; Battery-friendly never redraws by itself (no ANIMATED or ANIMATED_SWITCH) and needs no depth; 3D uses depth. */
    static boolean inCollection(int collection,Effects.Effect e){
        switch(collection){
            case 0:return CALM.contains(e.id);
            case 1:return VIVID.contains(e.id);
            case 2:return !e.has(Effects.ANIMATED)&&!e.has(Effects.ANIMATED_SWITCH)&&!e.has(Effects.DEPTH);
            case 3:return e.has(Effects.DEPTH);
            default:return false;
        }
    }
    /** "Surprise me": an effect other than current, its parameters jittered around the defaults and kept within 0.3..0.8. */
    static String surprise(Random r,String current,float[] params){
        String now=Effects.get(current).id;Effects.Effect pick;
        do pick=Effects.ALL[r.nextInt(Effects.ALL.length)];while(pick.id.equals(now));
        float[] d=pick.defaults();
        for(int i=0;i<2;i++)params[i]=Math.max(.3f,Math.min(.8f,d[i]+(r.nextFloat()-.5f)*.4f));
        params[2]=pick.hasSwitch()&&r.nextFloat()<.25f?0:1;
        return pick.id;
    }

    /** What one screen shows: an effect, its per-effect parameters, and how the photo is framed. */
    static final class Profile {
        String effect="classic";float cropX=.5f,cropY=.5f,scale=1.08f;
        /** Second layer drawn over the effect (Effects.LAYER), or null. Its sliders live in params like any effect's. */
        String overlay;
        final Map<String,float[]> params=new HashMap<>();
        Effects.Effect effect(){return Effects.get(effect);}
        /** [a, b, switch] for an effect, created from its defaults on first use. */
        float[] params(String id){return params.computeIfAbsent(id,k->Effects.get(k).defaults());}
        float[] current(){return params(effect);}
        boolean switchOn(){return current()[2]>.5f;}
        /** The second layer when it is valid over the current effect, else null. */
        Effects.Effect overlayEffect(){if(overlay==null)return null;Effects.Effect t=Effects.find(overlay);return t!=null&&Effects.layerable(effect(),t)?t:null;}
        void reset(String id){params.put(id,Effects.get(id).defaults());}
        void copyFrom(Profile p){effect=p.effect;overlay=p.overlay;cropX=p.cropX;cropY=p.cropY;scale=p.scale;params.clear();for(Map.Entry<String,float[]> e:p.params.entrySet())params.put(e.getKey(),e.getValue().clone());}
    }

    Profile profile(String slot){return LOCK.equals(slot)&&separateLock?lock:home;}
    boolean en(){return "en".equals(language);}
    boolean isFavorite(String id){return favorites.contains(id);}
    void toggleFavorite(String id){if(!favorites.remove(id))favorites.add(id);}
    void used(String id){recents.remove(id);recents.addFirst(id);while(recents.size()>8)recents.removeLast();}

    static float safe(double n,float fallback,float min,float max){return Double.isFinite(n)?Math.max(min,Math.min(max,(float)n)):fallback;}
    static WallpaperConfig load(Context c){return parse(ImageStore.prefs(c).getString(KEY,"{}"));}
    static WallpaperConfig parse(String json){
        WallpaperConfig c=new WallpaperConfig();
        try{
            JSONObject o=new JSONObject(json);int version=o.optInt("version",2);if(version>VERSION)return c;
            c.quality=Math.max(0,Math.min(3,o.optInt("qualityMode",0)));c.fps=Math.max(0,Math.min(2,o.optInt("fps",0)));
            c.sensitivity=safe(o.optDouble("sensorSensitivity",1),1,.1f,2);c.smoothing=safe(o.optDouble("motionSmoothing",.45),.45f,0,1);
            c.neutralX=safe(o.optDouble("neutralX",0),0,-120,120);c.neutralY=safe(o.optDouble("neutralY",0),0,-120,120);
            c.reduceMotion=o.optBoolean("reduceMotion",false);c.touch=o.optBoolean("touch",true);c.haptics=o.optBoolean("haptics",true);
            c.language="en".equals(o.optString("language"))?"en":"ru";c.separateLock=o.optBoolean("separateLock",false);
            JSONArray favs=o.optJSONArray("favorites");if(favs!=null)for(int i=0;i<favs.length();i++)c.favorites.add(Effects.get(favs.optString(i)).id);
            JSONArray recent=o.optJSONArray("recents");if(recent!=null)for(int i=0;i<recent.length()&&i<8;i++){String id=Effects.get(recent.optString(i)).id;if(!c.recents.contains(id))c.recents.add(id);}
            if(version>=3){readProfile(o.optJSONObject(HOME),c.home);readProfile(o.optJSONObject(LOCK),c.lock);}
            else{
                // v2 kept one flat profile (and, in the 49-effect builds, lock* copies of it).
                readFlat(o,"",c.home);
                if(o.has("lockEffectId"))readFlat(o,"lock",c.lock);else c.lock.copyFrom(c.home);
            }
            // v3 had no presets or schedule: they start empty, so a v3 config migrates by being read as is.
            if(version>=4){
                JSONArray list=o.optJSONArray("presets");Set<String> ids=new HashSet<>();
                if(list!=null)for(int i=0;i<list.length()&&c.presets.size()<MAX_PRESETS;i++){Preset p=Preset.parse(list.optJSONObject(i));if(p!=null&&ids.add(p.id))c.presets.add(p);}
                JSONObject schedule=o.optJSONObject("schedule");
                if(schedule!=null){Schedule.Plan.read(schedule.optJSONObject(HOME),c.homePlan);Schedule.Plan.read(schedule.optJSONObject(LOCK),c.lockPlan);}
                c.prunePlans();
            }
        }catch(JSONException ignored){}
        return c;
    }
    static void readProfile(JSONObject o,Profile p){
        if(o==null)return;
        p.effect=Effects.get(o.optString("effect","classic")).id;
        p.cropX=safe(o.optDouble("cropX",.5),.5f,0,1);p.cropY=safe(o.optDouble("cropY",.5),.5f,0,1);p.scale=safe(o.optDouble("scale",1.08),1.08f,1.02f,2.5f);
        JSONObject params=o.optJSONObject("params");
        if(params!=null)for(Iterator<String> it=params.keys();it.hasNext();){String id=it.next();readParams(p,id,params.optJSONArray(id));}
        Effects.Effect top=Effects.find(o.optString("overlay",""));p.overlay=top!=null&&top.has(Effects.LAYER)?top.id:null;
    }
    static void readFlat(JSONObject o,String prefix,Profile p){
        String effectKey=prefix.isEmpty()?"effectId":"lockEffectId",cap=prefix.isEmpty()?"":"lock";
        Object effect=o.opt(effectKey);
        // v2 beta stored the effect as a string id; earlier builds by index into {classic, depth, light, glass, focus}.
        String[] v2={"classic","depth","light","glass","focus"};
        p.effect=Effects.get(effect instanceof Number?v2[Math.max(0,Math.min(4,((Number)effect).intValue()))]:o.optString(effectKey,"classic")).id;
        p.cropX=safe(o.optDouble(cap.isEmpty()?"cropX":"lockCropX",.5),.5f,0,1);p.cropY=safe(o.optDouble(cap.isEmpty()?"cropY":"lockCropY",.5),.5f,0,1);
        p.scale=safe(o.optDouble(cap.isEmpty()?"scale":"lockScale",1.08),1.08f,1.02f,2.5f);
        JSONObject params=o.optJSONObject(cap.isEmpty()?"effectParameters":"lockEffectParameters");
        if(params!=null)for(Iterator<String> it=params.keys();it.hasNext();){String id=it.next();readParams(p,id,params.optJSONArray(id));}
    }
    static void readParams(Profile p,String rawId,JSONArray values){
        if(values==null)return;Effects.Effect e=Effects.get(rawId);
        if(!e.id.equals(Effects.RENAMED.getOrDefault(rawId,rawId)))return; // removed effect
        float[] d=e.defaults();
        p.params.put(e.id,new float[]{safe(values.optDouble(0,d[0]),d[0],0,1),safe(values.optDouble(1,d[1]),d[1],0,1),values.length()>2?(values.optDouble(2,1)>.5?1:0):1});
    }
    static JSONObject writeProfile(Profile p)throws JSONException{
        JSONObject params=new JSONObject();
        for(Map.Entry<String,float[]> e:p.params.entrySet())params.put(e.getKey(),new JSONArray().put(e.getValue()[0]).put(e.getValue()[1]).put(e.getValue()[2]));
        JSONObject o=new JSONObject().put("effect",p.effect).put("cropX",p.cropX).put("cropY",p.cropY).put("scale",p.scale).put("params",params);
        if(p.overlay!=null)o.put("overlay",p.overlay);return o;
    }
    String json(){try{
        JSONArray favs=new JSONArray();for(String f:favorites)favs.put(f);JSONArray recent=new JSONArray();for(String r:recents)recent.put(r);
        return new JSONObject().put("version",VERSION).put("qualityMode",quality).put("fps",fps).put("sensorSensitivity",sensitivity).put("motionSmoothing",smoothing)
            .put("neutralX",neutralX).put("neutralY",neutralY).put("reduceMotion",reduceMotion).put("touch",touch).put("haptics",haptics).put("language",language)
            .put("separateLock",separateLock).put("favorites",favs).put("recents",recent).put(HOME,writeProfile(home)).put(LOCK,writeProfile(lock))
            .put("presets",presetsJson()).put("schedule",new JSONObject().put(HOME,homePlan.json()).put(LOCK,lockPlan.json())).toString();
    }catch(JSONException e){throw new IllegalStateException(e);}}
    JSONArray presetsJson()throws JSONException{JSONArray a=new JSONArray();for(Preset p:presets)a.put(p.json());return a;}
    void save(Context c){ImageStore.prefs(c).edit().putString(KEY,json()).apply();ParallaxWidget.refresh(c);}
    WallpaperConfig copy(){return parse(json());}
}
