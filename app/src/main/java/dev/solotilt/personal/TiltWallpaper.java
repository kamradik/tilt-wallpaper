package dev.solotilt.personal;

import android.app.KeyguardManager;
import android.app.WallpaperManager;
import android.content.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

public final class TiltWallpaper extends WallpaperService {
    @Override public Engine onCreateEngine(){return new TiltEngine();}

    final class TiltEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        RenderLoop renderer;Motion motion;boolean visible,ready,locked;KeyguardManager keyguard;
        final Handler main=new Handler(Looper.getMainLooper());
        // Unlock without a broadcast (face unlock straight to home) is caught by a light poll that runs only while locked and visible.
        final Runnable poll=new Runnable(){public void run(){checkLock();if(visible&&locked)main.postDelayed(this,250);}};
        final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
            if(Intent.ACTION_USER_PRESENT.equals(i.getAction()))setLocked(false);
            else if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))main.postDelayed(TiltEngine.this::checkLock,400);
            else checkLock();
        }};

        public void onCreate(SurfaceHolder holder){
            super.onCreate(holder);
            renderer=new RenderLoop(TiltWallpaper.this,null);keyguard=getSystemService(KeyguardManager.class);
            motion=new Motion(TiltWallpaper.this,getSystemService(DisplayManager.class).getDisplay(0),renderer::tilt);renderer.sensorInfo(motion.description());
            ImageStore.prefs(TiltWallpaper.this).registerOnSharedPreferenceChangeListener(this);
            IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);
            if(Build.VERSION.SDK_INT>=33)registerReceiver(screen,filter,Context.RECEIVER_EXPORTED);else registerReceiver(screen,filter);
            setTouchEventsEnabled(true);setOffsetNotificationsEnabled(false);
            renderer.mode(mode());checkLock();
        }
        /** Android 14+ tells an engine which screen it serves; one engine on both screens follows the keyguard. */
        int mode(){
            if(Build.VERSION.SDK_INT>=34){
                int flags=getWallpaperFlags();
                if(flags==WallpaperManager.FLAG_LOCK)return RenderLoop.SHOW_LOCK;
                if(flags==WallpaperManager.FLAG_SYSTEM)return RenderLoop.SHOW_HOME;
            }
            return isPreview()?RenderLoop.SHOW_HOME:RenderLoop.SHOW_FOLLOW;
        }
        @Override public void onWallpaperFlagsChanged(int which){super.onWallpaperFlagsChanged(which);renderer.mode(mode());}
        void checkLock(){setLocked(keyguard!=null&&keyguard.isKeyguardLocked());}
        void setLocked(boolean value){
            locked=value;renderer.locked(value);
            main.removeCallbacks(poll);if(visible&&value)main.postDelayed(poll,250);
        }
        void update(){renderer.visible(visible&&ready);if(visible&&ready)motion.start();else motion.stop();}
        public void onVisibilityChanged(boolean v){visible=v;if(v)checkLock();else main.removeCallbacks(poll);update();}
        @Override public Bundle onCommand(String action,int x,int y,int z,Bundle extras,boolean resultExpected){
            // Hidden platform commands: sent as the unlock animation starts (before ACTION_USER_PRESENT) and on wake; switching here lets the crossfade ride the animation.
            if("android.wallpaper.keyguardgoingaway".equals(action))setLocked(false);
            else if("android.wallpaper.wakingup".equals(action))checkLock();
            return super.onCommand(action,x,y,z,extras,resultExpected);
        }
        public void onSurfaceChanged(SurfaceHolder holder,int format,int w,int h){super.onSurfaceChanged(holder,format,w,h);renderer.attach(holder.getSurface(),w,h);ready=true;update();}
        public void onSurfaceDestroyed(SurfaceHolder holder){ready=false;update();renderer.detach();super.onSurfaceDestroyed(holder);}
        public void onSharedPreferenceChanged(SharedPreferences p,String key){
            if(ImageStore.isRevisionKey(key))renderer.reload();
            else if(WallpaperConfig.KEY.equals(key))renderer.settings(WallpaperConfig.load(TiltWallpaper.this));
        }
        @Override public void onTouchEvent(MotionEvent e){
            int a=e.getActionMasked();if(a!=MotionEvent.ACTION_DOWN&&a!=MotionEvent.ACTION_MOVE)return;
            android.graphics.Rect r=getSurfaceHolder().getSurfaceFrame();renderer.touch(e.getX()/Math.max(1,r.width()),e.getY()/Math.max(1,r.height()),true);
        }
        public void onDestroy(){
            main.removeCallbacksAndMessages(null);try{unregisterReceiver(screen);}catch(IllegalArgumentException ignored){}
            ImageStore.prefs(TiltWallpaper.this).unregisterOnSharedPreferenceChangeListener(this);motion.close();renderer.close();super.onDestroy();
        }
    }
}
