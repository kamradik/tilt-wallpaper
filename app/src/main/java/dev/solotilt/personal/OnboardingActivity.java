package dev.solotilt.personal;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import static dev.solotilt.personal.Ios.*;

/**
 * First-launch introduction: four full-screen pages with a large title, a short text and a live illustration drawn
 * on a canvas, page dots and one prominent capsule. The third shows the on-device networks, the last explains Home vs Lock and offers to set the
 * wallpaper (RESULT_OK means "set it now"). Shown once; MainActivity reopens it on a long press on the title.
 */
public final class OnboardingActivity extends Activity {
    static final String SEEN="onboarded";
    static final int LAST=3;
    WallpaperConfig config;int page;Demo demo;TextView title,body;LinearLayout dots,texts;TextView next,later;float downX,downY;
    String tr(String ru,String en){return config.en()?en:ru;}
    int dp(float n){return Ios.dp(this,n);}
    /** Only new users see it. Someone updating from a version without onboarding has saved settings or an app that
     *  was updated rather than freshly installed: that is decided once and remembered. */
    static boolean seen(Context c){
        android.content.SharedPreferences p=ImageStore.prefs(c);
        if(p.contains(SEEN))return p.getBoolean(SEEN,false);
        boolean existing=p.contains(WallpaperConfig.KEY);
        try{android.content.pm.PackageInfo i=c.getPackageManager().getPackageInfo(c.getPackageName(),0);existing|=i.lastUpdateTime>i.firstInstallTime+60_000;}
        catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        p.edit().putBoolean(SEEN,existing).apply();return existing;
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);config=WallpaperConfig.load(this);Ios.reduceMotion(config.reduceMotion);
        if(state!=null)page=state.getInt("page");
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        demo=new Demo();root.addView(demo,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setClipChildren(false);root.addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        texts=new LinearLayout(this);texts.setOrientation(LinearLayout.VERTICAL);bottom.addView(texts);
        title=legible(text(this,"",34,BOLD,LABEL));texts.addView(title);
        body=legible(text(this,"",17,REGULAR,0xd9ffffff));body.setLineSpacing(px(this,3),1);body.setPadding(0,dp(10),0,0);texts.addView(body);
        dots=new LinearLayout(this);dots.setGravity(Gravity.CENTER);LinearLayout.LayoutParams dotsParams=new LinearLayout.LayoutParams(-1,dp(8));dotsParams.topMargin=dp(28);bottom.addView(dots,dotsParams);
        for(int i=0;i<=LAST;i++){View d=new View(this);d.setBackground(new Shape(LABEL,-1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(8),dp(8));lp.leftMargin=lp.rightMargin=dp(4);dots.addView(d,lp);}
        next=capsule(this,"",true);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(54));np.topMargin=dp(22);bottom.addView(next,np);
        next.setOnClickListener(v->{if(page<LAST)go(page+1);else finishWith(true);});
        later=text(this,"",17,REGULAR,BLUE);later.setGravity(Gravity.CENTER);later.setClickable(true);later.setOnClickListener(v->finishWith(false));
        bottom.addView(pressable(later),new LinearLayout.LayoutParams(-1,dp(48)));
        root.setOnApplyWindowInsetsListener((v,i)->{Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            bottom.setPadding(dp(28)+bars.left,0,dp(28)+bars.right,bars.bottom+dp(12));demo.top=bars.top;return i;});
        root.setOnTouchListener(this::swipe);
        show(false);
        if(state==null){for(int i=0;i<bottom.getChildCount();i++)rise(bottom.getChildAt(i),i+1);demo.setAlpha(0);demo.animate().alpha(1).setDuration(600).start();}
    }
    String[][] pages(){
        return new String[][]{
            {tr("Обои, которые\nчувствуют наклон","A wallpaper that\nfeels the tilt"),tr("Твоё фото оживает за стеклом: перспектива, свет и глубина следуют за телефоном.","Your photo comes alive behind glass: perspective, light and depth follow your phone.")},
            {tr("Эффекты на любой вкус","Effects for every mood"),tr("34 эффекта: вода, свет, объём, живое небо. Добавь второй слой, собери свой образ и поделись им по QR-коду.","34 effects: water, light, depth, a living sky. Add a second layer, build your own look and share it as a QR code.")},
            {tr("Нейросеть\nв телефоне","A neural network\non your phone"),tr("Она понимает глубину фото, отделяет объект и дорисовывает фон за ним. Видео и живые фото тоже подойдут, а синемаграф оставит движение только там, где нужно.","It reads your photo’s depth, separates the subject and paints the background behind it. Videos and motion photos work too, and a cinemagraph keeps motion only where you want it.")},
            {tr("Главный экран\nи блокировка","Home Screen\nand Lock Screen"),tr("Можно поставить одно и то же на оба экрана или отдельные обои на блокировку. При установке выбери «Главный экран и экран блокировки».","Use one look on both screens, or give the lock screen its own. When setting, choose “Home and lock screens”.")},
        };
    }
    void go(int target){if(target<0||target>LAST||target==page)return;int dir=target>page?1:-1;page=target;if(config.haptics)next.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        texts.animate().cancel();texts.animate().alpha(0).translationX(-dir*dp(40)).setDuration(still()?0:140).withEndAction(()->{show(true);texts.setTranslationX(still()?0:dir*dp(40));spring(texts,SMOOTH).alpha(1).translationX(0);}).start();
        demo.goTo(page);
    }
    void show(boolean animate){
        String[] p=pages()[page];title.setText(p[0]);body.setText(p[1]);
        boolean last=page==LAST;String label=last?tr("Установить обои","Set wallpaper"):tr("Продолжить","Continue");if(animate)swap(next,label);else next.setText(label);
        later.setText(last?tr("Позже","Later"):tr("Пропустить","Skip"));
        for(int i=0;i<=LAST;i++){View d=dots.getChildAt(i);LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)d.getLayoutParams();lp.width=dp(i==page?22:8);d.setLayoutParams(lp);d.setAlpha(i==page?1:.35f);}
        if(animate)demo.goTo(page);else{demo.target=demo.position=page;demo.invalidate();}
    }
    boolean swipe(View v,MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN->{downX=e.getX();downY=e.getY();}
            case MotionEvent.ACTION_UP->{float dx=e.getX()-downX;if(Math.abs(dx)>dp(60)&&Math.abs(dx)>Math.abs(e.getY()-downY))go(page+(dx<0?1:-1));else v.performClick();}
            default->{}
        }
        return true;
    }
    void finishWith(boolean install){ImageStore.prefs(this).edit().putBoolean(SEEN,true).apply();WhatsNewActivity.markSeen(this); /* the tour covers the news */setResult(install?RESULT_OK:RESULT_CANCELED);finish();}
    @SuppressWarnings("deprecation") @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){if(page>0)go(page-1);else finishWith(false);}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("page",page);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){demo.release();super.onDestroy();}

    /** The live illustration: phones with the sample wallpapers, tilting on their own. Page 0 shows one phone tilting
     *  with a moving glare, page 1 a holographic sweep and ripples, page 2 a depth scan sweeping down the photo,
     *  page 3 a home screen next to a lock screen. */
    final class Demo extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),frame=new Paint(Paint.ANTI_ALIAS_FLAG),glare=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Camera camera=new Camera();final Matrix matrix=new Matrix();final RectF box=new RectF();final Path clip=new Path();
        final Bitmap[] images={ImageStore.preset(1,.5f),ImageStore.preset(0,.5f),ImageStore.preset(2,.5f)};
        final long start=SystemClock.uptimeMillis();float position,target,fadeHeight;int top;Shader fade;android.animation.ValueAnimator move;
        Demo(){super(OnboardingActivity.this);frame.setStyle(Paint.Style.STROKE);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void goTo(int p){if(p==target)return;target=p;move=Ios.animate(move,position,p,SMOOTH,a->{position=(float)a.getAnimatedValue();invalidate();});}
        void release(){if(move!=null)move.cancel();for(Bitmap b:images)b.recycle();}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),t=still()?0:(SystemClock.uptimeMillis()-start)/1000f,d=px(getContext(),1);
            // Ambient backdrop: the current page's picture, very dim.
            paint.setAlpha(60);drawCover(c,images[Math.round(Math.max(0,Math.min(2,position)))],0,0,w,h);paint.setAlpha(255);
            float area=h*.62f,cx=w/2,cy=top+area*.52f;
            float tiltX=(float)Math.sin(t*.9)*16,tiltY=(float)Math.sin(t*.63+1)*11;
            float single=1-Math.min(1,Math.abs(position-LAST)),one=1-single;  // 0..1 toward the two-phone page
            if(one>.01f){
                float ph=area*.86f,pw=ph*.48f;
                phone(c,cx,cy,pw,ph,tiltX,tiltY,images[Math.round(Math.max(0,Math.min(2,position)))],one,Math.max(0,1-Math.abs(position-1)),Math.max(0,1-Math.abs(position-2)),t,d);
            }
            if(single>.01f){
                float ph=area*.7f,pw=ph*.48f,gap=pw*.62f;
                phone(c,cx-gap,cy,pw,ph,tiltX*.7f,tiltY*.7f,images[0],single,0,0,t,d);
                phone(c,cx+gap,cy,pw,ph,-tiltX*.7f,tiltY*.7f,images[2],single,0,0,t,d);
                // Labels under the phones.
                paint.setColor(LABEL);paint.setAlpha(Math.round(200*single));paint.setTextSize(px(getContext(),13));paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(font(SEMIBOLD));
                c.drawText(tr("Главный","Home"),cx-gap,cy+ph/2+22*d,paint);c.drawText(tr("Блокировка","Lock"),cx+gap,cy+ph/2+22*d,paint);paint.setAlpha(255);
                // Home grid on the left, a clock on the right.
                overlayHome(c,cx-gap,cy,pw,ph,single);overlayLock(c,cx+gap,cy,pw,ph,single);
            }
            // The text sits on a soft fade so it stays readable.
            if(fade==null||fadeHeight!=h){fade=new LinearGradient(0,h*.5f,0,h,0x00000000,0xf0000000,Shader.TileMode.CLAMP);fadeHeight=h;}paint.setShader(fade);c.drawRect(0,h*.5f,w,h,paint);paint.setShader(null);
            if(!still())postInvalidateOnAnimation();
        }
        void phone(Canvas c,float cx,float cy,float pw,float ph,float tx,float ty,Bitmap image,float alpha,float holo,float scan,float t,float d){
            c.save();camera.save();camera.rotateY(tx);camera.rotateX(-ty);camera.getMatrix(matrix);camera.restore();
            matrix.preTranslate(-cx,-cy);matrix.postTranslate(cx,cy);c.concat(matrix);
            box.set(cx-pw/2,cy-ph/2,cx+pw/2,cy+ph/2);float r=pw*.16f;clip.reset();clip.addRoundRect(box,r,r,Path.Direction.CW);
            c.save();c.clipPath(clip);paint.setAlpha(Math.round(255*alpha));
            // Parallax: the picture slides against the tilt, as the wallpaper does.
            drawCover(c,image,box.left-tx*d*1.4f-pw*.08f,box.top+ty*d*1.4f-ph*.08f,pw*1.16f,ph*1.16f);
            if(holo>.01f){
                float sweep=(t*.35f)%1;glare.setShader(new LinearGradient(box.left-pw+sweep*pw*3,box.top,box.left+sweep*pw*3,box.bottom,new int[]{0x00ff3b8b,0x66ff3b8b,0x6642e8ff,0x667dff7a,0x00ffffff},null,Shader.TileMode.CLAMP));
                glare.setAlpha(Math.round(255*holo*alpha));glare.setBlendMode(BlendMode.SCREEN);c.drawRect(box,glare);glare.setBlendMode(null);
                glare.setShader(null);glare.setStyle(Paint.Style.STROKE);glare.setStrokeWidth(1.5f*d);
                for(int i=0;i<3;i++){float k=((t*.5f)+i/3f)%1;glare.setColor(Color.WHITE);glare.setAlpha(Math.round(150*(1-k)*holo*alpha));c.drawCircle(cx,cy+ph*.12f,k*pw*.7f,glare);}
                glare.setStyle(Paint.Style.FILL);
            }
            if(scan>.01f){
                // Depth scan: a bright line sweeps down; above it the photo shows as a depth map (near light, far dark).
                float k=(t*.32f)%1.2f,y=box.top+Math.min(1,k)*ph;
                glare.setShader(new LinearGradient(0,box.top,0,box.bottom,new int[]{0xd9000000,0x59000000,0x00000000},new float[]{0,.45f,1},Shader.TileMode.CLAMP));
                glare.setAlpha(Math.round(255*scan*alpha));c.save();c.clipRect(box.left,box.top,box.right,y);c.drawRect(box,glare);
                glare.setShader(null);glare.setColor(0x3364d2ff);glare.setAlpha(Math.round(80*scan*alpha));glare.setStyle(Paint.Style.STROKE);glare.setStrokeWidth(d);
                for(float gy=box.top+ph/14;gy<y;gy+=ph/14)c.drawLine(box.left,gy,box.right,gy,glare);
                for(float gx=box.left+pw/7;gx<box.right;gx+=pw/7)c.drawLine(gx,box.top,gx,y,glare);
                glare.setStyle(Paint.Style.FILL);c.restore();
                if(k<1){glare.setShader(new LinearGradient(0,y-ph*.08f,0,y,0x0064d2ff,0xcc64d2ff,Shader.TileMode.CLAMP));glare.setAlpha(Math.round(255*scan*alpha));c.drawRect(box.left,y-ph*.08f,box.right,y,glare);glare.setShader(null);
                    glare.setColor(Color.WHITE);glare.setAlpha(Math.round(230*scan*alpha));c.drawRect(box.left,y-1.2f*d,box.right,y+1.2f*d,glare);}
            }
            // Moving glare follows the tilt.
            float gx=cx+tx/16f*pw*.6f;glare.setShader(new RadialGradient(gx,box.top+ph*.25f-ty/11f*ph*.2f,pw*.9f,0x40ffffff,0x00ffffff,Shader.TileMode.CLAMP));glare.setAlpha(Math.round(255*alpha));c.drawRect(box,glare);glare.setShader(null);
            c.restore();
            frame.setStrokeWidth(5*d);frame.setColor(0xff2c2c2e);frame.setAlpha(Math.round(255*alpha));c.drawRoundRect(box,r,r,frame);
            frame.setStrokeWidth(1*d);frame.setColor(0x66ffffff);frame.setAlpha(Math.round(102*alpha));c.drawRoundRect(box,r,r,frame);
            c.restore();
        }
        void overlayHome(Canvas c,float cx,float cy,float pw,float ph,float a){
            paint.setColor(Color.WHITE);float s=pw*.15f,g=pw*.07f,left=cx-(4*s+3*g)/2;
            for(int row=0;row<3;row++)for(int col=0;col<4;col++){paint.setAlpha(Math.round(110*a));float x=left+col*(s+g),y=cy-ph*.32f+row*(s+g*1.4f);c.drawRoundRect(x,y,x+s,y+s,s*.28f,s*.28f,paint);}
            paint.setAlpha(255);
        }
        void overlayLock(Canvas c,float cx,float cy,float pw,float ph,float a){
            paint.setColor(Color.WHITE);paint.setAlpha(Math.round(235*a));paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(font(BOLD));paint.setTextSize(pw*.3f);
            c.drawText("9:41",cx,cy-ph*.2f,paint);paint.setAlpha(255);
        }
        void drawCover(Canvas c,Bitmap b,float x,float y,float w,float h){
            if(b==null||b.isRecycled())return;float s=Math.max(w/b.getWidth(),h/b.getHeight());matrix.setScale(s,s);matrix.postTranslate(x+(w-b.getWidth()*s)/2,y+(h-b.getHeight()*s)/2);c.drawBitmap(b,matrix,paint);
        }
    }
}
