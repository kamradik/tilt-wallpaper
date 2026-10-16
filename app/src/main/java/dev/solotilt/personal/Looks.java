package dev.solotilt.personal;

import java.util.ArrayList;
import java.util.List;

/**
 * The built-in gallery of ready looks: hand-picked effect + second layer + slider combinations. Unlike a saved
 * preset, a look carries no picture: applying it keeps the screen's own photo or video and its framing, and only
 * changes the effect, its second layer and their settings. Users save their own looks (the effect constructor:
 * effect + settings + second layer + finishing modifiers); those live in WallpaperConfig.looks.
 */
final class Looks {
    static final class Look {
        final String id,ru,en,effect,overlay;final float[] base,top;
        /** Finishing modifiers (WallpaperConfig.Profile.mods); neutral for the built-in looks. */
        final float[] mods;
        Look(String id,String ru,String en,String effect,float[] base,String overlay,float[] top){this(id,ru,en,effect,base,overlay,top,new float[]{0,0,0,.5f});}
        Look(String id,String ru,String en,String effect,float[] base,String overlay,float[] top,float[] mods){this.id=id;this.ru=ru;this.en=en;this.effect=effect;this.base=base;this.overlay=overlay;this.top=top;this.mods=mods;}
        /** A user's look: one name in both languages. */
        static Look custom(String id,String name,WallpaperConfig.Profile p){
            Effects.Effect t=p.overlayEffect();
            return new Look(id,name,name,p.effect,p.current().clone(),t==null?null:t.id,t==null?new float[]{0,0,1}:p.params(t.id).clone(),p.mods.clone());
        }
        org.json.JSONObject json()throws org.json.JSONException{
            org.json.JSONObject o=new org.json.JSONObject().put("id",id).put("name",ru).put("effect",effect).put("base",array(base)).put("mods",array(mods));
            if(overlay!=null)o.put("overlay",overlay).put("top",array(top));return o;
        }
        static Look parse(org.json.JSONObject o){
            if(o==null||!WallpaperConfig.validId(o.optString("id","")))return null;
            Effects.Effect e=Effects.find(o.optString("effect",""));if(e==null)return null;
            Effects.Effect t=Effects.find(o.optString("overlay",""));if(t!=null&&!Effects.layerable(e,t))t=null;
            String name=WallpaperConfig.cleanName(o.optString("name",""));
            return new Look(o.optString("id"),name,name,e.id,floats(o.optJSONArray("base"),3,e.defaults()),t==null?null:t.id,
                floats(o.optJSONArray("top"),3,t==null?new float[]{0,0,1}:t.defaults()),floats(o.optJSONArray("mods"),4,new float[]{0,0,0,.5f}));
        }
        static org.json.JSONArray array(float[] v)throws org.json.JSONException{org.json.JSONArray a=new org.json.JSONArray();for(float x:v)a.put((double)x);return a;}
        static float[] floats(org.json.JSONArray a,int n,float[] fallback){
            float[] v=fallback.clone();if(a==null)return v;
            for(int i=0;i<n&&i<a.length();i++)v[i]=WallpaperConfig.safe(a.optDouble(i,v[i]),v[i],0,1);
            return v;
        }
        String name(boolean en){return en?this.en:ru;}
        /** "Window + Snow Globe" in the current language. */
        String recipe(boolean en){Effects.Effect t=overlay==null?null:Effects.get(overlay);return Effects.get(effect).name(en)+(t==null?"":" + "+t.name(en));}
    }
    static final Look[] ALL={
        new Look("winter-window","Зимнее окно","Winter Window","window",new float[]{.55f,.5f,1},"snowglobe",new float[]{.6f,.35f,0}),
        new Look("rainy-evening","Дождливый вечер","Rainy Evening","depth",new float[]{.55f,.5f,1},"droplets",new float[]{.7f,.6f,1}),
        new Look("old-cinema","Старое кино","Old Cinema","vintage",new float[]{.6f,.5f,1},"film",new float[]{.55f,.6f,1}),
        new Look("polar-night","Полярная ночь","Polar Night","classic",new float[]{.75f,.45f,1},"aurora",new float[]{.6f,.55f,1}),
        new Look("holo-card","Коллекционная карточка","Collector Card","holo",new float[]{.6f,.5f,1},"light",new float[]{.5f,.5f,1}),
        new Look("summer-glass","Летнее стекло","Summer Glass","glass",new float[]{.5f,.5f,1},"golden",new float[]{.55f,.5f,1}),
        new Look("floating-film","Объём на плёнке","Depth on Film","shadow",new float[]{.55f,.5f,1},"film",new float[]{.4f,.4f,1}),
        new Look("snow-portrait","Снег в объёме","Snow in Depth","depth",new float[]{.6f,.5f,1},"snowglobe",new float[]{.75f,.5f,1}),
        new Look("living-day","Живой день","Living Day","light",new float[]{.55f,.5f,1},"daytime",new float[]{.6f,.5f,1}),
        new Look("frozen-dusk","Морозные сумерки","Frosty Dusk","ice",new float[]{.5f,.5f,1},"golden",new float[]{.45f,.6f,1}),
    };

    /** Puts the look on HOME, LOCK or BOTH (Presets' targets) and saves; photos, videos and framing stay. */
    static void apply(android.content.Context c,WallpaperConfig config,Look look,String target){
        if(Presets.LOCK.equals(target)&&!config.separateLock){config.separateLock=true;config.lock.copyFrom(config.home);}
        List<WallpaperConfig.Profile> profiles=new ArrayList<>();
        if(!Presets.LOCK.equals(target))profiles.add(config.home);
        if(Presets.LOCK.equals(target)||Presets.BOTH.equals(target)&&config.separateLock)profiles.add(config.lock);
        for(WallpaperConfig.Profile p:profiles)put(p,look);
        config.used(look.effect);config.save(c);
    }
    static void put(WallpaperConfig.Profile p,Look look){
        p.effect=look.effect;p.params.put(look.effect,look.base.clone());p.overlay=look.overlay;
        if(look.overlay!=null)p.params.put(look.overlay,look.top.clone());
        System.arraycopy(look.mods,0,p.mods,0,4);
    }
    private Looks(){}
}
