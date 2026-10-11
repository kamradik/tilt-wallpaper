package dev.solotilt.personal;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaKeysTest {
    @Test public void homeKeepsLegacyNamesAndLockGetsSuffix(){
        assertEquals("video",ImageStore.key("video",WallpaperConfig.HOME));
        assertEquals("video_lock",ImageStore.key("video",WallpaperConfig.LOCK));
        assertEquals("revision_lock",ImageStore.key("revision",WallpaperConfig.LOCK));
        assertEquals("video.mp4",ImageStore.videoName(WallpaperConfig.HOME));
        assertEquals("video_lock.mp4",ImageStore.videoName(WallpaperConfig.LOCK));
    }
    @Test public void onlyRevisionsReloadEngines(){
        assertTrue(ImageStore.isRevisionKey(ImageStore.key("revision",WallpaperConfig.HOME)));
        assertTrue(ImageStore.isRevisionKey(ImageStore.key("revision",WallpaperConfig.LOCK)));
        assertTrue(ImageStore.isRevisionKey(ImageStore.key("depth_revision",WallpaperConfig.LOCK)));
        // The kind flag changes in the same edit as the revision; reacting to it too would prepare twice.
        assertFalse(ImageStore.isRevisionKey(ImageStore.key("video",WallpaperConfig.LOCK)));
        assertFalse(ImageStore.isRevisionKey(null));
    }
    @Test public void videoLimits(){
        assertNull(ImageStore.videoProblem(true,180_000,300L*1024*1024));
        assertNotNull(ImageStore.videoProblem(true,180_001,1000));
        assertNotNull(ImageStore.videoProblem(true,1000,300L*1024*1024+1));
        assertNotNull(ImageStore.videoProblem(false,1000,1000));
        assertNotNull(ImageStore.videoProblem(true,0,1000));
    }
    @Test public void videoCopyIsCappedKeepingAspect(){
        assertEquals(1280,VideoPlayer.capped(3840,2160));assertEquals(720,VideoPlayer.capped(2160,3840));
        assertEquals(1280,VideoPlayer.capped(1920,1080));assertEquals(720,VideoPlayer.capped(1080,1920));
        assertEquals(640,VideoPlayer.capped(640,480));assertEquals(480,VideoPlayer.capped(480,640)); // never upscaled
        assertEquals(1,VideoPlayer.capped(0,0));assertEquals(1,VideoPlayer.capped(1,100_000));
        assertEquals(1280,VideoPlayer.capped(1280,1280));
    }
}
