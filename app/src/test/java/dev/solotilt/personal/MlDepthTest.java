package dev.solotilt.personal;

import org.junit.Test;
import static org.junit.Assert.*;

public class MlDepthTest {
    @Test public void inputKeepsAspectOnPatchMultiples(){
        int[] s=MlDepth.inputSize(4000,3000);
        assertEquals(MlDepth.LONG_SIDE,s[0]);assertEquals(0,s[0]%MlDepth.PATCH);assertEquals(0,s[1]%MlDepth.PATCH);
        assertEquals(.75f,s[1]/(float)s[0],.03f);
        int[] p=MlDepth.inputSize(1080,2400);assertEquals(MlDepth.LONG_SIDE,p[1]);assertEquals(0,p[0]%MlDepth.PATCH);
        assertArrayEquals(new int[]{MlDepth.LONG_SIDE,14},MlDepth.inputSize(5000,10));
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
    @Test public void mirrorTwiceIsIdentity(){
        int[] a={1,2,3,4,5,6};assertArrayEquals(new int[]{3,2,1,6,5,4},MlDepth.mirror(a,3,2));
        assertArrayEquals(a,MlDepth.mirror(MlDepth.mirror(a,3,2),3,2));
        float[] f={1,2,3,4};assertArrayEquals(new float[]{2,1,4,3},MlDepth.mirror(f,2,2),0);
    }
    @Test public void boxIsTheWindowMeanClippedAtBorders(){
        float[] a=new float[25];a[12]=25;  // 5x5, one spike in the middle
        float[] m=MlDepth.box(a,5,5,1);
        assertEquals(25/9f,m[12],1e-5);assertEquals(25/9f,m[6],1e-5);assertEquals(0,m[0],1e-6);
        float[] ones=new float[25];java.util.Arrays.fill(ones,1);for(float v:MlDepth.box(ones,5,5,2))assertEquals(1,v,1e-6);
    }
    @Test public void resizeKeepsConstantsAndCorners(){
        float[] r=MlDepth.resize(new float[]{0,1,0,1},2,2,4,4);
        assertEquals(0,r[0],1e-6);assertEquals(1,r[3],1e-6);assertEquals(.5f,(r[1]+r[2])/2,1e-6);
    }
    /** A blurry depth step under a sharp photo edge comes out much sharper: the guided filter snaps it to the photo. */
    @Test public void guidedFilterSnapsDepthEdgesToThePhoto(){
        // The app's own window and regularization; a depth ramp 48..80 straddles a sharp photo edge at 64.
        int w=160,h=24;float[] guide=new float[w*h],depth=new float[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){guide[y*w+x]=x<64?.1f:.9f;depth[y*w+x]=Math.max(0,Math.min(1,(x-48)/32f));}
        float[] q=MlDepth.guided(guide,depth,w,h,MlDepth.RADIUS,MlDepth.EPS);
        int row=12*w;
        float before=depth[row+66]-depth[row+61],after=q[row+66]-q[row+61];
        assertTrue("the step across the photo edge should grow: "+before+" -> "+after,after>before*1.3f);
        // Far from the edge, flat photo regions keep their depth (no texture is invented).
        assertEquals(depth[row+10],q[row+10],.05f);assertEquals(depth[row+150],q[row+150],.05f);
        for(float v:q)assertTrue(v>=0&&v<=1);
    }
    @Test public void refinedMapNeverExceedsThePhoto(){
        assertArrayEquals(new int[]{1024,768},MlDepth.refinedSize(4000,3000));
        assertArrayEquals(new int[]{640,480},MlDepth.refinedSize(640,480));
    }
    @Test public void subjectMaskNeedsAClearSubject(){
        int w=40,h=40;float[] raw=new float[w*h],guide=new float[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){boolean in=Math.hypot(x-20,y-20)<9;raw[y*w+x]=in?5:-5;guide[y*w+x]=in?.8f:.2f;}
        float[] m=MlDepth.subjectMask(raw,w,h,guide,w,h);
        assertNotNull(m);assertEquals(1,m[20*w+20],.05);assertEquals(0,m[2*w+2],.05);
        assertNull("a flat output has no subject",MlDepth.subjectMask(new float[w*h],w,h,guide,w,h));
        float[] all=new float[w*h];java.util.Arrays.fill(all,3);all[0]=-3;
        assertNull("a subject filling the frame is not a subject",MlDepth.subjectMask(all,w,h,new float[w*h],w,h));
    }
    @Test public void layerFlattensTheSubjectInFrontOfItsSurroundings(){
        float[] depth={.2f,.2f,.5f,.7f},mask={0,0,1,1};
        MlDepth.layer(depth,mask);
        assertEquals(.2f,depth[0],1e-6);  // background untouched
        assertTrue(depth[2]>.2f&&depth[3]>.2f);
        assertTrue("relief is kept small",Math.abs(depth[3]-depth[2])<.1f);
    }
    @Test public void maskIsPackedInGreenAndMarkedInBlue(){
        int[] px=MlDepth.withMask(new float[]{1,0},new float[]{0,1});
        assertEquals(0xffff00ff,px[0]);assertEquals(0xff00ffff,px[1]);
    }
    @Test public void aSubjectMustStandInFront(){
        float[] mask={1,1,0,0};
        assertTrue(MlDepth.standsOut(new float[]{.8f,.8f,.3f,.3f},mask));
        assertFalse("not in front",MlDepth.standsOut(new float[]{.3f,.3f,.8f,.8f},mask));
        assertFalse("level with the rest",MlDepth.standsOut(new float[]{.5f,.5f,.48f,.48f},mask));
    }
}
