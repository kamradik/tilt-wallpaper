package dev.solotilt.personal;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity implements SurfaceHolder.Callback {
    RenderLoop renderer;Motion motion;WallpaperConfig config;SurfaceView preview;
    LinearLayout panel,parameterBox;TextView description,status,debug;Button choose,apply;ProgressBar progress;
    final View[] effectCards=new View[5];final ImageView[] effectImages=new ImageView[5];
    final Handler main=new Handler(Looper.getMainLooper());final ExecutorService imports=Executors.newSingleThreadExecutor();
    boolean resumed,ready,importing,bright,building;volatile boolean manual;int loadGeneration;
    Bitmap thumbnail,backdrop;float touchX,touchY;long lastTouch;
    String tr(String ru,String en){return config.en()?en:ru;}
    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
    LinearLayout.LayoutParams row(int height,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,height<0?height:dp(height));p.topMargin=dp(top);return p;}
    void glass(View v,int radius,boolean primary){v.setBackground(new GlassDrawable(v,backdrop,dp(radius),primary,bright));v.setElevation(dp(primary?3:6));}
    Button button(String title,boolean primary){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(null,1);b.setTextColor(primary?0xff172027:0xfff2f4f6);b.setMinimumHeight(dp(48));glass(b,26,primary);return b;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);config=WallpaperConfig.load(this);
        renderer=new RenderLoop(this,code->runOnUiThread(()->showError(code)));
        motion=new Motion(this,getDisplay(),(x,y,t)->{if(!manual)renderer.tilt(x,y,t);});renderer.sensorInfo(motion.description());
        buildUi();loadThumbnail();
    }
    void buildUi(){
        building=true;
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff10151a);
        preview=new SurfaceView(this);root.addView(preview,new FrameLayout.LayoutParams(-1,-1));preview.getHolder().addCallback(this);
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x95101920,0x00101920,0x0a101920,0xc0101920}));root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout overlay=new LinearLayout(this);overlay.setOrientation(1);overlay.setPadding(dp(20),dp(16),dp(20),dp(14));root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        overlay.setOnApplyWindowInsetsListener((v,insets)->{Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(dp(20)+i.left,dp(12)+i.top,dp(20)+i.right,dp(12)+i.bottom);return insets;});
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);overlay.addView(top,row(52,0));
        TextView logo=text("Solo",34,Color.WHITE);logo.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));logo.setLetterSpacing(-.035f);top.addView(logo,new LinearLayout.LayoutParams(0,-2,1));
        Button author=button("K",false);author.setContentDescription(tr("Об авторе Kamradik","About Kamradik"));top.addView(author,new LinearLayout.LayoutParams(dp(48),dp(48)));author.setOnClickListener(v->startActivity(new Intent(this,AuthorActivity.class)));
        Button settings=button("☷",false);settings.setTextSize(23);settings.setContentDescription(tr("Настройки","Settings"));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(48),dp(48));sp.leftMargin=dp(10);top.addView(settings,sp);settings.setOnClickListener(v->advanced());
        TextView subtitle=text(tr("Твоя фотография. Живое ощущение.","Your photo. A living feeling."),13,0xffe3e7e9);subtitle.setPadding(dp(2),dp(6),0,0);overlay.addView(subtitle);
        View hero=new View(this);overlay.addView(hero,new LinearLayout.LayoutParams(-1,0,1));hero.setContentDescription(tr("Интерактивный предпросмотр. Наклони телефон или проведи пальцем.","Interactive preview. Tilt your phone or swipe."));
        hero.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN||event.getActionMasked()==MotionEvent.ACTION_MOVE){
                long now=SystemClock.uptimeMillis();if(now-lastTouch>=16){renderer.touch(event.getRawX()/Math.max(1,root.getWidth()),event.getRawY()/Math.max(1,root.getHeight()));lastTouch=now;}
                return true;
            }if(event.getActionMasked()==MotionEvent.ACTION_UP){v.performClick();return true;}return true;
        });hero.setOnClickListener(v->{});
        ScrollView sheet=new ScrollView(this);sheet.setFillViewport(false);sheet.setVerticalScrollBarEnabled(false);
        int available=getResources().getConfiguration().screenHeightDp;overlay.addView(sheet,new LinearLayout.LayoutParams(-1,dp(Math.min(430,Math.max(320,available-230)))));
        panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(14),dp(14),dp(14),dp(14));glass(panel,30,false);sheet.addView(panel,new ScrollView.LayoutParams(-1,-2));
        HorizontalScrollView carousel=new HorizontalScrollView(this);carousel.setHorizontalScrollBarEnabled(false);carousel.setClipToPadding(false);LinearLayout effects=new LinearLayout(this);effects.setPadding(dp(2),dp(4),dp(2),dp(4));carousel.addView(effects);panel.addView(carousel,row(100,0));
        for(int i=0;i<5;i++){
            final int id=i;LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setGravity(Gravity.CENTER);card.setPadding(dp(5),dp(5),dp(5),dp(5));glass(card,17,false);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(78),dp(86));cp.setMargins(dp(3),dp(3),dp(3),dp(3));effects.addView(card,cp);effectCards[i]=card;
            ImageView image=new ImageView(this);image.setImageBitmap(thumbnail);image.setScaleType(ImageView.ScaleType.CENTER_CROP);GradientDrawable clip=new GradientDrawable();clip.setColor(0xff374650);clip.setCornerRadius(dp(11));image.setBackground(clip);image.setClipToOutline(true);card.addView(image,new LinearLayout.LayoutParams(dp(62),dp(46)));effectImages[i]=image;
            if(i==2)image.setColorFilter(0x19fff1d9,PorterDuff.Mode.SRC_ATOP);if(i==3&&Build.VERSION.SDK_INT>=31)image.setRenderEffect(RenderEffect.createBlurEffect(dp(1),dp(1),Shader.TileMode.CLAMP));
            TextView name=text(Effects.SHORT[i],11,Color.WHITE);name.setGravity(Gravity.CENTER);card.addView(name,row(-2,6));card.setContentDescription(Effects.NAMES[i]);card.setFocusable(true);card.setOnClickListener(v->selectEffect(id));
        }
        description=text("",12,0xffd4dee3);description.setPadding(dp(4),dp(10),dp(4),dp(4));description.setMinHeight(dp(40));panel.addView(description);
        parameterBox=new LinearLayout(this);parameterBox.setOrientation(1);panel.addView(parameterBox);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(View.GONE);panel.addView(progress,row(3,5));
        status=text("",11,0xffd4dee3);status.setPadding(dp(4),dp(2),dp(4),0);status.setVisibility(View.GONE);panel.addView(status,row(-2,2));
        LinearLayout actions=new LinearLayout(this);panel.addView(actions,row(54,10));choose=button(tr("Выбрать фото","Choose photo"),false);apply=button(tr("Установить","Apply wallpaper"),true);actions.addView(choose,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-1,1.15f);ap.leftMargin=dp(8);actions.addView(apply,ap);choose.setOnClickListener(v->photoOptions());apply.setOnClickListener(v->install());
        if(renderer.performance.debug){debug=text("",10,0xffbde8d5);debug.setTypeface(Typeface.MONOSPACE);overlay.addView(debug,row(-2,4));}
        setContentView(root);building=false;updateSelection();updateControls();if(motion.sensor==null)message(tr("Датчик недоступен. Ручной наклон — в настройках.","No motion sensor. Manual tilt is available in settings."));
    }
    void selectEffect(int id){if(importing)return;config.effect=id;persist();updateSelection();updateControls();if(Effects.DEPTH[id])message(tr("Глубина готовится один раз. Автокарта — приближённая.","Depth is prepared once. Automatic depth is approximate."));else status.setVisibility(View.GONE);}
    void updateSelection(){for(int i=0;i<5;i++){View v=effectCards[i];v.setSelected(i==config.effect);v.setAlpha(i==config.effect?1:.66f);if(!config.reduceMotion&&android.animation.ValueAnimator.areAnimatorsEnabled())v.animate().scaleX(i==config.effect?1.04f:1).scaleY(i==config.effect?1.04f:1).setDuration(150).start();else{v.setScaleX(1);v.setScaleY(1);}}
        description.setText(Effects.description(config.effect,config.en()));}
    interface Value {void set(float value);}
    void slider(LinearLayout parent,String label,float value,Value change){
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);parent.addView(line,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView caption=text(label,12,0xfff1f3f4);line.addView(caption,new LinearLayout.LayoutParams(dp(100),-2));
        SeekBar s=new SeekBar(this);s.setMax(100);s.setProgress(Math.round(value*100));s.setContentDescription(label);s.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffe2e8eb));s.setThumbTintList(android.content.res.ColorStateList.valueOf(0xfff8fafb));line.addView(s,new LinearLayout.LayoutParams(0,dp(48),1));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int p,boolean user){if(user)change.set(p/100f);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){saveNow();}});
    }
    void updateControls(){parameterBox.removeAllViews();int id=config.effect;
        String[] a=config.en()?new String[]{"Intensity","Depth","Light","Glass","Aperture"}:new String[]{"Сила","Глубина","Свет","Стекло","Диафрагма"};
        String[] b=config.en()?new String[]{"Smoothness","Motion","Width","Refraction","Focus travel"}:new String[]{"Плавность","Движение","Ширина","Преломление","Ход фокуса"};
        slider(parameterBox,a[id],config.a[id],v->{config.a[id]=v;persist();});slider(parameterBox,b[id],config.b[id],v->{config.b[id]=v;persist();});
    }
    final Runnable save=()->config.save(this);
    void persist(){renderer.settings(config);main.removeCallbacks(save);main.postDelayed(save,150);}
    void saveNow(){main.removeCallbacks(save);config.save(this);}
    void message(String s){if(isDestroyed()||status==null)return;status.setText(s);status.setVisibility(View.VISIBLE);}
    void showError(String code){if(isDestroyed())return;
        if("depth".equals(code)){config.effect=0;persist();updateSelection();updateControls();message(tr("Не удалось подготовить глубину. Выбран Classic; также доступен Light.","Depth could not be prepared. Classic selected; Light is also available."));}
        else if("image".equals(code))message(tr("Не удалось открыть фото. Выбери другое изображение.","Could not open this photo. Choose another image."));
        else message(tr("Эффект недоступен на этой поверхности. Показано статичное фото.","This surface cannot display the effect. Showing the still photo."));
    }
    void install(){saveNow();Intent intent=new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new ComponentName(this,TiltWallpaper.class));try{startActivity(intent);}catch(ActivityNotFoundException e){try{startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));}catch(ActivityNotFoundException missing){message(tr("Системная установка обоев недоступна.","System wallpaper picker is unavailable."));}}}
    void photoOptions(){new AlertDialog.Builder(this).setTitle(tr("Твоя фотография","Your photo")).setItems(config.en()?new String[]{"Choose photo","Rose ribbons","Blue ribbons","Amber ribbons"}:new String[]{"Выбрать фото","Розовые ленты","Синие ленты","Янтарные ленты"},(d,n)->{if(n==0)pick(10);else{ImageStore.prefs(this).edit().putBoolean("custom",false).putInt("preset",n-1).putLong("revision",System.nanoTime()).apply();renderer.reload();loadThumbnail();}}).show();}
    void pick(int request){
        Intent i=request==10&&Build.VERSION.SDK_INT>=33?new Intent("android.provider.action.PICK_IMAGES").setType("image/*"):new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(i,request);}catch(ActivityNotFoundException ex){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),request);}catch(ActivityNotFoundException e){message(tr("Не найдено приложение выбора файлов.","No image picker is available."));}}
    }
    void advanced(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(22),dp(12),dp(22),dp(16));box.setBackgroundColor(0xff172027);ScrollView scroll=new ScrollView(this);scroll.addView(box);
        String[] levels={"Auto","Battery","Balanced","High"};Spinner quality=new Spinner(this);quality.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,levels));quality.setSelection(config.quality);quality.setContentDescription(tr("Качество","Quality"));box.addView(text(tr("Качество и энергия","Quality & energy"),15,Color.WHITE));box.addView(quality,row(48,2));
        quality.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int i,long id){if(config.quality!=i){config.quality=i;persist();}}public void onNothingSelected(android.widget.AdapterView<?> p){}});
        slider(box,tr("Датчики","Sensitivity"),(config.sensitivity-.1f)/1.9f,v->{config.sensitivity=.1f+v*1.9f;persist();});slider(box,tr("Сглаживание","Smoothing"),config.smoothing,v->{config.smoothing=v;persist();});
        Switch reduce=new Switch(this);reduce.setText(tr("Меньше движения","Reduce Motion"));reduce.setTextColor(Color.WHITE);reduce.setChecked(config.reduceMotion);box.addView(reduce,row(52,4));reduce.setOnCheckedChangeListener((b,on)->{config.reduceMotion=on;persist();updateSelection();});
        Switch touch=new Switch(this);touch.setText(tr("Рябь от касаний","Touch ripples"));touch.setTextColor(Color.WHITE);touch.setChecked(config.touch);box.addView(touch,row(52,0));touch.setOnCheckedChangeListener((b,on)->{config.touch=on;persist();});
        Button neutral=button(tr("Это положение — нейтральное","Set current position as neutral"),false);box.addView(neutral,row(48,8));neutral.setOnClickListener(v->{config.neutralX=renderer.rawX;config.neutralY=renderer.rawY;persist();});
        slider(box,tr("Масштаб","Zoom"),(config.scale-1.02f)/1.48f,v->{config.scale=1.02f+v*1.48f;persist();});slider(box,tr("Кадр по X","Crop X"),config.cropX,v->{config.cropX=v;persist();});slider(box,tr("Кадр по Y","Crop Y"),config.cropY,v->{config.cropY=v;persist();});
        Button map=button(tr("Импорт карты глубины","Import depth map"),false);box.addView(map,row(48,8));map.setOnClickListener(v->pick(11));TextView note=text(tr("Автоглубина приближённая. В готовой карте белое — ближе, чёрное — дальше. Карта должна совпадать с фото.","Automatic depth is approximate. In an imported map white is near, black is far. The map must align with your photo."),12,0xffbdcbd3);box.addView(note,row(-2,8));
        Switch hand=new Switch(this);hand.setText(tr("Ручной предпросмотр","Manual preview"));hand.setTextColor(Color.WHITE);hand.setChecked(manual);box.addView(hand,row(52,8));hand.setOnCheckedChangeListener((b,on)->{manual=on;update();if(!on)renderer.tilt(renderer.rawX,renderer.rawY);});slider(box,tr("Наклон","Tilt"),.5f,v->{if(manual)renderer.tilt((v-.5f)*160,config.neutralY);});
        Button reset=button(tr("Сбросить эффект","Reset effect"),false);box.addView(reset,row(48,8));reset.setOnClickListener(v->{config.resetEffect();config.neutralX=config.neutralY=0;persist();updateControls();Toast.makeText(this,tr("Настройки эффекта сброшены","Effect reset"),Toast.LENGTH_SHORT).show();});
        Button lang=button(config.en()?"Язык: русский":"Language: English",false);box.addView(lang,row(48,8));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(tr("Настройки Solo","Solo settings")).setView(scroll).setPositiveButton(tr("Готово","Done"),(d,w)->saveNow()).create();lang.setOnClickListener(v->{config.language=config.en()?"ru":"en";saveNow();dialog.dismiss();recreate();});dialog.setOnDismissListener(d->saveNow());dialog.show();
    }
    void loadThumbnail(){final int request=++loadGeneration;imports.execute(()->{
        Bitmap thumb=null,blur=null;boolean isBright=false;
        try{Bitmap image=ImageStore.load(getApplicationContext());thumb=Bitmap.createScaledBitmap(image,120,180,true);blur=Bitmap.createScaledBitmap(image,16,24,true);if(image!=thumb&&image!=blur)image.recycle();int[] pixels=new int[16*24];blur.getPixels(pixels,0,16,0,0,16,24);double l=0;for(int p:pixels)l+=((p>>16)&255)*.2126+((p>>8)&255)*.7152+(p&255)*.0722;isBright=l/pixels.length>145;}catch(Exception|OutOfMemoryError e){}
        final Bitmap t=thumb,b=blur;final boolean light=isBright;runOnUiThread(()->{if(isDestroyed()||request!=loadGeneration){if(t!=null)t.recycle();if(b!=null)b.recycle();return;}Bitmap old=thumbnail;thumbnail=t;backdrop=b;bright=light;for(ImageView im:effectImages)im.setImageBitmap(t);glass(panel,30,false);glass(choose,26,false);glass(apply,26,true);if(old!=null)old.recycle();});
    });}
    void update(){if(renderer==null)return;renderer.visible(resumed&&ready);if(resumed&&ready&&!manual)motion.start();else motion.stop();}
    public void surfaceCreated(SurfaceHolder h){}
    public void surfaceChanged(SurfaceHolder h,int format,int w,int height){renderer.attach(h.getSurface(),w,height);ready=true;update();}
    public void surfaceDestroyed(SurfaceHolder h){ready=false;update();renderer.detach();}
    final Runnable diagnostic=new Runnable(){public void run(){if(resumed){if(renderer.performance.debug&&debug!=null){renderer.sensorInfo(motion.description());debug.setText(renderer.performance.lastSummary);}if(Effects.DEPTH[config.effect]&&!importing){progress.setVisibility(renderer.loading?View.VISIBLE:View.GONE);if(renderer.depthReady)message(renderer.manualDepth?tr("Используется твоя карта глубины.","Using your imported depth map."):tr("Приближённая глубина · импорт карты в настройках","Approximate depth · import a map in settings"));}main.postDelayed(this,1000);}}};
    @Override protected void onResume(){super.onResume();resumed=true;update();main.postDelayed(diagnostic,1000);}
    @Override protected void onPause(){saveNow();resumed=false;update();main.removeCallbacks(diagnostic);super.onPause();}
    @Override protected void onDestroy(){loadGeneration++;main.removeCallbacksAndMessages(null);imports.shutdown();motion.close();renderer.close();super.onDestroy();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if((request!=10&&request!=11)||result!=RESULT_OK||data==null||data.getData()==null)return;
        importing=true;choose.setEnabled(false);apply.setEnabled(false);progress.setVisibility(View.VISIBLE);message(tr("Подготовка изображения…","Preparing image…"));Uri uri=data.getData();Context app=getApplicationContext();
        imports.execute(()->{boolean ok=true;try{if(request==10){ImageStore.importImage(app,uri);DepthMapManager.prune(app);}else DepthMapManager.importDepth(app,uri);}catch(Exception|OutOfMemoryError e){ok=false;}final boolean success=ok;runOnUiThread(()->{if(isDestroyed())return;importing=false;choose.setEnabled(true);apply.setEnabled(true);progress.setVisibility(View.GONE);if(success){renderer.reload();loadThumbnail();message(tr("Готово. Можно настроить эффект.","Ready. Adjust your effect."));}else showError(request==11?"depth":"image");});});
    }
}
