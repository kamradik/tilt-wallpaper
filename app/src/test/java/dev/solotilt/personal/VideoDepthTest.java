package dev.solotilt.personal;

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoDepthTest {
    @Test public void keyframesCoverTheClipAboutOncePerSecond(){
        long[] t=VideoDepth.times(10_000);
        assertEquals(11,t.length);assertEquals(0,t[0]);assertEquals(9950,t[t.length-1]);
        for(int i=1;i<t.length;i++)assertTrue(t[i]>t[i-1]);
        assertEquals(2,VideoDepth.times(300).length);                     // even a short clip gets first and last
        assertEquals(VideoDepth.MAX,VideoDepth.times(180_000).length);    // a long one is capped
        assertArrayEquals(new long[]{0},VideoDepth.times(0));
    }
    @Test public void nearestKeyframe(){
        long[] t={0,1000,2000};
        assertEquals(0,VideoDepth.nearest(t,400));assertEquals(1,VideoDepth.nearest(t,600));assertEquals(2,VideoDepth.nearest(t,9999));
    }
}
