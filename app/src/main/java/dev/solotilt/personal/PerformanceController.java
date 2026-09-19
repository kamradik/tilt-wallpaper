package dev.solotilt.personal;

import android.app.ActivityManager;
import android.content.*;
import android.os.*;

/** Coarse quality tiers, not a device-name allowlist. Timing fields never allocate per frame. */
final class PerformanceController {
    final Context context;final PowerManager power;final boolean debug,lowRam;
    volatile int thermal;volatile boolean saver;
    int quality,pressure;float scale=1;double submitAverage;
    volatile long frames,dropped,eventCount;volatile float frameMs,swapMs,sensorAgeMs;volatile int width,height;
    volatile String sensor="unavailable",lastSummary="";private long lastFrames,lastEvents,lastReport;
    final PowerManager.OnThermalStatusChangedListener thermalListener=s->thermal=s;
    final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){saver=power.isPowerSaveMode();}};
    PerformanceController(Context c){
        context=c;lastReport=SystemClock.elapsedRealtime();power=c.getSystemService(PowerManager.class);debug=(c.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        lowRam=c.getSystemService(ActivityManager.class).isLowRamDevice();thermal=power.getCurrentThermalStatus();saver=power.isPowerSaveMode();
        power.addThermalStatusListener(thermalListener);if(Build.VERSION.SDK_INT>=33)c.registerReceiver(receiver,new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),Context.RECEIVER_NOT_EXPORTED);else c.registerReceiver(receiver,new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
    }
    float desiredScale(int mode,int w,int h){
        quality=mode;float max=mode==1?720:mode==3?1440:mode==2?1080:lowRam?720:1080;
        if(saver||thermal>=PowerManager.THERMAL_STATUS_MODERATE)max=Math.min(max,720);
        if(thermal>=PowerManager.THERMAL_STATUS_SEVERE)max=Math.min(max,540);
        max=Math.max(480,max-pressure*180);return Math.min(1,max/Math.max(1,Math.min(w,h)));
    }
    boolean sample(float submitMs){submitAverage=submitAverage==0?submitMs:submitAverage*.97+submitMs*.03;if(submitAverage>13&&pressure<2){overBudget++;if(overBudget>90){pressure++;overBudget=0;return true;}}else overBudget=0;return false;}
    int overBudget;
    String report(){long now=SystemClock.elapsedRealtime(),span=Math.max(1,now-lastReport);float fps=(frames-lastFrames)*1000f/span,hz=(eventCount-lastEvents)*1000f/span;lastFrames=frames;lastEvents=eventCount;lastReport=now;
        lastSummary=String.format(java.util.Locale.ROOT,"%.1f FPS · submit %.1f ms · swap %.1f ms\nmissed %d · sensor %.1f Hz · age %.1f ms\n%s · %d×%d · thermal %d",fps,frameMs,swapMs,dropped,hz,sensorAgeMs,sensor,width,height,thermal);return lastSummary;}
    void close(){power.removeThermalStatusListener(thermalListener);context.unregisterReceiver(receiver);}
}
