package dev.solotilt.personal;

import android.content.*;
import android.graphics.*;
import android.opengl.*;
import android.os.*;
import android.view.*;
import android.hardware.display.DisplayManager;
import android.util.Log;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class RenderLoop {
    // One preparation queue for preview and wallpaper; never decode/infer in a frame callback.
    static final ExecutorService PREPARE=Executors.newSingleThreadExecutor();
    /** Which profile a surface shows: fixed home or lock, or follow the keyguard when one engine serves both screens. */
    static final int SHOW_HOME=0,SHOW_LOCK=1,SHOW_FOLLOW=2;

    final Context context;final HandlerThread thread=new HandlerThread("SoloRenderer");final Handler handler;
    final java.util.function.Consumer<String> error;
    final RenderState input=new RenderState();final RenderState.Frame latest=new RenderState.Frame();
    final AtomicBoolean wakeQueued=new AtomicBoolean();final PerformanceController performance;final DisplayManager displays;
    Choreographer choreographer;volatile boolean framePending,closed,visible;
    EGLDisplay display=EGL14.EGL_NO_DISPLAY;EGLContext glContext=EGL14.EGL_NO_CONTEXT;EGLSurface eglSurface=EGL14.EGL_NO_SURFACE;
    GlScene scene;Surface surface;int w=1,h=1;volatile boolean failed,loading;
    WallpaperConfig config;float x,y;volatile float rawX,rawY;long previousVsync,lastDraw,transitionStart,transitionNanos=220_000_000L;
    volatile int generation;int mode=SHOW_HOME;boolean locked;
    // Per layer (0 home, 1 lock): CPU copy for the still fallback, the media key it was loaded from, depth state.
    final Bitmap[] bitmaps=new Bitmap[2];final String[] loadedKeys=new String[2];final boolean[] depthReady=new boolean[2],manualDepth=new boolean[2];
    final GlScene.Pass current=new GlScene.Pass(),outgoing=new GlScene.Pass();boolean hasCurrent,fading;
    // Per layer video: the media key whose clip the layer plays (null: a photo), its player (lives as long as the EGL
    // context, since its SurfaceTexture belongs to it), a key that failed to play, and where playback was when GL went away.
    final String[] videoKeys=new String[2],brokenVideo=new String[2],resumeKeys=new String[2];final int[] resumeAt=new int[2];final VideoPlayer[] players=new VideoPlayer[2];
    final VideoPlayer.Listener videoListener=new VideoPlayer.Listener(){
        public void onVideoFrame(VideoPlayer p){schedule();}
        public void onVideoError(VideoPlayer p){videoFailed(p);}
    };
    final long startTime=SystemClock.elapsedRealtimeNanos();long wakeTime=startTime,touchTime;
    final float[] touch={.5f,.5f,99,0};
    // Condensation: screen-space wipe coverage that decays back to fog.
    final byte[] wipeGrid=new byte[GlScene.WIPE_W*GlScene.WIPE_H];final ByteBuffer wipeBuffer=ByteBuffer.allocateDirect(wipeGrid.length);boolean wipeActive,wipeDirty;
    float refresh=60,qualityScale=1;long interval=16666667;int contextRetries;
    final Runnable wake=()->{wakeQueued.set(false);schedule();};
    final Runnable quality=()->{if(!closed&&scene!=null)try{configureQuality();schedule();}catch(Exception e){fail(e);}};
    final Runnable recover=()->{if(closed)return;try{releaseGl();initialize();uploadAll();syncVideo();schedule();}catch(Exception e){fail(e);}};

    // Inputs for event and physics effects (GlScene.ENV_*), updated on the render thread except battery.
    final float[] env=new float[GlScene.ENV_SIZE];final ByteBuffer fieldBuffer=ByteBuffer.allocateDirect(Simulation.W*Simulation.H*4);
    Simulation simulation;String simulationFor;float lastRawX,lastRawY,shake;long dayStamp;boolean simulationLive;
    final BroadcastReceiver battery=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        int status=i.getIntExtra(android.os.BatteryManager.EXTRA_STATUS,-1),level=i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,-1),scale=i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE,100);
        env[GlScene.ENV_CHARGING]=status==android.os.BatteryManager.BATTERY_STATUS_CHARGING||status==android.os.BatteryManager.BATTERY_STATUS_FULL?1:0;
        env[GlScene.ENV_LEVEL]=level<0?1:level/(float)Math.max(1,scale);if(handler!=null)handler.post(RenderLoop.this::schedule);
    }};
    RenderLoop(Context c,java.util.function.Consumer<String> e){
        context=c.getApplicationContext();error=e;config=WallpaperConfig.load(context);if(!android.animation.ValueAnimator.areAnimatorsEnabled())config.reduceMotion=true;performance=new PerformanceController(context);
        displays=c.getSystemService(DisplayManager.class);Display d=displays.getDisplay(0);if(d!=null)refresh=Math.max(30,d.getRefreshRate());
        thread.start();handler=new Handler(thread.getLooper());handler.post(()->choreographer=Choreographer.getInstance());displays.registerDisplayListener(displayListener,handler);
        // Sticky broadcast: the first call returns the current state, later ones keep env up to date.
        Intent now=Build.VERSION.SDK_INT>=33?context.registerReceiver(battery,new IntentFilter(Intent.ACTION_BATTERY_CHANGED),Context.RECEIVER_NOT_EXPORTED):context.registerReceiver(battery,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(now!=null)battery.onReceive(context,now);
    }

    // ---- inputs ----
    void attach(Surface s,int width,int height){handler.post(()->{
        if(closed)return;w=width;h=height;failed=false;
        try{if(surface==s&&scene!=null){configureQuality();schedule();return;}
            cancelFrame();releaseGl();surface=s;initialize();uploadAll();syncVideo();prepare();schedule();
        }catch(Exception|OutOfMemoryError e){fail(e);}
    });}
    void visible(boolean value){handler.post(()->{
        boolean appeared=value&&!visible;visible=value;previousVsync=lastDraw=0;handler.removeCallbacks(diagnostic);updatePlayback();
        if(value){if(appeared)wakeTime=SystemClock.elapsedRealtimeNanos();if(surface!=null&&surface.isValid())surface.setFrameRate(60,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);schedule();if(performance.debug)handler.postDelayed(diagnostic,2000);}
        else cancelFrame();
    });}
    /** SHOW_HOME, SHOW_LOCK or SHOW_FOLLOW. */
    void mode(int value){handler.post(()->{if(mode==value)return;mode=value;prepare();retarget(false);schedule();});}
    /** Keyguard state for SHOW_FOLLOW; the switch to the other profile crossfades like an iOS unlock. */
    void locked(boolean value){handler.post(()->{if(locked==value)return;locked=value;wakeTime=SystemClock.elapsedRealtimeNanos();retarget(true);schedule();});}
    void sensorInfo(String name){performance.sensor=name;}
    void tilt(float tx,float ty){tilt(tx,ty,SystemClock.elapsedRealtimeNanos());}
    void tilt(float tx,float ty,long timestamp){
        if(closed)return;rawX=tx;rawY=ty;
        boolean changed=input.set(tx,ty,timestamp);
        if(visible&&!failed&&changed&&!framePending&&wakeQueued.compareAndSet(false,true))handler.post(wake);
    }
    /** Normalized surface coordinates, y down. */
    void touch(float tx,float ty,boolean down){if(!config.touch||config.reduceMotion)return;handler.post(()->{
        touch[0]=tx;touch[1]=1-ty;touchTime=SystemClock.elapsedRealtimeNanos();if(down)wipe(tx,1-ty);schedule();
    });}
    void settings(WallpaperConfig next){WallpaperConfig copy=next.copy();if(!android.animation.ValueAnimator.areAnimatorsEnabled())copy.reduceMotion=true;handler.post(()->{
        if(closed)return;try{
            // Only a change in which layers or depth maps are needed reloads media; slider moves just retarget.
            boolean[] before={needed(0),needed(1),needsDepth(0),needsDepth(1)};config=copy;
            if(scene!=null)configureQuality();if(before[0]!=needed(0)||before[1]!=needed(1)||before[2]!=needsDepth(0)||before[3]!=needsDepth(1))prepare();
            retarget(false);schedule();
        }catch(Exception e){fail(e);}
    });}
    /** Media of a slot changed on disk (prefs revision); reloads only layers whose media key differs. */
    void reload(){if(!closed)handler.post(this::prepare);}

    // ---- slots and layers ----
    String slot(){return mode==SHOW_LOCK||mode==SHOW_FOLLOW&&locked?WallpaperConfig.LOCK:WallpaperConfig.HOME;}
    int layer(String slot){return config.separateLock&&WallpaperConfig.LOCK.equals(slot)?1:0;}
    static String mediaSlot(int layer){return layer==1?WallpaperConfig.LOCK:WallpaperConfig.HOME;}
    WallpaperConfig.Profile profile(int layer){return layer==1?config.lock:config.home;}
    boolean needed(int layer){return layer==0?!(config.separateLock&&mode==SHOW_LOCK):config.separateLock&&mode!=SHOW_HOME;}
    boolean needsDepth(int layer){return needed(layer)&&profile(layer).effect().has(Effects.DEPTH);}

    /** Points the current pass at the active profile; a different effect or layer starts a crossfade. */
    void retarget(boolean slotChange){
        int layer=layer(slot());WallpaperConfig.Profile p=config.profile(slot());
        if(scene==null||!scene.ready(layer)){if(scene!=null&&scene.ready(1-layer)&&!hasCurrent){current.set(1-layer,profile(1-layer));hasCurrent=true;}return;}
        // A depth effect shows Classic until its map is ready, then crossfades in.
        boolean fallback=p.effect().has(Effects.DEPTH)&&!depthReady[layer];
        String id=fallback?"classic":p.effect;
        if(hasCurrent&&(current.layer!=layer||!current.effect.id.equals(id))&&!config.reduceMotion){outgoing.copy(current);outgoing.alpha=1;fading=true;transitionStart=SystemClock.elapsedRealtimeNanos();transitionNanos=slotChange?380_000_000L:220_000_000L;}
        current.set(layer,p);if(fallback){current.effect=Effects.ALL[0];float[] d=current.effect.defaults();current.a=d[0];current.b=d[1];current.toggle=d[2];}
        hasCurrent=true;
    }
    void prepare(){
        if(closed)return;final int request=++generation;loading=true;
        // Snapshot render-thread state; the worker must not read fields this thread keeps changing.
        final boolean[] want=new boolean[2],needDepth=new boolean[2],missingDepth=new boolean[2];final String[] keys=new String[2],videos=new String[2],loaded=loadedKeys.clone();
        for(int i=0;i<2;i++){want[i]=needed(i);needDepth[i]=needsDepth(i);missingDepth[i]=needDepth[i]&&!depthReady[i];}
        PREPARE.execute(()->{
            final Bitmap[] photos=new Bitmap[2],maps=new Bitmap[2];final boolean[] imported=new boolean[2];String failure=null;
            for(int i=0;i<2;i++){
                if(!want[i])continue;String slot=mediaSlot(i);keys[i]=DepthMapManager.key(context,slot);
                // A video slot's photo is its poster frame: it is the still fallback, the first frame shown and the depth source.
                if(ImageStore.isVideo(context,slot))videos[i]=keys[i]+(ImageStore.isScrub(context,slot)?VideoPlayer.SCRUB:""); // Tilt Scrub: another key, another player
                if(keys[i].equals(loaded[i])&&!missingDepth[i])continue;
                try{photos[i]=ImageStore.load(context,slot);
                    if(needDepth[i])try{maps[i]=DepthMapManager.load(context,photos[i],keys[i]);imported[i]=DepthMapManager.manual(context,keys[i]);}catch(Exception|OutOfMemoryError e){Log.w("SoloDepth","Preparation failed",e);failure="depth";}
                }catch(Exception|OutOfMemoryError e){Log.w("SoloImage","Preparation failed",e);failure="image";}
            }
            final String problem=failure;
            boolean posted=handler.post(()->{
                if(closed||request!=generation){recycle(photos);recycle(maps);return;}
                loading=false;try{
                    System.arraycopy(videos,0,videoKeys,0,2);
                    for(int i=0;i<2;i++){
                        if(photos[i]==null)continue;
                        if(bitmaps[i]!=null)bitmaps[i].recycle();bitmaps[i]=photos[i];photos[i]=null;loadedKeys[i]=keys[i];depthReady[i]=false;
                        if(scene!=null)scene.upload(i,bitmaps[i]);
                        if(maps[i]!=null&&scene!=null){scene.uploadDepth(i,maps[i]);depthReady[i]=true;manualDepth[i]=imported[i];}
                    }
                    syncVideo();if(problem!=null)notifyError(problem);retarget(false);schedule();
                }catch(Exception|OutOfMemoryError ex){fail(ex);}finally{recycle(photos);recycle(maps);}
            });
            if(!posted){recycle(photos);recycle(maps);}
        });
    }
    static void recycle(Bitmap[] items){for(int i=0;i<items.length;i++)if(items[i]!=null){items[i].recycle();items[i]=null;}}
    void uploadAll()throws Exception{
        if(scene==null)return;
        for(int i=0;i<2;i++)if(bitmaps[i]!=null){scene.upload(i,bitmaps[i]);depthReady[i]=false;}
        hasCurrent=false;retarget(false); // photos re-uploaded from memory; prepare() reloads only the depth maps that died with the context
    }

    // ---- video ----
    /** Keeps one player per resident layer whose slot is a video; a clip that failed stays on its poster until the media changes. */
    void syncVideo(){
        for(int i=0;i<2;i++){
            String want=scene!=null&&!failed&&needed(i)?videoKeys[i]:null;if(want!=null&&want.equals(brokenVideo[i]))want=null;
            VideoPlayer p=players[i];if(p!=null&&!p.key.equals(want)){p.release();players[i]=null;}
            if(want!=null&&players[i]==null)try{
                players[i]=new VideoPlayer(i,want,ImageStore.videoFile(context,mediaSlot(i)),scene.videoTexture(i),handler,videoListener,want.equals(resumeKeys[i])?resumeAt[i]:0);
            }catch(Exception e){Log.w("SoloVideo","Cannot open video",e);brokenVideo[i]=want;showPoster(i);notifyError("video");}
        }
        updatePlayback();
    }
    /** Plays only the layer on screen (and the outgoing one during a crossfade); returns whether a clip is delivering frames. */
    boolean updatePlayback(){
        boolean any=false;
        for(int i=0;i<2;i++){VideoPlayer p=players[i];if(p==null)continue;
            boolean on=visible&&!failed&&surface!=null&&hasCurrent&&(current.layer==i||fading&&outgoing.layer==i);p.play(on);any|=on&&p.playing();
            if(on&&p.scrub)p.scrubTo(x);} // Tilt Scrub: the smoothed tilt picks the frame; a still phone issues no seek
        return any;
    }
    /** Render thread, context current: copies newly decoded frames into their layers. */
    void latchVideo(){
        for(int i=0;i<2;i++){VideoPlayer p=players[i];if(p==null||!p.latch())continue;
            int vw=p.width,vh=p.height;Bitmap poster=p.mediaKey().equals(loadedKeys[i])?bitmaps[i]:null;
            // Players report upright sizes on current platforms; the poster (always upright) catches one that does not.
            if(poster!=null&&vw!=vh&&poster.getWidth()!=poster.getHeight()&&(vw>vh)!=(poster.getWidth()>poster.getHeight())){int t=vw;vw=vh;vh=t;}
            try{scene.blitVideo(i,p.matrix,vw,vh,p.loopMix);if(p.firstFrame){scene.captureLoop(i);p.loopCaptured=true;}}catch(Exception e){Log.w("SoloVideo","Video frame copy failed",e);videoFailed(p);}
        }
    }
    void videoFailed(VideoPlayer p){
        int i=p.layer;if(players[i]!=p)return;brokenVideo[i]=p.key;p.release();players[i]=null;showPoster(i);notifyError("video");schedule();
    }
    /** Puts the poster back into a layer that held video frames; the depth map texture is untouched, so keep using it. */
    void showPoster(int i){
        if(scene==null||bitmaps[i]==null)return;
        try{boolean depth=scene.layers[i].hasDepth;scene.upload(i,bitmaps[i]);scene.layers[i].hasDepth=depth;}catch(RuntimeException e){fail(e);}
    }
    void releaseVideo(){for(int i=0;i<2;i++)if(players[i]!=null){resumeKeys[i]=players[i].key;resumeAt[i]=players[i].position();players[i].release();players[i]=null;}}

    // ---- condensation wipe ----
    void wipe(float tx,float ty){
        int cx=Math.round(tx*(GlScene.WIPE_W-1)),cy=Math.round(ty*(GlScene.WIPE_H-1)),r=6;
        for(int y=Math.max(0,cy-r);y<=Math.min(GlScene.WIPE_H-1,cy+r);y++)for(int x=Math.max(0,cx-r);x<=Math.min(GlScene.WIPE_W-1,cx+r);x++)
            if((x-cx)*(x-cx)+(y-cy)*(y-cy)<=r*r)wipeGrid[y*GlScene.WIPE_W+x]=(byte)255;
        wipeActive=wipeDirty=true;
    }
    void decayWipe(float seconds){
        float keep=(float)Math.exp(-seconds/20f);boolean any=false;
        for(int i=0;i<wipeGrid.length;i++){int v=wipeGrid[i]&0xff;if(v>0){v=Math.round(v*keep);wipeGrid[i]=(byte)v;any|=v>2;}}
        wipeActive=any;wipeDirty=true;
    }

    // ---- GL ----
    void initialize()throws Exception{
        if(surface==null||!surface.isValid())return;
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] vers=new int[2];if(!EGL14.eglInitialize(display,vers,0,vers,1))throw new IllegalStateException("EGL initialize");
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];int[] attrib={EGL14.EGL_RENDERABLE_TYPE,64,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_DEPTH_SIZE,0,EGL14.EGL_NONE};
        if(!EGL14.eglChooseConfig(display,attrib,0,configs,0,1,count,0)||count[0]==0)throw new IllegalStateException("OpenGL ES 3 unavailable");
        glContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        eglSurface=EGL14.eglCreateWindowSurface(display,configs[0],surface,new int[]{EGL14.EGL_NONE},0);
        if(!EGL14.eglMakeCurrent(display,eglSurface,eglSurface,glContext))throw new IllegalStateException("EGL surface");
        EGL14.eglSwapInterval(display,1);scene=new GlScene(context);configureQuality();
    }
    void configureQuality(){
        if(scene==null)return;float next=performance.desiredScale(config.quality,w,h);
        if(scene.renderWidth==0||Math.abs(next-qualityScale)>.01||Math.round(w*next)!=scene.renderWidth||Math.round(h*next)!=scene.renderHeight){qualityScale=next;scene.resize(w,h,next);}
        performance.width=scene.renderWidth;performance.height=scene.renderHeight;
        float target=config.fps==WallpaperConfig.FPS_30||config.quality==1||performance.saver?30:60;
        interval=(long)(Math.ceil(refresh/target)*1e9/refresh);
    }
    final Choreographer.FrameCallback frame=this::drawFrame;
    void drawFrame(long vsync){
        framePending=false;if(closed||!visible||surface==null||!surface.isValid())return;
        if(failed){still();return;}if(scene==null||!hasCurrent)return;
        // Ambient animation alone (no tilt in flight) runs at 30 fps under Auto; tilting gets the full rate.
        long pace=ambientOnly&&config.fps==WallpaperConfig.FPS_AUTO?Math.max(interval,33_333_333L):interval;
        if(lastDraw!=0&&vsync-lastDraw<pace-2000000){schedule();return;}
        try{
            long start=SystemClock.elapsedRealtimeNanos();input.read(latest);performance.eventCount=latest.count;
            float dt=previousVsync==0?interval*1e-6f:Math.min(100,(vsync-previousVsync)*1e-6f);
            if(previousVsync>0&&vsync-previousVsync>interval*1.5)performance.dropped+=Math.max(0,Math.round((vsync-previousVsync)/(double)interval)-1);
            previousVsync=lastDraw=vsync;
            float smooth=current.effect==Effects.ALL[0]?current.b:config.smoothing;
            float blend=1-(float)Math.exp(-dt/(18+smooth*72));
            float targetX=(latest.x-config.neutralX)*config.sensitivity,targetY=(latest.y-config.neutralY)*config.sensitivity;
            x+=(targetX-x)*blend;y+=(targetY-y)*blend;
            if(Math.abs(targetX-x)<.02f)x=targetX;if(Math.abs(targetY-y)<.02f)y=targetY;
            // Mip preparation is a one-off task between frame callbacks, never a frame-time allocation.
            if(scene.blurPending()&&!blurQueued){blurQueued=true;handler.post(blurStep);}
            float transition=config.reduceMotion?1:Math.min(1,(start-transitionStart)/(float)transitionNanos);
            fading=transition<1;boolean video=updatePlayback();latchVideo();if(scene==null)return; // a failed poster restore released GL
            touch[2]=touchTime==0?99:(start-touchTime)*1e-9f;touch[3]=config.touch&&!config.reduceMotion?1:0;
            if(current.effect.has(Effects.WIPE)){if(wipeActive)decayWipe(dt*1e-3f);if(wipeDirty){wipeBuffer.position(0);wipeBuffer.put(wipeGrid);scene.updateWipe(wipeBuffer);wipeDirty=false;}}
            updateEnvironment(start,dt);
            float motion=config.reduceMotion?.15f:1,time=((start-startTime)*1e-9f)%3600f,wakeAge=(start-wakeTime)*1e-9f;
            scene.begin();
            if(transition<1){scene.draw(outgoing,w,h,x,y,motion,touch,time,wakeAge,env);current.alpha=transition*transition*(3-2*transition);}else current.alpha=1;
            scene.draw(current,w,h,x,y,motion,touch,time,wakeAge,env);
            scene.end(w,h);
            long submitted=SystemClock.elapsedRealtimeNanos();
            if(!EGL14.eglSwapBuffers(display,eglSurface)){
                int code=EGL14.eglGetError();if(code==EGL14.EGL_CONTEXT_LOST&&contextRetries++<2){handler.post(recover);return;}throw new IllegalStateException("EGL swap "+Integer.toHexString(code));
            }
            performance.frames++;performance.frameMs=(submitted-start)*1e-6f;performance.swapMs=(SystemClock.elapsedRealtimeNanos()-submitted)*1e-6f;
            performance.sensorAgeMs=latest.timestamp==0?0:Math.max(0,(start-latest.timestamp)*1e-6f);
            float desired=performance.desiredScale(config.quality,w,h);
            if(performance.sample(performance.frameMs)||Math.abs(desired-qualityScale)>.01)handler.post(quality);
            boolean live=!config.reduceMotion&&(simulationLive||current.effect.animated(current.toggle>.5f)
                ||current.effect.has(Effects.TOUCH)&&touch[3]>0&&touch[2]<1.6f||current.effect.has(Effects.WIPE)&&wipeActive||wakeAge<1.2f);
            // A playing clip schedules its own frames as they decode (so at most its rate, paced like tilt by interval);
            // ambient animation rides on those frames instead of adding a clock of its own.
            boolean settling=scene.blurPending()||x!=targetX||y!=targetY||transition<1;ambientOnly=live&&!settling&&!video;
            if(settling||live&&!video)schedule();else{previousVsync=0;if(!video)lastDraw=0;}
        }catch(Exception e){fail(e);}
    }
    boolean blurQueued,ambientOnly;
    final Runnable diagnostic=new Runnable(){public void run(){if(!closed&&visible&&performance.debug){input.read(latest);performance.eventCount=latest.count;Log.i("SoloPerf",performance.report());handler.postDelayed(this,2000);}}};
    final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){public void onDisplayAdded(int id){}public void onDisplayRemoved(int id){}public void onDisplayChanged(int id){if(id!=0||closed)return;Display d=displays.getDisplay(id);if(d!=null){refresh=Math.max(30,d.getRefreshRate());handler.post(quality);}}};
    final Runnable blurStep=()->{blurQueued=false;if(!closed&&scene!=null&&visible)try{scene.prepareBlur();schedule();}catch(Exception e){fail(e);}};
    void schedule(){if(visible&&!closed&&surface!=null&&!framePending&&choreographer!=null){framePending=true;choreographer.postFrameCallback(frame);}}
    void cancelFrame(){if(choreographer!=null)choreographer.removeFrameCallback(frame);framePending=false;}
    void notifyError(String code){if(error!=null)error.accept(code);}
    void fail(Throwable e){Log.e("SoloRenderer","Rendering failed",e);failed=true;releaseGl();notifyError("gpu");if(visible)still();}
    void still(){
        Bitmap bitmap=bitmaps[hasCurrent?current.layer:0];if(bitmap==null)bitmap=bitmaps[0];
        if(surface==null||!surface.isValid()||bitmap==null)return;Canvas c=null;
        try{c=surface.lockCanvas(null);c.drawColor(Color.BLACK);float s=Math.max((float)c.getWidth()/bitmap.getWidth(),(float)c.getHeight()/bitmap.getHeight());float bw=bitmap.getWidth()*s,bh=bitmap.getHeight()*s;c.drawBitmap(bitmap,null,new RectF((c.getWidth()-bw)/2,(c.getHeight()-bh)/2,(c.getWidth()+bw)/2,(c.getHeight()+bh)/2),new Paint(3));}
        catch(Exception e){Log.w("SoloRenderer","Surface unavailable",e);}finally{if(c!=null)surface.unlockCanvasAndPost(c);}
    }
    void releaseGl(){
        releaseVideo();
        if(display!=EGL14.EGL_NO_DISPLAY){if(scene!=null){scene.close();scene=null;}EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(eglSurface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,eglSurface);if(glContext!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,glContext);EGL14.eglTerminate(display);EGL14.eglReleaseThread();}
        display=EGL14.EGL_NO_DISPLAY;glContext=EGL14.EGL_NO_CONTEXT;eglSurface=EGL14.EGL_NO_SURFACE;
    }
    void detach(){CountDownLatch latch=new CountDownLatch(1);handler.post(()->{try{cancelFrame();releaseGl();surface=null;}finally{latch.countDown();}});try{latch.await(250,TimeUnit.MILLISECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    /** Time of day (once a second), screen-space gravity and shake from the raw sensor tilt, and the current effect's physics. */
    void updateEnvironment(long now,float dtMs){
        if(now-dayStamp>1_000_000_000L){dayStamp=now;env[GlScene.ENV_DAY]=java.time.LocalTime.now().toSecondOfDay()/86400f;}
        // Tilt is the gravity direction scaled to its angle from face-up; gravity itself points the other way on screen.
        float ax=latest.x,ay=latest.y,a=(float)Math.hypot(ax,ay),s=(float)Math.sin(Math.toRadians(Math.min(90,a))),sec=Math.max(.001f,dtMs*1e-3f);
        env[GlScene.ENV_GX]=a<1e-3f?0:-ax/a*s;env[GlScene.ENV_GY]=a<1e-3f?0:-ay/a*s;
        float rate=(float)Math.hypot(ax-lastRawX,ay-lastRawY)/sec;lastRawX=ax;lastRawY=ay;
        shake=Math.max(shake*(float)Math.exp(-sec*4),Math.max(0,Math.min(1,(rate-60)/400)));env[GlScene.ENV_SHAKE]=shake;
        Effects.Effect e=current.effect;simulationLive=false;
        if(e.simulation==null){simulation=null;simulationFor=null;return;}
        if(!e.id.equals(simulationFor)){simulation=e.simulation.get();simulationFor=e.id;}
        boolean touching=touch[3]>0&&touch[2]<.12f;
        simulationLive=simulation.step(Math.min(.1f,sec),env[GlScene.ENV_GX],env[GlScene.ENV_GY],shake,touching?touch[0]:Float.NaN,touching?touch[1]:Float.NaN);
        fieldBuffer.position(0);simulation.fill(fieldBuffer);scene.updateField(fieldBuffer);
    }
    void close(){closed=true;generation++;try{context.unregisterReceiver(battery);}catch(IllegalArgumentException ignored){}displays.unregisterDisplayListener(displayListener);performance.close();handler.post(()->{visible=false;cancelFrame();releaseGl();surface=null;recycle(bitmaps);thread.quitSafely();});}
    /** Depth state of what is on screen, for the preview's status line. */
    boolean showingDepth(){return hasCurrent&&depthReady[current.layer];}
    boolean showingManualDepth(){return hasCurrent&&manualDepth[current.layer];}
}
