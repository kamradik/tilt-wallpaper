package dev.solotilt.personal;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;
import static dev.solotilt.personal.Ios.*;

/**
 * Diagnostics: live renderer stats (refreshed every second from PerformanceController's process-wide snapshot),
 * the per-effect battery estimate and the offline crash log with share / clear. The wallpaper stays visible
 * behind a dark scrim, so the live numbers are those of the wallpaper actually running.
 */
public final class DebugActivity extends Activity {
    static final int SHARE_LIMIT=60_000;  // chars: well under the Binder transaction limit for EXTRA_TEXT
    WallpaperConfig config;LinearLayout list,battery;TextView logView;boolean week;
    final Handler main=new Handler(Looper.getMainLooper());
    final Map<String,TextView> live=new LinkedHashMap<>();
    PerformanceController last;long lastFrames,lastEvents,lastTime;
    final Runnable tick=new Runnable(){public void run(){refreshLive();main.postDelayed(this,1000);}};
    String tr(String ru,String en){return config.en()?en:ru;}
    int dp(float n){return Ios.dp(this,n);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);config=WallpaperConfig.load(this);Ios.reduceMotion(config.reduceMotion);
        Window w=getWindow();w.setDecorFitsSystemWindows(false);w.setFormat(PixelFormat.TRANSLUCENT);w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xeb000000);setContentView(root);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);root.addView(nav,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        ImageView back=circle(this,Glyph.BACK,tr("Назад","Back"));back.setOnClickListener(v->finish());nav.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        root.setOnApplyWindowInsetsListener((v,i)->{Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            nav.setPadding(dp(16)+bars.left,bars.top+dp(8),dp(16)+bars.right,dp(8));list.setPadding(dp(16)+bars.left,bars.top+dp(64),dp(16)+bars.right,bars.bottom+dp(32));return i;});
        build();
        if(state==null)for(int i=0;i<list.getChildCount();i++)rise(list.getChildAt(i),Math.min(i,7));
    }
    void build(){
        list.removeAllViews();live.clear();
        list.addView(text(this,tr("Диагностика","Diagnostics"),34,BOLD,LABEL));

        LinearLayout now=group(this,list,tr("Сейчас","Live"));
        stat(now,"fps",tr("Кадров в секунду","Frames per second"));
        stat(now,"frame",tr("Кадр (CPU)","Frame (CPU submit)"));
        stat(now,"swap",tr("Показ (swap)","Swap"));
        stat(now,"dropped",tr("Пропущено кадров","Dropped frames"));
        stat(now,"sensor",tr("Датчик","Sensor"));
        stat(now,"rate",tr("Частота датчика","Sensor rate"));
        stat(now,"age",tr("Задержка датчика","Sensor age"));
        stat(now,"size",tr("Разрешение рендера","Render resolution"));
        stat(now,"thermal",tr("Нагрев","Thermal status"));
        stat(now,"saver",tr("Экономия энергии","Battery saver"));
        stat(now,"effect",tr("Эффект","Effect"));
        stat(now,"battery",tr("Заряд","Battery"));
        stat(now,"pause",tr("Пауза","Paused"));
        footer(this,list,tr("Цифры обоев, которые сейчас видны за этим экраном (или превью в приложении). Обновляются раз в секунду.","Numbers of the wallpaper visible behind this screen (or the in-app preview), refreshed every second."));

        Segmented range=new Segmented(this,tr("Сегодня","Today"),tr("7 дней","7 days"));range.select(week?1:0,false);range.listener=i->{week=i==1;fillBattery();};
        battery=group(this,list,tr("Расход батареи по эффектам","Battery use by effect"));
        list.removeView(battery);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(10);box.addView(range,rp);box.addView(battery,new LinearLayout.LayoutParams(-1,-2));list.addView(box);
        fillBattery();
        footer(this,list,tr("Оценка: кадры × время кадра (CPU + ожидание GPU). Чем больше время, тем больше эффект тратит батарею. Данные хранятся только на телефоне.","An estimate: frames × frame time (CPU + GPU wait). More time means more battery. Kept on this phone only."));

        LinearLayout crash=group(this,list,tr("Журнал сбоев","Crash log"));
        logView=text(this,"",12,REGULAR,SECONDARY);logView.setTypeface(Typeface.MONOSPACE);logView.setTextIsSelectable(true);logView.setPadding(dp(16),dp(12),dp(16),dp(12));crash.addView(logView);
        TextView share=action(tr("Поделиться логом","Share log"),accent);share.setOnClickListener(v->share());row(this,crash,null,share);
        TextView clear=action(tr("Очистить","Clear"),0xffff453a);clear.setOnClickListener(v->{CrashLog.clear();fillLog();});row(this,crash,null,clear);
        fillLog();
        footer(this,list,tr("Сбои и ошибки рендера записываются на телефоне, без интернета. Отправить журнал можно только вручную.","Crashes and renderer errors are written on the phone, offline. The log leaves it only if you share it."));
    }
    TextView action(String title,int color){TextView t=text(this,title,17,REGULAR,color);t.setClickable(true);t.setFocusable(true);t.setAccessibilityDelegate(BUTTON);t.setMinHeight(dp(40));t.setGravity(Gravity.CENTER_VERTICAL);return pressable(t);}
    void stat(LinearLayout card,String key,String title){
        TextView value=text(this,"—",15,REGULAR,SECONDARY);value.setFontFeatureSettings("tnum");value.setGravity(Gravity.END);value.setMaxWidth(dp(200));
        LinearLayout r=row(this,card,title,value);r.setMinimumHeight(dp(44));live.put(key,value);
    }
    void set(String key,String value){TextView t=live.get(key);if(t!=null&&!value.contentEquals(t.getText()))t.setText(value);}

    void refreshLive(){
        PerformanceController p=PerformanceController.active;long now=SystemClock.elapsedRealtime();
        if(p==null){for(TextView t:live.values())t.setText("—");set("pause",tr("Обои ещё не рисовали","Nothing rendered yet"));return;}
        if(p!=last){last=p;lastFrames=p.frames;lastEvents=p.eventCount;lastTime=now;}
        float span=Math.max(1,now-lastTime)/1000f,fps=(p.frames-lastFrames)/span,hz=(p.eventCount-lastEvents)/span;lastFrames=p.frames;lastEvents=p.eventCount;lastTime=now;
        set("fps",String.format(Locale.ROOT,"%.1f",fps));
        set("frame",String.format(Locale.ROOT,"%.2f ms · %s %.2f",p.frameMs,tr("ср.","avg"),p.submitAverage));
        set("swap",String.format(Locale.ROOT,"%.2f ms",p.swapMs));
        set("dropped",String.valueOf(p.dropped));
        set("sensor",p.sensor);
        set("rate",String.format(Locale.ROOT,"%.0f Hz",hz));
        set("age",String.format(Locale.ROOT,"%.1f ms",p.sensorAgeMs));
        set("size",p.width+"×"+p.height+(p.pressure>0?" · −"+p.pressure:""));
        set("thermal",thermal(p.thermal));
        set("saver",p.saver?tr("Вкл","On"):tr("Выкл","Off"));
        String id=PerformanceController.effect;Effects.Effect e=Effects.get(id);set("effect",id.isEmpty()?"—":e.id.equals(id)?e.name(config.en()):id);
        set("battery",p.batteryPct+"%"+(p.charging?tr(" · заряжается"," · charging"):""));
        String reason=p.pauseReason();
        set("pause",reason==null?tr("Нет","No"):"tile".equals(reason)?tr("Плитка в шторке","Quick Settings tile"):tr("Низкий заряд","Low battery"));
    }
    String thermal(int s){
        String[] ru={"нет","лёгкий","умеренный","сильный","критический","аварийный","отключение"},en={"none","light","moderate","severe","critical","emergency","shutdown"};
        return s>=0&&s<ru.length?(config.en()?en:ru)[s]+" ("+s+")":String.valueOf(s);
    }
    void fillBattery(){
        battery.removeAllViews();
        String from=week?null:PerformanceController.EffectStats.today();
        List<PerformanceController.EffectStats.Row> rows=PerformanceController.EffectStats.summarize(PerformanceController.STATS.history(),from);
        double total=0;for(PerformanceController.EffectStats.Row r:rows)total+=r.busyMs;
        if(rows.isEmpty()){row(this,battery,tr("Пока нет данных","No data yet"),null);return;}
        for(PerformanceController.EffectStats.Row r:rows){
            Effects.Effect e=Effects.get(r.effect);String name=e.id.equals(r.effect)?e.name(config.en()):r.effect;
            TextView v=text(this,String.format(Locale.ROOT,"%s · %.0f%%",busy(r.busyMs),total>0?100*r.busyMs/total:0),15,REGULAR,SECONDARY);v.setFontFeatureSettings("tnum");
            LinearLayout line=row(this,battery,name,v);line.setMinimumHeight(dp(44));
            line.setContentDescription(name+", "+String.format(Locale.ROOT,"%s, %d %s",busy(r.busyMs),r.frames,tr("кадров","frames")));
        }
        TextView reset=action(tr("Сбросить статистику","Reset statistics"),0xffff453a);reset.setOnClickListener(v->{PerformanceController.STATS.clear();fillBattery();});row(this,battery,null,reset);
    }
    String busy(double ms){double s=ms/1000;return s<60?String.format(Locale.ROOT,tr("%.1f с","%.1f s"),s):s<3600?String.format(Locale.ROOT,tr("%.1f мин","%.1f min"),s/60):String.format(Locale.ROOT,tr("%.1f ч","%.1f h"),s/3600);}
    void fillLog(){
        String log=CrashLog.read();
        logView.setText(log.isEmpty()?tr("Сбоев не было.","No crashes logged."):log.length()>4000?"…\n"+log.substring(log.length()-4000):log);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP,log.isEmpty()?15:12);
    }
    void share(){
        String log=CrashLog.read();
        if(log.isEmpty()){Toast.makeText(this,tr("Журнал пуст","The log is empty"),Toast.LENGTH_SHORT).show();return;}
        Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"Parallaxia crash log").putExtra(Intent.EXTRA_TEXT,CrashLog.forSharing(log,SHARE_LIMIT));
        try{startActivity(Intent.createChooser(send,tr("Поделиться логом","Share log")));}catch(Exception e){Toast.makeText(this,tr("Некуда отправить","No app to share with"),Toast.LENGTH_SHORT).show();}
    }
    @Override protected void onResume(){super.onResume();main.post(tick);}
    @Override protected void onPause(){main.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void finish(){super.finish();overridePendingTransition(R.anim.ios_pop_in,R.anim.ios_pop_out);}
}
