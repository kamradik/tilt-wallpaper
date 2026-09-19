package dev.solotilt.personal;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity implements SurfaceHolder.Callback {
    RenderLoop renderer;Motion motion;TextView angle,status;LinearLayout controls;Button choose,install;
    boolean resumed,ready,manual,importing;long labelTime;
    final ExecutorService imports=Executors.newSingleThreadExecutor();
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),0x30ffffff);return d;}
    TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    Button button(String title,boolean primary){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(null,1);b.setTextColor(primary?0xff18151b:Color.WHITE);b.setBackground(rounded(primary?0xfff9edf1:0xd5292931,28));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(10);b.setLayoutParams(p);return b;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff131118);setContentView(root);
        SurfaceView preview=new SurfaceView(this);root.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        renderer=new RenderLoop(this,error->runOnUiThread(()->{if(!isDestroyed()){status.setText(error);}}));
        motion=new Motion(this,getDisplay(),(x,y)->{if(!manual){renderer.tilt(x,y);if(android.os.SystemClock.uptimeMillis()-labelTime>150){angle.setText(String.format(java.util.Locale.ROOT,"Наклон %.0f°",Math.hypot(x,y)));labelTime=android.os.SystemClock.uptimeMillis();}}});
        preview.getHolder().addCallback(this);
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x990b0711,0x00000000,0x00000000,0xdf0b0711}));root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout overlay=new LinearLayout(this);overlay.setOrientation(1);overlay.setPadding(dp(24),dp(24),dp(24),dp(20));root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        overlay.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(dp(24)+i.left,dp(24)+i.top,dp(24)+i.right,dp(20)+i.bottom);return insets;});
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);overlay.addView(heading);
        TextView title=text("Tilt",38,Color.WHITE);title.setTypeface(null,1);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button author=new Button(this);author.setText("Об авторе ↗");author.setAllCaps(false);author.setTextSize(12);author.setTextColor(Color.WHITE);author.setBackground(rounded(0xb5292433,22));heading.addView(author,new LinearLayout.LayoutParams(dp(116),dp(44)));author.setOnClickListener(v->startActivity(new Intent(this,AuthorActivity.class)));
        overlay.addView(text("Твоя картинка. Новая перспектива.",14,0xffe2d5e0));angle=text("Наклони телефон",12,0xffc9bdca);angle.setPadding(0,dp(12),0,0);overlay.addView(angle);
        View space=new View(this);overlay.addView(space,new LinearLayout.LayoutParams(1,0,1));space.setOnClickListener(v->{controls.setVisibility(controls.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);});
        controls=new LinearLayout(this);controls.setOrientation(1);overlay.addView(controls);
        LinearLayout samples=new LinearLayout(this);samples.setGravity(Gravity.CENTER);controls.addView(samples);
        for(int i=0;i<3;i++){final int index=i;ImageView thumb=new ImageView(this);Bitmap full=ImageStore.preset(i);Bitmap small=Bitmap.createScaledBitmap(full,dp(66),dp(96),true);full.recycle();thumb.setImageBitmap(small);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);thumb.setBackground(rounded(0xff27222d,14));thumb.setClipToOutline(true);thumb.setContentDescription(new String[]{"Розовые ленты","Синие ленты","Оранжевые ленты"}[i]);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(66),dp(96));lp.setMargins(dp(5),0,dp(5),0);samples.addView(thumb,lp);thumb.setOnClickListener(v->{if(importing)return;ImageStore.prefs(this).edit().putBoolean("custom",false).putInt("preset",index).putLong("revision",System.nanoTime()).apply();renderer.reload();status.setText("Выбран образец");});}
        status=text(ImageStore.prefs(this).getBoolean("custom",false)?"Твоя картинка сохранена":"Выбери своё фото или образец",12,0xffeee0ea);status.setGravity(Gravity.CENTER);status.setPadding(0,dp(14),0,dp(4));controls.addView(status);
        choose=button("Выбрать свою картинку",false);controls.addView(choose);choose.setOnClickListener(v->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(pick,10);}catch(ActivityNotFoundException e){status.setText("На телефоне не найден системный выбор файлов");}});
        install=button("Установить живые обои",true);controls.addView(install);install.setOnClickListener(v->{Intent intent=new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new ComponentName(this,TiltWallpaper.class));try{startActivity(intent);}catch(ActivityNotFoundException e){try{startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));}catch(ActivityNotFoundException missing){status.setText("Системный выбор живых обоев недоступен");}}});
        TextView tune=text("Проверить эффект вручную",13,0xffead3df);tune.setGravity(Gravity.CENTER);tune.setPadding(0,dp(16),0,dp(12));controls.addView(tune);
        SeekBar slider=new SeekBar(this);slider.setMax(240);slider.setProgress(120);slider.setContentDescription("Ручной наклон от минус 120 до 120 градусов");slider.setVisibility(View.GONE);controls.addView(slider,new LinearLayout.LayoutParams(-1,dp(40)));
        tune.setOnClickListener(v->{manual=!manual;slider.setVisibility(manual?View.VISIBLE:View.GONE);tune.setText(manual?"Вернуться к датчикам":"Проверить эффект вручную");if(manual){motion.stop();renderer.tilt(slider.getProgress()-120,0);}else if(resumed&&ready)motion.start();});
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){if(manual){renderer.tilt(p-120,0);angle.setText("Наклон "+(p-120)+"° · вручную");}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        title.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Tilt — свои обои").setMessage("Независимая Android-реализация по анализу Solo Tilt.\n\nФото остаётся на телефоне. Эффект начинается от положения экраном вверх; при наклоне появляются перспектива и размытие.\n\nОптическая модель основана на DuoLikeAnimation, © 2026 Elijah Semyonov, MIT. Полный текст лицензии включён в APK.\n\nТочное совпадение с записью и работа на конкретной версии One UI требуют проверки на телефоне.").setPositiveButton("Понятно",null).show());
        if(motion.sensor==null)status.setText("Датчик не найден. Используй ручной предпросмотр.");
    }
    void update(){if(renderer==null)return;renderer.visible(resumed&&ready);if(resumed&&ready&&!manual)motion.start();else motion.stop();}
    public void surfaceCreated(SurfaceHolder h){}
    public void surfaceChanged(SurfaceHolder h,int format,int w,int height){renderer.attach(h.getSurface(),w,height);ready=true;update();}
    public void surfaceDestroyed(SurfaceHolder h){ready=false;update();renderer.detach();}
    protected void onResume(){super.onResume();resumed=true;update();}
    protected void onPause(){resumed=false;update();super.onPause();}
    protected void onDestroy(){imports.shutdown();motion.stop();renderer.close();super.onDestroy();}
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=10||result!=RESULT_OK||data==null||data.getData()==null)return;
        importing=true;choose.setEnabled(false);install.setEnabled(false);status.setText("Готовим картинку…");android.net.Uri uri=data.getData();Context app=getApplicationContext();
        imports.execute(()->{String failure=null;try{ImageStore.importImage(app,uri);}catch(Exception|OutOfMemoryError e){failure="Не удалось открыть фото. Попробуй другой файл.";}final String message=failure;runOnUiThread(()->{if(isDestroyed())return;importing=false;choose.setEnabled(true);install.setEnabled(true);status.setText(message==null?"Твоя картинка сохранена на телефоне":message);if(message==null)renderer.reload();});});
    }
}
