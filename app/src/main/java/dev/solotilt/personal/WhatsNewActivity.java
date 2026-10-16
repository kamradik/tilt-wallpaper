package dev.solotilt.personal;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import static dev.solotilt.personal.Ios.*;

/**
 * "What's New", as iOS apps show it after an update: a large title, a list of features with coloured symbol tiles
 * and one Continue capsule. Shown once per NEWS revision to people who updated (new users get the onboarding,
 * which covers the same ground); MainActivity decides with due().
 */
public final class WhatsNewActivity extends Activity {
    /** Bump when the list below changes enough to show it again. */
    static final int NEWS=2;
    static final String KEY="news";
    static final int NETWORK=0,SKY=1,CINEMA=2,LAYERS=3,LOOKS=4,WIDGET=5,MOTION=6;

    /** Whether to show the list now: an existing user who has not seen this revision. New users only get it marked seen. */
    static boolean due(Context c){
        android.content.SharedPreferences p=ImageStore.prefs(c);
        if(p.getInt(KEY,0)>=NEWS)return false;
        if(!OnboardingActivity.seen(c)){markSeen(c);return false;}
        return true;
    }
    static void markSeen(Context c){ImageStore.prefs(c).edit().putInt(KEY,NEWS).apply();}

    WallpaperConfig config;
    String tr(String ru,String en){return config.en()?en:ru;}
    int dp(float n){return Ios.dp(this,n);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);config=WallpaperConfig.load(this);Ios.reduceMotion(config.reduceMotion);
        markSeen(this);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);scroll.addView(column,new ScrollView.LayoutParams(-1,-2));
        TextView title=text(this,tr("Что нового\nв Parallaxia","What’s New\nin Parallaxia"),34,BOLD,LABEL);title.setGravity(Gravity.CENTER);title.setPadding(0,dp(36),0,dp(30));column.addView(title);
        String[][] items={
            {tr("Живой интерфейс","A living interface"),tr("Цвет из твоих обоев, волна при смене эффекта, сканирование глубины и шапка, которая парит над фото при наклоне.","Colour from your wallpaper, a ripple when the effect changes, a depth scan and a header that floats over the photo as you tilt.")},
            {tr("Нейросеть глубины","Depth network"),tr("Объект отделяется от фона, а фон за ним дорисовывается. Всё считается на телефоне.","The subject separates from the background, and the background behind it is painted in. All on the phone.")},
            {tr("Живое небо","Living Sky"),tr("Небо на фото оживает: облака плывут, садится солнце, ночью горят звёзды.","The sky in your photo comes alive: clouds drift, the sun sets, stars come out at night.")},
            {tr("Синемаграф и живые фото","Cinemagraphs and motion photos"),tr("Закрась, что должно двигаться в видео, остальное замрёт. Живые фото Samsung становятся видеообоями.","Paint what should move in a video and the rest freezes. Samsung motion photos become video wallpapers.")},
            {tr("Два слоя и конструктор","Two layers and a constructor"),tr("Капли поверх глубины, плёнка поверх воды. Зерно, виньетка, блик и тон к любому эффекту.","Drops over depth, film over water. Grain, vignette, glare and tone for any effect.")},
            {tr("Образы","Looks"),tr("Десять готовых образов, свои сохранения и обмен образами по QR-коду.","Ten ready-made looks, your own saves and sharing looks as QR codes.")},
            {tr("Виджет и иконки","Widget and icons"),tr("Смена образа с главного экрана и четыре иконки приложения на выбор.","Switch looks from the home screen and pick one of four app icons.")},
        };
        int[] kinds={MOTION,NETWORK,SKY,CINEMA,LAYERS,LOOKS,WIDGET};
        for(int i=0;i<items.length;i++){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(32),dp(10),dp(32),dp(10));
            row.addView(new Tile(this,kinds[i]),new LinearLayout.LayoutParams(dp(48),dp(48)));
            LinearLayout texts=new LinearLayout(this);texts.setOrientation(LinearLayout.VERTICAL);texts.setPadding(dp(16),0,0,0);row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
            texts.addView(text(this,items[i][0],16,SEMIBOLD,LABEL));
            TextView body=text(this,items[i][1],15,REGULAR,SECONDARY);body.setLineSpacing(px(this,2),1);texts.addView(body);
            column.addView(row);if(state==null)rise(row,i+1);
        }
        View spacer=new View(this);column.addView(spacer,new LinearLayout.LayoutParams(-1,dp(130)));
        // Continue stays pinned over a fade at the bottom, like the iOS sheet.
        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,new int[]{0xff000000,0xf2000000,0x00000000}));
        root.addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        TextView next=capsule(this,tr("Продолжить","Continue"),true);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(54));np.topMargin=dp(36);bottom.addView(next,np);
        next.setOnClickListener(v->finish());
        root.setOnApplyWindowInsetsListener((v,i)->{Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            column.setPadding(bars.left,bars.top,bars.right,0);bottom.setPadding(dp(28)+bars.left,0,dp(28)+bars.right,bars.bottom+dp(20));return i;});
        if(state==null){rise(title,0);rise(next,items.length+1);}
    }
    @SuppressWarnings("deprecation") @Override public void finish(){super.finish();overridePendingTransition(R.anim.ios_pop_in,R.anim.ios_pop_out);}

    /** A rounded gradient tile with a simple white symbol for one feature. */
    static final class Tile extends View {
        final int kind;final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),ink=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF r=new RectF();final Path path=new Path();
        static final int[][] COLORS={{0xff5e5ce6,0xffbf5af2},{0xff0a84ff,0xff64d2ff},{0xffff9f0a,0xffff375f},{0xff30d158,0xff00c7be},{0xffff375f,0xffbf5af2},{0xff8e8e93,0xff48484a},{0xff64d2ff,0xff5e5ce6}};
        Tile(Context c,int kind){super(c);this.kind=kind;ink.setColor(Color.WHITE);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),s=Math.min(w,h),u=s/24f;
            fill.setShader(new LinearGradient(0,0,w,h,COLORS[kind][0],COLORS[kind][1],Shader.TileMode.CLAMP));
            r.set(0,0,w,h);c.drawPath(squircle(path,r,s*.24f),fill);fill.setShader(null);
            c.save();c.translate((w-s)/2,(h-s)/2);ink.setStrokeWidth(1.8f*u);
            switch(kind){
                case NETWORK->{ // three depth planes, front to back
                    ink.setStyle(Paint.Style.FILL);
                    for(int i=0;i<3;i++){ink.setAlpha(255-i*80);float o=i*3.2f*u;r.set(6*u+o,6*u+o,13*u+o,13*u+o);c.drawRoundRect(r,2*u,2*u,ink);}
                    ink.setAlpha(255);
                }
                case SKY->{ // a sun behind a cloud
                    ink.setStyle(Paint.Style.FILL);ink.setAlpha(200);c.drawCircle(15*u,9*u,4*u,ink);ink.setAlpha(255);
                    c.drawCircle(9*u,14*u,3.6f*u,ink);c.drawCircle(13.5f*u,12.6f*u,4.4f*u,ink);c.drawCircle(17.4f*u,15f*u,3f*u,ink);r.set(5.4f*u,14f*u,20.4f*u,18f*u);c.drawRoundRect(r,2*u,2*u,ink);
                }
                case CINEMA->{ // a frame with a play triangle and a brush stroke
                    ink.setStyle(Paint.Style.STROKE);r.set(5*u,6*u,19*u,18*u);c.drawRoundRect(r,2.5f*u,2.5f*u,ink);
                    ink.setStyle(Paint.Style.FILL);path.reset();path.moveTo(10.5f*u,9.5f*u);path.lineTo(15f*u,12f*u);path.lineTo(10.5f*u,14.5f*u);path.close();c.drawPath(path,ink);
                }
                case LAYERS->{ // two stacked sheets
                    ink.setStyle(Paint.Style.FILL);ink.setAlpha(150);path.reset();path.moveTo(12*u,10*u);path.lineTo(20*u,14*u);path.lineTo(12*u,18*u);path.lineTo(4*u,14*u);path.close();c.drawPath(path,ink);
                    ink.setAlpha(255);path.reset();path.moveTo(12*u,6*u);path.lineTo(20*u,10*u);path.lineTo(12*u,14*u);path.lineTo(4*u,10*u);path.close();c.drawPath(path,ink);
                }
                case LOOKS->{ // a QR-like grid
                    ink.setStyle(Paint.Style.FILL);
                    for(int y=0;y<3;y++)for(int x=0;x<3;x++){if((x+y)%2==1&&!(x==1&&y==1))continue;r.set((6+x*4.4f)*u,(6+y*4.4f)*u,(9.6f+x*4.4f)*u,(9.6f+y*4.4f)*u);c.drawRoundRect(r,.8f*u,.8f*u,ink);}
                }
                case MOTION->{ // a ripple: a dot and two rings spreading from it
                    ink.setStyle(Paint.Style.FILL);c.drawCircle(12*u,12*u,2.4f*u,ink);ink.setStyle(Paint.Style.STROKE);
                    ink.setAlpha(220);c.drawCircle(12*u,12*u,5.6f*u,ink);ink.setAlpha(120);c.drawCircle(12*u,12*u,8.8f*u,ink);ink.setAlpha(255);
                }
                default->{ // a 2x2 widget grid
                    ink.setStyle(Paint.Style.FILL);
                    for(int y=0;y<2;y++)for(int x=0;x<2;x++){r.set((5.5f+x*7)*u,(5.5f+y*7)*u,(11.5f+x*7)*u,(11.5f+y*7)*u);c.drawRoundRect(r,1.8f*u,1.8f*u,ink);}
                }
            }
            c.restore();
        }
    }
}
