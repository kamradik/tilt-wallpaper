package dev.solotilt.personal;

import org.junit.Test;
import static org.junit.Assert.*;

public class SeriesTest {
    @Test public void framesHoldEachPhotoAndDissolveBetweenThem(){
        int n=3,total=SeriesImport.frames(n);
        assertEquals(3*SeriesImport.HOLD+2*SeriesImport.BLEND,total);
        assertArrayEquals(new float[]{0,0,0},SeriesImport.mix(0,n),0);                         // first photo held
        float[] mid=SeriesImport.mix(SeriesImport.HOLD+SeriesImport.BLEND/2,n);
        assertEquals(0,mid[0],0);assertEquals(1,mid[1],0);assertTrue(mid[2]>.3f&&mid[2]<.7f);    // halfway into the second
        float[] last=SeriesImport.mix(total-1,n);assertEquals(2,last[0],0);assertEquals(0,last[2],0);  // last photo held
        float previous=-1;int span=SeriesImport.HOLD+SeriesImport.BLEND;
        for(int f=0;f<total;f++){float[] m=SeriesImport.mix(f,n);float pos=m[0]+(m[1]-m[0])*m[2];assertTrue("monotonic at "+f,pos>=previous-1e-6);previous=pos;}
        assertEquals(2,previous,1e-6);
    }
}
