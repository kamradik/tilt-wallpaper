package dev.solotilt.personal;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import java.io.File;
import java.io.IOException;

/**
 * One muted video decoding into a SurfaceTexture on an external (OES) texture of the render thread's EGL context.
 * Confined to the render thread: it is created there, so MediaPlayer callbacks arrive on that thread's looper, and
 * frame-available callbacks are posted to the same handler, so no field needs locking. GlScene copies each latched
 * frame into the layer's 2D texture, where the effects read it like a photo.
 *
 * Two modes, chosen by the media key (see SCRUB):
 * - Loop: plays and loops. MediaPlayer's own looping stalls for a moment at the wrap (the decoder flushes and
 *   restarts from the first sync frame) and the last frame cuts hard to the first. The layer therefore crossfades its
 *   last LOOP_FADE_MS into a copy of the clip's first frame (loopMix, captured by GlScene.captureLoop), so the frame
 *   held during the stall already equals the one that follows it.
 * - Scrub: never plays; the frame shown follows the tilt (scrubTo). Exact seeks are issued one at a time, the next only
 *   after OnSeekComplete and only when the target moved by more than a frame, so a still phone costs nothing.
 */
final class VideoPlayer implements SurfaceTexture.OnFrameAvailableListener {
    /** Longest side of the 2D copy: effects sample blurred mips, so more pixels cost bandwidth without showing. */
    static final int MAX_SIDE=1280;
    /** Suffix of a layer's video key that selects Tilt Scrub, so a mode change recreates the player like new media. */
    static final String SCRUB="#scrub";
    /** Loop crossfade length, the remaining time at which the first frame fully covers the clip, the shortest clip that fades, and how close to zero a latched frame counts as the first. */
    static final long LOOP_FADE_MS=250,LOOP_HOLD_MS=40,LOOP_MIN_MS=1000,FIRST_FRAME_MS=100;
    /** Tilt range (degrees, after sensitivity) mapped onto the whole clip, one seek step, and how long a lost seek blocks the next. */
    static final float SCRUB_DEGREES=40;static final long SCRUB_STEP_MS=33,SEEK_TIMEOUT_MS=1000;
    interface Listener { void onVideoFrame(VideoPlayer p);void onVideoError(VideoPlayer p); }

    final int layer;final String key;final Listener listener;final boolean scrub;
    final float[] matrix=new float[16];
    final SurfaceTexture texture;final Surface surface;final MediaPlayer player;
    int width,height,pending,duration;boolean prepared,wanted,started,released;final int startAt;
    // Loop: last latched position, whether this latch is the clip's first frame, whether GlScene holds a copy of it, the blend.
    int lastPosition=-1;boolean firstFrame,loopCaptured;float loopMix;
    // Scrub: the time the tilt asks for, the last time sought, and when a seek in flight was issued (0: none).
    long scrubTarget=-1,scrubShown=-1,seekSince;

    /** Starts preparing asynchronously; playback begins only when play(true) is called (or was, before prepare finished). */
    VideoPlayer(int layer,String key,File file,int oesTexture,Handler handler,Listener listener,int startAtMs)throws IOException{
        this.layer=layer;this.key=key;this.listener=listener;scrub=isScrubKey(key);startAt=scrub?0:startAtMs;
        texture=new SurfaceTexture(oesTexture);texture.setOnFrameAvailableListener(this,handler);surface=new Surface(texture);player=new MediaPlayer();
        try{
            player.setOnPreparedListener(this::prepared);
            player.setOnVideoSizeChangedListener((mp,w,h)->{if(w>0&&h>0){width=w;height=h;}});
            player.setOnErrorListener((mp,what,extra)->{Log.w("SoloVideo","MediaPlayer error "+what+"/"+extra);failed();return true;});
            player.setOnSeekCompleteListener(mp->seekDone());
            player.setDataSource(file.getPath());player.setSurface(surface);
            // A live wallpaper must never make sound, whatever the clip contains.
            player.setVolume(0,0);player.setLooping(!scrub);player.prepareAsync();
        }catch(IOException|RuntimeException e){release();throw e instanceof IOException?(IOException)e:new IOException(e);}
    }
    static boolean isScrubKey(String key){return key!=null&&key.endsWith(SCRUB);}
    /** The media key without the mode suffix: what the layer's poster was loaded from. */
    String mediaKey(){return scrub?key.substring(0,key.length()-SCRUB.length()):key;}
    void prepared(MediaPlayer mp){
        if(released)return;prepared=true;
        if(width==0){width=mp.getVideoWidth();height=mp.getVideoHeight();}
        if(width<=0||height<=0){failed();return;} // audio-only or undecodable track
        duration=Math.max(0,mp.getDuration());
        // Seeking before start renders that frame while paused, so a scrub layer shows video without ever playing.
        if(scrub)seek(scrubTarget<0?0:scrubTarget);else if(startAt>0)mp.seekTo(startAt);
        apply();
    }
    void failed(){if(!released)listener.onVideoError(this);}
    /** Plays or pauses; cheap and idempotent, so the render loop can call it every frame. A scrub player never plays. */
    void play(boolean value){if(wanted!=value){wanted=value;apply();}}
    void apply(){
        if(released||!prepared||scrub||started==wanted)return;
        try{if(wanted)player.start();else player.pause();started=wanted;}catch(IllegalStateException e){Log.w("SoloVideo","Playback state",e);failed();}
    }
    /** Decoding and delivering frames right now; frames, not the animation clock, then pace the redraws. */
    boolean playing(){return started&&!released;}
    int position(){try{return prepared&&!released?player.getCurrentPosition():0;}catch(IllegalStateException e){return 0;}}

    // ---- Tilt Scrub ----
    /** Render thread, every drawn frame of the visible layer: shows the frame for the smoothed tilt (degrees). */
    void scrubTo(float tiltX){
        if(!scrub||released)return;scrubTarget=scrubTime(tiltX,duration);
        if(!prepared||duration<=0)return;
        if(seekSince!=0&&SystemClock.uptimeMillis()-seekSince<SEEK_TIMEOUT_MS)return; // one seek in flight; seekDone picks up the latest target
        if(Math.abs(scrubTarget-scrubShown)>=SCRUB_STEP_MS)seek(scrubTarget);
    }
    void seek(long ms){
        try{player.seekTo(ms,MediaPlayer.SEEK_CLOSEST);scrubShown=ms;seekSince=Math.max(1,SystemClock.uptimeMillis());}
        catch(IllegalStateException e){Log.w("SoloVideo","Seek",e);failed();}
    }
    void seekDone(){
        if(released||!scrub)return;seekSince=0;
        if(scrubTarget>=0&&Math.abs(scrubTarget-scrubShown)>=SCRUB_STEP_MS)seek(scrubTarget);
    }
    /** Smoothed tilt from -SCRUB_DEGREES..+SCRUB_DEGREES to 0..duration, stopping a frame short of the end (a seek there may show nothing). */
    static long scrubTime(float tiltX,long durationMs){
        if(durationMs<=0||Float.isNaN(tiltX))return 0;
        float t=Math.max(0,Math.min(1,(tiltX+SCRUB_DEGREES)/(2*SCRUB_DEGREES)));
        return Math.min(Math.round(t*(double)durationMs),Math.max(0,durationMs-SCRUB_STEP_MS));
    }

    // ---- loop crossfade ----
    /** Weight of the first frame over the clip at position (ms) of duration: 0 until LOOP_FADE_MS before the end, 1 at LOOP_HOLD_MS, eased. */
    static float loopMix(long positionMs,long durationMs){
        if(durationMs<LOOP_MIN_MS||positionMs<0)return 0;
        float t=(LOOP_FADE_MS-(durationMs-positionMs))/(float)(LOOP_FADE_MS-LOOP_HOLD_MS);t=Math.max(0,Math.min(1,t));
        return t*t*(3-2*t);
    }

    @Override public void onFrameAvailable(SurfaceTexture st){if(released||st!=texture)return;pending++;listener.onVideoFrame(this);}
    /**
     * Render thread with the EGL context current: latches the newest decoded frame and its transform
     * (which carries the clip's rotation metadata). Drains every queued frame so a slow redraw never lags the clip.
     * In loop mode also works out firstFrame (copy this one for the crossfade) and loopMix for the blit.
     */
    boolean latch(){
        if(released||pending==0)return false;
        while(pending>0){texture.updateTexImage();pending--;}
        texture.getTransformMatrix(matrix);
        if(!scrub){
            int at=position();
            // The clip's start: the first frame of a playback from zero, or the first one after the position wrapped.
            firstFrame=at<=FIRST_FRAME_MS&&(lastPosition<0?startAt==0:at<lastPosition);
            loopMix=firstFrame||!loopCaptured?0:loopMix(at,duration);lastPosition=at;
        }
        return width>0&&height>0;
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
