package dev.solotilt.personal;

import android.content.Context;
import android.graphics.*;
import android.opengl.*;
import android.os.*;
import android.view.Surface;
import android.util.Log;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class RenderLoop {
    final Context context;final HandlerThread thread=new HandlerThread("TiltRenderer");final Handler handler;
    final java.util.function.Consumer<String> error;
    EGLDisplay display=EGL14.EGL_NO_DISPLAY;EGLContext glContext=EGL14.EGL_NO_CONTEXT;EGLSurface eglSurface=EGL14.EGL_NO_SURFACE;
    GlScene scene;Surface surface;Bitmap bitmap;int w=1,h=1;boolean visible,closed,failed;float targetX,targetY,x,y;long lastFrame;
    RenderLoop(Context c,java.util.function.Consumer<String> e){context=c.getApplicationContext();error=e;thread.start();handler=new Handler(thread.getLooper());}
    void attach(Surface s,int width,int height){handler.post(()->{if(closed)return;releaseGl();surface=s;w=width;h=height;failed=false;try{load();initialize();schedule();}catch(Exception e){fail(e);}});}
    void visible(boolean value){handler.post(()->{visible=value;lastFrame=0;if(value)schedule();else handler.removeCallbacks(frame);});}
    void tilt(float tx,float ty){handler.post(()->{if(Math.abs(tx-targetX)<.05f&&Math.abs(ty-targetY)<.05f)return;targetX=tx;targetY=ty;schedule();});}
    void reload(){handler.post(()->{if(closed)return;try{load();if(scene!=null)scene.upload(bitmap);schedule();}catch(Exception e){fail(e);}});}
    void load()throws Exception{Bitmap next=ImageStore.load(context);if(bitmap!=null)bitmap.recycle();bitmap=next;}
    void initialize()throws Exception{
        if(surface==null||!surface.isValid())return;
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] vers=new int[2];if(!EGL14.eglInitialize(display,vers,0,vers,1))throw new IllegalStateException("EGL initialize");
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];int[] attrib={EGL14.EGL_RENDERABLE_TYPE,64,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_DEPTH_SIZE,0,EGL14.EGL_NONE};
        if(!EGL14.eglChooseConfig(display,attrib,0,configs,0,1,count,0)||count[0]==0)throw new IllegalStateException("OpenGL ES 3 unavailable");
        glContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        eglSurface=EGL14.eglCreateWindowSurface(display,configs[0],surface,new int[]{EGL14.EGL_NONE},0);
        if(!EGL14.eglMakeCurrent(display,eglSurface,eglSurface,glContext))throw new IllegalStateException("EGL surface");
        EGL14.eglSwapInterval(display,1);scene=new GlScene(context);scene.upload(bitmap);
    }
    final Runnable frame=new Runnable(){public void run(){
        if(closed||!visible||surface==null||!surface.isValid())return;
        if(failed){still();return;}if(scene==null)return;
        try{
            long now=SystemClock.uptimeMillis();float dt=lastFrame==0?33:Math.min(100,now-lastFrame);lastFrame=now;
            float blend=1-(float)Math.exp(-dt/70);x+=(targetX-x)*blend;y+=(targetY-y)*blend;
            if(Math.abs(targetX-x)<.02f)x=targetX;if(Math.abs(targetY-y)<.02f)y=targetY;
            boolean preparing=scene.prepareBlur();scene.draw(w,h,x,y);
            if(!EGL14.eglSwapBuffers(display,eglSurface)){
                int code=EGL14.eglGetError();if(code==EGL14.EGL_CONTEXT_LOST){releaseGl();initialize();schedule();return;}
                throw new IllegalStateException("EGL swap "+Integer.toHexString(code));
            }
            if(preparing||x!=targetX||y!=targetY)handler.postDelayed(this,Math.max(1,33-(SystemClock.uptimeMillis()-now)));else lastFrame=0;
        }catch(Exception e){fail(e);}
    }};
    void schedule(){if(visible&&!closed&&surface!=null){handler.removeCallbacks(frame);handler.postDelayed(frame,Math.max(0,33-(SystemClock.uptimeMillis()-lastFrame)));}}
    void fail(Exception e){Log.e("TiltRenderer","Rendering failed",e);failed=true;releaseGl();if(error!=null)error.accept("Эффект недоступен: "+e.getMessage());if(visible)still();}
    void still(){if(surface==null||!surface.isValid()||bitmap==null)return;Canvas c=null;try{c=surface.lockCanvas(null);c.drawColor(Color.BLACK);float s=Math.max((float)c.getWidth()/bitmap.getWidth(),(float)c.getHeight()/bitmap.getHeight());float bw=bitmap.getWidth()*s,bh=bitmap.getHeight()*s;c.drawBitmap(bitmap,null,new RectF((c.getWidth()-bw)/2,(c.getHeight()-bh)/2,(c.getWidth()+bw)/2,(c.getHeight()+bh)/2),new Paint(3));}catch(Exception e){Log.w("TiltRenderer","Surface unavailable",e);}finally{if(c!=null)surface.unlockCanvasAndPost(c);}}
    void releaseGl(){
        if(display!=EGL14.EGL_NO_DISPLAY){if(scene!=null){scene.close();scene=null;}EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(eglSurface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,eglSurface);if(glContext!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,glContext);EGL14.eglTerminate(display);EGL14.eglReleaseThread();}
        display=EGL14.EGL_NO_DISPLAY;glContext=EGL14.EGL_NO_CONTEXT;eglSurface=EGL14.EGL_NO_SURFACE;
    }
    void detach(){CountDownLatch latch=new CountDownLatch(1);handler.post(()->{try{handler.removeCallbacks(frame);releaseGl();surface=null;}finally{latch.countDown();}});try{latch.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    void close(){handler.post(()->{closed=true;visible=false;handler.removeCallbacks(frame);releaseGl();surface=null;if(bitmap!=null){bitmap.recycle();bitmap=null;}thread.quitSafely();});}
}
