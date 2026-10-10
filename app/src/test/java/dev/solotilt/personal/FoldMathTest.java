package dev.solotilt.personal;

import org.junit.Test;

public class FoldMathTest {
    static void near(float a,float b){if(Math.abs(a-b)>0.0001f)throw new AssertionError(a+" != "+b);}
    @Test public void projectionGravityAndAngleSweep(){
        float[] p=FoldMath.projection(0,0,72,.45f);near(p[0],1);near(p[4],1);near(p[8],1);near(p[14],0);
        float[] a=FoldMath.projection(60,20,72,.45f),b=FoldMath.projection(-60,-20,72,.45f);
        near(a[0],b[0]);near(a[2],-b[2]);near(a[6],-b[6]);near(a[8],b[8]);near(a[12],-b[12]);near(a[13],-b[13]);near(a[14],b[14]);
        near(a[0],(float)Math.cos(Math.PI/4));near(a[8],1-(float)Math.sin(Math.PI/4)*36/320);
        float[] flat=FoldMath.gravity(0,0,9.81f,0);near(flat[0],0);near(flat[1],0);
        float[] reused=new float[15];FoldMath.projection(60,20,72,.45f,reused);for(int i=0;i<15;i++)near(reused[i],a[i]);
        float[] sensorBuffer=new float[2];if(!FoldMath.gravity(9.81f,0,0,0,sensorBuffer))throw new AssertionError();near(sensorBuffer[0],90);
        float[] side=FoldMath.gravity(9.81f,0,0,0);near(side[0],90);near(side[1],0);
        float[] rotate=FoldMath.gravity(9.81f,0,0,1);near(rotate[0],0);near(rotate[1],90);
        float[] inverted=FoldMath.gravity(0,0,-9.81f,0);near(inverted[0],0);near(inverted[1],0);
        if(FoldMath.gravity(Float.NaN,0,0,0)!=null)throw new AssertionError("NaN accepted");
        for(int x=-120;x<=120;x++)for(int y=-120;y<=120;y+=20){p=FoldMath.projection(x,y,72,.45f);for(float f:p)if(!Float.isFinite(f))throw new AssertionError("Nonfinite");if(p[8]-.5f*Math.abs(p[6])<=0)throw new AssertionError("Invalid ray depth");}
        boolean caught=false;try{FoldMath.projection(0,0,0,1);}catch(IllegalArgumentException e){caught=true;}if(!caught)throw new AssertionError("Invalid dimensions accepted");
    }
}
