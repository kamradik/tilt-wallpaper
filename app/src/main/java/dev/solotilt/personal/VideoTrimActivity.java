package dev.solotilt.personal;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static dev.solotilt.personal.Ios.*;

/**
 * Trims a picked clip before it becomes a slot's video, like the trim bar of iOS Photos: the clip loops (muted) between
 * two handles on a filmstrip, the yellow frame shows what is kept (up to 3 minutes), and Done imports that window with
 * ImageStore.importVideo off the UI thread. RESULT_OK means the slot's video was replaced and its revision bumped.
 */
public final class VideoTrimActivity extends Activity implements TextureView.SurfaceTextureListener {
    static final String EXTRA_URI="uri",EXTRA_SLOT="slot";
    static final long MIN_MS=1000,MAX_MS=ImageStore.MAX_VIDEO_MS;
    static final int THUMBS=8,YELLOW=0xffffd60a;

    /** Opens the trimmer for a picked clip; the read grant on uri is passed along. Start it for a result. */
    public static Intent intent(Context c,Uri uri,String slot){
        Intent i=new Intent(c,VideoTrimActivity.class).putExtra(EXTRA_URI,uri.toString()).putExtra(EXTRA_SLOT,WallpaperConfig.LOCK.equals(slot)?WallpaperConfig.LOCK:WallpaperConfig.HOME);
        i.setClipData(ClipData.newRawUri("video",uri));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);return i;
    }

    WallpaperConfig config;Uri uri;String slot;
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService thumbnails=Executors.newSingleThreadExecutor(),io=Executors.newSingleThreadExecutor();
    FrameLayout stage;TextureView video;Surface surface;MediaPlayer player;Strip strip;TextView length,hint,done,cancel;
    long duration,start,end,seekWanted=-1;int videoW,videoH;
    boolean prepared,seekPending,resumed,busy;volatile boolean closed;
    String tr(String ru,String en){return config.en()?en:ru;}
    int dp(float n){return Ios.dp(this,n);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);config=WallpaperConfig.load(this);Ios.reduceMotion(config.reduceMotion);
        String value=getIntent().getStringExtra(EXTRA_URI);uri=value==null?null:Uri.parse(value);
        slot=WallpaperConfig.LOCK.equals(getIntent().getStringExtra(EXTRA_SLOT))?WallpaperConfig.LOCK:WallpaperConfig.HOME;
        if(uri==null){setResult(RESULT_CANCELED);finish();return;}

        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);root.addView(column,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setGravity(Gravity.CENTER_HORIZONTAL);column.addView(head,new LinearLayout.LayoutParams(-1,-2));
        head.addView(text(this,tr("Обрезка видео","Trim Video"),17,SEMIBOLD,LABEL));
        head.addView(text(this,WallpaperConfig.LOCK.equals(slot)?tr("Экран блокировки","Lock Screen"):tr("Главный экран","Home Screen"),13,REGULAR,SECONDARY));

        stage=new FrameLayout(this);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(12);sp.bottomMargin=dp(12);column.addView(stage,sp);
        video=new TextureView(this);video.setSurfaceTextureListener(this);video.setContentDescription(tr("Предпросмотр видео","Video preview"));
        stage.addView(video,new FrameLayout.LayoutParams(1,1,Gravity.CENTER));
        stage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->fit());

        length=text(this,"",15,SEMIBOLD,LABEL);length.setFontFeatureSettings("tnum");length.setGravity(Gravity.CENTER);column.addView(length,new LinearLayout.LayoutParams(-1,-2));
        strip=new Strip(this);strip.setHapticFeedbackEnabled(config.haptics);strip.setContentDescription(tr("Обрезка: перетащи края","Trim: drag the edges"));
        LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-1,dp(56));stp.topMargin=dp(10);stp.leftMargin=stp.rightMargin=dp(16);column.addView(strip,stp);
        hint=text(this,tr("Потяни за края, чтобы обрезать: до 3 минут. Звук не сохраняется.","Drag the edges to trim, up to 3 minutes. Sound is not kept."),13,REGULAR,SECONDARY);
        hint.setGravity(Gravity.CENTER);hint.setPadding(dp(24),dp(10),dp(24),0);column.addView(hint,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout actions=new LinearLayout(this);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(50));ap.topMargin=dp(18);ap.leftMargin=ap.rightMargin=dp(16);column.addView(actions,ap);
        cancel=capsule(this,tr("Отмена","Cancel"),false);actions.addView(cancel,new LinearLayout.LayoutParams(0,-1,1));cancel.setOnClickListener(v->{if(!busy){setResult(RESULT_CANCELED);finish();}});
        done=capsule(this,tr("Готово","Done"),true);LinearLayout.LayoutParams dpm=new LinearLayout.LayoutParams(0,-1,1);dpm.leftMargin=dp(10);actions.addView(done,dpm);done.setOnClickListener(v->save());
        done.setEnabled(false);done.setAlpha(.45f);

        root.setOnApplyWindowInsetsListener((v,i)->{Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            column.setPadding(bars.left,bars.top+dp(10),bars.right,bars.bottom+dp(16));return i;});
        if(state==null)for(int i=0;i<column.getChildCount();i++)rise(column.getChildAt(i),i);
        strip.listener=new Strip.Listener(){
            public void moved(int handle,long s,long e,long at){
                start=s;end=e;showLength();if(player==null||!prepared)return;
                if(player.isPlaying())player.pause();seek(handle==Strip.END?Math.max(s,e-40):at); // the end handle shows the last kept frame
            }
            public void released(int handle){if(player!=null&&prepared){strip.head=handle==Strip.HEAD?strip.head:start;if(handle!=Strip.HEAD)seek(start);if(resumed)player.start();}}
        };
        thumbnails.execute(this::loadThumbnails);
    }

    // ---------------------------------------------------------------- metadata and filmstrip
    void loadThumbnails(){
        long ms=0;boolean hasVideo=false;
        try(MediaMetadataRetriever r=new MediaMetadataRetriever()){
            r.setDataSource(this,uri);
            hasVideo="yes".equals(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));ms=ImageStore.number(r,MediaMetadataRetriever.METADATA_KEY_DURATION);
            final long known=ms;final boolean ok=hasVideo;main.post(()->{if(!closed)metadata(known,ok);});
            if(!hasVideo||ms<=0)return;
            int side=dp(72);
            for(int i=0;i<THUMBS&&!closed;i++){
                final int index=i;long at=Math.round((i+.5)*ms*1000.0/THUMBS);Bitmap b;
                try{b=r.getScaledFrameAtTime(at,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,side,side);}catch(RuntimeException e){b=null;}
                if(b==null)continue;final Bitmap frame=b;
                main.post(()->{if(closed){frame.recycle();return;}strip.thumb(index,frame);});
            }
        }catch(Exception e){
            Log.w("SoloVideo","Cannot read the clip",e);final long known=ms;final boolean ok=hasVideo;
            main.post(()->{if(!closed)metadata(known,ok);});
        }
    }
    /** UI thread: the clip's length; the window starts as the whole clip, capped at 3 minutes. */
    void metadata(long ms,boolean hasVideo){
        if(!hasVideo){problem(tr("В файле нет видео.","The file has no video track."));return;}
        if(ms<=0||duration>0)return; // unknown here: the player's duration fills it in
        duration=ms;start=0;end=Math.min(ms,MAX_MS);strip.set(duration,start,end);showLength();
        done.setEnabled(true);spring(done,SMOOTH).alpha(1);
    }
    void showLength(){
        String text=VideoImport.clock(end-start);
        if(duration>MAX_MS||end-start<duration)text+=tr("  из ","  of ")+VideoImport.clock(duration);
        length.setText(text);
    }
    void problem(String message){hint.setTextColor(0xffff453a);swap(hint,message);}

    // ---------------------------------------------------------------- preview
    @Override public void onSurfaceTextureAvailable(SurfaceTexture st,int w,int h){
        surface=new Surface(st);player=new MediaPlayer();
        try{
            player.setOnPreparedListener(mp->{prepared=true;
                if(duration==0){long d=mp.getDuration();if(d>0)metadata(d,true);}
                if(videoW==0){videoW=mp.getVideoWidth();videoH=mp.getVideoHeight();fit();}
                seek(start);if(resumed)mp.start();main.post(tick);});
            player.setOnVideoSizeChangedListener((mp,w2,h2)->{if(w2>0&&h2>0){videoW=w2;videoH=h2;fit();}});
            player.setOnSeekCompleteListener(mp->{seekPending=false;if(seekWanted>=0){long next=seekWanted;seekWanted=-1;seek(next);}});
            player.setOnCompletionListener(mp->{if(strip.grab==Strip.NONE&&resumed){seek(start);mp.start();}});
            player.setOnErrorListener((mp,what,extra)->{Log.w("SoloVideo","Preview error "+what+"/"+extra);releasePlayer();problem(tr("Предпросмотр недоступен, но видео можно сохранить.","Preview is unavailable, but the video can still be saved."));return true;});
            player.setDataSource(this,uri);player.setSurface(surface);player.setVolume(0,0);player.prepareAsync();
        }catch(Exception e){Log.w("SoloVideo","Preview failed",e);releasePlayer();problem(tr("Предпросмотр недоступен, но видео можно сохранить.","Preview is unavailable, but the video can still be saved."));}
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st,int w,int h){}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st){releasePlayer();return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st){}
    /** Aspect-fits the preview into the stage. */
    void fit(){
        int sw=stage.getWidth(),sh=stage.getHeight();if(sw==0||sh==0||videoW<=0||videoH<=0)return;
        float s=Math.min(sw/(float)videoW,sh/(float)videoH);int w=Math.max(1,Math.round(videoW*s)),h=Math.max(1,Math.round(videoH*s));
        ViewGroup.LayoutParams lp=video.getLayoutParams();if(lp.width==w&&lp.height==h)return;
        lp.width=w;lp.height=h;video.setLayoutParams(lp);
    }
    /** Exact seeks one at a time: while one runs only the latest target is remembered, so dragging never queues up. */
    void seek(long ms){
        if(player==null||!prepared)return;
        if(seekPending){seekWanted=ms;return;}
        try{seekPending=true;player.seekTo(ms,MediaPlayer.SEEK_CLOSEST);}catch(IllegalStateException e){seekPending=false;}
    }
    /** Keeps playback inside the window and the playhead under it, about 30 times a second while visible. */
    final Runnable tick=new Runnable(){public void run(){
        if(closed||player==null||!prepared||!resumed)return;
        try{if(strip.grab==Strip.NONE&&player.isPlaying()&&!seekPending){
            long at=player.getCurrentPosition();
            if(at>=end||at<start-250){seek(start);at=start;}
            strip.playhead(at);
        }}catch(IllegalStateException ignored){}
        main.postDelayed(this,33);
    }};
    void releasePlayer(){
        prepared=false;seekPending=false;seekWanted=-1;main.removeCallbacks(tick);
        if(player!=null){try{player.release();}catch(RuntimeException ignored){}player=null;}
        if(surface!=null){surface.release();surface=null;}
    }

    // ---------------------------------------------------------------- import
    void save(){
        if(busy||duration<=0)return;busy=true;holdBack(true);
        done.performHapticFeedback(HapticFeedbackConstants.CONFIRM);swap(done,tr("Сохранение…","Saving…"));done.setEnabled(false);cancel.setEnabled(false);cancel.setAlpha(.45f);strip.setEnabled(false);
        hint.setTextColor(SECONDARY);swap(hint,tr("Видео сохраняется без звука.","Saving the video without sound."));
        final long s=start*1000,e=end>=duration?-1:end*1000;final Context app=getApplicationContext();final String target=slot;final Uri source=uri;
        io.execute(()->{
            String error=null;
            // Progress from the transcoder, shown on the button at most every few percent.
            int[] shown={-1};VideoTranscode.Progress progress=f->{int pct=Math.round(f*100);if(pct>=shown[0]+3){shown[0]=pct;main.post(()->{if(!closed)done.setText(tr("Сохранение… ","Saving… ")+pct+"%");});}};
            try{ImageStore.importVideo(app,source,target,s,e,progress);DepthMapManager.prune(app);}
            catch(Exception|OutOfMemoryError ex){Log.w("SoloVideo","Import failed",ex);error=ex.getMessage()==null?"":ex.getMessage();}
            final String failure=error;
            main.post(()->{
                if(failure==null){setResult(RESULT_OK);finish();return;}
                if(closed)return;busy=false;holdBack(false);done.setEnabled(true);cancel.setEnabled(true);cancel.setAlpha(1);strip.setEnabled(true);swap(done,tr("Готово","Done"));
                problem(tr("Не удалось сохранить видео.","Could not save the video.")+(config.en()&&!failure.isEmpty()?" "+failure:""));
            });
        });
    }

    /** Android 13+: back does nothing while the import runs (registered only then; otherwise back cancels as usual). */
    Object holdBack;
    void holdBack(boolean on){
        if(Build.VERSION.SDK_INT<33)return;
        android.window.OnBackInvokedDispatcher d=getOnBackInvokedDispatcher();
        if(on&&holdBack==null){android.window.OnBackInvokedCallback cb=()->{};holdBack=cb;d.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,cb);}
        else if(!on&&holdBack!=null){d.unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback)holdBack);holdBack=null;}
    }
    @Override protected void onResume(){super.onResume();resumed=true;if(player!=null&&prepared&&!busy){try{player.start();}catch(IllegalStateException ignored){}main.removeCallbacks(tick);main.post(tick);}}
    @Override protected void onPause(){resumed=false;main.removeCallbacks(tick);if(player!=null&&prepared)try{player.pause();}catch(IllegalStateException ignored){}super.onPause();}
    @Override protected void onDestroy(){
        closed=true;main.removeCallbacksAndMessages(null);releasePlayer();thumbnails.shutdownNow();io.shutdown(); // a running import still finishes
        if(strip!=null)strip.recycle();super.onDestroy();
    }
    @SuppressWarnings("deprecation") @Override public void finish(){super.finish();overridePendingTransition(R.anim.ios_pop_in,R.anim.ios_pop_out);}

    // ---------------------------------------------------------------- trim bar
    /** Filmstrip with the iOS Photos trim frame: two yellow handles, the kept part framed, the rest dimmed, a white playhead. */
    static final class Strip extends View {
        interface Listener{void moved(int handle,long startMs,long endMs,long atMs);void released(int handle);}
        static final int NONE=0,START=1,END=2,HEAD=3;
        final Bitmap[] thumbs=new Bitmap[THUMBS];final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),line=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path frame=new Path(),clip=new Path();final RectF r=new RectF();final Rect src=new Rect();
        long duration,start,end,head;int grab=NONE;float grabOffset;boolean atLimit;Listener listener;
        Strip(Context c){super(c);line.setStyle(Paint.Style.STROKE);line.setStrokeCap(Paint.Cap.ROUND);line.setStrokeJoin(Paint.Join.ROUND);line.setStrokeWidth(px(c,2.2f));setFocusable(true);}
        void set(long d,long s,long e){duration=d;start=s;end=e;head=s;invalidate();}
        void thumb(int i,Bitmap b){if(thumbs[i]!=null)thumbs[i].recycle();thumbs[i]=b;invalidate();}
        void playhead(long at){if(at!=head){head=at;invalidate();}}
        void recycle(){for(int i=0;i<thumbs.length;i++)if(thumbs[i]!=null){thumbs[i].recycle();thumbs[i]=null;}}
        float handle(){return px(getContext(),14);}
        float left(){return handle();}
        float right(){return Math.max(left()+1,getWidth()-handle());}
        float xOf(long t){return left()+(right()-left())*(duration<=0?0:t/(float)duration);}
        long tOf(float x){return duration<=0?0:Math.max(0,Math.min(duration,Math.round((x-left())/(right()-left())*duration)));}

        @Override protected void onDraw(Canvas c){
            float d=px(getContext(),1),h=getHeight(),border=3*d,top=border,bottom=h-border,l=left(),rr=right();
            // Filmstrip, rounded like the track in Photos; thumbnails are center-cropped into equal cells.
            c.save();r.set(l,top,rr,bottom);clip.reset();clip.addRoundRect(r,6*d,6*d,Path.Direction.CW);c.clipPath(clip);
            paint.setColor(0xff1c1c1e);c.drawRect(r,paint);float cell=(rr-l)/THUMBS;
            for(int i=0;i<THUMBS;i++){Bitmap b=thumbs[i];if(b==null||b.isRecycled())continue;
                float cw=cell,ch=bottom-top,scale=Math.max(cw/b.getWidth(),ch/b.getHeight());int sw=Math.round(cw/scale),sh=Math.round(ch/scale);
                src.set((b.getWidth()-sw)/2,(b.getHeight()-sh)/2,(b.getWidth()+sw)/2,(b.getHeight()+sh)/2);r.set(l+i*cell,top,l+(i+1)*cell+.5f,bottom);paint.setColor(Color.WHITE);c.drawBitmap(b,src,r,paint);}
            if(duration>0){paint.setColor(0x9e000000);float sx=xOf(start),ex=xOf(end);if(sx>l)c.drawRect(l,top,sx,bottom,paint);if(ex<rr)c.drawRect(ex,top,rr,bottom,paint);}
            c.restore();
            if(duration<=0)return;
            float sx=xOf(start),ex=xOf(end),hw=handle();
            // Trim frame: a yellow capsule-cornered outline, thick at both ends where the handles are.
            frame.reset();frame.setFillType(Path.FillType.EVEN_ODD);r.set(sx-hw,0,ex+hw,h);frame.addRoundRect(r,7*d,7*d,Path.Direction.CW);r.set(sx,border,ex,h-border);frame.addRoundRect(r,2*d,2*d,Path.Direction.CW);
            paint.setColor(isEnabled()?YELLOW:0xff8e8e93);c.drawPath(frame,paint);
            line.setColor(0xd9000000);float cy=h/2,a=4.5f*d,cx=sx-hw/2;
            c.drawLine(cx+a*.45f,cy-a,cx-a*.45f,cy,line);c.drawLine(cx-a*.45f,cy,cx+a*.45f,cy+a,line);
            cx=ex+hw/2;c.drawLine(cx-a*.45f,cy-a,cx+a*.45f,cy,line);c.drawLine(cx+a*.45f,cy,cx-a*.45f,cy+a,line);
            if(head>=start&&head<=end&&grab!=START&&grab!=END){
                float x=Math.max(sx+d,Math.min(ex-d,xOf(head)));paint.setColor(Color.WHITE);paint.setShadowLayer(2*d,0,0,0x80000000);
                r.set(x-1.5f*d,0,x+1.5f*d,h);c.drawRoundRect(r,1.5f*d,1.5f*d,paint);paint.clearShadowLayer();
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(!isEnabled()||duration<=0)return false;float x=e.getX();
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN->{
                    float hw=handle(),reach=hw+px(getContext(),14),sx=xOf(start),ex=xOf(end),ds=Math.abs(x-(sx-hw/2)),de=Math.abs(x-(ex+hw/2));
                    if(Math.min(ds,de)<=reach){grab=ds<de||ds==de&&x<(sx+ex)/2?START:END;grabOffset=x-(grab==START?sx:ex);}
                    else if(x>sx&&x<ex){grab=HEAD;grabOffset=0;}
                    else return false;
                    getParent().requestDisallowInterceptTouchEvent(true);atLimit=end-start>=MAX_MS;
                    if(grab!=HEAD)performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);move(x);return true;
                }
                case MotionEvent.ACTION_MOVE->{if(grab!=NONE){move(x);return true;}return false;}
                case MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{
                    if(grab==NONE)return false;int was=grab;grab=NONE;invalidate();if(listener!=null)listener.released(was);
                    if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;
                }
            }
            return false;
        }
        void move(float x){
            if(grab==HEAD){head=Math.max(start,Math.min(end,tOf(x)));}
            else{
                long[] w=VideoImport.drag(grab==START,tOf(x-grabOffset),start,end,duration,MIN_MS,MAX_MS);start=w[0];end=w[1];
                boolean limit=end-start>=MAX_MS;if(limit&&!atLimit)performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);atLimit=limit;
            }
            invalidate();if(listener!=null)listener.moved(grab,start,end,grab==START?start:grab==END?end:head);
        }
        @Override public boolean performClick(){return super.performClick();}
    }
}
