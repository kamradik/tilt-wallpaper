package dev.solotilt.personal;

import android.animation.*;
import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.Locale;
import static dev.solotilt.personal.Ios.*;

/** Local author page in the style of an iOS contact card; music and animations run only while this activity is resumed. */
public final class AuthorActivity extends Activity {
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean resumed,prepared,enabled,focused;
    private int position;
    private ImageView play,backdrop;
    private TextView musicHint,elapsed,remaining;
    private Equalizer equalizer;
    private Scrubber scrubber;
    private Animator ambient,floatAnimation;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable progress=new Runnable(){public void run(){updateProgress();if(resumed)main.postDelayed(this,250);}};
    int dp(float n){return Ios.dp(this,n);}
    LinearLayout.LayoutParams row(int width,int height,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,height);p.topMargin=dp(top);p.gravity=Gravity.CENTER_HORIZONTAL;return p;}
    TextView centered(String value,float size,int weight,int color){TextView t=text(this,value,size,weight,color);t.setGravity(Gravity.CENTER);return t;}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);
        enabled=getPreferences(0).getBoolean("music",true);position=state==null?0:state.getInt("position",0);
        audio=getSystemService(AudioManager.class);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(change->{
                focused=change==AudioManager.AUDIOFOCUS_GAIN;
                if(focused){if(resumed&&enabled&&prepared&&player!=null)player.start();}
                else pauseMusic();
                updateMusic();
            },main).build();
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);

        // Album-art backdrop: the avatar, heavily blurred and slowly drifting, as in Apple Music's now-playing screen.
        Bitmap avatar=BitmapFactory.decodeResource(getResources(),R.drawable.author_avatar);
        backdrop=new ImageView(this);backdrop.setImageBitmap(Bitmap.createScaledBitmap(avatar,24,24,true));backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);backdrop.setScaleX(1.5f);backdrop.setScaleY(1.5f);
        if(Build.VERSION.SDK_INT>=31)backdrop.setRenderEffect(RenderEffect.createBlurEffect(px(this,48),px(this,48),Shader.TileMode.MIRROR));
        root.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        View scrim=new View(this);scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x40000000,0x8c000000,0xe0000000}));root.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        ObjectAnimator drift=ObjectAnimator.ofPropertyValuesHolder(backdrop,PropertyValuesHolder.ofFloat(View.SCALE_X,1.5f,1.8f),PropertyValuesHolder.ofFloat(View.SCALE_Y,1.5f,1.8f),PropertyValuesHolder.ofFloat(View.ROTATION,-6,6));
        drift.setDuration(16000);drift.setRepeatCount(ValueAnimator.INFINITE);drift.setRepeatMode(ValueAnimator.REVERSE);ambient=drift;

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.CENTER);content.setClipChildren(false);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));

        FrameLayout portrait=new FrameLayout(this);portrait.setClipChildren(false);content.addView(portrait,row(dp(132),dp(132),12));
        ImageView photo=new ImageView(this);photo.setImageBitmap(avatar);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setContentDescription("Аватарка Kamradik");
        photo.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline o){o.setOval(0,0,v.getWidth(),v.getHeight());}});photo.setClipToOutline(true);photo.setElevation(px(this,20));photo.setOutlineSpotShadowColor(0xff000000);
        photo.setForeground(new Shape(0,-1).stroke(px(this,1),0x40ffffff));portrait.addView(photo,new FrameLayout.LayoutParams(-1,-1));
        ObjectAnimator hover=ObjectAnimator.ofFloat(portrait,View.TRANSLATION_Y,-px(this,4),px(this,4));hover.setDuration(1800);hover.setRepeatCount(ValueAnimator.INFINITE);hover.setRepeatMode(ValueAnimator.REVERSE);floatAnimation=hover;
        TextView name=centered("Kamradik",34,BOLD,LABEL);content.addView(name,row(-2,-2,20));
        content.addView(centered("Разработчик · любитель красивых пикселей",15,REGULAR,SECONDARY),row(-2,-2,4));
        TextView message=centered("Немного магии.\nИ совершенно новый взгляд на экран.",19,MEDIUM,0xebffffff);message.setLineSpacing(px(this,4),1);content.addView(message,row(-2,-2,26));
        LinearLayout github=new LinearLayout(this);github.setGravity(Gravity.CENTER);github.setBackground(new Shape(0xfff5f5f7,-1));github.setClickable(true);github.setFocusable(true);
        github.addView(text(this,"GitHub · kamradik",17,SEMIBOLD,0xff0b0b0d));ImageView arrow=new ImageView(this);arrow.setImageDrawable(new Glyph(this,Glyph.LINK,0xff0b0b0d,18));
        LinearLayout.LayoutParams arrowParams=new LinearLayout.LayoutParams(-2,-2);arrowParams.leftMargin=dp(6);github.addView(arrow,arrowParams);
        github.setContentDescription("Открыть GitHub разработчика Kamradik");content.addView(pressable(github),row(-1,dp(54),30));
        github.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/kamradik")));}catch(ActivityNotFoundException e){Toast.makeText(this,"Не найден браузер для открытия GitHub",Toast.LENGTH_LONG).show();}});

        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setBackground(new Shape(0x33000000,px(this,26)).glass(Math.max(1,px(this,.7f))));card.setPadding(dp(16),dp(16),dp(16),dp(14));content.addView(card,row(-1,-2,14));
        LinearLayout track=new LinearLayout(this);track.setGravity(Gravity.CENTER_VERTICAL);card.addView(track);
        ImageView art=new ImageView(this);art.setImageBitmap(avatar);art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),px(AuthorActivity.this,10));}});art.setClipToOutline(true);art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        track.addView(art,new LinearLayout.LayoutParams(dp(56),dp(56)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.setPadding(dp(12),0,dp(8),0);track.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);titles.addView(titleRow);
        TextView song=text(this,"отвратительный король",17,SEMIBOLD,LABEL);song.setSingleLine(true);song.setEllipsize(android.text.TextUtils.TruncateAt.END);titleRow.addView(song,new LinearLayout.LayoutParams(0,-2,1));
        equalizer=new Equalizer();LinearLayout.LayoutParams eqParams=new LinearLayout.LayoutParams(dp(16),dp(14));eqParams.leftMargin=dp(8);titleRow.addView(equalizer,eqParams);
        TextView about=text(this,"Саундтрек этой страницы",15,REGULAR,SECONDARY);about.setPadding(0,dp(2),0,0);titles.addView(about);
        play=new ImageView(this);play.setScaleType(ImageView.ScaleType.CENTER);play.setBackground(new Shape(0xfff5f5f7,-1));play.setClickable(true);play.setFocusable(true);track.addView(pressable(play),new LinearLayout.LayoutParams(dp(48),dp(48)));
        play.setOnClickListener(v->{
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if(player!=null&&prepared&&player.isPlaying()){enabled=false;pauseMusic();abandonFocus();}
            else {enabled=true;if(player==null)prepareMusic();else if(prepared)startMusic();}
            getPreferences(0).edit().putBoolean("music",enabled).apply();updateMusic();
        });
        scrubber=new Scrubber();card.addView(scrubber,row(-1,dp(4),16));
        LinearLayout times=new LinearLayout(this);card.addView(times,row(-1,-2,6));
        elapsed=text(this,"0:00",12,MEDIUM,SECONDARY);elapsed.setFontFeatureSettings("tnum");times.addView(elapsed,new LinearLayout.LayoutParams(0,-2,1));
        remaining=text(this,"−0:00",12,MEDIUM,SECONDARY);remaining.setFontFeatureSettings("tnum");times.addView(remaining);
        musicHint=centered("Тихо · только пока открыта эта страница",13,REGULAR,SECONDARY);card.addView(musicHint,row(-1,-2,10));
        content.addView(centered("TILT 1.1 · СДЕЛАНО KAMRADIK",11,MEDIUM,TERTIARY),row(-2,-2,28));
        updateMusic();

        // Navigation bar: a glass back button, and a compact title that appears once the large name scrolls under it (UINavigationBar).
        FrameLayout nav=new FrameLayout(this);root.addView(nav,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        View navBackground=new View(this);navBackground.setBackgroundColor(0xd9101012);navBackground.setAlpha(0);nav.addView(navBackground,new FrameLayout.LayoutParams(-1,-1));
        View navLine=hairline(this);navLine.setAlpha(0);nav.addView(navLine,new FrameLayout.LayoutParams(-1,Math.max(1,dp(.5f)),Gravity.BOTTOM));
        ImageView back=circle(this,Glyph.BACK,"Назад");back.setOnClickListener(v->finish());FrameLayout.LayoutParams backParams=new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.START|Gravity.BOTTOM);backParams.leftMargin=dp(16);backParams.bottomMargin=dp(8);nav.addView(back,backParams);
        TextView navTitle=text(this,"Kamradik",17,SEMIBOLD,LABEL);navTitle.setAlpha(0);FrameLayout.LayoutParams navTitleParams=new FrameLayout.LayoutParams(-2,dp(44),Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM);navTitleParams.bottomMargin=dp(8);navTitle.setGravity(Gravity.CENTER);nav.addView(navTitle,navTitleParams);
        scroll.setOnScrollChangeListener((v,x,y,ox,oy)->{float t=Math.max(0,Math.min(1,(y+nav.getHeight()-name.getTop()-name.getHeight()/2f)/px(this,24)));navTitle.setAlpha(t);navTitle.setTranslationY((1-t)*px(this,6));navBackground.setAlpha(t);navLine.setAlpha(t);});
        root.setOnApplyWindowInsetsListener((v,i)->{
            Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            nav.setPadding(bars.left,bars.top,bars.right,0);nav.getLayoutParams().height=bars.top+dp(60);nav.requestLayout();
            content.setPadding(dp(20)+bars.left,bars.top+dp(60),dp(20)+bars.right,dp(32)+bars.bottom);return i;
        });

        if(state==null){
            portrait.setScaleX(.82f);portrait.setScaleY(.82f);portrait.setAlpha(0);spring(portrait,BOUNCY).scaleX(1).scaleY(1).alpha(1).setStartDelay(120);
            for(int i=1;i<content.getChildCount();i++)rise(content.getChildAt(i),i);
        }
    }
    @Override public void finish(){super.finish();overridePendingTransition(R.anim.ios_pop_in,R.anim.ios_pop_out);}

    private void prepareMusic(){
        if(!resumed||!enabled||player!=null)return;
        MediaPlayer next=new MediaPlayer();player=next;prepared=false;
        try(android.content.res.AssetFileDescriptor fd=getResources().openRawResourceFd(R.raw.author_music)){
            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            next.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());next.setLooping(true);next.setVolume(.12f,.12f);
            next.setOnPreparedListener(p->{if(player!=p||!resumed)return;prepared=true;if(position>0)p.seekTo(position);if(enabled)startMusic();else updateMusic();});
            next.setOnErrorListener((p,what,extra)->{if(player==p){releaseMusic();updateMusic();swap(musicHint,"Не удалось воспроизвести музыку · нажми ▶ для повтора");}return true;});next.prepareAsync();
        }catch(IOException|RuntimeException e){releaseMusic();updateMusic();swap(musicHint,"Не удалось открыть аудиофайл · нажми ▶ для повтора");return;}
        updateMusic();
    }
    private void startMusic(){if(!resumed||!enabled||!prepared||player==null)return;focused=audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;if(focused)player.start();updateMusic();}
    private void pauseMusic(){if(player!=null&&prepared&&player.isPlaying())player.pause();}
    private void abandonFocus(){audio.abandonAudioFocusRequest(focus);focused=false;}
    private boolean playing(){return player!=null&&prepared&&player.isPlaying();}
    private void updateMusic(){
        if(play==null)return;boolean playing=playing(),loading=player!=null&&!prepared;
        int glyph=playing?Glyph.PAUSE:Glyph.PLAY;
        if(!(play.getTag() instanceof Integer)||(int)play.getTag()!=glyph){
            boolean first=play.getTag()==null;play.setTag(glyph);play.setImageDrawable(new Glyph(this,glyph,0xff0b0b0d,20));
            if(!first){play.setScaleX(.84f);play.setScaleY(.84f);spring(play,BOUNCY).scaleX(1).scaleY(1);}  // SF Symbols bounce on swap
        }
        play.setContentDescription(playing?"Пауза":"Включить музыку");play.setAlpha(loading?.6f:1);
        equalizer.playing=playing&&resumed;equalizer.invalidate();
        swap(musicHint,playing?"Тихо · только пока открыта эта страница":loading?"Загрузка музыки…":enabled&&prepared&&!focused?"Звук занят другим приложением · нажми для повтора":"Музыка на паузе");
        updateProgress();
    }
    private void updateProgress(){
        if(scrubber==null)return;int now=position,total=0;
        if(player!=null&&prepared){try{now=player.getCurrentPosition();total=player.getDuration();}catch(IllegalStateException ignored){}}
        scrubber.set(total>0?now/(float)total:0);elapsed.setText(clock(now));remaining.setText("−"+clock(Math.max(0,total-now)));
    }
    static String clock(int ms){int s=Math.max(0,ms)/1000;return String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
    private void releaseMusic(){if(player!=null){if(prepared){try{position=player.getCurrentPosition();}catch(IllegalStateException ignored){}}player.release();player=null;}prepared=false;abandonFocus();}
    @Override protected void onResume(){super.onResume();resumed=true;if(!still()){ambient.start();floatAnimation.start();}main.post(progress);if(enabled)prepareMusic();else updateMusic();}
    @Override protected void onPause(){resumed=false;ambient.cancel();floatAnimation.cancel();main.removeCallbacks(progress);releaseMusic();updateMusic();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle b){b.putInt("position",player!=null&&prepared?player.getCurrentPosition():position);super.onSaveInstanceState(b);}
    @Override protected void onDestroy(){releaseMusic();super.onDestroy();}

    /** Apple Music's now-playing bars: three capsules bouncing out of phase while audio plays. */
    private final class Equalizer extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF r=new RectF();boolean playing;
        Equalizer(){super(AuthorActivity.this);paint.setColor(0xffff375f);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            float w=getWidth()/5f,h=getHeight(),t=SystemClock.uptimeMillis()/1000f;boolean animate=playing&&ValueAnimator.areAnimatorsEnabled();
            paint.setAlpha(playing?255:110);
            for(int i=0;i<3;i++){float level=animate?.3f+.7f*Math.abs((float)Math.sin(t*(5.2f+i*1.7f)+i*1.3f)):.28f;r.set(i*2*w,h*(1-level),i*2*w+w,h);c.drawRoundRect(r,w/2,w/2,paint);}
            if(animate)postInvalidateOnAnimation();
        }
    }
    /** Thin playback progress capsule, as in the iOS now-playing card. */
    private final class Scrubber extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF r=new RectF();float value;
        Scrubber(){super(AuthorActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void set(float next){if(Math.abs(next-value)>.0005f){value=next;invalidate();}}
        @Override protected void onDraw(Canvas c){float h=getHeight();paint.setColor(0x40ffffff);r.set(0,0,getWidth(),h);c.drawRoundRect(r,h/2,h/2,paint);paint.setColor(0xd9ffffff);r.set(0,0,Math.max(h,getWidth()*value),h);c.drawRoundRect(r,h/2,h/2,paint);}
    }
}
