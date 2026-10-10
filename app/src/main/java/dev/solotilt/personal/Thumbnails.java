package dev.solotilt.personal;

import android.content.Context;
import android.graphics.*;
import android.opengl.*;
import android.os.*;
import android.util.Log;
import java.nio.*;
import java.util.function.BiConsumer;

/**
 * Catalog previews: every effect rendered once on the slot's photo with the real shaders, in an off-screen
 * pbuffer on its own thread, so cards show what the wallpaper will look like rather than a generic icon.
 */
final class Thumbnails {
    static final int WIDTH=210,HEIGHT=330;
    final Context context;final HandlerThread thread=new HandlerThread("SoloThumbnails");final Handler handler,main=new Handler(Looper.getMainLooper());
    EGLDisplay display=EGL14.EGL_NO_DISPLAY;EGLContext glContext=EGL14.EGL_NO_CONTEXT;EGLSurface surface=EGL14.EGL_NO_SURFACE;GlScene scene;
    volatile int generation;boolean broken;

    Thumbnails(Context c){context=c.getApplicationContext();thread.start();handler=new Handler(thread.getLooper());}

    /** Renders each effect of the profile's slot; results arrive on the main thread, newest request wins. */
    void render(String slot,WallpaperConfig.Profile profile,BiConsumer<String,Bitmap> result){
        final int request=++generation;final WallpaperConfig.Profile p=copy(profile);
        handler.post(()->{
            if(request!=generation||broken)return;Bitmap photo=null,depth=null;
            try{
                ensureGl();
                photo=ImageStore.isCustom(context,slot)?ImageStore.thumbnail(context,slot,HEIGHT*2):ImageStore.preset(ImageStore.presetIndex(context,slot),HEIGHT*2f/1560);
                if(photo==null)photo=ImageStore.preset(ImageStore.presetIndex(context,slot),HEIGHT*2f/1560);
                scene.upload(0,photo);while(scene.prepareBlur()){}
                try{depth=DepthMapManager.load(context,photo,DepthMapManager.key(context,slot));scene.uploadDepth(0,depth);}catch(Exception|OutOfMemoryError e){Log.w("SoloThumbs","No depth for previews",e);}
                GlScene.Pass pass=new GlScene.Pass();ByteBuffer pixels=ByteBuffer.allocateDirect(WIDTH*HEIGHT*4).order(ByteOrder.nativeOrder());
                float[] touch={.5f,.5f,99,0};android.graphics.Matrix flip=new android.graphics.Matrix();flip.setScale(1,-1);
                for(Effects.Effect e:Effects.ALL){
                    if(request!=generation)return;
                    p.effect=e.id;pass.set(0,p);
                    scene.begin();scene.draw(pass,WIDTH,HEIGHT,28,-14,1,touch,2.4f,9);scene.end(WIDTH,HEIGHT);
                    pixels.position(0);GLES30.glReadPixels(0,0,WIDTH,HEIGHT,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixels);
                    Bitmap raw=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);pixels.position(0);raw.copyPixelsFromBuffer(pixels);
                    Bitmap shot=Bitmap.createBitmap(raw,0,0,WIDTH,HEIGHT,flip,false);raw.recycle();
                    String id=e.id;main.post(()->{if(request==generation)result.accept(id,shot);else shot.recycle();});
                }
            }catch(Exception|OutOfMemoryError e){Log.w("SoloThumbs","Previews unavailable",e);broken=true;release();}
            finally{if(photo!=null)photo.recycle();if(depth!=null)depth.recycle();}
        });
    }
    static WallpaperConfig.Profile copy(WallpaperConfig.Profile from){WallpaperConfig.Profile p=new WallpaperConfig.Profile();p.copyFrom(from);return p;}
    void ensureGl()throws Exception{
        if(scene!=null)return;
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] vers=new int[2];if(!EGL14.eglInitialize(display,vers,0,vers,1))throw new IllegalStateException("EGL initialize");
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        int[] attrib={EGL14.EGL_RENDERABLE_TYPE,64,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE};
        if(!EGL14.eglChooseConfig(display,attrib,0,configs,0,1,count,0)||count[0]==0)throw new IllegalStateException("No pbuffer config");
        glContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        surface=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,WIDTH,EGL14.EGL_HEIGHT,HEIGHT,EGL14.EGL_NONE},0);
        if(!EGL14.eglMakeCurrent(display,surface,surface,glContext))throw new IllegalStateException("EGL pbuffer");
        scene=new GlScene(context);scene.resize(WIDTH,HEIGHT,1);
    }
    void release(){
        if(display==EGL14.EGL_NO_DISPLAY)return;if(scene!=null){scene.close();scene=null;}
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,glContext);EGL14.eglTerminate(display);EGL14.eglReleaseThread();
        display=EGL14.EGL_NO_DISPLAY;
    }
    void close(){generation++;handler.post(()->{release();thread.quitSafely();});}
}
