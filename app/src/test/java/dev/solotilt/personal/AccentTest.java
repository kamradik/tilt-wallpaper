package dev.solotilt.personal;

import org.junit.Test;
import static org.junit.Assert.*;

public class AccentTest {
    static float hue(int c){float[] h=new float[3];Accent.hsv(c,h);return h[0];}
    @Test public void aGreyPhotoKeepsTheSystemBlue(){
        int[] grey=new int[400];java.util.Arrays.fill(grey,0xff808080);assertEquals(Ios.BLUE,Accent.pick(grey));
    }
    @Test public void theMostPresentVividHueWins(){
        int[] px=new int[400];
        for(int i=0;i<px.length;i++)px[i]=i<260?0xff1e6a2a:i<300?0xffd02020:0xff202020;  // mostly forest green, a red spot, dark
        float h=hue(Accent.pick(px));assertTrue("green, got "+h,h>100&&h<150);
    }
    @Test public void theTintIsBrightEnoughForTextOnBlack(){
        int[] px=new int[400];java.util.Arrays.fill(px,0xff141e5a);  // a deep navy photo
        int c=Accent.pick(px);float[] v=new float[3];Accent.hsv(c,v);assertEquals(1f,v[2],.01f);assertTrue(v[1]>.5f);
    }
    @Test public void hsvRoundTrips(){
        for(int c:new int[]{0xffff0000,0xff00ff00,0xff0000ff,0xffff9f0a,0xff64d2ff}){float[] h=new float[3];Accent.hsv(c,h);assertEquals(Integer.toHexString(c),c,Accent.rgb(h[0],h[1],h[2]));}
    }
}
