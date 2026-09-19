package dev.solotilt.personal;
import android.service.wallpaper.WallpaperService;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.view.SurfaceHolder;
import android.view.MotionEvent;

public final class TiltWallpaper extends WallpaperService {
    @Override public Engine onCreateEngine(){return new TiltEngine();}
    final class TiltEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        RenderLoop renderer;Motion motion;boolean visible,ready;
        public void onCreate(SurfaceHolder holder){super.onCreate(holder);renderer=new RenderLoop(TiltWallpaper.this,null);motion=new Motion(TiltWallpaper.this,getSystemService(DisplayManager.class).getDisplay(0),renderer::tilt);renderer.sensorInfo(motion.description());ImageStore.prefs(TiltWallpaper.this).registerOnSharedPreferenceChangeListener(this);setTouchEventsEnabled(true);setOffsetNotificationsEnabled(false);}
        void update(){renderer.visible(visible&&ready);if(visible&&ready)motion.start();else motion.stop();}
        public void onVisibilityChanged(boolean v){visible=v;update();}
        public void onSurfaceChanged(SurfaceHolder holder,int format,int w,int h){super.onSurfaceChanged(holder,format,w,h);renderer.attach(holder.getSurface(),w,h);ready=true;update();}
        public void onSurfaceDestroyed(SurfaceHolder holder){ready=false;update();renderer.detach();super.onSurfaceDestroyed(holder);}
        public void onSharedPreferenceChanged(SharedPreferences p,String key){if("revision".equals(key)||"depth_revision".equals(key))renderer.reload();if(WallpaperConfig.KEY.equals(key))renderer.settings(WallpaperConfig.load(TiltWallpaper.this));}
        @Override public void onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN||e.getActionMasked()==MotionEvent.ACTION_MOVE){android.graphics.Rect r=getSurfaceHolder().getSurfaceFrame();renderer.touch(e.getX()/Math.max(1,r.width()),e.getY()/Math.max(1,r.height()));}}
        public void onDestroy(){ImageStore.prefs(TiltWallpaper.this).unregisterOnSharedPreferenceChangeListener(this);motion.close();renderer.close();super.onDestroy();}
    }
}
