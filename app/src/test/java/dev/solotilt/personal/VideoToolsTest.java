package dev.solotilt.personal;

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoToolsTest {
    static final long MAX=ImageStore.MAX_VIDEO_MS,S=1_000_000;

    @Test public void windowClampsIntoTheClip(){
        assertArrayEquals(new long[]{0,Long.MAX_VALUE},VideoImport.window(0,-1,10*S));          // whole clip
        assertArrayEquals(new long[]{2*S,5*S},VideoImport.window(2*S,5*S,10*S));
        assertArrayEquals(new long[]{0,5*S},VideoImport.window(-3*S,5*S,10*S));                 // negative start
        assertArrayEquals(new long[]{2*S,Long.MAX_VALUE},VideoImport.window(2*S,10*S,10*S));     // end at the clip's end stays open
        assertArrayEquals(new long[]{2*S,Long.MAX_VALUE},VideoImport.window(2*S,99*S,10*S));
        assertArrayEquals(new long[]{2*S,2*S+VideoImport.MIN_US},VideoImport.window(2*S,2*S+1,10*S)); // too short grows
        assertArrayEquals(new long[]{10*S-VideoImport.MIN_US,Long.MAX_VALUE},VideoImport.window(20*S,-1,10*S)); // start past the end
        assertArrayEquals(new long[]{0,Long.MAX_VALUE},VideoImport.window(5,-1,200_000));       // clip shorter than the minimum
        assertArrayEquals(new long[]{3*S,Long.MAX_VALUE},VideoImport.window(3*S,-1,0));          // unknown duration
        assertArrayEquals(new long[]{3*S,7*S},VideoImport.window(3*S,7*S,0));
    }
    @Test public void windowLengthIsLimitedToThreeMinutes(){
        long dur=5*60*S;
        assertNotNull("whole 5 min clip is refused, not cut",VideoImport.lengthProblem(VideoImport.window(0,-1,dur),dur));
        assertNull(VideoImport.lengthProblem(VideoImport.window(60*S,60*S+MAX*1000,dur),dur));
        assertNotNull(VideoImport.lengthProblem(VideoImport.window(60*S,60*S+MAX*1000+1,dur),dur));
        assertNull(VideoImport.lengthProblem(VideoImport.window(dur-MAX*1000,-1,dur),dur)); // last 3 minutes, open end
        assertNull(VideoImport.lengthProblem(VideoImport.window(0,-1,0),0));                // unknown: the size cap guards it
        assertEquals(3*S,VideoImport.length(new long[]{2*S,Long.MAX_VALUE},5*S));
    }
    @Test public void samplesFollowTheSyncFrameAndStopAtTheEnd(){
        long origin=4*S,end=9*S;
        assertEquals(VideoImport.KEEP,VideoImport.sample(origin,origin,end));          // the sync frame the cut starts on
        assertEquals(VideoImport.KEEP,VideoImport.sample(origin+33_000,origin,end));
        assertEquals(VideoImport.SKIP,VideoImport.sample(origin-33_000,origin,end));   // open-GOP leading B-frame
        assertEquals(VideoImport.STOP,VideoImport.sample(end,origin,end));
        assertEquals(VideoImport.KEEP,VideoImport.sample(end-1,origin,end));
        assertEquals(VideoImport.KEEP,VideoImport.sample(Long.MAX_VALUE-1,0,Long.MAX_VALUE)); // open end copies everything
        assertTrue(VideoImport.wholeClip(0,-1,10*S));assertTrue(VideoImport.wholeClip(0,10*S,10*S));assertTrue(VideoImport.wholeClip(0,-1,-1));
        assertFalse(VideoImport.wholeClip(1,-1,10*S));assertFalse(VideoImport.wholeClip(0,5*S,10*S));assertFalse(VideoImport.wholeClip(0,5*S,-1));
    }
    @Test public void rotationIsAQuarterTurn(){
        assertEquals(0,VideoImport.normalizeRotation(0));assertEquals(90,VideoImport.normalizeRotation(90));
        assertEquals(270,VideoImport.normalizeRotation(-90));assertEquals(180,VideoImport.normalizeRotation(540));
        assertEquals(90,VideoImport.normalizeRotation(89));assertEquals(0,VideoImport.normalizeRotation(360));
    }
    @Test public void handlesKeepMinAndMaxAndPushEachOther(){
        long dur=10*60_000;
        assertArrayEquals(new long[]{5000,60_000},VideoImport.drag(true,5000,0,60_000,dur,1000,MAX));
        assertArrayEquals(new long[]{59_000,60_000},VideoImport.drag(true,70_000,0,60_000,dur,1000,MAX));   // min length
        assertArrayEquals(new long[]{0,60_000},VideoImport.drag(true,-500,1000,60_000,dur,1000,MAX));
        assertArrayEquals(new long[]{10_000,10_000+MAX},VideoImport.drag(true,10_000,200_000,300_000,dur,1000,MAX)); // pushes end back
        assertArrayEquals(new long[]{400_000-MAX,400_000},VideoImport.drag(false,400_000,0,100_000,dur,1000,MAX));   // pulls start along
        assertArrayEquals(new long[]{0,dur},VideoImport.drag(false,dur+9,0,0,dur,1000,dur));
        assertArrayEquals(new long[]{20_000,21_000},VideoImport.drag(false,0,20_000,50_000,dur,1000,MAX));
        assertArrayEquals(new long[]{0,400},VideoImport.drag(false,100,0,400,400,1000,MAX));                        // clip shorter than min
        assertEquals("0:00",VideoImport.clock(0));assertEquals("0:05",VideoImport.clock(4600));assertEquals("3:00",VideoImport.clock(MAX));assertEquals("12:34",VideoImport.clock(754_000));
    }
    @Test public void tiltMapsOntoTheClip(){
        assertEquals(0,VideoPlayer.scrubTime(-40,10_000));assertEquals(0,VideoPlayer.scrubTime(-90,10_000));
        assertEquals(5000,VideoPlayer.scrubTime(0,10_000));assertEquals(7500,VideoPlayer.scrubTime(20,10_000));
        assertEquals(10_000-VideoPlayer.SCRUB_STEP_MS,VideoPlayer.scrubTime(40,10_000));     // never seeks past the last frame
        assertEquals(10_000-VideoPlayer.SCRUB_STEP_MS,VideoPlayer.scrubTime(80,10_000));
        assertEquals(0,VideoPlayer.scrubTime(10,0));assertEquals(0,VideoPlayer.scrubTime(Float.NaN,10_000));
        assertEquals(0,VideoPlayer.scrubTime(30,20));                                          // shorter than a step
    }
    @Test public void loopFadesIntoTheFirstFrame(){
        assertEquals(0,VideoPlayer.loopMix(0,10_000),0);assertEquals(0,VideoPlayer.loopMix(9_750,10_000),0);
        float mid=VideoPlayer.loopMix(9_855,10_000);assertTrue(mid>0&&mid<1);
        assertEquals(1,VideoPlayer.loopMix(9_960,10_000),0);assertEquals(1,VideoPlayer.loopMix(10_000,10_000),0);
        assertTrue(VideoPlayer.loopMix(9_900,10_000)>VideoPlayer.loopMix(9_800,10_000));
        assertEquals(0,VideoPlayer.loopMix(900,950),0);   // too short to fade
        assertEquals(0,VideoPlayer.loopMix(-1,10_000),0);
    }
    @Test public void scrubIsAModeOfTheVideoKey(){
        assertTrue(VideoPlayer.isScrubKey("custom-12"+VideoPlayer.SCRUB));assertFalse(VideoPlayer.isScrubKey("custom-12"));assertFalse(VideoPlayer.isScrubKey(null));
        assertTrue(ImageStore.isRevisionKey(ImageStore.key("video_revision",WallpaperConfig.LOCK)));
        assertEquals("scrub_lock",ImageStore.key("scrub",WallpaperConfig.LOCK));
    }

    @Test public void transcodeSizeCapsTheLongSideKeepsAspectAndStaysEven(){
        assertArrayEquals(new int[]{1280,720},VideoTranscode.outputSize(3840,2160,1280));
        assertArrayEquals(new int[]{720,1280},VideoTranscode.outputSize(2160,3840,1280));
        assertArrayEquals(new int[]{640,360},VideoTranscode.outputSize(640,360,1280));  // never upscales
        int[] odd=VideoTranscode.outputSize(1921,1081,1280);
        assertEquals(0,odd[0]%2);assertEquals(0,odd[1]%2);assertTrue(odd[0]<=1280);
        assertArrayEquals(new int[]{2,2},VideoTranscode.outputSize(1,1,1280));
    }
    @Test public void transcodeBitRateKeepsThreeMinutesUnderTheCap(){
        int r=VideoTranscode.bitRate(1280,720,60);
        assertTrue(r<=12_000_000&&r>=2_000_000);
        assertTrue("3 min at the highest rate fits",12_000_000L/8*180<ImageStore.MAX_VIDEO_BYTES);
        assertEquals(2_000_000,VideoTranscode.bitRate(320,240,15));
    }
}
