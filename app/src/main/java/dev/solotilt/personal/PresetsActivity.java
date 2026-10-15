package dev.solotilt.personal;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.*;
import static dev.solotilt.personal.Ios.*;
import static dev.solotilt.personal.WallpaperConfig.Preset;

/** Saved looks as an inset grouped list: save the current screen, apply to home/lock/both, rename, duplicate,
 *  export and import .tilt files, delete in edit mode. Also opens .tilt files sent from other apps. */
public final class PresetsActivity extends Activity {
    static final int EXPORT=1,IMPORT=2;static final String EXTRA_SLOT="slot";
    WallpaperConfig config;LinearLayout list;TextView edit;boolean editing,busy;String exporting;
    final Handler main=new Handler(Looper.getMainLooper());final ExecutorService io=Executors.newSingleThreadExecutor();
    String tr(String ru,String en){return config.en()?en:ru;}
    int dp(float n){return Ios.dp(this,n);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);config=WallpaperConfig.load(this);Ios.reduceMotion(config.reduceMotion);
        if(state!=null){exporting=state.getString("exporting");editing=state.getBoolean("editing");}
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);root.addView(nav,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        ImageView back=circle(this,Glyph.BACK,tr("Назад","Back"));back.setOnClickListener(v->finish());nav.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        nav.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        edit=capsule(this,"",false);edit.setOnClickListener(v->{editing=!editing;build();});nav.addView(edit,new LinearLayout.LayoutParams(-2,dp(44)));
        ImageView add=circle(this,Glyph.PLUS,tr("Сохранить текущий экран","Save the current screen"));add.setOnClickListener(v->saveCurrent());
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(44),dp(44));ap.leftMargin=dp(10);nav.addView(add,ap);
        root.setOnApplyWindowInsetsListener((v,i)->{Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            nav.setPadding(dp(16)+bars.left,bars.top+dp(8),dp(16)+bars.right,dp(8));list.setPadding(dp(16)+bars.left,bars.top+dp(64),dp(16)+bars.right,bars.bottom+dp(32));return i;});
        build();
        if(state==null)for(int i=0;i<list.getChildCount();i++)rise(list.getChildAt(i),Math.min(i,7));
        // Files of presets that never made it into the config (the app was killed mid-save) are cleaned up here.
        if(state==null){WallpaperConfig snapshot=config.copy();Context app=getApplicationContext();io.execute(()->Presets.prune(app,snapshot));handleOpen(getIntent());}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);handleOpen(intent);}
    /** A .tilt file opened from another app. */
    void handleOpen(Intent intent){if(intent!=null&&Intent.ACTION_VIEW.equals(intent.getAction())&&intent.getData()!=null)importFrom(intent.getData());}

    void build(){
        list.removeAllViews();edit.setText(editing?tr("Готово","Done"):tr("Править","Edit"));edit.setVisibility(config.presets.isEmpty()&&!editing?View.GONE:View.VISIBLE);
        list.addView(text(this,tr("Пресеты","Presets"),34,BOLD,LABEL));
        LinearLayout actions=group(this,list,null);
        link(actions,tr("Сохранить текущий экран","Save the current screen"),this::saveCurrent);
        link(actions,tr("Импорт файла .tilt","Import a .tilt file"),this::pickImport);
        footer(this,list,tr("Пресет запоминает эффект, все его настройки, кадр и копию фото. Видео до 50 МБ сохраняется целиком, длиннее — только первый кадр.","A preset keeps the effect, all its settings, the framing and a copy of the photo. Videos up to 50 MB are kept whole; larger ones keep only their first frame."));
        LinearLayout saved=group(this,list,tr("Сохранённые","Saved"));
        if(config.presets.isEmpty()){TextView none=text(this,tr("Пока пусто. Нажми +, чтобы сохранить то, что сейчас на экране.","Nothing yet. Tap + to save what is on screen now."),15,REGULAR,SECONDARY);none.setPadding(dp(16),dp(14),dp(16),dp(14));saved.addView(none);}
        for(Preset p:config.presets)presetRow(saved,p);
        list.setHapticFeedbackEnabled(config.haptics);
    }
    void link(LinearLayout card,String title,Runnable action){
        TextView t=text(this,title,17,REGULAR,BLUE);t.setClickable(true);t.setOnClickListener(v->{if(!busy)action.run();});row(this,card,null,pressable(t));
    }
    void presetRow(LinearLayout card,Preset p){
        separate(this,card);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(72));row.setPadding(dp(12),dp(8),dp(14),dp(8));row.setClickable(true);row.setFocusable(true);
        if(editing){ImageView minus=new ImageView(this);minus.setImageDrawable(new Minus());minus.setContentDescription(tr("Удалить","Delete"));minus.setClickable(true);minus.setOnClickListener(v->confirmDelete(p));
            LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(30),dp(30));mp.rightMargin=dp(10);row.addView(pressable(minus),mp);}
        ImageView thumb=new ImageView(this);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);thumb.setBackground(new Shape(0xff2c2c2e,px(this,8)));thumb.setOutlineProvider(ViewOutlineProvider.BACKGROUND);thumb.setClipToOutline(true);
        row.addView(thumb,new LinearLayout.LayoutParams(dp(40),dp(56)));int side=dp(56);Context app=getApplicationContext();
        io.execute(()->{Bitmap b=Presets.thumbnail(app,p,side);runOnUiThread(()->{if(b!=null&&!isDestroyed())thumb.setImageBitmap(b);});});
        LinearLayout texts=new LinearLayout(this);texts.setOrientation(LinearLayout.VERTICAL);texts.setPadding(dp(12),0,dp(8),0);
        TextView name=text(this,p.name,17,REGULAR,LABEL);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);texts.addView(name);
        String media=Preset.VIDEO.equals(p.media)?tr("видео","video"):Preset.PHOTO.equals(p.media)?tr("фото","photo"):tr("образец","sample");
        texts.addView(text(this,p.profile.effect().name(config.en())+" · "+media,13,REGULAR,SECONDARY));
        row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        if(!editing){ImageView chevron=new ImageView(this);chevron.setImageDrawable(new Glyph(this,Glyph.CHEVRON,TERTIARY,16));row.addView(chevron);}
        row.setOnClickListener(v->{if(busy)return;if(editing)rename(p);else actionsFor(p);});
        card.addView(pressable(row));
    }

    // ---------------------------------------------------------------- actions
    void saveCurrent(){
        if(busy)return;
        if(config.presets.size()>=WallpaperConfig.MAX_PRESETS){alert(this,tr("Слишком много пресетов","Too many presets"),tr("Удали ненужные, чтобы сохранить новый.","Delete some to save a new one."),"OK");return;}
        String slot=getIntent().getStringExtra(EXTRA_SLOT);String screen=WallpaperConfig.LOCK.equals(slot)?WallpaperConfig.LOCK:WallpaperConfig.HOME;
        String suggested=config.profile(screen).effect().name(config.en());
        prompt(this,tr("Новый пресет","New preset"),tr("Название для того, что сейчас на экране.","A name for what is on screen now."),suggested,tr("Сохранить","Save"),tr("Отмена","Cancel"),name->
            work(tr("Сохраняю…","Saving…"),()->{Preset p=Presets.capture(getApplicationContext(),config,screen,name);return ()->{config.presets.add(0,p);config.save(this);build();done(tr("Пресет сохранён","Preset saved"));};}));
    }
    void actionsFor(Preset p){
        String[] items={tr("Применить к главному экрану","Apply to Home Screen"),tr("Применить к блокировке","Apply to Lock Screen"),tr("Применить к обоим","Apply to both"),
            tr("Переименовать","Rename"),tr("Дублировать","Duplicate"),tr("Экспорт файла .tilt","Export as .tilt"),tr("Удалить","Delete")};
        actionSheet(this,p.name,items,6,tr("Отмена","Cancel"),i->{
            switch(i){
                case 0,1,2->{String target=i==0?Presets.HOME:i==1?Presets.LOCK:Presets.BOTH;
                    work(tr("Применяю…","Applying…"),()->{Presets.apply(getApplicationContext(),config,p,target);return ()->done(i==1?tr("Теперь на блокировке","Now on the lock screen"):i==0?tr("Теперь на главном экране","Now on the home screen"):tr("Теперь на обоих экранах","Now on both screens"));});}
                case 3->rename(p);
                case 4->{if(config.presets.size()>=WallpaperConfig.MAX_PRESETS){alert(this,tr("Слишком много пресетов","Too many presets"),tr("Удали ненужные, чтобы добавить новый.","Delete some to add a new one."),"OK");return;}
                    work(tr("Копирую…","Copying…"),()->{Preset copy=Presets.duplicate(getApplicationContext(),p,p.name+tr(" — копия"," copy"));return ()->{config.presets.add(config.presets.indexOf(p)+1,copy);config.save(this);build();};});}
                case 5->{exporting=p.id;try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,Presets.fileName(p)),EXPORT);}
                    catch(ActivityNotFoundException e){done(tr("Нет приложения для сохранения файлов","No app can save files"));}}
                case 6->confirmDelete(p);
                default->{}
            }
        });
    }
    void rename(Preset p){
        prompt(this,tr("Переименовать","Rename"),null,p.name,tr("Готово","Done"),tr("Отмена","Cancel"),name->{p.name=WallpaperConfig.cleanName(name);config.save(this);build();});
    }
    void confirmDelete(Preset p){
        actionSheet(this,tr("Удалить «"+p.name+"»? Расписание с ним тоже очистится.","Delete “"+p.name+"”? Schedule entries using it are removed too."),new String[]{tr("Удалить пресет","Delete preset")},0,tr("Отмена","Cancel"),i->{
            config.presets.remove(p);config.prunePlans();config.save(this);Context app=getApplicationContext();io.execute(()->Presets.delete(app,p));
            if(config.presets.isEmpty())editing=false;build();
        });
    }
    void pickImport(){
        try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),IMPORT);}
        catch(ActivityNotFoundException e){done(tr("Не найдено приложение выбора файлов.","No file picker is available."));}
    }
    void importFrom(Uri uri){
        if(config.presets.size()>=WallpaperConfig.MAX_PRESETS){alert(this,tr("Слишком много пресетов","Too many presets"),tr("Удали ненужные, чтобы импортировать новый.","Delete some to import a new one."),"OK");return;}
        work(tr("Импорт…","Importing…"),()->{Preset p=Presets.importTilt(getApplicationContext(),uri);return ()->{config.presets.add(0,p);config.save(this);build();done(tr("Импортирован «"+p.name+"»","Imported “"+p.name+"”"));};});
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==IMPORT)importFrom(uri);
        else if(request==EXPORT){Preset p=config.preset(exporting);if(p==null)return;Context app=getApplicationContext();
            work(tr("Экспорт…","Exporting…"),()->{try(OutputStream out=open(app,uri)){if(out==null)throw new IOException("Cannot open");Presets.export(app,p,out);}return ()->done(tr("Файл сохранён","File saved"));});}
    }

    /** Truncating write where the provider supports it; some only accept plain "w". */
    static OutputStream open(Context c,Uri uri)throws IOException{
        try{return c.getContentResolver().openOutputStream(uri,"wt");}catch(IllegalArgumentException|UnsupportedOperationException|FileNotFoundException e){return c.getContentResolver().openOutputStream(uri,"w");}
    }
    interface Job{Runnable run()throws Exception;}
    /** Runs IO off the main thread with the list disabled; the returned runnable finishes on the main thread. */
    void work(String message,Job job){
        busy=true;list.setAlpha(.6f);Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
        io.execute(()->{Runnable after=null;Throwable error=null;try{after=job.run();}catch(Exception|OutOfMemoryError e){error=e;}
            final Runnable finish=after;final Throwable failed=error;
            runOnUiThread(()->{if(isDestroyed())return;busy=false;list.setAlpha(1);
                if(failed!=null){if(config.haptics)list.performHapticFeedback(HapticFeedbackConstants.REJECT);alert(this,tr("Не получилось","Something went wrong"),failed instanceof IOException&&failed.getMessage()!=null?failed.getMessage():tr("Файл повреждён или не подходит.","The file is damaged or not supported."),"OK");}
                else if(finish!=null)finish.run();});});
    }
    void done(String message){if(config.haptics)list.performHapticFeedback(HapticFeedbackConstants.CONFIRM);Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}

    @Override protected void onSaveInstanceState(Bundle out){out.putString("exporting",exporting);out.putBoolean("editing",editing);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);io.shutdown();super.onDestroy();}
    @Override public void finish(){super.finish();overridePendingTransition(R.anim.ios_pop_in,R.anim.ios_pop_out);}

    /** Red "remove" disc of a table in edit mode. */
    final class Minus extends android.graphics.drawable.Drawable {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c){Rect b=getBounds();float r=Math.min(b.width(),b.height())*.36f;paint.setColor(0xffff453a);c.drawCircle(b.exactCenterX(),b.exactCenterY(),r,paint);
            paint.setColor(Color.WHITE);c.drawRoundRect(b.exactCenterX()-r*.55f,b.exactCenterY()-r*.11f,b.exactCenterX()+r*.55f,b.exactCenterY()+r*.11f,r*.11f,r*.11f,paint);}
        @Override public void setAlpha(int a){paint.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    // ---------------------------------------------------------------- iOS dialogs shared with ScheduleActivity
    static Dialog dialog(Activity a,View content,int gravity){
        Dialog d=new Dialog(a);d.requestWindowFeature(Window.FEATURE_NO_TITLE);d.setContentView(content);
        Window w=d.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(-1,-2);w.setGravity(gravity);w.setDimAmount(.45f);
        return d;
    }
    /** UIAlertController with a text field. */
    static void prompt(Activity a,String title,String message,String initial,String ok,String cancel,Consumer<String> done){
        FrameLayout frame=new FrameLayout(a);LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);card.setBackground(new Shape(ALERT,px(a,GROUPED)));
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);card.setClipToOutline(true);frame.addView(card,new FrameLayout.LayoutParams(Ios.dp(a,280),-2,Gravity.CENTER));
        TextView head=text(a,title,17,SEMIBOLD,LABEL);head.setGravity(Gravity.CENTER);head.setPadding(Ios.dp(a,18),Ios.dp(a,20),Ios.dp(a,18),Ios.dp(a,4));card.addView(head);
        if(message!=null){TextView body=text(a,message,13,REGULAR,0xd9ffffff);body.setGravity(Gravity.CENTER);body.setPadding(Ios.dp(a,18),Ios.dp(a,2),Ios.dp(a,18),0);card.addView(body);}
        EditText field=new EditText(a);field.setText(initial);field.setSelectAllOnFocus(true);field.setSingleLine(true);field.setTextColor(LABEL);field.setTextSize(15);field.setTypeface(font(REGULAR));
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});field.setBackground(new Shape(0xff1c1c1e,px(a,8)).stroke(Math.max(1,px(a,.5f)),SEPARATOR));field.setPadding(Ios.dp(a,8),0,Ios.dp(a,8),0);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,Ios.dp(a,34));fp.setMargins(Ios.dp(a,16),Ios.dp(a,14),Ios.dp(a,16),Ios.dp(a,18));card.addView(field,fp);
        card.addView(hairline(a),new LinearLayout.LayoutParams(-1,Math.max(1,Ios.dp(a,.5f))));
        LinearLayout buttons=new LinearLayout(a);card.addView(buttons,new LinearLayout.LayoutParams(-1,Ios.dp(a,48)));
        Dialog d=dialog(a,frame,Gravity.CENTER);
        TextView no=text(a,cancel,17,REGULAR,BLUE),yes=text(a,ok,17,SEMIBOLD,BLUE);
        for(TextView b:new TextView[]{no,yes}){b.setGravity(Gravity.CENTER);b.setClickable(true);b.setFocusable(true);}
        buttons.addView(pressable(no),new LinearLayout.LayoutParams(0,-1,1));View line=new View(a);line.setBackgroundColor(SEPARATOR);buttons.addView(line,new LinearLayout.LayoutParams(Math.max(1,Ios.dp(a,.5f)),-1));buttons.addView(pressable(yes),new LinearLayout.LayoutParams(0,-1,1));
        no.setOnClickListener(v->d.dismiss());
        yes.setOnClickListener(v->{String value=field.getText().toString();d.dismiss();done.accept(value);});
        field.setOnEditorActionListener((v,id,e)->{yes.performClick();return true;});
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        d.setOnShowListener(x->{card.setScaleX(1.15f);card.setScaleY(1.15f);card.setAlpha(0);spring(card,SMOOTH).scaleX(1).scaleY(1).alpha(1);field.requestFocus();
            InputMethodManager m=a.getSystemService(InputMethodManager.class);if(m!=null)field.post(()->m.showSoftInput(field,0));});
        d.show();
    }
    /** UIAlertController in action-sheet style: a card of choices and a separate Cancel, rising from the bottom. */
    static void actionSheet(Activity a,String title,String[] items,int destructive,String cancel,IntConsumer pick){
        LinearLayout column=new LinearLayout(a);column.setOrientation(LinearLayout.VERTICAL);column.setPadding(Ios.dp(a,8),0,Ios.dp(a,8),Ios.dp(a,8));
        LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);card.setBackground(new Shape(ALERT,px(a,CARD)));card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);card.setClipToOutline(true);column.addView(card);
        Dialog d=dialog(a,column,Gravity.BOTTOM);
        if(title!=null){TextView t=text(a,title,13,SEMIBOLD,SECONDARY);t.setGravity(Gravity.CENTER);t.setPadding(Ios.dp(a,16),Ios.dp(a,14),Ios.dp(a,16),Ios.dp(a,14));card.addView(t);}
        for(int i=0;i<items.length;i++){
            if(card.getChildCount()>0)card.addView(hairline(a),new LinearLayout.LayoutParams(-1,Math.max(1,Ios.dp(a,.5f))));
            final int index=i;TextView b=text(a,items[i],19,REGULAR,i==destructive?0xffff453a:BLUE);b.setGravity(Gravity.CENTER);b.setClickable(true);b.setFocusable(true);b.setMaxLines(1);
            b.setOnClickListener(v->{d.dismiss();pick.accept(index);});card.addView(pressable(b),new LinearLayout.LayoutParams(-1,Ios.dp(a,56)));
        }
        TextView c=text(a,cancel,19,SEMIBOLD,BLUE);c.setGravity(Gravity.CENTER);c.setBackground(new Shape(0xff2c2c2e,px(a,CARD)));c.setClickable(true);c.setFocusable(true);c.setOnClickListener(v->d.dismiss());
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,Ios.dp(a,56));cp.topMargin=Ios.dp(a,8);column.addView(pressable(c),cp);
        column.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(Ios.dp(a,8),0,Ios.dp(a,8),Ios.dp(a,8)+i.getInsets(WindowInsets.Type.navigationBars()).bottom);return i;});
        d.setOnShowListener(x->{column.setTranslationY(column.getHeight()+Ios.dp(a,40));spring(column,SMOOTH).translationY(0);});
        d.show();
    }
}
