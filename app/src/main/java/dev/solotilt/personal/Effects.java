package dev.solotilt.personal;

/** Small registry: one shared shader pipeline, no per-effect service or sensor instances. */
final class Effects {
    static final String[] IDS={"classic","depth","light","glass","focus"};
    static final String[] NAMES={"Classic Tilt","Depth Parallax","Dynamic Light","Liquid Glass","Focus Rack"};
    static final String[] SHORT={"Classic","Depth","Light","Glass","Focus"};
    static final boolean[] DEPTH={false,true,false,false,true};
    static final float[] DEFAULT_A={.8f,.5f,.4f,.45f,.55f},DEFAULT_B={.45f,.6f,.6f,.5f,.55f};
    static int index(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return 0;}
    static String description(int id,boolean en){return (en?new String[]{"The original optical tilt.","Layered depth from a single photo.","A soft reflection that follows you.","Subtle refraction. Touch to ripple.","Shift the focus through your photo."}:new String[]{"Знакомая оптика. Новая плавность.","Объём и движение внутри фотографии.","Мягкое отражение следует за наклоном.","Тонкое стекло. Коснись для ряби.","Переводи фокус между планами."})[id];}
}
