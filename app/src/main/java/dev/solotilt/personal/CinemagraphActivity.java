package dev.solotilt.personal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static dev.solotilt.personal.Ios.*;

/**
 * Cinemagraph editor: paint with a finger over the slot's video where it should keep moving (water, hair, smoke,
 * clouds); everything else holds the first frame. The preview is live: the clip plays under a copy of its poster with
 * the painted strokes cut out of it, which is exactly what the wallpaper's blit does (video_blit.glsl). Strokes are
 * soft-edged, so the moving part blends into the still one. Done saves the mask with ImageStore.setCine (an empty
 * mask removes it); RESULT_OK means the slot's cinemagraph changed.
 */
public final class CinemagraphActivity extends Activity implements TextureView.SurfaceTextureListener {
    static final String EXTRA_SLOT="slot";
    /** Longest side of the mask: it is sampled smoothly and its edges are soft, so more pixels show nothing. */
    static final int MASK_SIDE=720;
    static final int YELLOW=0xffffd60a;

    static Intent intent(Context c,String slot){return new Intent(c,CinemagraphActivity.class).putExtra(EXTRA_SLOT,WallpaperConfig.LOCK.equals(slot)?WallpaperConfig.LOCK:WallpaperConfig.HOME);}

    WallpaperConfig config;String slot;
    final Handler main=new Handler(Looper.getMainLooper());final ExecutorService io=Executors.newSingleThreadExecutor();
    FrameLayout stage;TextureView video;Painter painter;Surface surface;MediaPlayer player;TextView hint,done,cancel,brush,eraser,undo,clear;
    Bitmap poster,mask,before;boolean erase,busy,resumed,changed;volatile boolean closed;
    String tr(String ru,String en){return config.en()?en:ru;}
    int dp(float n){return Ios.dp(this,n);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);config=WallpaperConfig.load(this);Ios.reduceMotion(config.reduceMotion);
        slot=WallpaperConfig.LOCK.equals(getIntent().getStringExtra(EXTRA_SLOT))?WallpaperConfig.LOCK:WallpaperConfig.HOME;
        if(!ImageStore.isVideo(this,slot)){setResult(RESULT_CANCELED);finish();return;}

        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);root.addView(column,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setGravity(Gravity.CENTER_HORIZONTAL);column.addView(head,new LinearLayout.LayoutParams(-1,-2));
        head.addView(text(this,tr("Синемаграф","Cinemagraph"),17,SEMIBOLD,LABEL));
        head.addView(text(this,WallpaperConfig.LOCK.equals(slot)?tr("Экран блокировки","Lock Screen"):tr("Главный экран","Home Screen"),13,REGULAR,SECONDARY));

        stage=new FrameLayout(this);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(12);sp.bottomMargin=dp(8);column.addView(stage,sp);
        video=new TextureView(this);video.setSurfaceTextureListener(this);video.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        stage.addView(video,new FrameLayout.LayoutParams(1,1,Gravity.CENTER));
        painter=new Painter(this);painter.setContentDescription(tr("Закрась, что должно двигаться","Paint what should move"));
        stage.addView(painter,new FrameLayout.LayoutParams(1,1,Gravity.CENTER));
        stage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->fit());

        hint=text(this,tr("Закрась пальцем то, что должно двигаться: воду, волосы, облака. Остальное замрёт.","Paint what should move: water, hair, clouds. Everything else freezes."),13,REGULAR,SECONDARY);
        hint.setGravity(Gravity.CENTER);hint.setPadding(dp(24),dp(4),dp(24),0);column.addView(hint,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout tools=new LinearLayout(this);tools.setGravity(Gravity.CENTER);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(40));tp.topMargin=dp(14);column.addView(tools,tp);
        brush=chip(this,tr("Кисть","Brush"));eraser=chip(this,tr("Ластик","Eraser"));undo=chip(this,tr("Отменить","Undo"));clear=chip(this,tr("Очистить","Clear"));
        for(TextView t:new TextView[]{brush,eraser,undo,clear}){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-1);lp.leftMargin=lp.rightMargin=dp(4);tools.addView(t,lp);}
        brush.setOnClickListener(v->tool(false));eraser.setOnClickListener(v->tool(true));
        undo.setOnClickListener(v->{if(before==null||mask==null)return;new Canvas(mask).drawBitmap(before,0,0,new Paint(Paint.FILTER_BITMAP_FLAG){{setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));}});before.recycle();before=null;changed=true;tools();painter.invalidate();});
        clear.setOnClickListener(v->{if(mask==null)return;remember();mask.eraseColor(Color.TRANSPARENT);changed=true;tools();painter.invalidate();if(config.haptics)v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);});

        LinearLayout actions=new LinearLayout(this);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(50));ap.topMargin=dp(16);ap.leftMargin=ap.rightMargin=dp(16);column.addView(actions,ap);
        cancel=capsule(this,tr("Отмена","Cancel"),false);actions.addView(cancel,new LinearLayout.LayoutParams(0,-1,1));cancel.setOnClickListener(v->{if(!busy){setResult(RESULT_CANCELED);finish();}});
        done=capsule(this,tr("Готово","Done"),true);LinearLayout.LayoutParams dpm=new LinearLayout.LayoutParams(0,-1,1);dpm.leftMargin=dp(10);actions.addView(done,dpm);done.setOnClickListener(v->save());
        done.setEnabled(false);done.setAlpha(.45f);

        root.setOnApplyWindowInsetsListener((v,i)->{Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            column.setPadding(bars.left,bars.top+dp(10),bars.right,bars.bottom+dp(16));return i;});
        if(state==null)for(int i=0;i<column.getChildCount();i++)rise(column.getChildAt(i),i);
        tool(false);
        final Context app=getApplicationContext();
        io.execute(()->{
            Bitmap still=null,saved=null,m=null;
            try{still=ImageStore.load(app,slot);saved=ImageStore.cine(app,slot);
                float s=Math.min(1,MASK_SIDE/(float)Math.max(still.getWidth(),still.getHeight()));
                m=Bitmap.createBitmap(Math.max(1,Math.round(still.getWidth()*s)),Math.max(1,Math.round(still.getHeight()*s)),Bitmap.Config.ARGB_8888);
                if(saved!=null)new Canvas(m).drawBitmap(saved,null,new Rect(0,0,m.getWidth(),m.getHeight()),new Paint(Paint.FILTER_BITMAP_FLAG));
            }catch(Exception|OutOfMemoryError e){Log.w("SoloVideo","Cinemagraph editor cannot load the clip",e);}
            finally{if(saved!=null)saved.recycle();}
            final Bitmap p=still,k=m;
            main.post(()->{
                if(closed){if(p!=null)p.recycle();if(k!=null)k.recycle();return;}
                if(p==null||k==null){problem(tr("Не удалось открыть видео.","Could not open the video."));return;}
                poster=p;mask=k;fit();tools();done.setEnabled(true);spring(done,SMOOTH).alpha(1);painter.invalidate();
            });
        });
    }
    void tool(boolean eraseMode){erase=eraseMode;chipSelected(brush,!erase);chipSelected(eraser,erase);}
    void tools(){boolean has=mask!=null;undo.setEnabled(before!=null);undo.setAlpha(before!=null?1:.4f);clear.setEnabled(has);}
    void remember(){if(mask==null)return;if(before!=null)before.recycle();before=mask.copy(Bitmap.Config.ARGB_8888,true);}
    void problem(String message){hint.setTextColor(0xffff453a);swap(hint,message);}

    /** Aspect-fits the preview (video and painter, which cover each other exactly) into the stage. */
    void fit(){
        int sw=stage.getWidth(),sh=stage.getHeight();if(sw==0||sh==0||poster==null)return;
        float s=Math.min(sw/(float)poster.getWidth(),sh/(float)poster.getHeight());int w=Math.max(1,Math.round(poster.getWidth()*s)),h=Math.max(1,Math.round(poster.getHeight()*s));
        for(View v:new View[]{video,painter}){ViewGroup.LayoutParams lp=v.getLayoutParams();if(lp.width==w&&lp.height==h)continue;lp.width=w;lp.height=h;v.setLayoutParams(lp);}
    }

    // ---------------------------------------------------------------- preview
    @Override public void onSurfaceTextureAvailable(SurfaceTexture st,int w,int h){
        surface=new Surface(st);player=new MediaPlayer();
        try{
            player.setOnPreparedListener(mp->{if(resumed)mp.start();});
            player.setOnErrorListener((mp,what,extra)->{Log.w("SoloVideo","Cinemagraph preview error "+what+"/"+extra);releasePlayer();return true;});
            player.setDataSource(ImageStore.videoFile(this,slot).getPath());player.setSurface(surface);player.setVolume(0,0);player.setLooping(true);player.prepareAsync();
        }catch(Exception e){Log.w("SoloVideo","Cinemagraph preview failed",e);releasePlayer();}
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st,int w,int h){}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st){releasePlayer();return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st){}
    void releasePlayer(){
        if(player!=null){try{player.release();}catch(RuntimeException ignored){}player=null;}
        if(surface!=null){surface.release();surface=null;}
    }

    // ---------------------------------------------------------------- save
    /** Whether any part of the mask is painted (a few percent of alpha counts as nothing). Pure, unit-tested. */
    static boolean painted(int[] pixels){for(int p:pixels)if((p>>>24)>10)return true;return false;}
    void save(){
        if(busy||mask==null)return;
        if(!changed){setResult(RESULT_CANCELED);finish();return;}
        busy=true;done.performHapticFeedback(HapticFeedbackConstants.CONFIRM);swap(done,tr("Сохранение…","Saving…"));done.setEnabled(false);cancel.setEnabled(false);cancel.setAlpha(.45f);
        final Bitmap copy=mask.copy(Bitmap.Config.ARGB_8888,false);final Context app=getApplicationContext();final String target=slot;
        io.execute(()->{
            boolean ok=true;
            try{int[] px=new int[copy.getWidth()*copy.getHeight()];copy.getPixels(px,0,copy.getWidth(),0,0,copy.getWidth(),copy.getHeight());
                ImageStore.setCine(app,target,painted(px)?copy:null);}
            catch(Exception|OutOfMemoryError e){Log.w("SoloVideo","Cinemagraph save failed",e);ok=false;}
            finally{copy.recycle();}
            final boolean success=ok;
            main.post(()->{
                if(success){setResult(RESULT_OK);finish();return;}
                if(closed)return;busy=false;done.setEnabled(true);cancel.setEnabled(true);cancel.setAlpha(1);swap(done,tr("Готово","Done"));
                problem(tr("Не удалось сохранить синемаграф.","Could not save the cinemagraph."));
            });
        });
    }

    @Override protected void onResume(){super.onResume();resumed=true;if(player!=null)try{player.start();}catch(IllegalStateException ignored){}}
    @Override protected void onPause(){resumed=false;if(player!=null)try{player.pause();}catch(IllegalStateException ignored){}super.onPause();}
    @Override protected void onDestroy(){
        closed=true;main.removeCallbacksAndMessages(null);releasePlayer();io.shutdown(); // a running save still finishes
        for(Bitmap b:new Bitmap[]{poster,mask,before})if(b!=null)b.recycle();super.onDestroy();
    }
    @SuppressWarnings("deprecation") @Override public void finish(){super.finish();overridePendingTransition(R.anim.ios_pop_in,R.anim.ios_pop_out);}

    // ---------------------------------------------------------------- painting
    /**
     * The poster with the painted mask cut out of it (the video plays underneath), plus a yellow tint of the painted
     * area while the finger is down and for a moment after, so the user sees what is selected.
     */
    final class Painter extends View {
        final Paint image=new Paint(Paint.FILTER_BITMAP_FLAG),cut=new Paint(Paint.FILTER_BITMAP_FLAG),tint=new Paint(Paint.FILTER_BITMAP_FLAG),stroke=new Paint(Paint.ANTI_ALIAS_FLAG),ring=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path=new Path();final RectF box=new RectF();float lastX,lastY,touchX=-1,touchY;float shown;android.animation.ValueAnimator fade;
        Painter(Context c){super(c);
            cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            tint.setColorFilter(new PorterDuffColorFilter(YELLOW,PorterDuff.Mode.SRC_IN));
            stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeCap(Paint.Cap.ROUND);stroke.setStrokeJoin(Paint.Join.ROUND);stroke.setColor(Color.WHITE);
            ring.setStyle(Paint.Style.STROKE);ring.setStrokeWidth(px(c,1.5f));ring.setColor(0xccffffff);
        }
        /** Brush diameter in mask pixels: about a fingertip on screen. */
        float brushSize(){return mask==null||getWidth()==0?1:px(getContext(),34)*mask.getWidth()/(float)getWidth();}
        @Override protected void onDraw(Canvas c){
            if(poster==null||mask==null)return;box.set(0,0,getWidth(),getHeight());
            int layer=c.saveLayer(box,null);c.drawBitmap(poster,null,box,image);c.drawBitmap(mask,null,box,cut);c.restoreToCount(layer);
            if(shown>0){tint.setAlpha(Math.round(110*shown));c.drawBitmap(mask,null,box,tint);}
            if(touchX>=0){float r=brushSize()/2*getWidth()/mask.getWidth();c.drawCircle(touchX,touchY,r,ring);}
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(mask==null||busy)return false;float sx=mask.getWidth()/(float)Math.max(1,getWidth()),x=e.getX()*sx,y=e.getY()*sx;
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN->{getParent().requestDisallowInterceptTouchEvent(true);remember();lastX=x;lastY=y;dab(x,y,x,y);show(1);}
                case MotionEvent.ACTION_MOVE->{for(int h=0;h<e.getHistorySize();h++){float hx=e.getHistoricalX(h)*sx,hy=e.getHistoricalY(h)*sx;dab(lastX,lastY,hx,hy);lastX=hx;lastY=hy;}dab(lastX,lastY,x,y);lastX=x;lastY=y;}
                case MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{touchX=-1;changed=true;tools();show(0);if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();invalidate();return true;}
                default->{return true;}
            }
            touchX=e.getX();touchY=e.getY();invalidate();return true;
        }
        /** One stroke segment with a soft edge; the eraser cuts the same soft shape out. */
        void dab(float x0,float y0,float x1,float y1){
            float size=brushSize();stroke.setStrokeWidth(size*.7f);stroke.setMaskFilter(new BlurMaskFilter(Math.max(1,size*.18f),BlurMaskFilter.Blur.NORMAL));
            stroke.setXfermode(erase?new PorterDuffXfermode(PorterDuff.Mode.DST_OUT):null);
            path.reset();path.moveTo(x0,y0);path.lineTo(x1+.01f,y1);new Canvas(mask).drawPath(path,stroke);
        }
        /** Fades the selection tint in at once while painting, out over a second after. */
        void show(float to){
            if(fade!=null)fade.cancel();
            if(to>0||still()){shown=to;invalidate();return;}
            fade=android.animation.ValueAnimator.ofFloat(shown,0);fade.setStartDelay(500);fade.setDuration(600);fade.addUpdateListener(a->{shown=(float)a.getAnimatedValue();invalidate();});fade.start();
        }
        @Override public boolean performClick(){return super.performClick();}
    }
}
