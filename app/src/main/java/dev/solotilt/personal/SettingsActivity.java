package dev.solotilt.personal;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import static dev.solotilt.personal.Ios.*;

/** Global settings as an inset grouped list; every change is saved at once and picked up by the running wallpaper. */
public final class SettingsActivity extends Activity {
    WallpaperConfig config;Motion motion;volatile float rawX,rawY;LinearLayout list;
    final Handler main=new Handler(Looper.getMainLooper());
    String tr(String ru,String en){return config.en()?en:ru;}
    int dp(float n){return Ios.dp(this,n);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);config=WallpaperConfig.load(this);Ios.reduceMotion(config.reduceMotion);
        motion=new Motion(this,getDisplay(),(x,y,t)->{rawX=x;rawY=y;});
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);root.addView(nav,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        ImageView back=circle(this,Glyph.BACK,tr("Назад","Back"));back.setOnClickListener(v->finish());nav.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        root.setOnApplyWindowInsetsListener((v,i)->{Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            nav.setPadding(dp(16)+bars.left,bars.top+dp(8),dp(16)+bars.right,dp(8));list.setPadding(dp(16)+bars.left,bars.top+dp(64),dp(16)+bars.right,bars.bottom+dp(32));return i;});
        build();
        if(state==null)for(int i=0;i<list.getChildCount();i++)rise(list.getChildAt(i),Math.min(i,7));  // the list settles in, as when a page is pushed
    }
    void build(){
        list.removeAllViews();
        list.addView(text(this,tr("Настройки","Settings"),34,BOLD,LABEL));

        LinearLayout motionCard=group(this,list,tr("Движение","Motion"));
        unitSlider(this,motionCard,tr("Чувствительность","Sensitivity"),(config.sensitivity-.1f)/1.9f,v->{config.sensitivity=.1f+v/100f*1.9f;save();});
        unitSlider(this,motionCard,tr("Плавность","Smoothing"),config.smoothing,v->{config.smoothing=v/100f;save();});
        TextView neutral=text(this,tr("Задать","Set"),17,REGULAR,BLUE);neutral.setClickable(true);neutral.setAccessibilityDelegate(BUTTON);
        neutral.setOnClickListener(v->{config.neutralX=rawX;config.neutralY=rawY;save();v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);Toast.makeText(this,tr("Это положение теперь нейтральное","This position is now neutral"),Toast.LENGTH_SHORT).show();});
        row(this,motionCard,tr("Нейтраль — как сейчас","Neutral — current pose"),pressable(neutral));
        TextView resetNeutral=text(this,tr("Сбросить","Reset"),17,REGULAR,BLUE);resetNeutral.setClickable(true);resetNeutral.setAccessibilityDelegate(BUTTON);resetNeutral.setOnClickListener(v->{config.neutralX=config.neutralY=0;save();});
        row(this,motionCard,tr("Нейтраль — экраном вверх","Neutral — face up"),pressable(resetNeutral));
        toggle(motionCard,tr("Меньше движения","Reduce Motion"),config.reduceMotion,on->{config.reduceMotion=on;Ios.reduceMotion(on);});
        footer(this,list,tr("«Нейтраль» — положение телефона, при котором картинка ровная. Удобно, если держишь телефон под углом.","Neutral is the pose where the picture sits flat — handy if you usually hold the phone at an angle."));

        LinearLayout touchCard=group(this,list,tr("Касания","Touch"));
        toggle(touchCard,tr("Реакция на касания","Touch effects"),config.touch,on->config.touch=on);
        toggle(touchCard,tr("Вибрация в интерфейсе","Haptics"),config.haptics,on->config.haptics=on);
        footer(this,list,tr("Касания на рабочем столе передаёт лаунчер; некоторые лаунчеры их не передают.","On the home screen, touches come from the launcher; some launchers don't pass them on."));

        LinearLayout perf=group(this,list,tr("Производительность","Performance"));
        Display d=getSystemService(DisplayManager.class).getDisplay(0);int hz=d==null?60:Math.round(d.getRefreshRate());
        Segmented fps=new Segmented(this,tr("Авто","Auto"),"30","60");fps.select(config.fps,false);fps.listener=i->{config.fps=i;save();};
        row(this,perf,null,fps);
        Segmented quality=new Segmented(this,tr("Авто","Auto"),tr("Эконом","Battery"),tr("Баланс","Balanced"),tr("Макс","High"));quality.select(config.quality,false);quality.listener=i->{config.quality=i;save();};
        row(this,perf,null,quality);
        android.content.SharedPreferences prefs=ImageStore.prefs(this);
        Toggle low=new Toggle(this);low.setOn(prefs.getBoolean(PerformanceController.KEY_LOW_BATTERY,true),false);low.setContentDescription(tr("Пауза при низком заряде","Pause on low battery"));low.setHapticFeedbackEnabled(config.haptics);
        low.listener=on->prefs.edit().putBoolean(PerformanceController.KEY_LOW_BATTERY,on).apply();row(this,perf,tr("Пауза при низком заряде","Pause on low battery"),low);
        footer(this,list,tr("Частота кадров: «Авто» — до 60 кадров в секунду, при экономии энергии 30. Экран: "+hz+" Гц. Качество задаёт разрешение рендера; при нагреве оно снижается само. Без движения обои не перерисовываются. При заряде "+PerformanceController.LOW_BATTERY+"% и меньше без зарядки обои замирают.","Frame rate: Auto is up to 60 fps, 30 in battery saver. Display: "+hz+" Hz. Quality sets the render resolution and drops by itself when the phone heats up. A still wallpaper does not redraw. At "+PerformanceController.LOW_BATTERY+"% or less, unplugged, the wallpaper holds still."));

        LinearLayout language=group(this,list,tr("Язык","Language"));
        Segmented lang=new Segmented(this,"Русский","English");lang.select(config.en()?1:0,false);
        lang.listener=i->{config.language=i==1?"en":"ru";save();main.postDelayed(this::recreate,250);};row(this,language,null,lang);

        LinearLayout about=group(this,list,tr("О приложении","About"));
        String version="";try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        LinearLayout versionRow=row(this,about,tr("Версия","Version"),text(this,version,17,REGULAR,SECONDARY));
        versionRow.setOnLongClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);openDiagnostics();return true;});  // hidden shortcut, as in many iOS apps
        LinearLayout author=row(this,about,tr("Автор — Kamradik","Author — Kamradik"),chevron());author.setClickable(true);author.setFocusable(true);
        author.setOnClickListener(v->{startActivity(new Intent(this,AuthorActivity.class));overridePendingTransition(R.anim.ios_push_in,R.anim.ios_push_out);});
        footer(this,list,tr("Независимая реализация по анализу Solo Tilt. Оптическая модель основана на DuoLikeAnimation, © 2026 Elijah Semyonov, MIT. Работает без интернета.","An independent implementation based on an analysis of Solo Tilt. The optical model is based on DuoLikeAnimation, © 2026 Elijah Semyonov, MIT. Works offline."));

        LinearLayout developer=group(this,list,tr("Для разработчика","Developer"));
        LinearLayout diagnostics=row(this,developer,tr("Диагностика","Diagnostics"),chevron());diagnostics.setClickable(true);diagnostics.setFocusable(true);diagnostics.setOnClickListener(v->openDiagnostics());
        footer(this,list,tr("Кадры, датчик, нагрев, расход батареи по эффектам и журнал сбоев.","Frames, sensor, heat, battery use by effect and the crash log."));
        list.setHapticFeedbackEnabled(config.haptics);
    }
    void openDiagnostics(){startActivity(new Intent(this,DebugActivity.class));overridePendingTransition(R.anim.ios_push_in,R.anim.ios_push_out);}
    interface Change{void set(boolean on);}
    void toggle(LinearLayout card,String title,boolean value,Change change){Toggle t=new Toggle(this);t.setOn(value,false);t.setContentDescription(title);t.setHapticFeedbackEnabled(config.haptics);t.listener=on->{change.set(on);save();};row(this,card,title,t);}
    ImageView chevron(){ImageView v=new ImageView(this);v.setImageDrawable(new Glyph(this,Glyph.CHEVRON,TERTIARY,16));return v;}
    void save(){config.save(this);}
    @Override protected void onResume(){super.onResume();motion.start();}
    @Override protected void onPause(){motion.stop();save();super.onPause();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);motion.close();super.onDestroy();}
    @Override public void finish(){super.finish();overridePendingTransition(R.anim.ios_pop_in,R.anim.ios_pop_out);}
}
