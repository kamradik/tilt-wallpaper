package dev.solotilt.personal;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.util.Log;
import java.io.*;
import java.nio.ByteBuffer;

/**
 * Turns a picked clip into the wallpaper file without decoding it: MediaExtractor reads the compressed video samples,
 * MediaMuxer writes them into a new container. Only the video track is copied (a wallpaper is silent, so the audio is
 * dead weight), the copy can be cut to a window, and the rotation tag is carried over. Formats the muxer cannot write
 * fall back to a plain copy of the whole file (audio included; the player is muted anyway). Pure helpers are unit-tested.
 */
final class VideoImport {
    static final long MAX_US=ImageStore.MAX_VIDEO_MS*1000,MIN_US=500_000;
    /** What remux does with one sample (decode order). */
    static final int KEEP=0,SKIP=1,STOP=2;

    /**
     * Writes [startUs,endUs] of source's video track to target (overwritten) and returns target; endUs<=0 means to the
     * end. The cut begins at the sync sample at or before startUs (frames before it cannot be decoded), so the clip may
     * start up to one keyframe interval early. Refuses (IOException with a readable message) a source without a video
     * track, a window longer than 3 minutes and an output over 300 MB. Blocking I/O: call off the main thread.
     */
    static File remux(Context c,Uri source,long startUs,long endUs,File target)throws IOException{
        MediaExtractor extractor=new MediaExtractor();
        try{
            try{extractor.setDataSource(c,source,null);}
            catch(IOException|RuntimeException e){Log.w("SoloVideo","Extractor cannot open the clip",e);return copyWhole(c,source,startUs,endUs,-1,target,e);}
            int track=-1;MediaFormat format=null;
            for(int i=0;i<extractor.getTrackCount();i++){MediaFormat f=extractor.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("video/")){track=i;format=f;break;}}
            if(format==null)throw new IOException(ImageStore.videoProblem(false,1,1));
            long duration=format.containsKey(MediaFormat.KEY_DURATION)?format.getLong(MediaFormat.KEY_DURATION):0;
            if(duration<=0)duration=metadataDurationUs(c,source);
            long[] window=window(startUs,endUs,duration);
            String problem=lengthProblem(window,duration);if(problem!=null)throw new IOException(problem);
            try{return mux(extractor,track,format,rotation(c,source,format),window,target);}
            catch(IllegalArgumentException|IllegalStateException e){
                // The muxer refused the format (or died mid-way): only a whole-file copy keeps the clip usable.
                Log.w("SoloVideo","Muxer rejected the clip",e);return copyWhole(c,source,startUs,endUs,duration,target,e);
            }
        }finally{extractor.release();}
    }

    static File mux(MediaExtractor extractor,int track,MediaFormat format,int rotation,long[] window,File target)throws IOException{
        String mime=format.getString(MediaFormat.KEY_MIME);boolean webm="video/x-vnd.on2.vp8".equals(mime)||"video/x-vnd.on2.vp9".equals(mime);
        extractor.selectTrack(track);extractor.seekTo(window[0],MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        int capacity=format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)?Math.max(64*1024,format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)):8*1024*1024;
        ByteBuffer buffer=ByteBuffer.allocateDirect(capacity);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        MediaMuxer muxer=new MediaMuxer(target.getPath(),webm?MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM:MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        boolean started=false;
        try{
            // One source of truth for the tag: the hint, not a track key some writers would read as well.
            format.removeKey(MediaFormat.KEY_ROTATION);if(!webm&&rotation!=0)muxer.setOrientationHint(rotation);
            int out=muxer.addTrack(format);muxer.start();started=true;
            long origin=-1,bytes=0,last=0;int samples=0;
            while(true){
                long time=extractor.getSampleTime();if(time<0)break;
                boolean sync=(extractor.getSampleFlags()&MediaExtractor.SAMPLE_FLAG_SYNC)!=0;
                if(origin<0){if(!sync){extractor.advance();continue;}origin=time;} // the window starts on a decodable frame
                int action=sample(time,origin,window[1]);if(action==STOP)break;
                if(action==KEEP){
                    int size;
                    while(true){
                        try{buffer.clear();size=extractor.readSampleData(buffer,0);break;}
                        catch(IllegalArgumentException small){if(buffer.capacity()>=64*1024*1024)throw new IOException("The video cannot be read",small);buffer=ByteBuffer.allocateDirect(buffer.capacity()*2);}
                    }
                    if(size<0)break;
                    bytes+=size;if(bytes>ImageStore.MAX_VIDEO_BYTES)throw new IOException(ImageStore.videoProblem(true,1,bytes));
                    info.set(0,size,time-origin,sync?MediaCodec.BUFFER_FLAG_KEY_FRAME:0);
                    muxer.writeSampleData(out,buffer,info);samples++;last=Math.max(last,time-origin);
                }
                if(!extractor.advance())break;
            }
            if(samples==0)throw new IOException("The video cannot be read");
            muxer.stop();started=false;
            Log.i("SoloVideo","Remuxed "+samples+" samples, "+(last/1000)+" ms, "+(bytes>>10)+" KiB from "+(origin/1000)+" ms");
            return target;
        }finally{
            if(started)try{muxer.stop();}catch(RuntimeException ignored){}
            try{muxer.release();}catch(RuntimeException ignored){}
        }
    }

    /** The muxer cannot help: copy the whole file as it is, which is only right when no cut was asked for. */
    static File copyWhole(Context c,Uri source,long startUs,long endUs,long durationUs,File target,Exception cause)throws IOException{
        if(!wholeClip(startUs,endUs,durationUs))throw new IOException("This video format cannot be trimmed. Import it whole.",cause);
        long bytes=0;
        try(InputStream in=c.getContentResolver().openInputStream(source);OutputStream out=new FileOutputStream(target)){
            if(in==null)throw new IOException("The video cannot be opened");byte[] buffer=new byte[65536];int n;
            while((n=in.read(buffer))!=-1){bytes+=n;if(bytes>ImageStore.MAX_VIDEO_BYTES)throw new IOException(ImageStore.videoProblem(true,1,bytes));out.write(buffer,0,n);}
        }
        try(MediaMetadataRetriever r=new MediaMetadataRetriever()){
            r.setDataSource(target.getPath());
            String problem=ImageStore.videoProblem("yes".equals(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)),ImageStore.number(r,MediaMetadataRetriever.METADATA_KEY_DURATION),bytes);
            if(problem!=null)throw new IOException(problem);
        }catch(RuntimeException e){throw new IOException("This video format is not supported",e);}
        return target;
    }

    static long metadataDurationUs(Context c,Uri source){
        try(MediaMetadataRetriever r=new MediaMetadataRetriever()){r.setDataSource(c,source);return ImageStore.number(r,MediaMetadataRetriever.METADATA_KEY_DURATION)*1000;}
        catch(IOException|RuntimeException e){return 0;}
    }
    /** Clockwise display rotation of the track: the extractor's format when it has it, else the container metadata. */
    static int rotation(Context c,Uri source,MediaFormat format){
        if(format.containsKey(MediaFormat.KEY_ROTATION))return normalizeRotation(format.getInteger(MediaFormat.KEY_ROTATION));
        try(MediaMetadataRetriever r=new MediaMetadataRetriever()){r.setDataSource(c,source);return normalizeRotation(ImageStore.number(r,MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));}
        catch(IOException|RuntimeException e){return 0;}
    }

    // ---- pure helpers ----
    /** The muxer accepts only 0, 90, 180 and 270: any angle is wrapped and rounded to the nearest quarter turn. */
    static int normalizeRotation(long degrees){return (int)(Math.floorMod(Math.round(degrees/90.0),4L)*90);}
    /**
     * The window remux copies, {start,end} in µs: start clamped into the clip, end<=0 or past the clip meaning "to the
     * end" (Long.MAX_VALUE, so the last frame is never lost to a rounded duration), and at least MIN_US long when the clip
     * allows. An unknown duration (<=0) leaves start as asked and the end open.
     */
    static long[] window(long startUs,long endUs,long durationUs){
        long start=Math.max(0,startUs);
        if(durationUs<=0)return new long[]{start,endUs<=start?Long.MAX_VALUE:endUs};
        start=Math.min(start,Math.max(0,durationUs-Math.min(MIN_US,durationUs)));
        long end=endUs<=0||endUs>=durationUs?Long.MAX_VALUE:Math.max(endUs,Math.min(durationUs,start+MIN_US));
        if(end>=durationUs)end=Long.MAX_VALUE;
        return new long[]{start,end};
    }
    /** Length of a window from window(); the open end counts to the clip's end (0 when that is unknown). */
    static long length(long[] window,long durationUs){
        long end=window[1]==Long.MAX_VALUE?durationUs:window[1];return Math.max(0,end-window[0]);
    }
    /** Why a window cannot become a wallpaper, or null. An unknown length passes: the muxer stops at 300 MB anyway. */
    static String lengthProblem(long[] window,long durationUs){
        if(durationUs<=0&&window[1]==Long.MAX_VALUE)return null;
        return length(window,durationUs)>MAX_US?ImageStore.videoProblem(true,ImageStore.MAX_VIDEO_MS+1,1):null;
    }
    /**
     * Fate of one sample in decode order, origin being the time of the sync sample the copy starts on. A sample shown
     * before the origin (an open GOP's leading B-frame) refers to the previous GOP and is skipped. The copy stops at the
     * first sample shown at or after the end: every sample before it in decode order is kept, and since a frame only
     * references frames decoded earlier, nothing kept can depend on a dropped one (a B-frame or two before the end may go).
     */
    static int sample(long timeUs,long originUs,long endUs){return timeUs>=endUs?STOP:timeUs<originUs?SKIP:KEEP;}
    /** True when the request is the whole clip, so a plain copy honours it. */
    static boolean wholeClip(long startUs,long endUs,long durationUs){return startUs<=0&&(endUs<=0||durationUs>0&&endUs>=durationUs);}

    // ---- trim handles (milliseconds, the trimming screen's unit) ----
    /**
     * Moves one trim handle to valueMs and returns {start,end}: the window stays inside [0,duration], at least minMs
     * long (or the whole clip if shorter) and at most maxMs; dragging past maxMs pushes the other handle along.
     */
    static long[] drag(boolean startHandle,long valueMs,long startMs,long endMs,long durationMs,long minMs,long maxMs){
        long d=Math.max(0,durationMs),min=Math.min(minMs,d),s=Math.max(0,Math.min(startMs,d)),e=Math.max(s,Math.min(endMs,d));
        if(startHandle){s=Math.max(0,Math.min(valueMs,e-min));if(e-s>maxMs)e=s+maxMs;}
        else{e=Math.min(d,Math.max(valueMs,s+min));if(e-s>maxMs)s=e-maxMs;}
        return new long[]{s,e};
    }
    /** "m:ss" for a length or position, rounded to the nearest second. */
    static String clock(long ms){long s=Math.max(0,Math.round(ms/1000.0));return s/60+":"+(s%60<10?"0":"")+s%60;}

    private VideoImport(){}
}
