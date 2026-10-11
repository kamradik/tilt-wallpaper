package dev.solotilt.personal;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import java.io.File;
import java.io.IOException;

/**
 * One looping, muted video decoding into a SurfaceTexture on an external (OES) texture of the render thread's
 * EGL context. Confined to the render thread: it is created there, so MediaPlayer callbacks arrive on that
 * thread's looper, and frame-available callbacks are posted to the same handler, so no field needs locking.
 * GlScene copies each latched frame into the layer's 2D texture, where the effects read it like a photo.
 */
final class VideoPlayer implements SurfaceTexture.OnFrameAvailableListener {
    /** Longest side of the 2D copy: effects sample blurred mips, so more pixels cost bandwidth without showing. */
    static final int MAX_SIDE=1280;
    interface Listener { void onVideoFrame(VideoPlayer p);void onVideoError(VideoPlayer p); }

    final int layer;final String key;final Listener listener;
    final float[] matrix=new float[16];
    final SurfaceTexture texture;final Surface surface;final MediaPlayer player;
    int width,height,pending;boolean prepared,wanted,started,released;final int startAt;

    /** Starts preparing asynchronously; playback begins only when play(true) is called (or was, before prepare finished). */
    VideoPlayer(int layer,String key,File file,int oesTexture,Handler handler,Listener listener,int startAtMs)throws IOException{
        this.layer=layer;this.key=key;this.listener=listener;startAt=startAtMs;
        texture=new SurfaceTexture(oesTexture);texture.setOnFrameAvailableListener(this,handler);surface=new Surface(texture);player=new MediaPlayer();
        try{
            player.setOnPreparedListener(this::prepared);
            player.setOnVideoSizeChangedListener((mp,w,h)->{if(w>0&&h>0){width=w;height=h;}});
            player.setOnErrorListener((mp,what,extra)->{Log.w("SoloVideo","MediaPlayer error "+what+"/"+extra);failed();return true;});
            player.setDataSource(file.getPath());player.setSurface(surface);
            // A live wallpaper must never make sound, whatever the clip contains.
            player.setVolume(0,0);player.setLooping(true);player.prepareAsync();
        }catch(IOException|RuntimeException e){release();throw e instanceof IOException?(IOException)e:new IOException(e);}
    }
    void prepared(MediaPlayer mp){
        if(released)return;prepared=true;
        if(width==0){width=mp.getVideoWidth();height=mp.getVideoHeight();}
        if(width<=0||height<=0){failed();return;} // audio-only or undecodable track
        if(startAt>0)mp.seekTo(startAt);
        apply();
    }
    void failed(){if(!released)listener.onVideoError(this);}
    /** Plays or pauses; cheap and idempotent, so the render loop can call it every frame. */
    void play(boolean value){if(wanted!=value){wanted=value;apply();}}
    void apply(){
        if(released||!prepared||started==wanted)return;
        try{if(wanted)player.start();else player.pause();started=wanted;}catch(IllegalStateException e){Log.w("SoloVideo","Playback state",e);failed();}
    }
    /** Decoding and delivering frames right now; frames, not the animation clock, then pace the redraws. */
    boolean playing(){return started&&!released;}
    int position(){try{return prepared&&!released?player.getCurrentPosition():0;}catch(IllegalStateException e){return 0;}}

    @Override public void onFrameAvailable(SurfaceTexture st){if(released||st!=texture)return;pending++;listener.onVideoFrame(this);}
    /**
     * Render thread with the EGL context current: latches the newest decoded frame and its transform
     * (which carries the clip's rotation metadata). Drains every queued frame so a slow redraw never lags the clip.
     */
    boolean latch(){
        if(released||pending==0)return false;
        while(pending>0){texture.updateTexImage();pending--;}
        texture.getTransformMatrix(matrix);return width>0&&height>0;
    }
    void release(){
        if(released)return;released=true;started=false;
        // Producer before consumer: stop the decoder writing into the surface, then free the SurfaceTexture.
        try{player.release();}catch(RuntimeException ignored){}
        surface.release();texture.setOnFrameAvailableListener(null);texture.release();
    }

    /** One side of a video frame scaled so the longer side fits MAX_SIDE, keeping the aspect ratio; never upscales. */
    static int capped(int side,int other){
        if(side<=0)return 1;float scale=Math.min(1f,MAX_SIDE/(float)Math.max(side,Math.max(1,other)));
        return Math.max(1,Math.round(side*scale));
    }
}
