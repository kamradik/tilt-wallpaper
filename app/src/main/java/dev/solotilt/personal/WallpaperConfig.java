package dev.solotilt.personal;

import android.content.*;
import java.util.*;
import org.json.*;

/**
 * Versioned persistent state, shared by the preview and every wallpaper engine.
 * Global motion/performance settings plus one Profile per screen; the lock profile is used only with separateLock.
 */
final class WallpaperConfig {
    static final String KEY="config_v2";static final int VERSION=3;
    static final String HOME="home",LOCK="lock";
    /** Frame-rate cap: follow the display, or a fixed 30/60. */
    static final int FPS_AUTO=0,FPS_30=1,FPS_60=2;

    int quality,fps;float sensitivity=1,smoothing=.45f,neutralX,neutralY;
    boolean reduceMotion,touch=true,haptics=true,separateLock;String language="ru";
    final Set<String> favorites=new LinkedHashSet<>();final LinkedList<String> recents=new LinkedList<>();
    final Profile home=new Profile(),lock=new Profile();

    /** What one screen shows: an effect, its per-effect parameters, and how the photo is framed. */
    static final class Profile {
        String effect="classic";float cropX=.5f,cropY=.5f,scale=1.08f;
        final Map<String,float[]> params=new HashMap<>();
        Effects.Effect effect(){return Effects.get(effect);}
        /** [a, b, switch] for an effect, created from its defaults on first use. */
        float[] params(String id){return params.computeIfAbsent(id,k->Effects.get(k).defaults());}
        float[] current(){return params(effect);}
        boolean switchOn(){return current()[2]>.5f;}
        void reset(String id){params.put(id,Effects.get(id).defaults());}
        void copyFrom(Profile p){effect=p.effect;cropX=p.cropX;cropY=p.cropY;scale=p.scale;params.clear();for(Map.Entry<String,float[]> e:p.params.entrySet())params.put(e.getKey(),e.getValue().clone());}
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
        }catch(JSONException ignored){}
        return c;
    }
    static void readProfile(JSONObject o,Profile p){
        if(o==null)return;
        p.effect=Effects.get(o.optString("effect","classic")).id;
        p.cropX=safe(o.optDouble("cropX",.5),.5f,0,1);p.cropY=safe(o.optDouble("cropY",.5),.5f,0,1);p.scale=safe(o.optDouble("scale",1.08),1.08f,1.02f,2.5f);
        JSONObject params=o.optJSONObject("params");
        if(params!=null)for(Iterator<String> it=params.keys();it.hasNext();){String id=it.next();readParams(p,id,params.optJSONArray(id));}
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
        return new JSONObject().put("effect",p.effect).put("cropX",p.cropX).put("cropY",p.cropY).put("scale",p.scale).put("params",params);
    }
    String json(){try{
        JSONArray favs=new JSONArray();for(String f:favorites)favs.put(f);JSONArray recent=new JSONArray();for(String r:recents)recent.put(r);
        return new JSONObject().put("version",VERSION).put("qualityMode",quality).put("fps",fps).put("sensorSensitivity",sensitivity).put("motionSmoothing",smoothing)
            .put("neutralX",neutralX).put("neutralY",neutralY).put("reduceMotion",reduceMotion).put("touch",touch).put("haptics",haptics).put("language",language)
            .put("separateLock",separateLock).put("favorites",favs).put("recents",recent).put(HOME,writeProfile(home)).put(LOCK,writeProfile(lock)).toString();
    }catch(JSONException e){throw new IllegalStateException(e);}}
    void save(Context c){ImageStore.prefs(c).edit().putString(KEY,json()).apply();}
    WallpaperConfig copy(){return parse(json());}
}
