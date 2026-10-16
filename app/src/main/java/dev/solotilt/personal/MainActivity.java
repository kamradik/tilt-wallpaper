package dev.solotilt.personal;

import android.animation.LayoutTransition;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import static dev.solotilt.personal.Ios.*;

/** The hub: live preview of the selected screen, the effect sheet, and the searchable catalog. */
public final class MainActivity extends Activity implements SurfaceHolder.Callback {
    static final int PICK_PHOTO=10,PICK_DEPTH=11,ONBOARD=12,TRIM=13;
    /** Catalog filters below -2 are the curated collections: -10 is collection 0, -11 collection 1, ... */
    static final int COLLECTION_FILTER=-10;
    static final String[] PRESETS_RU={"Розовые ленты","Синие ленты","Янтарные ленты"},PRESETS_EN={"Rose ribbons","Blue ribbons","Amber ribbons"};
    RenderLoop renderer;Motion motion;WallpaperConfig config;Thumbnails thumbnails;
    String slot=WallpaperConfig.HOME,previewKey;boolean resumed,ready,importing,hidden,dragging;float downX,downY,dragTiltX,dragTiltY;long labelTime;
    final Map<String,Bitmap> previews=new HashMap<>();
    final Handler main=new Handler(Looper.getMainLooper());final ExecutorService io=Executors.newSingleThreadExecutor();
    FrameLayout root,stage,catalog;Curtain curtain;String ringShown;boolean entering;float catalogY,catalogLastY,catalogSpeed;long catalogTime;
    LinearLayout header,sheetBody,strip,params,editing,lockCard,frame,catalogGrid;ScrollView sheet;Segmented screens;
    TextView angle,effectTitle,effectAbout,status,choose,install;ImageView heart;View dot;EditText search;
    final Tile[] tiles=new Tile[4];final List<TextView> chips=new ArrayList<>();int filter=-1; // -1 all, -2 favorites, <=-10 a collection, else a category
    HorizontalScrollView stripScroll;final List<String> stripOrder=new ArrayList<>();ImageView dice;android.animation.ValueAnimator stripMove;final Random random=new Random();long seenHome,seenLock;
    String tr(String ru,String en){return config.en()?en:ru;}
    int dp(float n){return Ios.dp(this,n);}
    WallpaperConfig.Profile profile(){return config.profile(slot);}
    /** The lock tab edits its own profile only once the lock screen has its own wallpaper. */
    boolean editable(){return WallpaperConfig.HOME.equals(slot)||config.separateLock;}
    String mediaSlot(){return config.separateLock&&WallpaperConfig.LOCK.equals(slot)?WallpaperConfig.LOCK:WallpaperConfig.HOME;}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);config=WallpaperConfig.load(this);Ios.reduceMotion(config.reduceMotion);
        if(state!=null)slot=state.getString("slot",slot);
        renderer=new RenderLoop(this,code->runOnUiThread(()->showError(code)));renderer.mode(WallpaperConfig.LOCK.equals(slot)?RenderLoop.SHOW_LOCK:RenderLoop.SHOW_HOME);
        // Sensor thread: Ios.light coalesces to ~30 Hz and posts the glass redraw to the UI thread itself.
        motion=new Motion(this,getDisplay(),(x,y,t)->{if(dragging)return;renderer.tilt(x,y,t);Ios.light(x-config.neutralX,y-config.neutralY);if(SystemClock.uptimeMillis()-labelTime>150){labelTime=SystemClock.uptimeMillis();runOnUiThread(()->angle.setText(String.format(Locale.ROOT,tr("Наклон %.0f°","Tilt %.0f°"),Math.hypot(x,y))));}});
        renderer.sensorInfo(motion.description());motion.jolt=renderer::jolt;thumbnails=new Thumbnails(this);
        build(state==null);rememberMedia();
        // App shortcut "Choose photo" (ShortcutActivity) opens the picker straight away.
        if(state==null&&"pick".equals(getIntent().getStringExtra("action")))main.postDelayed(()->pick(PICK_PHOTO),400);
        if(state==null&&!OnboardingActivity.seen(this))main.postDelayed(this::openOnboarding,350);
    }
    void openOnboarding(){if(isFinishing()||isDestroyed())return;startActivityForResult(new Intent(this,OnboardingActivity.class),ONBOARD);overridePendingTransition(R.anim.ios_push_in,R.anim.ios_push_out);}
    void push(Class<?> activity){saveNow();startActivity(new Intent(this,activity).putExtra(PresetsActivity.EXTRA_SLOT,slot));overridePendingTransition(R.anim.ios_push_in,R.anim.ios_push_out);}
    /** Presets and the schedule change media from other screens; onResume reloads when a slot's revision moved. */
    void rememberMedia(){seenHome=ImageStore.revision(this,WallpaperConfig.HOME);seenLock=ImageStore.revision(this,WallpaperConfig.LOCK);}
    boolean mediaMoved(){return seenHome!=ImageStore.revision(this,WallpaperConfig.HOME)||seenLock!=ImageStore.revision(this,WallpaperConfig.LOCK);}

    // ---------------------------------------------------------------- layout
    void build(boolean animate){
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        // Everything behind the catalog lives on a stage that recedes like the presenting view of an iOS sheet.
        stage=new FrameLayout(this);stage.setClipChildren(false);root.addView(stage,new FrameLayout.LayoutParams(-1,-1));
        SurfaceView preview=new SurfaceView(this);stage.addView(preview,new FrameLayout.LayoutParams(-1,-1));preview.getHolder().addCallback(this);
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x99000000,0x00000000,0x00000000,0x80000000}));stage.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        root.setOnTouchListener(this::previewTouch);
        curtain=new Curtain();root.addView(curtain,new FrameLayout.LayoutParams(-1,-1));

        header=new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);header.setClipChildren(false);stage.addView(header,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setClipChildren(false);header.addView(bar);
        TextView appTitle=legible(text(this,"Parallaxia",34,BOLD,LABEL));bar.addView(appTitle,new LinearLayout.LayoutParams(0,-2,1));
        appTitle.setOnLongClickListener(v->{tick(v);openOnboarding();return true;});
        ImageView presets=circle(this,Glyph.PLUS,tr("Пресеты","Presets"));presets.setImageDrawable(new StackIcon());bar.addView(presets,new LinearLayout.LayoutParams(dp(40),dp(40)));
        presets.setOnClickListener(v->push(PresetsActivity.class));
        ImageView settings=circle(this,Glyph.GEAR,tr("Настройки","Settings"));LinearLayout.LayoutParams gearParams=new LinearLayout.LayoutParams(dp(40),dp(40));gearParams.leftMargin=dp(10);bar.addView(settings,gearParams);
        settings.setOnClickListener(v->{startActivity(new Intent(this,SettingsActivity.class));overridePendingTransition(R.anim.ios_push_in,R.anim.ios_push_out);});
        ImageView author=new ImageView(this);author.setImageResource(R.drawable.author_avatar);author.setScaleType(ImageView.ScaleType.CENTER_CROP);author.setForeground(new Shape(0,-1).stroke(px(this,1.5f),0x66ffffff));
        author.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline o){o.setOval(0,0,v.getWidth(),v.getHeight());}});author.setClipToOutline(true);author.setContentDescription(tr("Об авторе","About the author"));author.setClickable(true);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(40),dp(40));ap.leftMargin=dp(10);bar.addView(pressable(author),ap);
        author.setOnClickListener(v->{startActivity(new Intent(this,AuthorActivity.class));overridePendingTransition(R.anim.ios_push_in,R.anim.ios_push_out);});
        screens=new Segmented(this,tr("Главный экран","Home Screen"),tr("Блокировка","Lock Screen"));screens.select(WallpaperConfig.LOCK.equals(slot)?1:0,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(12);header.addView(screens,sp);
        screens.listener=i->switchSlot(i==1?WallpaperConfig.LOCK:WallpaperConfig.HOME);
        LinearLayout chip=new LinearLayout(this);chip.setGravity(Gravity.CENTER_VERTICAL);chip.setBackground(glass(this,-1));chip.setPadding(dp(12),0,dp(14),0);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(30));cp.topMargin=dp(10);header.addView(chip,cp);
        dot=new View(this);dot.setBackground(new Shape(motion.sensor==null?0xff8e8e93:GREEN,-1));LinearLayout.LayoutParams dotParams=new LinearLayout.LayoutParams(dp(7),dp(7));dotParams.rightMargin=dp(8);chip.addView(dot,dotParams);
        angle=text(this,motion.sensor==null?tr("Веди пальцем по экрану","Drag on the screen"):tr("Наклони телефон","Tilt your phone"),13,SEMIBOLD,LABEL);angle.setFontFeatureSettings("tnum");chip.addView(legible(angle));

        // Secondary text sits on the sheet, so its glass lets through less light than a button's.
        sheet=new Sheet();sheet.setVerticalScrollBarEnabled(false);sheet.setOverScrollMode(View.OVER_SCROLL_ALWAYS);sheet.setBackground(new Shape(SHEET,px(this,SHEET_RADIUS)).glass(Math.max(1,px(this,.7f))).legible(.06f));
        sheet.setOutlineProvider(ViewOutlineProvider.BACKGROUND);sheet.setClipToOutline(true);sheet.setOnScrollChangeListener((v,x,y,ox,oy)->Ios.refract());
        stage.addView(sheet,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        sheetBody=new LinearLayout(this);sheetBody.setOrientation(LinearLayout.VERTICAL);sheetBody.setClipChildren(false);sheetBody.setPadding(dp(16),dp(8),dp(16),dp(16));sheet.addView(sheetBody);
        LayoutTransition lt=new LayoutTransition();lt.enableTransitionType(LayoutTransition.CHANGING);lt.setDuration(SMOOTH.duration);sheetBody.setLayoutTransition(lt);
        View grabber=new View(this);grabber.setBackground(new Shape(0x66ebebf5,-1));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(36),dp(5));gp.gravity=Gravity.CENTER_HORIZONTAL;gp.bottomMargin=dp(12);sheetBody.addView(grabber,gp);

        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);titleRow.setClipChildren(false);sheetBody.addView(titleRow);
        effectTitle=text(this,"",22,BOLD,LABEL);titleRow.addView(effectTitle,new LinearLayout.LayoutParams(0,-2,1));
        heart=circle(this,Glyph.HEART,tr("В избранное","Add to favorites"));titleRow.addView(heart,new LinearLayout.LayoutParams(dp(38),dp(38)));heart.setOnClickListener(v->toggleFavorite(profile().effect,v));
        dice=circle(this,Glyph.PLUS,tr("Случайный эффект","Surprise me"));dice.setImageDrawable(new Dice());LinearLayout.LayoutParams dp2=new LinearLayout.LayoutParams(dp(38),dp(38));dp2.leftMargin=dp(8);titleRow.addView(dice,dp2);dice.setOnClickListener(this::surprise);
        TextView all=capsule(this,tr("Все эффекты","All effects"),false);LinearLayout.LayoutParams allp=new LinearLayout.LayoutParams(-2,dp(38));allp.leftMargin=dp(8);titleRow.addView(all,allp);all.setOnClickListener(v->openCatalog());
        effectAbout=text(this,"",15,REGULAR,SECONDARY);effectAbout.setPadding(0,dp(4),0,dp(10));sheetBody.addView(effectAbout);

        stripScroll=new HorizontalScrollView(this);stripScroll.setHorizontalScrollBarEnabled(false);stripScroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        strip=new LinearLayout(this);stripScroll.addView(strip);sheetBody.addView(stripScroll,new LinearLayout.LayoutParams(-1,dp(116)));

        // Lock tab without its own wallpaper: one switch, the editing controls hidden.
        lockCard=new LinearLayout(this);lockCard.setOrientation(LinearLayout.VERTICAL);sheetBody.addView(lockCard);
        editing=new LinearLayout(this);editing.setOrientation(LinearLayout.VERTICAL);sheetBody.addView(editing);
        params=new LinearLayout(this);params.setOrientation(LinearLayout.VERTICAL);editing.addView(params);

        TextView backgroundTitle=text(this,tr("Фон","Background"),15,SEMIBOLD,LABEL);backgroundTitle.setPadding(dp(4),dp(18),0,dp(10));editing.addView(backgroundTitle);
        LinearLayout tileRow=new LinearLayout(this);editing.addView(tileRow);
        for(int i=0;i<4;i++){
            final int index=i-1;Tile tile=new Tile(i==0?null:ImageStore.preset(index,dp(74)/720f));tiles[i]=tile;tile.setContentDescription(i==0?tr("Своё фото","Your photo"):(config.en()?PRESETS_EN:PRESETS_RU)[index]);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=i==0?0:dp(8);tileRow.addView(pressable(tile),lp);
            tile.setOnClickListener(v->{if(importing)return;tick(v);if(index<0)photoTile();else{ImageStore.usePreset(this,mediaSlot(),index);mediaChanged(tr("Образец «"+PRESETS_RU[index]+"»","Preset “"+PRESETS_EN[index]+"”"));}});
        }
        frame=group(this,editing,tr("Кадр","Framing"));

        status=text(this,"",13,REGULAR,SECONDARY);status.setGravity(Gravity.CENTER);status.setPadding(0,dp(14),0,dp(4));sheetBody.addView(status);
        LinearLayout actions=new LinearLayout(this);actions.setClipChildren(false);LinearLayout.LayoutParams acp=new LinearLayout.LayoutParams(-1,dp(52));acp.topMargin=dp(8);sheetBody.addView(actions,acp);
        choose=capsule(this,tr("Фото или видео","Photo or video"),false);actions.addView(choose,new LinearLayout.LayoutParams(0,-1,1));choose.setOnClickListener(v->{if(editable())pick(PICK_PHOTO);});
        install=capsule(this,tr("Установить","Set wallpaper"),true);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-1,1);ip.leftMargin=dp(10);actions.addView(install,ip);install.setOnClickListener(v->{tick(v);install();});
        // Presets and the schedule sit below the main actions: one grouped card, like a settings list.
        LinearLayout more=group(this,sheetBody,null);((LinearLayout.LayoutParams)more.getLayoutParams()).topMargin=dp(16);
        navRow(more,tr("Пресеты","Presets"),()->push(PresetsActivity.class));
        navRow(more,tr("Расписание","Schedule"),()->push(ScheduleActivity.class));

        root.setOnApplyWindowInsetsListener((v,insets)->{
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            header.setPadding(dp(20)+bars.left,dp(8)+bars.top,dp(20)+bars.right,0);
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)sheet.getLayoutParams();p.leftMargin=dp(8)+bars.left;p.rightMargin=dp(8)+bars.right;p.bottomMargin=dp(8)+bars.bottom;sheet.setLayoutParams(p);
            return insets;
        });
        // The sheet scrolls rather than covering the preview: at most 58% of the screen.
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int max=Math.round((b-t)*.58f);FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)sheet.getLayoutParams();int want=sheetBody.getHeight()>max?max:-2;if(p.height!=want){p.height=want;sheet.post(()->sheet.setLayoutParams(p));}});
        refresh();rebuildFraming();
        if(animate){
            for(int i=0;i<header.getChildCount();i++)rise(header.getChildAt(i),i);
            sheet.setAlpha(0);sheet.post(()->{sheet.setTranslationY(sheet.getHeight()+dp(40));sheet.setAlpha(1);spring(sheet,SMOOTH).translationY(0).setStartDelay(160);});
        }
        applyHaptics(root);
    }

    /** Rebuilds everything that depends on the slot, the selected effect or its parameters. */
    void refresh(){
        WallpaperConfig.Profile p=profile();Effects.Effect e=p.effect();boolean en=config.en(),edit=editable();
        // A new effect crossfades its name in; the first fill just sets it.
        if(effectTitle.getText().length()==0){effectTitle.setText(e.name(en));effectAbout.setText(e.about(en));}else{swap(effectTitle,e.name(en));swap(effectAbout,e.about(en));}
        Ios.backdropFrame(p.scale,p.cropX,p.cropY);
        boolean fav=config.isFavorite(e.id);heart.setImageDrawable(new Glyph(this,fav?Glyph.HEART_FILL:Glyph.HEART,fav?0xffff375f:LABEL,19));heart.setContentDescription(fav?tr("Убрать из избранного","Remove from favorites"):tr("В избранное","Add to favorites"));
        heart.setVisibility(edit?View.VISIBLE:View.GONE);dice.setVisibility(edit?View.VISIBLE:View.GONE);
        buildStrip();
        params.removeAllViews();
        if(edit){
            LinearLayout card=group(this,params,tr("Параметры","Parameters"));float[] v=p.current();
            unitSlider(this,card,en?e.aEn:e.aRu,v[0],x->{profile().current()[0]=x/100f;persist();});
            unitSlider(this,card,en?e.bEn:e.bRu,v[1],x->{profile().current()[1]=x/100f;persist();});
            if(e.hasSwitch()){Toggle t=new Toggle(this);t.setOn(v[2]>.5f,false);t.setContentDescription(en?e.switchEn:e.switchRu);t.listener=on->{profile().current()[2]=on?1:0;persist();};row(this,card,en?e.switchEn:e.switchRu,t);}
            if(ImageStore.isVideo(this,mediaSlot())){Toggle scrub=new Toggle(this);scrub.setOn(ImageStore.isScrub(this,mediaSlot()),false);scrub.setContentDescription(tr("Перемотка наклоном","Tilt scrub"));
                scrub.listener=on->{ImageStore.setScrub(this,mediaSlot(),on);renderer.reload();swap(status,on?tr("Наклоняй телефон, чтобы листать видео","Tilt to scrub through the video"):"");};row(this,card,tr("Перемотка наклоном","Tilt scrub"),scrub);}
            if(e.has(Effects.DEPTH)){TextView importDepth=text(this,tr("Импорт карты","Import map"),17,REGULAR,BLUE);importDepth.setClickable(true);importDepth.setOnClickListener(x->pick(PICK_DEPTH));row(this,card,tr("Глубина","Depth"),pressable(importDepth));}
            TextView reset=text(this,tr("Сбросить эффект","Reset effect"),17,REGULAR,0xffff453a);reset.setClickable(true);reset.setOnClickListener(x->{profile().reset(e.id);persist();refresh();});row(this,card,null,pressable(reset));
            if(e.has(Effects.DEPTH))footer(this,params,tr("Глубину по фото рассчитывает нейросеть Depth Anything прямо на телефоне, один раз на фото. Можно загрузить свою карту: белое — ближе, чёрное — дальше.","Depth is computed from your photo by the Depth Anything network, on the phone, once per photo. You can also import your own map: white is near, black is far."));
            // Second layer: weather, light or grain over the main effect, with its own sliders.
            Effects.Effect top=p.overlayEffect();
            LinearLayout layer=group(this,params,tr("Второй слой","Second layer"));
            TextView choice=text(this,top==null?tr("Нет","None"):top.name(en),17,REGULAR,BLUE);choice.setClickable(true);choice.setOnClickListener(x->chooseLayer());
            row(this,layer,tr("Поверх эффекта","On top"),pressable(choice));
            if(top!=null){float[] lv=p.params(top.id);
                unitSlider(this,layer,en?top.aEn:top.aRu,lv[0],x->{profile().params(top.id)[0]=x/100f;persist();});
                unitSlider(this,layer,en?top.bEn:top.bRu,lv[1],x->{profile().params(top.id)[1]=x/100f;persist();});
                if(top.hasSwitch()){Toggle t=new Toggle(this);t.setOn(lv[2]>.5f,false);t.setContentDescription(en?top.switchEn:top.switchRu);t.listener=on->{profile().params(top.id)[2]=on?1:0;persist();};row(this,layer,en?top.switchEn:top.switchRu,t);}}
            footer(this,params,tr("Снег, капли, плёнка, свет или время суток поверх основного эффекта.","Snow, drops, film, light or time of day over the main effect."));
        }
        lockCard.removeAllViews();
        if(WallpaperConfig.LOCK.equals(slot)){
            LinearLayout card=group(this,lockCard,null);Toggle own=new Toggle(this);own.setOn(config.separateLock,false);own.setContentDescription(tr("Свои обои на блокировке","Own lock screen wallpaper"));
            own.listener=this::setSeparateLock;row(this,card,tr("Свои обои на блокировке","Own lock screen wallpaper"),own);
            footer(this,lockCard,config.separateLock?tr("Своя картинка и эффект для экрана блокировки. При установке выбери «Главный экран и экран блокировки».","Its own picture and effect. When setting the wallpaper, choose “Home and lock screens”."):tr("Сейчас блокировка показывает то же, что и главный экран.","The lock screen currently mirrors the home screen."));
        }
        editing.setVisibility(edit?View.VISIBLE:View.GONE);choose.setEnabled(edit);choose.setAlpha(edit?1:.45f);
        int selected=ImageStore.isCustom(this,mediaSlot())?0:ImageStore.presetIndex(this,mediaSlot())+1;for(int i=0;i<4;i++)tiles[i].setSelectedAnimated(i==selected);
        if(status.getText().length()==0)status.setText(tr("Наклони телефон или веди пальцем по фото","Tilt your phone or drag across the photo"));
        refreshPreviews();applyHaptics(sheetBody);
    }
    void rebuildFraming(){
        frame.removeAllViews();WallpaperConfig.Profile p=profile();
        unitSlider(this,frame,tr("Масштаб","Zoom"),(p.scale-1.02f)/1.48f,v->{profile().scale=1.02f+v/100f*1.48f;persist();});
        unitSlider(this,frame,tr("Сдвиг по горизонтали","Horizontal position"),p.cropX,v->{profile().cropX=v/100f;persist();});
        unitSlider(this,frame,tr("Сдвиг по вертикали","Vertical position"),p.cropY,v->{profile().cropY=v/100f;persist();});
    }
    /** Favorites first, then recents, then the rest of the catalog. */
    void buildStrip(){
        strip.removeAllViews();LinkedHashSet<String> order=new LinkedHashSet<>(config.favorites);order.addAll(config.recents);for(Effects.Effect e:Effects.ALL)order.add(e.id);
        stripOrder.clear();stripOrder.addAll(order);
        for(String id:order){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(72),-1);lp.rightMargin=dp(8);strip.addView(card(Effects.get(id),dp(72),false),lp);}
        ringShown=editable()?profile().effect:null;
    }
    /** Effect card: preview rendered on the slot's photo, name, selection ring. */
    View card(Effects.Effect e,int width,boolean large){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setClickable(true);card.setFocusable(true);card.setContentDescription(e.name(config.en()));
        FrameLayout box=new FrameLayout(this);float radius=px(this,large?18:14);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(new Shape(0xff2c2c2e,radius));image.setOutlineProvider(ViewOutlineProvider.BACKGROUND);image.setClipToOutline(true);
        Bitmap b=previews.get(e.id);if(b!=null)image.setImageBitmap(b);image.setTag("preview:"+e.id);
        box.addView(image,new FrameLayout.LayoutParams(-1,-1));
        boolean selected=e.id.equals(profile().effect)&&editable();
        if(selected)ring(box,radius,!large&&!e.id.equals(ringShown));
        if(large&&config.isFavorite(e.id)){ImageView h=new ImageView(this);h.setImageDrawable(new Glyph(this,Glyph.HEART_FILL,0xffff375f,16));h.setBackground(new Shape(0x99000000,-1));h.setScaleType(ImageView.ScaleType.CENTER);FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.TOP|Gravity.END);hp.setMargins(0,dp(6),dp(6),0);box.addView(h,hp);}
        card.addView(box,new LinearLayout.LayoutParams(-1,large?Math.round(width*1.45f):dp(86)));
        TextView name=text(this,e.name(config.en()),large?15:11,large?SEMIBOLD:MEDIUM,selected?LABEL:0xd9ffffff);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(dp(2),dp(5),dp(2),0);if(!large)name.setGravity(Gravity.CENTER);card.addView(name);
        if(large){TextView cat=text(this,e.category(config.en()),13,REGULAR,SECONDARY);cat.setPadding(dp(2),dp(1),0,0);card.addView(cat);}
        card.setOnClickListener(v->{tick(v);if(large&&!selected&&editable())ring(box,radius,true);selectEffect(e.id);if(catalog!=null&&catalog.getParent()!=null)closeCatalog();});
        return pressable(card);
    }
    /** Selection ring; a newly selected card pops its preview on a spring while the ring fades in around it. */
    void ring(FrameLayout box,float radius,boolean animate){
        Shape ring=new Shape(0,radius).stroke(px(this,2.5f),LABEL);box.setForeground(ring);if(!animate)return;
        ring.setAlpha(0);Ios.animate(null,0,1,SMOOTH,a->ring.setAlpha(Math.round(255*Math.min(1,(float)a.getAnimatedValue()))));
        View image=box.getChildAt(0);if(!still()){image.setScaleX(.88f);image.setScaleY(.88f);}spring(image,POP).scaleX(1).scaleY(1);
    }
    void refreshPreviews(){
        String key=mediaSlot()+":"+DepthMapManager.key(this,mediaSlot());
        if(key.equals(previewKey))return;previewKey=key;
        thumbnails.render(mediaSlot(),profile(),(id,bitmap)->{Bitmap old=previews.put(id,bitmap);for(View v:findAll("preview:"+id))((ImageView)v).setImageBitmap(bitmap);if(old!=null)main.postDelayed(old::recycle,500);});
    }
    List<View> findAll(String tag){List<View> out=new ArrayList<>();collect(root,tag,out);if(catalog!=null&&catalog.getParent()==null)collect(catalog,tag,out);return out;}
    static void collect(View v,String tag,List<View> out){if(tag.equals(v.getTag()))out.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collect(((ViewGroup)v).getChildAt(i),tag,out);}

    // ---------------------------------------------------------------- catalog
    void openCatalog(){
        if(catalog==null)buildCatalog();else search.setText("");
        if(catalog.getParent()==null)root.addView(catalog,new FrameLayout.LayoutParams(-1,-1));
        entering=true;fillCatalog();entering=false;
        catalog.setTranslationY(root.getHeight());present();spring(catalog,SMOOTH).translationY(0).setUpdateListener(a->present());updateBack();
    }
    void closeCatalog(){
        if(catalog==null||catalog.getParent()==null)return;hideKeyboard();
        spring(catalog,SNAPPY).translationY(root.getHeight()).setUpdateListener(a->present()).withEndAction(()->{root.removeView(catalog);present();updateBack();});
    }
    /** iOS sheet presentation, driven by the catalog's position: the stage recedes to 94%, rounds its corners and dims. */
    void present(){
        float h=Math.max(1,root.getHeight()),p=catalogOpen()?clamp(1-catalog.getTranslationY()/h,0,1):0,s=still()?1:1-.06f*p;
        stage.setPivotX(root.getWidth()/2f);stage.setPivotY(h/2);stage.setScaleX(s);stage.setScaleY(s);curtain.set(p);
    }
    /** Swipe down on the catalog's header: follows the finger (rubber band upward), then closes or springs back. */
    boolean catalogDrag(View v,MotionEvent e){
        float y=e.getRawY();
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN->{catalogY=catalogLastY=y;catalogTime=e.getEventTime();catalogSpeed=0;catalog.animate().cancel();}
            case MotionEvent.ACTION_MOVE->{
                float dy=y-catalogY;catalog.setTranslationY(dy>0?dy:rubber(dy,dp(90)));present();
                float dt=Math.max(1,e.getEventTime()-catalogTime)/1000f;catalogSpeed=.6f*(y-catalogLastY)/dt+.4f*catalogSpeed;catalogLastY=y;catalogTime=e.getEventTime();
            }
            case MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{
                float ty=catalog.getTranslationY();
                boolean moved=Math.abs(y-catalogY)>dp(8);
                if(moved&&(ty>root.getHeight()*.22f||catalogSpeed>dp(800)&&ty>dp(12)))closeCatalog();else spring(catalog,BOUNCY).translationY(0).setUpdateListener(a->present());
            }
        }
        return true;
    }
    void buildCatalog(){
        catalog=new FrameLayout(this);catalog.setBackground(new Shape(0xd90c0c0e,px(this,SHEET_RADIUS)).glass(Math.max(1,px(this,.7f))).legible(.04f));catalog.setClickable(true);
        catalog.setOutlineProvider(ViewOutlineProvider.BACKGROUND);catalog.setClipToOutline(true);
        LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);catalog.addView(column,new FrameLayout.LayoutParams(-1,-1));
        // A sheet card: starts just below the status bar, so the receding stage shows above it.
        catalog.setOnApplyWindowInsetsListener((v,i)->{Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
            column.setPadding(bars.left,0,bars.right,bars.bottom);FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)v.getLayoutParams();int top=bars.top+dp(10);
            if(lp!=null&&lp.topMargin!=top){lp.topMargin=top;v.post(()->v.setLayoutParams(lp));}return i;});
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);top.setClipChildren(false);column.addView(top);top.setOnTouchListener(this::catalogDrag);
        View grabber=new View(this);grabber.setBackground(new Shape(0x66ebebf5,-1));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(36),dp(5));gp.gravity=Gravity.CENTER_HORIZONTAL;gp.topMargin=dp(8);top.addView(grabber,gp);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setClipChildren(false);head.setPadding(dp(20),dp(6),dp(16),0);top.addView(head);
        head.addView(text(this,tr("Эффекты","Effects"),34,BOLD,LABEL),new LinearLayout.LayoutParams(0,-2,1));
        ImageView close=circle(this,Glyph.CLOSE,tr("Закрыть","Close"));head.addView(close,new LinearLayout.LayoutParams(dp(36),dp(36)));close.setOnClickListener(v->closeCatalog());
        LinearLayout searchBox=new LinearLayout(this);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(40));sp.setMargins(dp(16),dp(12),dp(16),0);column.addView(searchBox,sp);
        search=searchField(this,tr("Поиск эффектов","Search effects"),searchBox);
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int n){}public void onTextChanged(CharSequence s,int a,int b,int n){fillCatalog();}public void afterTextChanged(android.text.Editable s){}});
        HorizontalScrollView chipScroll=new HorizontalScrollView(this);chipScroll.setHorizontalScrollBarEnabled(false);chipScroll.setClipToPadding(false);chipScroll.setPadding(dp(16),0,dp(16),0);
        chipScroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);LinearLayout chipRow=new LinearLayout(this);chipRow.setClipChildren(false);chipScroll.addView(chipRow);LinearLayout.LayoutParams crp=new LinearLayout.LayoutParams(-1,dp(36));crp.topMargin=dp(12);column.addView(chipScroll,crp);
        String[] cats=config.en()?Effects.CATEGORY_EN:Effects.CATEGORY_RU,collections=config.en()?WallpaperConfig.COLLECTION_EN:WallpaperConfig.COLLECTION_RU;chips.clear();
        // All, Favorites, the curated collections, then the categories.
        List<Integer> order=new ArrayList<>(List.of(-1,-2));for(int i=0;i<collections.length;i++)order.add(COLLECTION_FILTER-i);for(int i=0;i<cats.length;i++)order.add(i);
        for(int f:order){
            String label=f==-1?tr("Все","All"):f==-2?tr("Избранное","Favorites"):f<=COLLECTION_FILTER?collections[COLLECTION_FILTER-f]:cats[f];
            TextView c=chip(this,label);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-1);lp.rightMargin=dp(8);chipRow.addView(c,lp);chips.add(c);c.setTag(f);
            c.setOnClickListener(v->{tick(v);filter=f;fillCatalog();});
        }
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);column.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        catalogGrid=new LinearLayout(this);catalogGrid.setOrientation(LinearLayout.VERTICAL);catalogGrid.setPadding(dp(16),dp(14),dp(16),dp(24));scroll.addView(catalogGrid);
    }
    void fillCatalog(){
        for(TextView c:chips)chipSelected(c,(int)c.getTag()==filter);
        List<Effects.Effect> found=new ArrayList<>();
        for(Effects.Effect e:Effects.search(search==null?"":search.getText().toString()))if(filter==-1||filter==-2&&config.isFavorite(e.id)||filter<=COLLECTION_FILTER&&WallpaperConfig.inCollection(COLLECTION_FILTER-filter,e)||filter==e.category)found.add(e);
        catalogGrid.removeAllViews();int width=(root.getWidth()-dp(16)*2-dp(12))/2;
        if(found.isEmpty()){TextView empty=text(this,filter==-2?tr("Пока пусто. Нажми ♥ у эффекта, чтобы добавить его сюда.","Nothing yet. Tap ♥ on an effect to add it here."):tr("Ничего не найдено","No effects found"),17,REGULAR,SECONDARY);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(20),dp(60),dp(20),0);catalogGrid.addView(empty);return;}
        LinearLayout line=null;
        for(int i=0;i<found.size();i++){
            if(i%2==0){line=new LinearLayout(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(18);catalogGrid.addView(line,lp);}
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(width,-2);if(i%2==1)cp.leftMargin=dp(12);View card=card(found.get(i),width,true);line.addView(card,cp);
            if(i<12)appear(card,i,!entering);  // only what fits the first screens; the rest is below the fold
        }
        applyHaptics(catalog);
    }

    // ---------------------------------------------------------------- actions
    void switchSlot(String next){
        slot=next;renderer.mode(WallpaperConfig.LOCK.equals(next)?RenderLoop.SHOW_LOCK:RenderLoop.SHOW_HOME);status.setText("");refresh();rebuildFraming();loadTileThumbnail();
    }
    void selectEffect(String id){
        if(!editable())return;profile().effect=id;config.used(id);persist();refresh();
        Effects.Effect e=Effects.get(id);
        swap(status,e.has(Effects.DEPTH)?tr("Глубина готовится по фото…","Estimating depth from your photo…"):e.has(Effects.WIPE)?tr("Проведи пальцем по фото","Swipe across the photo"):e.has(Effects.TOUCH)?tr("Коснись фото","Touch the photo"):tr("Наклони телефон","Tilt your phone"));
    }
    /** SF Symbols bounce: the heart is squeezed, then overshoots on a loose spring; a new favorite also tips sideways. */
    void toggleFavorite(String id,View v){
        config.toggleFavorite(id);tick(v);persist();refresh();boolean fav=config.isFavorite(id);
        if(!still()){v.setScaleX(.62f);v.setScaleY(.62f);v.setRotation(fav?-14:0);}
        spring(v,POP).scaleX(1).scaleY(1).rotation(0).translationX(0).translationY(0).alpha(1);
    }
    /** "Surprise me": a different effect with tasteful random parameters; the dice tumbles and the strip glides to the card. */
    void surprise(View v){
        if(!editable())return;tick(v);float[] values=new float[3];String id=WallpaperConfig.surprise(random,profile().effect,values);
        profile().params.put(id,values);selectEffect(id);
        if(!still()){v.setRotation(-200);v.setScaleX(.7f);v.setScaleY(.7f);}spring(v,POP).rotation(0).scaleX(1).scaleY(1).translationX(0).translationY(0).alpha(1);
        stripScroll.post(()->{
            int index=stripOrder.indexOf(id);if(index<0)return;int step=dp(72)+dp(8);
            int max=Math.max(0,strip.getWidth()-stripScroll.getWidth()),to=Math.max(0,Math.min(max,index*step-(stripScroll.getWidth()-dp(72))/2));
            stripMove=Ios.animate(stripMove,stripScroll.getScrollX(),to,SMOOTH,a->stripScroll.scrollTo(Math.round((float)a.getAnimatedValue()),0));
        });
    }
    /** A row with a title and a chevron that pushes another screen. */
    void navRow(LinearLayout card,String title,Runnable open){
        ImageView chevron=new ImageView(this);chevron.setImageDrawable(new Glyph(this,Glyph.CHEVRON,TERTIARY,16));
        LinearLayout r=row(this,card,title,chevron);r.setClickable(true);r.setFocusable(true);r.setOnClickListener(x->{tick(x);open.run();});pressable(r);
    }
    void setSeparateLock(boolean on){
        config.separateLock=on;
        if(on){config.lock.copyFrom(config.home);io.execute(()->{try{ImageStore.copyMedia(getApplicationContext(),WallpaperConfig.HOME,WallpaperConfig.LOCK);}catch(Exception e){runOnUiThread(()->swap(status,tr("Не удалось скопировать фото","Could not copy the photo")));}runOnUiThread(()->{if(!isDestroyed()){previewKey=null;refresh();loadTileThumbnail();}});});}
        persist();previewKey=null;refresh();rebuildFraming();loadTileThumbnail();
    }
    void photoTile(){
        String m=mediaSlot();
        if(!ImageStore.hasCustom(this,m)||ImageStore.isCustom(this,m)){pick(PICK_PHOTO);return;}
        ImageStore.useCustom(this,m);mediaChanged(tr("Твоё фото","Your photo"));
    }
    void mediaChanged(String message){renderer.reload();previewKey=null;refresh();swap(status,message);loadTileThumbnail();}
    void loadTileThumbnail(){String m=mediaSlot();int side=dp(74);View host=root;Context app=getApplicationContext();io.execute(()->{Bitmap b=null;try{b=ImageStore.thumbnail(app,m,side);}catch(Exception|OutOfMemoryError ignored){}final Bitmap t=b;
        // Glass refracts what the wallpaper shows: the saved photo, or the selected preset drawn small.
        try{if(t!=null&&ImageStore.isCustom(app,m))Ios.backdrop(host,t);else{Bitmap preset=ImageStore.preset(ImageStore.presetIndex(app,m),side/720f);Ios.backdrop(host,preset);preset.recycle();}}catch(RuntimeException|OutOfMemoryError ignored){}
        runOnUiThread(()->{if(!isDestroyed())tiles[0].setImage(t);});});}
    void install(){
        saveNow();Intent intent=new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new ComponentName(this,TiltWallpaper.class));
        try{startActivity(intent);}catch(ActivityNotFoundException e){try{startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));}catch(ActivityNotFoundException missing){swap(status,tr("Системная установка обоев недоступна.","System wallpaper picker is unavailable."));}}
    }
    void pick(int request){
        // Photo or video for the slot; the Android 13+ picker without a type offers both.
        Intent i=request!=PICK_PHOTO?new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
            :Build.VERSION.SDK_INT>=33?new Intent("android.provider.action.PICK_IMAGES")
            :new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"}).addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(i,request);}catch(ActivityNotFoundException ex){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),request);}catch(ActivityNotFoundException e){swap(status,tr("Не найдено приложение выбора файлов.","No image picker is available."));}}
    }
    /** Drag on the preview tilts manually (touch effects receive the touch instead); a tap hides the interface. */
    boolean previewTouch(View v,MotionEvent e){
        boolean touchEffect=profile().effect().has(Effects.TOUCH);float w=Math.max(1,root.getWidth()),h=Math.max(1,root.getHeight());
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN->{downX=e.getX();downY=e.getY();dragging=false;if(touchEffect)renderer.touch(e.getX()/w,e.getY()/h,true);}
            case MotionEvent.ACTION_MOVE->{
                float dx=e.getX()-downX,dy=e.getY()-downY;
                if(touchEffect){renderer.touch(e.getX()/w,e.getY()/h,true);break;}
                if(!dragging&&Math.hypot(dx,dy)>dp(10))dragging=true;
                if(dragging){dragTiltX=FoldMath.clamp(dx/px(this,3),-80,80);dragTiltY=FoldMath.clamp(-dy/px(this,3),-80,80);renderer.tilt(dragTiltX+config.neutralX,dragTiltY+config.neutralY);Ios.light(dragTiltX,dragTiltY);angle.setText(String.format(Locale.ROOT,tr("Наклон %.0f° · пальцем","Tilt %.0f° · finger"),Math.hypot(dragTiltX,dragTiltY)));}
            }
            case MotionEvent.ACTION_UP->{if(!dragging&&Math.hypot(e.getX()-downX,e.getY()-downY)<dp(10)&&!touchEffect)setHidden(!hidden);dragging=false;}
            case MotionEvent.ACTION_CANCEL->dragging=false;
        }
        return true;
    }
    void setHidden(boolean value){hidden=value;updateBack();float drop=sheet.getHeight()+dp(60);spring(header,SMOOTH).alpha(value?0:1).translationY(value?-dp(24):0);spring(sheet,SMOOTH).translationY(value?drop:0);}

    // ---------------------------------------------------------------- persistence & feedback
    final Runnable save=()->config.save(this);
    /** Action sheet of the effects that can lie over the current one, plus None. */
    void chooseLayer(){
        WallpaperConfig.Profile p=profile();List<Effects.Effect> options=new ArrayList<>();
        for(Effects.Effect x:Effects.ALL)if(Effects.layerable(p.effect(),x))options.add(x);
        String[] items=new String[options.size()+1];items[0]=tr("Нет","None");for(int i=0;i<options.size();i++)items[i+1]=options.get(i).name(config.en());
        PresetsActivity.actionSheet(this,tr("Второй слой","Second layer"),items,-1,tr("Отмена","Cancel"),i->{profile().overlay=i==0?null:options.get(i-1).id;persist();refresh();});
    }
    void persist(){renderer.settings(config);WallpaperConfig.Profile p=profile();Ios.backdropFrame(p.scale,p.cropX,p.cropY);main.removeCallbacks(save);main.postDelayed(save,150);}
    void saveNow(){main.removeCallbacks(save);config.save(this);}
    void tick(View v){if(config.haptics)v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);}
    void applyHaptics(View v){if(v==null)return;v.setHapticFeedbackEnabled(config.haptics);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)applyHaptics(((ViewGroup)v).getChildAt(i));}
    void hideKeyboard(){android.view.inputmethod.InputMethodManager m=getSystemService(android.view.inputmethod.InputMethodManager.class);if(m!=null&&search!=null)m.hideSoftInputFromWindow(search.getWindowToken(),0);}
    void showError(String code){if(isDestroyed())return;
        if("depth".equals(code))swap(status,tr("Не удалось подготовить глубину — пока показана Классика.","Depth could not be prepared — showing Classic for now."));
        else if("image".equals(code))swap(status,tr("Не удалось открыть фото. Выбери другое.","Could not open this photo. Choose another."));
        else swap(status,tr("Эффект недоступен на этом экране. Показано статичное фото.","This surface cannot show the effect. Showing the still photo."));
    }
    // Back closes the catalog or brings the hidden interface back. Android 13+ (and target 36 gestures) only
    // deliver back to a registered callback, so one is registered exactly while there is something to close.
    final android.window.OnBackInvokedCallback back=this::handleBack;boolean backRegistered;
    boolean catalogOpen(){return catalog!=null&&catalog.getParent()!=null;}
    void handleBack(){if(catalogOpen())closeCatalog();else if(hidden)setHidden(false);}
    void updateBack(){
        if(Build.VERSION.SDK_INT<33)return;boolean want=catalogOpen()||hidden;
        if(want&&!backRegistered)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,back);
        else if(!want&&backRegistered)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(back);
        backRegistered=want;
    }
    @SuppressWarnings("deprecation") @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){if(catalogOpen()||hidden)handleBack();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("slot",slot);super.onSaveInstanceState(out);}
    void update(){if(renderer==null)return;renderer.visible(resumed&&ready);if(resumed&&ready)motion.start();else motion.stop();}
    public void surfaceCreated(SurfaceHolder h){}
    public void surfaceChanged(SurfaceHolder h,int format,int w,int height){renderer.attach(h.getSurface(),w,height);ready=true;update();}
    public void surfaceDestroyed(SurfaceHolder h){ready=false;update();renderer.detach();}
    @Override protected void onResume(){
        super.onResume();resumed=true;
        // Settings may have changed in SettingsActivity.
        WallpaperConfig latest=WallpaperConfig.load(this);Ios.reduceMotion(latest.reduceMotion);
        if(!latest.json().equals(config.json())){boolean language=!latest.language.equals(config.language);config=latest;renderer.settings(config);if(language){recreate();return;}refresh();rebuildFraming();applyHaptics(root);}
        // A preset applied (or a scheduled photo) elsewhere replaced a slot's media.
        if(mediaMoved()){rememberMedia();renderer.reload();previewKey=null;refresh();}
        loadTileThumbnail();update();
    }
    @Override protected void onPause(){saveNow();rememberMedia();resumed=false;update();super.onPause();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);io.shutdown();motion.close();renderer.close();thumbnails.close();for(Bitmap b:previews.values())b.recycle();super.onDestroy();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==ONBOARD){if(result==RESULT_OK)main.postDelayed(this::install,300);return;}
        // The trim screen imports the clip itself; refresh either way, since a back press during saving still finishes the import.
        if(request==TRIM){renderer.reload();mediaChanged(result==RESULT_OK?tr("Видео готово. Выбери эффект.","Video ready. Pick an effect."):"");return;}
        // Videos go through the trim screen (cut, audio removed) before they become the wallpaper.
        if(request==PICK_PHOTO&&result==RESULT_OK&&data!=null&&data.getData()!=null){String type=getContentResolver().getType(data.getData());
            if(type!=null&&type.startsWith("video/")){startActivityForResult(VideoTrimActivity.intent(this,data.getData(),mediaSlot()),TRIM);return;}}
        if((request!=PICK_PHOTO&&request!=PICK_DEPTH)||result!=RESULT_OK||data==null||data.getData()==null)return;
        importing=true;choose.setEnabled(false);install.setEnabled(false);swap(status,tr("Подготовка изображения…","Preparing image…"));Uri uri=data.getData();Context app=getApplicationContext();String m=mediaSlot();
        io.execute(()->{boolean ok=true;try{if(request==PICK_PHOTO){String type=app.getContentResolver().getType(uri);if(type!=null&&type.startsWith("video/"))ImageStore.importVideo(app,uri,m);else ImageStore.importImage(app,uri,m);DepthMapManager.prune(app);}else DepthMapManager.importDepth(app,uri,m);}catch(Exception|OutOfMemoryError e){ok=false;}
            final boolean success=ok;runOnUiThread(()->{if(isDestroyed())return;importing=false;choose.setEnabled(editable());install.setEnabled(true);
                if(success){if(config.haptics)status.performHapticFeedback(HapticFeedbackConstants.CONFIRM);mediaChanged(request==PICK_DEPTH?tr("Используется твоя карта глубины","Using your depth map"):tr("Готово. Выбери эффект.","Ready. Pick an effect."));}
                else{if(config.haptics)status.performHapticFeedback(HapticFeedbackConstants.REJECT);showError(request==PICK_DEPTH?"depth":"image");}});});
    }

    /** The effect sheet. Pulled by its top edge, or anywhere while scrolled to the top, it follows the finger on a
     *  rubber band; let go it springs back, or past a third of its height (or flung) it tucks the interface away. */
    final class Sheet extends ScrollView {
        final int slop=ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();float startX,startY,localY,lastY,speed;long lastTime;boolean pulling;
        Sheet(){super(MainActivity.this);}
        boolean shouldPull(MotionEvent e){
            float dx=e.getRawX()-startX,dy=e.getRawY()-startY;
            return !hidden&&Math.abs(dy)>slop&&Math.abs(dy)>Math.abs(dx)*1.3f&&(dy>0&&getScrollY()==0||localY<dp(44));
        }
        void begin(MotionEvent e){pulling=true;startY=lastY=e.getRawY();lastTime=e.getEventTime();speed=0;animate().cancel();getParent().requestDisallowInterceptTouchEvent(true);}
        @Override public boolean onInterceptTouchEvent(MotionEvent e){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startX=e.getRawX();startY=e.getRawY();localY=e.getY();pulling=false;}
            else if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!pulling&&shouldPull(e)){begin(e);return true;}
            return pulling||super.onInterceptTouchEvent(e);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){startX=e.getRawX();startY=e.getRawY();localY=e.getY();pulling=false;}
            if(!pulling&&action==MotionEvent.ACTION_MOVE&&shouldPull(e)){
                // Hand the gesture over from scrolling: the scroll view gets a cancel so it drops its own drag state.
                MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.onTouchEvent(cancel);cancel.recycle();begin(e);
            }
            if(!pulling)return super.onTouchEvent(e);
            float y=e.getRawY(),dy=y-startY;
            if(action==MotionEvent.ACTION_MOVE){
                setTranslationY(dy>0?rubber(dy,getHeight()*1.6f):rubber(dy,dp(60)));
                float dt=Math.max(1,e.getEventTime()-lastTime)/1000f;speed=.6f*(y-lastY)/dt+.4f*speed;lastY=y;lastTime=e.getEventTime();
            }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                pulling=false;float ty=getTranslationY();
                if(action==MotionEvent.ACTION_UP&&(ty>getHeight()*.3f||speed>dp(900)&&ty>dp(16)))setHidden(true);else spring(this,BOUNCY).translationY(0);
            }
            return true;
        }
        @Override public boolean performClick(){return super.performClick();}
    }
    /** Behind the presented catalog: black outside the receding stage's rounded card, and a dim over it. */
    final class Curtain extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path();final RectF card=new RectF();float progress;
        Curtain(){super(MainActivity.this);setVisibility(GONE);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void set(float p){if(p==progress)return;progress=p;setVisibility(p>.001f?VISIBLE:GONE);invalidate();}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),s=stage.getScaleX(),r=px(MainActivity.this,14)*progress;
            card.set(w/2-w*s/2,h/2-h*s/2,w/2+w*s/2,h/2+h*s/2);
            path.reset();path.setFillType(Path.FillType.EVEN_ODD);path.addRect(0,0,w,h,Path.Direction.CW);path.addRoundRect(card,r,r,Path.Direction.CW);
            paint.setColor(Color.BLACK);c.drawPath(path,paint);paint.setColor(Color.argb(Math.round(110*progress),0,0,0));c.drawRoundRect(card,r,r,paint);
        }
    }

    /** Line icons the kit has no glyph for, drawn on the same unit grid and stroke weight as Ios.Glyph. */
    abstract class LineIcon extends Drawable {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final int size=dp(20);
        LineIcon(){paint.setColor(LABEL);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.085f);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);setBounds(0,0,size,size);}
        abstract void unit(Canvas c);
        @Override public void draw(Canvas c){Rect b=getBounds();float s=Math.min(b.width(),b.height());c.save();c.translate(b.exactCenterX()-s/2,b.exactCenterY()-s/2);c.scale(s,s);unit(c);c.restore();}
        @Override public int getIntrinsicWidth(){return size;}
        @Override public int getIntrinsicHeight(){return size;}
        @Override public void setAlpha(int a){paint.setAlpha(a);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    /** die.face.5: rounded square with five pips. */
    final class Dice extends LineIcon {
        @Override void unit(Canvas c){
            paint.setStyle(Paint.Style.STROKE);c.drawRoundRect(.16f,.16f,.84f,.84f,.17f,.17f,paint);paint.setStyle(Paint.Style.FILL);
            float[][] pips={{.34f,.34f},{.66f,.34f},{.5f,.5f},{.34f,.66f},{.66f,.66f}};for(float[] p:pips)c.drawCircle(p[0],p[1],.06f,paint);
        }
    }
    /** square.stack: a card with two cards peeking behind it, for presets. */
    final class StackIcon extends LineIcon {
        @Override void unit(Canvas c){
            paint.setStyle(Paint.Style.STROKE);c.drawRoundRect(.18f,.36f,.82f,.86f,.1f,.1f,paint);
            c.drawLine(.26f,.25f,.74f,.25f,paint);c.drawLine(.34f,.14f,.66f,.14f,paint);
        }
    }

    /** Wallpaper swatch: continuous-corner thumbnail with the iOS selection ring drawn outside a small gap. */
    final class Tile extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),ring=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path(),outer=new Path();final RectF box=new RectF();
        final Shape empty=glass(MainActivity.this,14);final Glyph plus=new Glyph(MainActivity.this,Glyph.PHOTO,LABEL,24);Bitmap image;float selection;android.animation.ValueAnimator animator;
        Tile(Bitmap image){super(MainActivity.this);setClickable(true);setFocusable(true);ring.setStyle(Paint.Style.STROKE);ring.setColor(LABEL);setImage(image);}
        void setImage(Bitmap next){image=next;paint.setShader(null);invalidate();}
        void setSelectedAnimated(boolean value){
            if(value==isSelected()&&animator!=null)return;setSelected(value);
            animator=Ios.animate(animator,selection,value?1:0,BOUNCY,a->{selection=(float)a.getAnimatedValue();invalidate();});
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
