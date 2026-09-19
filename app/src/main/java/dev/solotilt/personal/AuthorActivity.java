package dev.solotilt.personal;

import android.animation.ValueAnimator;
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

/** Local author page; music and animations run only while this activity is resumed. */
public final class AuthorActivity extends Activity {
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean resumed,prepared,enabled,focused;
    private int position;
    private Button music;
    private TextView musicHint;
    private PixelScene backdrop;
    private PixelName nickname;
    private ValueAnimator floatAnimation;
    private final Handler main=new Handler(Looper.getMainLooper());
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    GradientDrawable box(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;}
    TextView label(String text,int size,int color){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);return t;}
    LinearLayout.LayoutParams row(int height,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,height<0?height:dp(height));p.topMargin=dp(top);return p;}
    Button action(String title,boolean filled){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(null,1);b.setTextColor(filled?0xff1c1724:0xfff2e7fb);b.setBackground(box(filled?0xffe6c7ff:0xb01d192e,filled?0xffe6c7ff:0xff5a416f,16));b.setMinHeight(dp(48));return b;}

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
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff100d1c);setContentView(root);
        backdrop=new PixelScene();root.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(1);root.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        shell.setOnApplyWindowInsetsListener((v,i)->{Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return i;});
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);nav.setPadding(dp(18),dp(8),dp(20),dp(8));shell.addView(nav,row(64,0));
        Button back=action("‹  Назад",false);nav.addView(back,new LinearLayout.LayoutParams(dp(110),dp(46)));back.setOnClickListener(v->finish());
        TextView tag=label("PLAYER PROFILE",11,0xffbd9fd7);tag.setLetterSpacing(.14f);tag.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);nav.addView(tag,new LinearLayout.LayoutParams(0,-1,1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=new LinearLayout(this);content.setOrientation(1);content.setGravity(Gravity.CENTER);content.setPadding(dp(28),dp(24),dp(28),dp(32));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        TextView badge=label("✦  СОЗДАТЕЛЬ ПРИЛОЖЕНИЯ",11,0xffd9c1eb);badge.setLetterSpacing(.12f);content.addView(badge,row(-2,0));
        FrameLayout portrait=new FrameLayout(this);portrait.setBackground(box(0xff251d35,0xffcf9cff,22));portrait.setPadding(dp(7),dp(7),dp(7),dp(7));
        LinearLayout.LayoutParams portraitParams=new LinearLayout.LayoutParams(dp(166),dp(166));portraitParams.topMargin=dp(30);portraitParams.bottomMargin=dp(18);portraitParams.gravity=Gravity.CENTER;content.addView(portrait,portraitParams);
        ImageView avatar=new ImageView(this);avatar.setImageResource(R.drawable.author_avatar);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.setBackground(box(0xff1c1724,0,16));avatar.setClipToOutline(true);avatar.setContentDescription("Аватарка Kamradik");portrait.addView(avatar,new FrameLayout.LayoutParams(-1,-1));
        floatAnimation=ValueAnimator.ofFloat(0,1);floatAnimation.setDuration(3600);floatAnimation.setRepeatCount(ValueAnimator.INFINITE);floatAnimation.setInterpolator(new android.view.animation.LinearInterpolator());floatAnimation.addUpdateListener(a->{float t=(float)a.getAnimatedValue();portrait.setTranslationY(dp(5)*(float)Math.sin(t*Math.PI*2));});
        nickname=new PixelName();nickname.setContentDescription("Kamradik");nickname.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);content.addView(nickname,row(80,0));
        content.addView(label("Разработчик · любитель красивых пикселей",13,0xffc5b6d2),row(-2,2));
        TextView message=label("Немного магии.\nИ совершенно новый взгляд на экран.",18,0xfff4eafa);message.setLineSpacing(dp(5),1);content.addView(message,row(-2,26));
        Button github=action("GitHub  ↗  kamradik",true);content.addView(github,row(54,28));github.setContentDescription("Открыть GitHub разработчика Kamradik");github.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/kamradik")));}catch(ActivityNotFoundException e){Toast.makeText(this,"Не найден браузер для открытия GitHub",Toast.LENGTH_LONG).show();}});
        LinearLayout soundCard=new LinearLayout(this);soundCard.setOrientation(1);soundCard.setPadding(dp(16),dp(15),dp(16),dp(14));soundCard.setBackground(box(0xb51d172b,0xff40314f,18));content.addView(soundCard,row(-2,16));
        TextView soundLabel=label("САУНДТРЕК ЭТОЙ СТРАНИЦЫ",10,0xffbfa8d2);soundLabel.setLetterSpacing(.10f);soundCard.addView(soundLabel);
        soundCard.addView(label("отвратительный король",15,0xfff3e9fc),row(-2,8));
        music=action("♫  Загрузка музыки…",false);soundCard.addView(music,row(48,12));music.setOnClickListener(v->{
            if(player!=null&&prepared&&player.isPlaying()){enabled=false;pauseMusic();abandonFocus();}
            else {enabled=true;if(player==null)prepareMusic();else if(prepared)startMusic();}
            getPreferences(0).edit().putBoolean("music",enabled).apply();updateMusic();
        });
        musicHint=label("Тихо · только пока открыта эта страница",11,0xffaf9cbd);soundCard.addView(musicHint,row(-2,10));
        TextView footer=label("MADE BY KAMRADIK  /  TILT",10,0xff9b81b3);footer.setLetterSpacing(.12f);content.addView(footer,row(-2,26));
    }
    private void prepareMusic(){
        if(!resumed||!enabled||player!=null)return;
        MediaPlayer next=new MediaPlayer();player=next;prepared=false;
        try(android.content.res.AssetFileDescriptor fd=getResources().openRawResourceFd(R.raw.author_music)){
            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            next.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());next.setLooping(true);next.setVolume(.12f,.12f);
            next.setOnPreparedListener(p->{if(player!=p||!resumed)return;prepared=true;if(position>0)p.seekTo(position);if(enabled)startMusic();else updateMusic();});
            next.setOnErrorListener((p,what,extra)->{if(player==p){releaseMusic();music.setText("↻  Повторить загрузку");musicHint.setText("Не удалось воспроизвести музыку");}return true;});next.prepareAsync();
        }catch(IOException|RuntimeException e){releaseMusic();music.setText("↻  Повторить загрузку");musicHint.setText("Не удалось открыть аудиофайл");}
    }
    private void startMusic(){if(!resumed||!enabled||!prepared||player==null)return;focused=audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;if(focused)player.start();updateMusic();}
    private void pauseMusic(){if(player!=null&&prepared&&player.isPlaying())player.pause();}
    private void abandonFocus(){audio.abandonAudioFocusRequest(focus);focused=false;}
    private void updateMusic(){if(music==null)return;boolean playing=player!=null&&prepared&&player.isPlaying();music.setText(playing?"Ⅱ  Пауза":player!=null&&!prepared?"♫  Загрузка музыки…":"♫  Включить музыку");musicHint.setText(playing?"Тихо · только пока открыта эта страница":enabled&&prepared&&!focused?"Звук занят другим приложением · нажми для повтора":"Музыка на паузе");}
    private void releaseMusic(){if(player!=null){if(prepared){try{position=player.getCurrentPosition();}catch(IllegalStateException ignored){}}player.release();player=null;}prepared=false;abandonFocus();}
    @Override protected void onResume(){super.onResume();resumed=true;backdrop.active=true;nickname.active=true;backdrop.invalidate();nickname.invalidate();if(ValueAnimator.areAnimatorsEnabled())floatAnimation.start();if(enabled)prepareMusic();else updateMusic();}
    @Override protected void onPause(){resumed=false;backdrop.active=false;nickname.active=false;floatAnimation.cancel();releaseMusic();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle b){b.putInt("position",player!=null&&prepared?player.getCurrentPosition():position);super.onSaveInstanceState(b);}
    @Override protected void onDestroy(){releaseMusic();super.onDestroy();}

    private final class PixelScene extends View {
        final Paint paint=new Paint();boolean active;
        PixelScene(){super(AuthorActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),t=SystemClock.uptimeMillis()/1000f;boolean animate=active&&ValueAnimator.areAnimatorsEnabled();
            paint.setAlpha(255);paint.setShader(new RadialGradient(w*.5f,h*.36f,Math.max(w,h)*.66f,new int[]{0xff342047,0xff100d1c},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,paint);paint.setShader(null);
            for(int i=0;i<40;i++){float x=((i*73+19)%997)/997f*w,y=(((i*137+71)%991)/991f*h+(animate?t*(5+i%4):0))%h;int size=dp(i%5==0?3:2);paint.setColor(i%3==0?0xffeaba8a:0xffc999ef);paint.setAlpha(animate?50+(int)(45*(1+Math.sin(t*.8+i))):95);c.drawRect(x,y,x+size,y+size,paint);}
            paint.setColor(0x096e5489);for(int x=0;x<w;x+=dp(32))c.drawRect(x,0,x+1,h,paint);for(int y=0;y<h;y+=dp(32))c.drawRect(0,y,w,y+1,paint);
            if(animate)postInvalidateDelayed(33);
        }
    }
    private final class PixelName extends View {
        // Original 5x7 block lettering, Minecraft-inspired. No external font dependency.
        final String[] glyphs={"10001100101010011000101001001010001","00000000000111000001011111000101111","00000000001101010101101011010110101","00000000001011011001100001000010000","00000000000111000001011111000101111","00001000010110110011100011000101111","00100000000110000100001000010001110","10000100001001010100110001010010010"};
        final Paint paint=new Paint();boolean active;
        PixelName(){super(AuthorActivity.this);}
        @Override protected void onDraw(Canvas c){
            float unit=Math.max(1,(float)Math.floor(Math.min(getWidth()/49f,dp(5))));float left=(getWidth()-47*unit)/2,top=(getHeight()-7*unit)/2,t=SystemClock.uptimeMillis()/1000f;boolean animate=active&&ValueAnimator.areAnimatorsEnabled();
            for(int i=0;i<glyphs.length;i++){float rise=animate?(float)Math.round(Math.sin(t*2.1-i*.65))*unit*.45f:0;
                for(int y=0;y<7;y++)for(int x=0;x<5;x++)if(glyphs[i].charAt(y*5+x)=='1'){
                    float px=left+(i*6+x)*unit,py=top+y*unit+rise;paint.setColor(0xff613b85);c.drawRect(px+unit*.6f,py+unit*.8f,px+unit*1.6f,py+unit*1.8f,paint);
                }
                for(int y=0;y<7;y++)for(int x=0;x<5;x++)if(glyphs[i].charAt(y*5+x)=='1'){
                    float px=left+(i*6+x)*unit,py=top+y*unit+rise;paint.setColor(y<3?0xffffe9b1:y<5?0xffe5bdfb:0xffbe8dea);c.drawRect(px,py,px+unit,py+unit,paint);
                }
            }
            if(animate)postInvalidateDelayed(33);
        }
    }
}
