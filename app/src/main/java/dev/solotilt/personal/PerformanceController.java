package dev.solotilt.personal;

import android.app.ActivityManager;
import android.content.*;
import android.os.*;
import java.util.*;
import org.json.*;

/** Coarse quality tiers, not a device-name allowlist. Timing fields never allocate per frame.
 *  Also the process-wide diagnostics source (DebugActivity) and the battery / quick-settings pause switch. */
final class PerformanceController {
    static final String KEY_PAUSED="paused",KEY_LOW_BATTERY="pause_low_battery";
    /** At or below this charge, and not charging, the wallpaper holds a still frame (when the setting is on). */
    static final int LOW_BATTERY=15;
    /** After a pause starts, or the picture changes while paused, frames run this long so the still frame is a settled one. */
    static final long GRACE_MS=600;

    // ---- process-wide view for the diagnostics screen ----
    /** The controller that rendered most recently (preview or wallpaper); null until one draws. */
    @android.annotation.SuppressLint("StaticFieldLeak") // holds the application context only
    static volatile PerformanceController active;
    /** Effect id on screen, set by RenderLoop's diagnostics hook each frame. */
    static volatile String effect="";
    @android.annotation.SuppressLint("StaticFieldLeak") // application context only
    static final EffectStats STATS=new EffectStats();

    final Context context;final PowerManager power;final boolean debug,lowRam;
    volatile int thermal;volatile boolean saver;
    int quality,pressure;float scale=1;double submitAverage;
    volatile long frames,dropped,eventCount;volatile float frameMs,swapMs,sensorAgeMs;volatile int width,height;
    volatile String sensor="unavailable",lastSummary="";private long lastFrames,lastEvents,lastReport;
    // Pause inputs: the quick-settings tile, the low-battery setting and the battery itself.
    volatile boolean pausedPref,lowBatteryOption=true,charging;volatile int batteryPct=100;
    volatile boolean holding;private int stamp;private long graceUntil;private volatile Handler wakeHandler;private volatile Runnable wakeRunnable;
    final PowerManager.OnThermalStatusChangedListener thermalListener=s->{if(s>=PowerManager.THERMAL_STATUS_SEVERE&&thermal<PowerManager.THERMAL_STATUS_SEVERE)CrashLog.note("Thermal status "+s+": render resolution capped");thermal=s;};
    final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){saver=power.isPowerSaveMode();}};
    final BroadcastReceiver battery=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        int level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,100),status=i.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
        batteryPct=level<0?100:Math.round(level*100f/Math.max(1,scale));
        charging=status==BatteryManager.BATTERY_STATUS_CHARGING||status==BatteryManager.BATTERY_STATUS_FULL||i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
        pauseInputsChanged();
    }};
    // SharedPreferences keeps listeners weakly: this field is the strong reference.
    final SharedPreferences.OnSharedPreferenceChangeListener prefsListener=(p,key)->{if(KEY_PAUSED.equals(key)||KEY_LOW_BATTERY.equals(key)){readPrefs(p);pauseInputsChanged();}};

    PerformanceController(Context c){
        context=c;lastReport=SystemClock.elapsedRealtime();power=c.getSystemService(PowerManager.class);debug=(c.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        lowRam=c.getSystemService(ActivityManager.class).isLowRamDevice();thermal=power.getCurrentThermalStatus();saver=power.isPowerSaveMode();
        power.addThermalStatusListener(thermalListener);register(receiver,new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        STATS.attach(c);SharedPreferences p=ImageStore.prefs(c);readPrefs(p);p.registerOnSharedPreferenceChangeListener(prefsListener);
        Intent now=register(battery,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(now!=null)battery.onReceive(c,now);  // sticky: current state at once
    }
    /** The flag is ignored before Android 13, where these system broadcasts reach unexported receivers anyway. */
    Intent register(BroadcastReceiver r,IntentFilter f){return context.registerReceiver(r,f,Context.RECEIVER_NOT_EXPORTED);}
    float desiredScale(int mode,int w,int h){
        quality=mode;float max=mode==1?720:mode==3?1440:mode==2?1080:lowRam?720:1080;
        if(saver||thermal>=PowerManager.THERMAL_STATUS_MODERATE)max=Math.min(max,720);
        if(thermal>=PowerManager.THERMAL_STATUS_SEVERE)max=Math.min(max,540);
        max=Math.max(480,max-pressure*180);return Math.min(1,max/Math.max(1,Math.min(w,h)));
    }
    /** Once per drawn frame (render thread): adaptive quality, the diagnostics snapshot and per-effect battery accounting. */
    boolean sample(float submitMs){
        active=this;STATS.add(effect,frameMs+swapMs);
        submitAverage=submitAverage==0?submitMs:submitAverage*.97+submitMs*.03;
        if(submitAverage>13&&pressure<2){overBudget++;if(overBudget>90){pressure++;overBudget=0;CrashLog.note(String.format(Locale.ROOT,"Frames over budget (%.1f ms avg, effect %s): quality step %d",submitAverage,effect,pressure));return true;}}else overBudget=0;
        return false;
    }
    int overBudget;
    String report(){long now=SystemClock.elapsedRealtime(),span=Math.max(1,now-lastReport);float fps=(frames-lastFrames)*1000f/span,hz=(eventCount-lastEvents)*1000f/span;lastFrames=frames;lastEvents=eventCount;lastReport=now;
        lastSummary=String.format(java.util.Locale.ROOT,"%.1f FPS · submit %.1f ms · swap %.1f ms\nmissed %d · sensor %.1f Hz · age %.1f ms\n%s · %d×%d · thermal %d",fps,frameMs,swapMs,dropped,hz,sensorAgeMs,sensor,width,height,thermal);return lastSummary;}

    // ---- pause (quick-settings tile, low battery) ----
    void readPrefs(SharedPreferences p){pausedPref=p.getBoolean(KEY_PAUSED,false);lowBatteryOption=p.getBoolean(KEY_LOW_BATTERY,true);}
    static boolean pauseFor(boolean paused,boolean lowBatteryOption,int batteryPct,boolean charging){return paused||lowBatteryOption&&!charging&&batteryPct<=LOW_BATTERY;}
    boolean shouldPause(){return pauseFor(pausedPref,lowBatteryOption,batteryPct,charging);}
    /** Why the wallpaper is held, for the diagnostics screen: "tile", "battery" or null. */
    String pauseReason(){return pausedPref?"tile":shouldPause()?"battery":null;}
    /**
     * RenderLoop's pause hook, called at the top of each frame: true means "draw nothing, schedule nothing".
     * Frames still run for GRACE_MS after the pause starts and whenever what would be drawn changes (surface, size,
     * settings, photo), so the frame left on screen is current and settled. Remembers how to wake the loop on resume.
     * Only the wallpaper is held: the in-app preview is something the user is looking at on purpose.
     */
    boolean hold(boolean wallpaper,Object surface,int w,int h,Object settings,Object photo,Handler handler,Runnable wake){
        wakeHandler=handler;wakeRunnable=wake;
        if(!wallpaper||!shouldPause()){holding=false;return false;}
        int s=((System.identityHashCode(surface)*31+w)*31+h)*31+System.identityHashCode(settings)*17+System.identityHashCode(photo);
        long now=SystemClock.uptimeMillis();
        if(!holding||s!=stamp){holding=true;stamp=s;graceUntil=now+GRACE_MS;}
        return now>graceUntil;
    }
    /** Main thread: a resume (or the pause starting mid-animation) needs a frame now, not on the next tilt. */
    void pauseInputsChanged(){
        if(holding==shouldPause())return;Handler h=wakeHandler;Runnable r=wakeRunnable;if(h!=null&&r!=null)h.post(r);
    }

    void close(){
        power.removeThermalStatusListener(thermalListener);context.unregisterReceiver(receiver);
        try{context.unregisterReceiver(battery);}catch(IllegalArgumentException ignored){}
        ImageStore.prefs(context).unregisterOnSharedPreferenceChangeListener(prefsListener);
        if(active==this)active=null;STATS.flush();
    }

    /**
     * Battery estimate per effect: frames drawn and their busy time (submit + swap, a GPU-busy proxy), per local day.
     * Accumulates in memory on the render thread and is written to the "diagnostics" prefs every 30 s; 7 days are kept.
     */
    static final class EffectStats {
        static final String PREFS="diagnostics",KEY="effect_stats";static final int KEEP_DAYS=7;static final long FLUSH_MS=30_000;
        private final Map<String,double[]> pending=new HashMap<>();private Context app;private long lastFlush;
        synchronized void attach(Context c){if(app==null)app=c.getApplicationContext();}
        synchronized void add(String effect,float busyMs){
            if(effect==null||effect.isEmpty())return;
            double[] v=pending.get(effect);if(v==null){v=new double[2];pending.put(effect,v);}v[0]++;v[1]+=Math.max(0,busyMs);
            long now=SystemClock.uptimeMillis();if(lastFlush==0)lastFlush=now;else if(now-lastFlush>FLUSH_MS)flush();
        }
        synchronized void flush(){
            lastFlush=SystemClock.uptimeMillis();if(app==null||pending.isEmpty())return;
            SharedPreferences p=app.getSharedPreferences(PREFS,0);
            JSONObject merged=merge(parse(p.getString(KEY,"{}")),today(),pending,KEEP_DAYS);pending.clear();
            p.edit().putString(KEY,merged.toString()).apply();
        }
        /** Everything stored plus what has not been flushed yet. */
        synchronized JSONObject history(){flush();return app==null?new JSONObject():parse(app.getSharedPreferences(PREFS,0).getString(KEY,"{}"));}
        synchronized void clear(){pending.clear();if(app!=null)app.getSharedPreferences(PREFS,0).edit().remove(KEY).apply();}
        static String today(){return java.time.LocalDate.now().toString();}
        static JSONObject parse(String json){try{return new JSONObject(json);}catch(JSONException e){return new JSONObject();}}

        // ---- pure helpers (unit-tested on the JVM) ----
        /** Adds the counts to the given day and drops all but the newest keepDays days (ISO dates sort as text). */
        static JSONObject merge(JSONObject history,String day,Map<String,double[]> add,int keepDays){
            try{
                JSONObject d=history.optJSONObject(day);if(d==null){d=new JSONObject();history.put(day,d);}
                for(Map.Entry<String,double[]> e:add.entrySet()){
                    JSONArray old=d.optJSONArray(e.getKey());double f=old==null?0:old.optDouble(0,0),ms=old==null?0:old.optDouble(1,0);
                    d.put(e.getKey(),new JSONArray().put(f+e.getValue()[0]).put(ms+e.getValue()[1]));
                }
                List<String> days=new ArrayList<>();for(Iterator<String> it=history.keys();it.hasNext();)days.add(it.next());
                Collections.sort(days);for(int i=0;i<days.size()-keepDays;i++)history.remove(days.get(i));
            }catch(JSONException ignored){}
            return history;
        }
        /** One effect's totals over the chosen days. */
        static final class Row {final String effect;final long frames;final double busyMs;Row(String e,long f,double ms){effect=e;frames=f;busyMs=ms;}}
        /** Totals per effect for days >= fromDay (null: all kept days), busiest first. */
        static List<Row> summarize(JSONObject history,String fromDay){
            Map<String,double[]> sum=new HashMap<>();
            for(Iterator<String> it=history.keys();it.hasNext();){
                String day=it.next();if(fromDay!=null&&day.compareTo(fromDay)<0)continue;JSONObject d=history.optJSONObject(day);if(d==null)continue;
                for(Iterator<String> e=d.keys();e.hasNext();){String id=e.next();JSONArray v=d.optJSONArray(id);if(v==null)continue;
                    double[] s=sum.computeIfAbsent(id,k->new double[2]);s[0]+=v.optDouble(0,0);s[1]+=v.optDouble(1,0);}
            }
            List<Row> rows=new ArrayList<>();for(Map.Entry<String,double[]> e:sum.entrySet())rows.add(new Row(e.getKey(),Math.round(e.getValue()[0]),e.getValue()[1]));
            rows.sort((a,b)->Double.compare(b.busyMs,a.busyMs));return rows;
        }
    }
}
