package dev.solotilt.personal;

import android.animation.*;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;
import java.util.concurrent.*;
import static dev.solotilt.personal.Ios.*;

public final class MainActivity extends Activity implements SurfaceHolder.Callback {
    static final String[] PRESETS={"Розовые ленты","Синие ленты","Оранжевые ленты"};
    RenderLoop renderer;Motion motion;TextView angle,status,choose,install,hint;View dot;LinearLayout header,sheet,manualRow;Toggle toggle;Slider slider;
    final Tile[] tiles=new Tile[4];
    boolean resumed,ready,manual,importing,hidden;long labelTime;int bottomInset;
    final ExecutorService imports=Executors.newSingleThreadExecutor();
    int dp(float n){return Ios.dp(this,n);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        SurfaceView preview=new SurfaceView(this);root.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        renderer=new RenderLoop(this,error->runOnUiThread(()->{if(!isDestroyed())swap(status,error);}));
        motion=new Motion(this,getDisplay(),(x,y)->{if(!manual){renderer.tilt(x,y);if(SystemClock.uptimeMillis()-labelTime>150){angle.setText(String.format(Locale.ROOT,"Наклон %.0f°",Math.hypot(x,y)));labelTime=SystemClock.uptimeMillis();}}});
        preview.getHolder().addCallback(this);
        // Lock-screen style scrims keep white type legible over any photo.
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x8c000000,0x00000000,0x00000000,0x99000000}));root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        root.setOnClickListener(v->setHidden(!hidden));

        header=new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);root.addView(header,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);header.addView(bar);
        TextView title=text(this,"Tilt",34,BOLD,LABEL);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        ImageView info=circle(this,Glyph.INFO,"О приложении");bar.addView(info,new LinearLayout.LayoutParams(dp(40),dp(40)));info.setOnClickListener(v->about());
        ImageView author=new ImageView(this);author.setImageResource(R.drawable.author_avatar);author.setScaleType(ImageView.ScaleType.CENTER_CROP);author.setForeground(new Shape(0,-1).stroke(px(this,1.5f),0x66ffffff));
        author.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline o){o.setOval(0,0,v.getWidth(),v.getHeight());}});author.setClipToOutline(true);author.setContentDescription("Об авторе");author.setClickable(true);
        LinearLayout.LayoutParams authorParams=new LinearLayout.LayoutParams(dp(40),dp(40));authorParams.leftMargin=dp(10);bar.addView(pressable(author),authorParams);
        author.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);startActivity(new Intent(this,AuthorActivity.class));overridePendingTransition(R.anim.ios_push_in,R.anim.ios_push_out);});
        TextView subtitle=text(this,"Твоя картинка. Новая перспектива.",15,REGULAR,0xccffffff);header.addView(subtitle);
        LinearLayout chip=new LinearLayout(this);chip.setGravity(Gravity.CENTER_VERTICAL);chip.setBackground(glass(this,-1));chip.setPadding(dp(12),0,dp(14),0);
        LinearLayout.LayoutParams chipParams=new LinearLayout.LayoutParams(-2,dp(32));chipParams.topMargin=dp(14);header.addView(chip,chipParams);
        dot=new View(this);dot.setBackground(new Shape(GREEN,-1));LinearLayout.LayoutParams dotParams=new LinearLayout.LayoutParams(dp(7),dp(7));dotParams.rightMargin=dp(8);chip.addView(dot,dotParams);
        angle=text(this,"Наклони телефон",13,SEMIBOLD,LABEL);angle.setFontFeatureSettings("tnum");chip.addView(angle);

        sheet=new LinearLayout(this);sheet.setOrientation(LinearLayout.VERTICAL);sheet.setBackground(new Shape(SHEET,px(this,30)).glass(Math.max(1,px(this,.7f))));sheet.setPadding(dp(16),dp(8),dp(16),dp(16));sheet.setClickable(true);
        root.addView(sheet,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        LayoutTransition motionLayout=new LayoutTransition();motionLayout.enableTransitionType(LayoutTransition.CHANGING);motionLayout.setDuration(SMOOTH.duration);
        for(int type:new int[]{LayoutTransition.CHANGING,LayoutTransition.CHANGE_APPEARING,LayoutTransition.CHANGE_DISAPPEARING})motionLayout.setInterpolator(type,SMOOTH);sheet.setLayoutTransition(motionLayout);
        View grabber=new View(this);grabber.setBackground(new Shape(0x66ebebf5,-1));LinearLayout.LayoutParams grabberParams=new LinearLayout.LayoutParams(dp(36),dp(5));grabberParams.gravity=Gravity.CENTER_HORIZONTAL;grabberParams.bottomMargin=dp(12);sheet.addView(grabber,grabberParams);
        TextView section=text(this,"Фон",15,SEMIBOLD,LABEL);section.setPadding(dp(4),0,0,dp(10));sheet.addView(section);
        LinearLayout row=new LinearLayout(this);sheet.addView(row);
        for(int i=0;i<4;i++){
            final int index=i-1;Tile tile=new Tile(i==0?null:ImageStore.preset(index,dp(74)/720f));tiles[i]=tile;tile.setContentDescription(i==0?"Своё фото":PRESETS[index]);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=i==0?0:dp(8);row.addView(pressable(tile),lp);
            tile.setOnClickListener(v->{if(importing)return;v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);if(index<0)photoTile();else choosePreset(index);});
        }
        status=text(this,ImageStore.prefs(this).getBoolean("custom",false)?"Твоя картинка сохранена":"Выбери своё фото или образец",13,REGULAR,SECONDARY);status.setGravity(Gravity.CENTER);status.setPadding(0,dp(12),0,dp(12));sheet.addView(status);
        sheet.addView(hairline(this),new LinearLayout.LayoutParams(-1,Math.max(1,dp(.5f))));
        LinearLayout toggleRow=new LinearLayout(this);toggleRow.setGravity(Gravity.CENTER_VERTICAL);toggleRow.setPadding(dp(4),dp(8),0,dp(8));sheet.addView(toggleRow);
        toggleRow.addView(text(this,"Ручной наклон",17,REGULAR,LABEL),new LinearLayout.LayoutParams(0,-2,1));
        toggle=new Toggle(this);toggle.setContentDescription("Ручной наклон");toggleRow.addView(toggle);toggle.listener=this::setManual;
        manualRow=new LinearLayout(this);manualRow.setGravity(Gravity.CENTER_VERTICAL);manualRow.setVisibility(View.GONE);sheet.addView(manualRow);
        manualRow.addView(text(this,"−120°",12,MEDIUM,TERTIARY));
        slider=new Slider(this,-120,120);slider.setContentDescription("Ручной наклон от минус 120 до 120 градусов");manualRow.addView(slider,new LinearLayout.LayoutParams(0,-2,1));
        manualRow.addView(text(this,"120°",12,MEDIUM,TERTIARY));
        slider.listener=value->{if(manual){renderer.tilt(value,0);angle.setText("Наклон "+value+"° · вручную");}};
        LinearLayout actions=new LinearLayout(this);LinearLayout.LayoutParams actionsParams=new LinearLayout.LayoutParams(-1,dp(52));actionsParams.topMargin=dp(8);sheet.addView(actions,actionsParams);
        choose=capsule(this,"Выбрать фото",false);actions.addView(choose,new LinearLayout.LayoutParams(0,-1,1));choose.setOnClickListener(v->pick());
        install=capsule(this,"Установить обои",true);LinearLayout.LayoutParams installParams=new LinearLayout.LayoutParams(0,-1,1);installParams.leftMargin=dp(10);actions.addView(install,installParams);install.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);installWallpaper();});

        hint=text(this,"Коснись, чтобы вернуть панель",13,MEDIUM,LABEL);hint.setBackground(glass(this,-1));hint.setGravity(Gravity.CENTER);hint.setPadding(dp(16),0,dp(16),0);hint.setAlpha(0);
        root.addView(hint,new FrameLayout.LayoutParams(-2,dp(36),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());bottomInset=bars.bottom;
            header.setPadding(dp(20)+bars.left,dp(10)+bars.top,dp(20)+bars.right,0);
            setMargins(sheet,dp(10)+bars.left,dp(10)+bars.right,dp(10)+bars.bottom);setMargins(hint,0,0,dp(24)+bars.bottom);return insets;
        });
        selectTile();refreshThumbnail();
        if(motion.sensor==null){status.setText("Датчик не найден. Включи ручной наклон.");dot.setBackground(new Shape(0xff8e8e93,-1));}
        if(state==null){
            for(int i=0;i<header.getChildCount();i++)rise(header.getChildAt(i),i);
            sheet.setAlpha(0);sheet.post(()->{sheet.setTranslationY(sheet.getHeight()+dp(40));sheet.setAlpha(1);spring(sheet,SMOOTH).translationY(0).setStartDelay(160);});
        }
    }
    static void setMargins(View v,int left,int right,int bottom){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)v.getLayoutParams();p.leftMargin=left;p.rightMargin=right;p.bottomMargin=bottom;v.setLayoutParams(p);}

    void about(){alert(this,"Tilt — свои обои","Независимая Android-реализация по анализу Solo Tilt.\n\nФото остаётся на телефоне. Эффект начинается от положения экраном вверх; при наклоне появляются перспектива и размытие.\n\nОптическая модель основана на DuoLikeAnimation, © 2026 Elijah Semyonov, MIT. Полный текст лицензии включён в APK.\n\nТочное совпадение с записью и работа на конкретной версии One UI требуют проверки на телефоне.","Понятно");}
    /** Hides every control so the wallpaper can be judged full screen, as on the iOS lock-screen editor. */
    void setHidden(boolean value){
        hidden=value;float drop=sheet.getHeight()+dp(10)+bottomInset+dp(30);
        spring(header,SMOOTH).alpha(value?0:1).translationY(value?-dp(24):0);spring(sheet,SMOOTH).translationY(value?drop:0);
        if(value){hint.setTranslationY(dp(12));spring(hint,SMOOTH).alpha(1).translationY(0).setStartDelay(250).withEndAction(()->{if(hidden)hint.animate().alpha(0).setStartDelay(1600).setDuration(400).start();});}
        else spring(hint,SNAPPY).alpha(0);
    }
    void setManual(boolean value){
        manual=value;manualRow.setVisibility(value?View.VISIBLE:View.GONE);dot.setBackground(new Shape(value?ORANGE:motion.sensor==null?0xff8e8e93:GREEN,-1));
        if(value){motion.stop();renderer.tilt(slider.value,0);angle.setText("Наклон "+slider.value+"° · вручную");}else{angle.setText("Наклони телефон");if(resumed&&ready)motion.start();}
    }
    int selected(){SharedPreferences p=ImageStore.prefs(this);return p.getBoolean("custom",false)?0:Math.floorMod(p.getInt("preset",0),3)+1;}
    void selectTile(){int s=selected();for(int i=0;i<4;i++)tiles[i].setSelectedAnimated(i==s);}
    void choosePreset(int index){ImageStore.prefs(this).edit().putBoolean("custom",false).putInt("preset",index).putLong("revision",System.nanoTime()).apply();renderer.reload();selectTile();swap(status,"Образец «"+PRESETS[index]+"»");}
    void photoTile(){
        if(!ImageStore.hasCustom(this)||selected()==0){pick();return;}
        ImageStore.prefs(this).edit().putBoolean("custom",true).putLong("revision",System.nanoTime()).apply();renderer.reload();selectTile();swap(status,"Твоя картинка");
    }
    void pick(){Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(pick,10);}catch(ActivityNotFoundException e){swap(status,"На телефоне не найден системный выбор файлов");}}
    void installWallpaper(){Intent intent=new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new ComponentName(this,TiltWallpaper.class));try{startActivity(intent);}catch(ActivityNotFoundException e){try{startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));}catch(ActivityNotFoundException missing){swap(status,"Системный выбор живых обоев недоступен");}}}
    void refreshThumbnail(){Context app=getApplicationContext();int side=dp(74);imports.execute(()->{Bitmap b=null;try{b=ImageStore.thumbnail(app,side);}catch(Exception|OutOfMemoryError ignored){}final Bitmap thumb=b;runOnUiThread(()->{if(!isDestroyed())tiles[0].setImage(thumb);});});}
    void setImporting(boolean value){importing=value;for(View v:new View[]{choose,install}){v.setEnabled(!value);spring(v,SNAPPY).alpha(value?.45f:1);}}

    void update(){if(renderer==null)return;renderer.visible(resumed&&ready);if(resumed&&ready&&!manual)motion.start();else motion.stop();}
    public void surfaceCreated(SurfaceHolder h){}
    public void surfaceChanged(SurfaceHolder h,int format,int w,int height){renderer.attach(h.getSurface(),w,height);ready=true;update();}
    public void surfaceDestroyed(SurfaceHolder h){ready=false;update();renderer.detach();}
    protected void onResume(){super.onResume();resumed=true;update();}
    protected void onPause(){resumed=false;update();super.onPause();}
    protected void onDestroy(){imports.shutdown();motion.stop();renderer.close();super.onDestroy();}
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=10||result!=RESULT_OK||data==null||data.getData()==null)return;
        setImporting(true);swap(status,"Готовим картинку…");android.net.Uri uri=data.getData();Context app=getApplicationContext();
        imports.execute(()->{String failure=null;try{ImageStore.importImage(app,uri);}catch(Exception|OutOfMemoryError e){failure="Не удалось открыть фото. Попробуй другой файл.";}final String message=failure;runOnUiThread(()->{
            if(isDestroyed())return;setImporting(false);swap(status,message==null?"Твоя картинка сохранена на телефоне":message);
            status.performHapticFeedback(message==null?HapticFeedbackConstants.CONFIRM:HapticFeedbackConstants.REJECT);
            if(message==null){renderer.reload();selectTile();refreshThumbnail();}
        });});
    }

    /** Wallpaper swatch: continuous-corner thumbnail with the iOS selection ring drawn outside a small gap. */
    final class Tile extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),ring=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path(),outer=new Path();final RectF box=new RectF();
        final Shape empty=glass(MainActivity.this,14);final Glyph plus=new Glyph(MainActivity.this,Glyph.PHOTO,LABEL,24);Bitmap image;float selection;ValueAnimator animator;
        Tile(Bitmap image){super(MainActivity.this);setClickable(true);setFocusable(true);ring.setStyle(Paint.Style.STROKE);ring.setColor(LABEL);setImage(image);}
        void setImage(Bitmap next){image=next;paint.setShader(null);requestLayout();invalidate();}
        void setSelectedAnimated(boolean value){
            setSelected(value);if(animator!=null)animator.cancel();
            animator=ValueAnimator.ofFloat(selection,value?1:0);animator.setInterpolator(BOUNCY);animator.setDuration(BOUNCY.duration);animator.addUpdateListener(a->{selection=(float)a.getAnimatedValue();invalidate();});animator.start();
        }
        @Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w);setMeasuredDimension(width,Math.round(width*1.42f));}
        @Override protected void onDraw(Canvas c){
            float d=px(getContext(),1),gap=5.5f*d,radius=14*d;box.set(gap,gap,getWidth()-gap,getHeight()-gap);squircle(path,box,radius);
            if(image!=null){
                if(paint.getShader()==null){BitmapShader shader=new BitmapShader(image,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);float s=Math.max(box.width()/image.getWidth(),box.height()/image.getHeight());Matrix m=new Matrix();m.setScale(s,s);m.postTranslate(box.centerX()-image.getWidth()*s/2,box.centerY()-image.getHeight()*s/2);shader.setLocalMatrix(m);paint.setShader(shader);}
                c.drawPath(path,paint);
            }else{
                empty.setBounds((int)box.left,(int)box.top,(int)box.right,(int)box.bottom);empty.draw(c);
                int size=plus.getIntrinsicWidth();plus.setBounds((int)(box.centerX()-size/2f),(int)(box.centerY()-size/2f),(int)(box.centerX()+size/2f),(int)(box.centerY()+size/2f));plus.draw(c);
            }
            if(selection>.01f){ring.setStrokeWidth(2.5f*d);ring.setAlpha((int)(255*Math.min(1,selection)));float inset=1.25f*d+(1-Math.min(1,selection))*3*d;box.set(inset,inset,getWidth()-inset,getHeight()-inset);c.drawPath(squircle(outer,box,radius+gap-inset),ring);}
        }
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){paint.setShader(null);}
    }
}
