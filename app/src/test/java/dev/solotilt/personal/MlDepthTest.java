package dev.solotilt.personal;

import org.junit.Test;
import static org.junit.Assert.*;

public class MlDepthTest {
    @Test public void inputKeepsAspectOnPatchMultiples(){
        int[] s=MlDepth.inputSize(4000,3000);
        assertEquals(518,s[0]);assertEquals(0,s[1]%MlDepth.PATCH);assertEquals(392,s[1]);
        int[] p=MlDepth.inputSize(1080,2400);assertEquals(518,p[1]);assertEquals(0,p[0]%MlDepth.PATCH);
        assertArrayEquals(new int[]{518,14},MlDepth.inputSize(5000,10));
    }
    @Test public void tensorIsNormalizedPlanarRgb(){
        float[] t=MlDepth.tensor(new int[]{0xffffffff,0xff000000},2,1);
        assertEquals((1-.485f)/.229f,t[0],1e-4);assertEquals(-.485f/.229f,t[1],1e-4);  // R plane
        assertEquals((1-.456f)/.224f,t[2],1e-4);assertEquals(-.406f/.225f,t[5],1e-4);  // G, B planes
    }
    @Test public void greyStretchesBetweenPercentilesWhiteNear(){
        float[] d=new float[1000];for(int i=0;i<d.length;i++)d[i]=i;d[999]=1e9f;  // one outlier
        int[] g=MlDepth.grey(d);
        assertEquals(0,g[0]&255);assertEquals(255,g[999]&255);
        assertTrue("the outlier must not flatten the rest",(g[500]&255)>110&&(g[500]&255)<145);
    }
}
