package dev.solotilt.personal;

import android.content.Context;
import android.graphics.*;
import android.opengl.*;
import android.os.*;
import android.view.*;
import android.hardware.display.DisplayManager;
import android.util.Log;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class RenderLoop {
    // One preparation queue for preview and wallpaper; never decode/infer in a frame callback.
    static final ExecutorService PREPARE=Executors.newSingleThreadExecutor();
    final Context context;final HandlerThread thread=new HandlerThread("SoloRenderer");final Handler handler;
    final java.util.function.Consumer<String> error;
    final RenderState input=new RenderState();final RenderState.Frame latest=new RenderState.Frame();
    final AtomicBoolean wakeQueued=new AtomicBoolean();final PerformanceController performance;final DisplayManager displays;
    Choreographer choreographer;volatile boolean framePending,closed,visible;
    EGLDisplay display=EGL14.EGL_NO_DISPLAY;EGLContext glContext=EGL14.EGL_NO_CONTEXT;EGLSurface eglSurface=EGL14.EGL_NO_SURFACE;
    GlScene scene;Surface surface;Bitmap bitmap;int w=1,h=1;volatile boolean failed,depthReady,loading,manualDepth;
    WallpaperConfig config;float x,y;volatile float rawX,rawY;long previousVsync,lastDraw,transitionStart,touchTime;
    float touchX=.5f,touchY=.5f,oldA,oldB;int effect,previous;volatile int generation;
    float refresh=60,qualityScale=1;long interval=16666667;int contextRetries;
    final Runnable wake=()->{wakeQueued.set(false);schedule();};
    final Runnable quality=()->{if(!closed&&scene!=null)try{configureQuality();schedule();}catch(Exception e){fail(e);}};
    final Runnable recover=()->{if(closed)return;try{releaseGl();initialize();if(bitmap!=null)scene.upload(bitmap);depthReady=false;reload();schedule();}catch(Exception e){fail(e);}};
    RenderLoop(Context c,java.util.function.Consumer<String> e){
        context=c.getApplicationContext();error=e;config=WallpaperConfig.load(context);if(!android.animation.ValueAnimator.areAnimatorsEnabled())config.reduceMotion=true;effect=previous=config.effect;performance=new PerformanceController(context);
        displays=c.getSystemService(DisplayManager.class);Display d=displays.getDisplay(0);if(d!=null)refresh=Math.max(30,d.getRefreshRate());
        thread.start();handler=new Handler(thread.getLooper());handler.post(()->choreographer=Choreographer.getInstance());displays.registerDisplayListener(displayListener,handler);
    }
    void attach(Surface s,int width,int height){handler.post(()->{
        if(closed)return;w=width;h=height;failed=false;
        try{if(surface==s&&scene!=null){configureQuality();schedule();return;}
            cancelFrame();releaseGl();surface=s;initialize();if(bitmap!=null)scene.upload(bitmap);depthReady=false;
            if(bitmap==null||Effects.DEPTH[config.effect])reload();schedule();
        }catch(Exception|OutOfMemoryError e){fail(e);}
    });}
    void visible(boolean value){handler.post(()->{visible=value;previousVsync=lastDraw=0;handler.removeCallbacks(diagnostic);if(value){if(surface!=null&&surface.isValid())surface.setFrameRate(60,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);schedule();if(performance.debug)handler.postDelayed(diagnostic,2000);}else cancelFrame();});}
    void sensorInfo(String name){performance.sensor=name;}
    void tilt(float tx,float ty){tilt(tx,ty,SystemClock.elapsedRealtimeNanos());}
    void tilt(float tx,float ty,long timestamp){
        if(closed)return;rawX=tx;rawY=ty;
        boolean changed=input.set(tx,ty,timestamp);
        if(visible&&!failed&&changed&&!framePending&&wakeQueued.compareAndSet(false,true))handler.post(wake);
    }
    void touch(float tx,float ty){if(!config.touch)return;handler.post(()->{touchX=tx;touchY=1-ty;touchTime=SystemClock.elapsedRealtimeNanos();schedule();});}
    void settings(WallpaperConfig next){WallpaperConfig copy=WallpaperConfig.parse(next.json());if(!android.animation.ValueAnimator.areAnimatorsEnabled())copy.reduceMotion=true;handler.post(()->{
        if(closed)return;try{config=copy;configureEffect();if(scene!=null)configureQuality();
        if(Effects.DEPTH[copy.effect]&&!depthReady&&!loading)reload();schedule();}catch(Exception e){fail(e);}
    });}
    void configureEffect(){int requested=Effects.DEPTH[config.effect]&&!depthReady?0:config.effect;if(requested!=effect){previous=effect;oldA=config.a[effect];oldB=config.b[effect];effect=requested;transitionStart=SystemClock.elapsedRealtimeNanos();}}
    void reload(){if(closed)return;handler.post(()->prepare());}
    void prepare(){
        if(closed)return;final int request=++generation;loading=true;
        final boolean needDepth=Effects.DEPTH[config.effect];
        PREPARE.execute(()->{
            Bitmap photo=null,depth=null;String failure=null;String key=DepthMapManager.key(context);
            try{photo=ImageStore.load(context);if(needDepth)try{depth=DepthMapManager.load(context,photo,key);}catch(Exception|OutOfMemoryError e){Log.w("SoloDepth","Preparation failed",e);failure="depth";}}
            catch(Exception|OutOfMemoryError e){Log.w("SoloImage","Preparation failed",e);failure="image";}
            final Bitmap ready=photo,readyDepth=depth;final String problem=failure;final boolean imported=DepthMapManager.manual(context,key);
            if(closed||request!=generation||!key.equals(DepthMapManager.key(context))){if(ready!=null)ready.recycle();if(readyDepth!=null)readyDepth.recycle();if(!closed&&request==generation)handler.post(this::prepare);return;}
            boolean posted=handler.post(()->{
                if(closed||request!=generation){if(ready!=null)ready.recycle();if(readyDepth!=null)readyDepth.recycle();return;}
                loading=false;try{
                    if(ready==null){notifyError("image");return;}
                    if(bitmap!=null)bitmap.recycle();bitmap=ready;depthReady=false;manualDepth=imported;
                    if(scene!=null){scene.upload(bitmap);if(readyDepth!=null){scene.uploadDepth(readyDepth);depthReady=true;}}
                    configureEffect();if(problem!=null)notifyError(problem);schedule();
                    if(!needDepth&&Effects.DEPTH[config.effect]&&scene!=null)prepare();
                }catch(Exception|OutOfMemoryError ex){fail(ex);}finally{if(readyDepth!=null)readyDepth.recycle();}
            });
            if(!posted){if(ready!=null)ready.recycle();if(readyDepth!=null)readyDepth.recycle();}
        });
    }
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
        float target=config.quality==1||performance.saver?30:60;
        interval=(long)(Math.ceil(refresh/target)*1e9/refresh);
    }
    final Choreographer.FrameCallback frame=this::drawFrame;
    void drawFrame(long vsync){
        framePending=false;if(closed||!visible||surface==null||!surface.isValid())return;
        if(failed){still();return;}if(scene==null||bitmap==null)return;
        if(lastDraw!=0&&vsync-lastDraw<interval-2000000){schedule();return;}
        try{
            long start=SystemClock.elapsedRealtimeNanos();input.read(latest);performance.eventCount=latest.count;
            float dt=previousVsync==0?interval*1e-6f:Math.min(100,(vsync-previousVsync)*1e-6f);
            if(previousVsync>0&&vsync-previousVsync>interval*1.5)performance.dropped+=Math.max(0,Math.round((vsync-previousVsync)/(double)interval)-1);
            previousVsync=lastDraw=vsync;
            float smooth=config.effect==0?config.b[0]:config.smoothing;
            float blend=1-(float)Math.exp(-dt/(18+smooth*72));
            float targetX=(latest.x-config.neutralX)*config.sensitivity,targetY=(latest.y-config.neutralY)*config.sensitivity;
            x+=(targetX-x)*blend;y+=(targetY-y)*blend;
            if(Math.abs(targetX-x)<.02f)x=targetX;if(Math.abs(targetY-y)<.02f)y=targetY;
            // Mip preparation is a one-off task between frame callbacks, never a frame-time allocation.
            if(scene.nextLevel<=scene.levels&&!this.blurQueued){this.blurQueued=true;handler.post(this.blurStep);}
            float transition=config.reduceMotion?1:Math.min(1,(start-transitionStart)/220000000f);
            float touchAge=touchTime==0?99:(start-touchTime)*1e-9f;
            scene.draw(w,h,x,y,config,effect,previous,transition,oldA,oldB,touchX,touchY,touchAge);
            long submitted=SystemClock.elapsedRealtimeNanos();
            if(!EGL14.eglSwapBuffers(display,eglSurface)){
                int code=EGL14.eglGetError();if(code==EGL14.EGL_CONTEXT_LOST&&contextRetries++<2){handler.post(recover);return;}throw new IllegalStateException("EGL swap "+Integer.toHexString(code));
            }
            performance.frames++;performance.frameMs=(submitted-start)*1e-6f;performance.swapMs=(SystemClock.elapsedRealtimeNanos()-submitted)*1e-6f;
            performance.sensorAgeMs=latest.timestamp==0?0:Math.max(0,(start-latest.timestamp)*1e-6f);
            float desired=performance.desiredScale(config.quality,w,h);
            if(performance.sample(performance.frameMs)||Math.abs(desired-qualityScale)>.01)handler.post(quality);
            if(scene.nextLevel<=scene.levels||x!=targetX||y!=targetY||transition<1||(effect==3&&config.touch&&!config.reduceMotion&&touchAge<1.6f))schedule();else previousVsync=lastDraw=0;
        }catch(Exception e){fail(e);}
    }
    boolean blurQueued;
    final Runnable diagnostic=new Runnable(){public void run(){if(!closed&&visible&&performance.debug){input.read(latest);performance.eventCount=latest.count;Log.i("SoloPerf",performance.report());handler.postDelayed(this,2000);}}};
    final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){public void onDisplayAdded(int id){}public void onDisplayRemoved(int id){}public void onDisplayChanged(int id){if(id!=0||closed)return;Display d=displays.getDisplay(id);if(d!=null){refresh=Math.max(30,d.getRefreshRate());handler.post(quality);}}};
    final Runnable blurStep=()->{blurQueued=false;if(!closed&&scene!=null&&visible)try{scene.prepareBlur();schedule();}catch(Exception e){fail(e);}};
    void schedule(){if(visible&&!closed&&surface!=null&&!framePending){framePending=true;choreographer.postFrameCallback(frame);}}
    void cancelFrame(){choreographer.removeFrameCallback(frame);framePending=false;}
    void notifyError(String code){if(error!=null)error.accept(code);}
    void fail(Throwable e){Log.e("SoloRenderer","Rendering failed",e);failed=true;releaseGl();notifyError("gpu");if(visible)still();}
    void still(){if(surface==null||!surface.isValid()||bitmap==null)return;Canvas c=null;try{c=surface.lockCanvas(null);c.drawColor(Color.BLACK);float s=Math.max((float)c.getWidth()/bitmap.getWidth(),(float)c.getHeight()/bitmap.getHeight());float bw=bitmap.getWidth()*s,bh=bitmap.getHeight()*s;c.drawBitmap(bitmap,null,new RectF((c.getWidth()-bw)/2,(c.getHeight()-bh)/2,(c.getWidth()+bw)/2,(c.getHeight()+bh)/2),new Paint(3));}catch(Exception e){Log.w("SoloRenderer","Surface unavailable",e);}finally{if(c!=null)surface.unlockCanvasAndPost(c);}}
    void releaseGl(){
        if(display!=EGL14.EGL_NO_DISPLAY){if(scene!=null){scene.close();scene=null;}EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(eglSurface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,eglSurface);if(glContext!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,glContext);EGL14.eglTerminate(display);EGL14.eglReleaseThread();}
        display=EGL14.EGL_NO_DISPLAY;glContext=EGL14.EGL_NO_CONTEXT;eglSurface=EGL14.EGL_NO_SURFACE;
    }
    void detach(){CountDownLatch latch=new CountDownLatch(1);handler.post(()->{try{cancelFrame();releaseGl();surface=null;}finally{latch.countDown();}});try{latch.await(250,TimeUnit.MILLISECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    void close(){closed=true;generation++;displays.unregisterDisplayListener(displayListener);performance.close();handler.post(()->{visible=false;cancelFrame();releaseGl();surface=null;if(bitmap!=null){bitmap.recycle();bitmap=null;}thread.quitSafely();});}
}
