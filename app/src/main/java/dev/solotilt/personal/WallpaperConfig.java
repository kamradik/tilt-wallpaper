package dev.solotilt.personal;

import android.content.*;
import org.json.*;

/** Versioned persistent state, shared by preview and all wallpaper engines. */
final class WallpaperConfig {
    static final String KEY="config_v2";
    int effect,quality;float cropX=.5f,cropY=.5f,scale=1.08f,sensitivity=1,smoothing=.45f,neutralX,neutralY;
    boolean reduceMotion,touch=true;String language="ru";
    final float[] a=Effects.DEFAULT_A.clone(),b=Effects.DEFAULT_B.clone();
    static float safe(double n,float fallback,float min,float max){return Double.isFinite(n)?Math.max(min,Math.min(max,(float)n)):fallback;}
    static WallpaperConfig load(Context c){return parse(ImageStore.prefs(c).getString(KEY,"{}"));}
    static WallpaperConfig parse(String json){
        WallpaperConfig c=new WallpaperConfig();
        try{
            JSONObject o=new JSONObject(json);int version=o.optInt("version",2);if(version>2)return c;
            c.effect=Effects.index(o.optString("effectId","classic"));c.quality=Math.max(0,Math.min(3,o.optInt("qualityMode",0)));
            c.cropX=safe(o.optDouble("cropX",.5),.5f,0,1);c.cropY=safe(o.optDouble("cropY",.5),.5f,0,1);
            c.scale=safe(o.optDouble("scale",1.08),1.08f,1.02f,2.5f);c.sensitivity=safe(o.optDouble("sensorSensitivity",1),1,.1f,2);
            c.smoothing=safe(o.optDouble("motionSmoothing",.45),.45f,0,1);c.neutralX=safe(o.optDouble("neutralX",0),0,-120,120);c.neutralY=safe(o.optDouble("neutralY",0),0,-120,120);
            c.reduceMotion=o.optBoolean("reduceMotion",false);c.touch=o.optBoolean("touch",true);c.language="en".equals(o.optString("language"))?"en":"ru";
            JSONObject params=o.optJSONObject("effectParameters");if(params!=null)for(int i=0;i<5;i++){JSONArray p=params.optJSONArray(Effects.IDS[i]);if(p!=null){c.a[i]=safe(p.optDouble(0,c.a[i]),c.a[i],0,1);c.b[i]=safe(p.optDouble(1,c.b[i]),c.b[i],0,1);}}
        }catch(JSONException ignored){}return c;
    }
    String json(){try{
        JSONObject p=new JSONObject();for(int i=0;i<5;i++)p.put(Effects.IDS[i],new JSONArray().put(a[i]).put(b[i]));
        return new JSONObject().put("version",2).put("effectId",Effects.IDS[effect]).put("effectParameters",p).put("cropX",cropX).put("cropY",cropY).put("scale",scale).put("sensorSensitivity",sensitivity).put("motionSmoothing",smoothing).put("neutralX",neutralX).put("neutralY",neutralY).put("qualityMode",quality).put("reduceMotion",reduceMotion).put("touch",touch).put("language",language).toString();
    }catch(JSONException e){throw new IllegalStateException(e);}}
    void save(Context c){ImageStore.prefs(c).edit().putString(KEY,json()).apply();}
    void resetEffect(){a[effect]=Effects.DEFAULT_A[effect];b[effect]=Effects.DEFAULT_B[effect];}
    boolean en(){return "en".equals(language);}
}
