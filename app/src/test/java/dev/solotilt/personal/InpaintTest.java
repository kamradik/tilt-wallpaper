package dev.solotilt.personal;

import org.junit.Test;
import static org.junit.Assert.*;

public class InpaintTest {
    @Test public void holeCoversTheSubjectPlusAMargin(){
        int w=40,h=40;float[] m=new float[w*h];
        for(int y=18;y<22;y++)for(int x=18;x<22;x++)m[y*w+x]=1;      // a 4x4 subject
        byte[] keep=Inpaint.hole(m,w,h,5);
        assertEquals(0,keep[20*w+20]);                                 // the subject is painted over
        assertEquals(0,keep[20*w+25]);assertEquals(0,keep[14*w+20]);   // and the margin around it
        assertEquals((byte)255,keep[20*w+28]);assertEquals((byte)255,keep[2*w+2]);  // the rest is kept
    }
    @Test public void noSubjectNoHole(){
        byte[] keep=Inpaint.hole(new float[100],10,10,3);
        for(byte b:keep)assertEquals((byte)255,b);
    }
}
