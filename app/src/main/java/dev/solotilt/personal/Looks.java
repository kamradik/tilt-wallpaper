package dev.solotilt.personal;

import java.util.ArrayList;
import java.util.List;

/**
 * The built-in gallery of ready looks: hand-picked effect + second layer + slider combinations. Unlike a saved
 * preset, a look carries no picture: applying it keeps the screen's own photo or video and its framing, and only
 * changes the effect, its second layer and their settings.
 */
final class Looks {
    static final class Look {
        final String id,ru,en,effect,overlay;final float[] base,top;
        Look(String id,String ru,String en,String effect,float[] base,String overlay,float[] top){this.id=id;this.ru=ru;this.en=en;this.effect=effect;this.base=base;this.overlay=overlay;this.top=top;}
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
    }
    private Looks(){}
}
