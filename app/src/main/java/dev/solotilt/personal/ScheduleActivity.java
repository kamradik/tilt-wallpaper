package dev.solotilt.personal;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import static dev.solotilt.personal.Ios.*;
import static dev.solotilt.personal.WallpaperConfig.Preset;

/** Per-screen schedule: up to four time windows, each showing a preset, and a photo of the day from an album. */
public final class ScheduleActivity extends Activity {
    static final int PICK_ALBUM=1;
    WallpaperConfig config;LinearLayout list;String slot=WallpaperConfig.HOME;
    String tr(String ru,String en){return config.en()?en:ru;}
    int dp(float n){return Ios.dp(this,n);}
    Schedule.Plan plan(){return config.plan(slot);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);config=WallpaperConfig.load(this);Ios.reduceMotion(config.reduceMotion);
        String requested=state!=null?state.getString("slot"):getIntent().getStringExtra(PresetsActivity.EXTRA_SLOT);if(WallpaperConfig.LOCK.equals(requested))slot=WallpaperConfig.LOCK;
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);root.addView(nav,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        ImageView back=circle(this,Glyph.BACK,tr("Назад","Back"));back.setOnClickListener(v->finish());nav.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        root.setOnApplyWindowInsetsListener((v,i)->{Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            nav.setPadding(dp(16)+bars.left,bars.top+dp(8),dp(16)+bars.right,dp(8));list.setPadding(dp(16)+bars.left,bars.top+dp(64),dp(16)+bars.right,bars.bottom+dp(32));return i;});
        build();
        if(state==null)for(int i=0;i<list.getChildCount();i++)rise(list.getChildAt(i),Math.min(i,7));
    }
    @Override protected void onResume(){
        super.onResume();WallpaperConfig latest=WallpaperConfig.load(this);
        if(!latest.json().equals(config.json())){config=latest;build();}  // presets may have changed meanwhile
    }

    void build(){
        list.removeAllViews();boolean en=config.en();
        list.addView(text(this,tr("Расписание","Schedule"),34,BOLD,LABEL));
        Segmented screens=new Segmented(this,tr("Главный экран","Home Screen"),tr("Блокировка","Lock Screen"));screens.select(WallpaperConfig.LOCK.equals(slot)?1:0,false);
        screens.listener=i->{slot=i==1?WallpaperConfig.LOCK:WallpaperConfig.HOME;list.postDelayed(this::build,180);};
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(14);list.addView(screens,sp);
        if(WallpaperConfig.LOCK.equals(slot)&&!config.separateLock){
            footer(this,list,tr("Сейчас блокировка повторяет главный экран, и расписание главного экрана действует на оба. Включи «Свои обои на блокировке» на вкладке «Блокировка», чтобы задать отдельное.","The lock screen mirrors the home screen now, so the home schedule applies to both. Turn on “Own lock screen wallpaper” on the Lock Screen tab to give it its own."));
            return;
        }
        Schedule.Plan plan=plan();

        LinearLayout timed=group(this,list,tr("По времени суток","By time of day"));
        toggle(timed,tr("Менять пресеты по времени","Switch presets by time"),plan.enabled,on->{plan().enabled=on;changed();});
        for(Schedule.Window w:plan.windows)windowRow(timed,w);
        if(plan.windows.size()<Schedule.MAX_WINDOWS&&!config.presets.isEmpty()){
            TextView add=text(this,tr("Добавить время","Add a time"),17,REGULAR,BLUE);add.setClickable(true);add.setOnClickListener(v->addWindow());row(this,timed,null,pressable(add));
        }
        footer(this,list,config.presets.isEmpty()?tr("Сначала сохрани хотя бы один пресет: каждое время показывает свой пресет.","Save at least one preset first: each time shows a preset.")
            :tr("Каждый пресет держится до следующего времени, последний — до утра. Обои переключаются, когда ты в следующий раз видишь экран.","Each preset stays until the next time; the last one runs through the night. The wallpaper switches the next time the screen is shown."));

        LinearLayout photo=group(this,list,tr("Фото дня","Photo of the day"));
        toggle(photo,tr("Менять фото из альбома","Rotate photos from an album"),plan.rotate,on->{plan().rotate=on;changed();if(on&&plan().album==null)pickAlbum();});
        TextView album=text(this,plan.album==null?tr("Выбрать","Choose"):albumName(plan.album),17,REGULAR,plan.album==null?BLUE:SECONDARY);album.setMaxLines(1);album.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);album.setMaxWidth(dp(170));
        LinearLayout albumRow=row(this,photo,tr("Альбом","Album"),album);albumRow.setClickable(true);albumRow.setOnClickListener(v->pickAlbum());pressable(albumRow);
        Segmented when=new Segmented(this,tr("Каждый день","Every day"),tr("При разблокировке","Every unlock"));when.select(plan.perUnlock?1:0,false);when.listener=i->{plan().perUnlock=i==1;changed();};
        row(this,photo,null,when);
        footer(this,list,tr("Фото берутся по порядку из выбранной папки. Если включено и время суток, пресет задаёт эффект, а фото — из альбома.","Photos are taken in order from the chosen folder. With a time schedule too, the preset sets the effect and the album sets the photo."));
        list.setHapticFeedbackEnabled(config.haptics);
    }
    void windowRow(LinearLayout card,Schedule.Window w){
        separate(this,card);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(56));row.setPadding(dp(16),dp(6),dp(14),dp(6));
        TextView time=text(this,Schedule.hhmm(w.minute),22,MEDIUM,BLUE);time.setFontFeatureSettings("tnum");time.setClickable(true);time.setContentDescription(tr("Время ","Time ")+Schedule.hhmm(w.minute));
        time.setOnClickListener(v->pickTime(w));row.addView(pressable(time),new LinearLayout.LayoutParams(-2,-2));
        Preset p=config.preset(w.preset);
        TextView name=text(this,p==null?"—":p.name,17,REGULAR,SECONDARY);name.setGravity(Gravity.END);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.leftMargin=dp(12);row.addView(name,np);
        ImageView chevron=new ImageView(this);chevron.setImageDrawable(new Glyph(this,Glyph.CHEVRON,TERTIARY,16));row.addView(chevron);
        row.setClickable(true);row.setOnClickListener(v->choosePreset(w));
        card.addView(pressable(row));
    }

    // ---------------------------------------------------------------- edits
    void addWindow(){
        Schedule.Plan plan=plan();
        // A sensible next slot: morning, evening, then the gaps between.
        int[] guesses={7*60,19*60,13*60,22*60,0};int minute=0;
        for(int g:guesses){boolean used=false;for(Schedule.Window w:plan.windows)used|=w.minute==g;if(!used){minute=g;break;}}
        Schedule.Window w=new Schedule.Window(minute,config.presets.get(0).id);plan.windows.add(w);plan.sort();if(plan.windows.size()==1)plan.enabled=true;
        changed();build();pickTime(w);
    }
    void pickTime(Schedule.Window w){
        TimePickerDialog d=new TimePickerDialog(this,android.R.style.Theme_Material_Dialog_Alert,(v,h,m)->{
            int minute=h*60+m;for(Schedule.Window o:plan().windows)if(o!=w&&o.minute==minute){Toast.makeText(this,tr("Это время уже есть","This time is already used"),Toast.LENGTH_SHORT).show();return;}
            w.minute=minute;plan().sort();changed();build();
        },w.minute/60,w.minute%60,android.text.format.DateFormat.is24HourFormat(this));
        d.show();
    }
    void choosePreset(Schedule.Window w){
        int n=config.presets.size();String[] items=new String[n+2];
        for(int i=0;i<n;i++)items[i]=(config.presets.get(i).id.equals(w.preset)?"✓ ":"")+config.presets.get(i).name;
        items[n]=tr("Изменить время","Change time");items[n+1]=tr("Удалить время","Remove this time");
        PresetsActivity.actionSheet(this,Schedule.hhmm(w.minute),items,n+1,tr("Отмена","Cancel"),i->{
            if(i<n){w.preset=config.presets.get(i).id;changed();build();}
            else if(i==n)pickTime(w);
            else{plan().windows.remove(w);if(plan().windows.isEmpty())plan().enabled=false;changed();build();}
        });
    }
    void pickAlbum(){
        try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),PICK_ALBUM);}
        catch(ActivityNotFoundException e){Toast.makeText(this,tr("Не найдено приложение выбора папок.","No folder picker is available."),Toast.LENGTH_SHORT).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_ALBUM)return;
        if(result!=RESULT_OK||data==null||data.getData()==null){if(plan().album==null&&plan().rotate){plan().rotate=false;changed();build();}return;}
        Uri tree=data.getData();
        try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        catch(SecurityException e){Toast.makeText(this,tr("Нет доступа к этой папке.","This folder cannot be accessed."),Toast.LENGTH_SHORT).show();return;}
        String old=plan().album;plan().album=tree.toString();plan().rotate=true;changed();release(old);build();
    }
    /** Gives back a folder permission no plan uses any more (apps may hold only a limited number). */
    void release(String album){
        if(album==null||album.equals(config.homePlan.album)||album.equals(config.lockPlan.album))return;
        try{getContentResolver().releasePersistableUriPermission(Uri.parse(album),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
    }
    String albumName(String album){
        try{String id=DocumentsContract.getTreeDocumentId(Uri.parse(album));String tail=id.substring(id.indexOf(':')+1);tail=tail.substring(tail.lastIndexOf('/')+1);return tail.isEmpty()?tr("Корень хранилища","Storage root"):tail;}
        catch(RuntimeException e){return tr("Папка","Folder");}
    }
    void changed(){config.save(this);Schedule.reset(this,slot);}
    interface Change{void set(boolean on);}
    void toggle(LinearLayout card,String title,boolean value,Change change){Toggle t=new Toggle(this);t.setOn(value,false);t.setContentDescription(title);t.setHapticFeedbackEnabled(config.haptics);t.listener=change::set;row(this,card,title,t);}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("slot",slot);super.onSaveInstanceState(out);}
    @Override public void finish(){super.finish();overridePendingTransition(R.anim.ios_pop_in,R.anim.ios_pop_out);}
}
