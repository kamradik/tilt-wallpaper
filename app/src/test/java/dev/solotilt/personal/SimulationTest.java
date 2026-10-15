package dev.solotilt.personal;

import java.nio.ByteBuffer;
import java.util.function.Supplier;
import org.junit.Test;
import static org.junit.Assert.*;

public class SimulationTest {
    static final float DT=1/60f;
    static final Supplier<?>[] ALL={SnowGlobe::new,Sand::new,Droplets::new};

    /** Steps with constant gravity until step() reports rest; returns the seconds it took, or fails after the limit. */
    static float settle(Simulation s,float gx,float gy,float limit){
        float t=0;
        while(s.step(DT,gx,gy,0,Float.NaN,Float.NaN)){t+=DT;assertTrue("still moving after "+limit+" s",t<limit);}
        return t;
    }

    @Test public void everySimulationSettlesAndStaysAsleep(){
        for(Supplier<?> f:ALL){
            for(float[] g:new float[][]{{0,-1},{.7f,-.7f},{0,0}}){
                Simulation s=(Simulation)f.get();
                settle(s,g[0],g[1],40);
                assertFalse(s.step(DT,g[0],g[1],0,Float.NaN,Float.NaN));
            }
        }
    }

    @Test public void fillWritesExactlyTheFieldWithMarker(){
        for(Supplier<?> f:ALL){
            Simulation s=(Simulation)f.get();s.step(DT,0,-1,0,Float.NaN,Float.NaN);
            ByteBuffer b=ByteBuffer.allocateDirect(Simulation.W*Simulation.H*4);
            s.fill(b);assertEquals(Simulation.W*Simulation.H*4,b.position());
            // Writing twice from a dirty buffer gives the same bytes: fill is a pure function of the state.
            ByteBuffer again=ByteBuffer.allocateDirect(b.capacity());again.put(0,(byte)7);s.fill(again);
            b.position(0);again.position(0);assertEquals(b,again);
            int m=(Simulation.W*Simulation.H-1)*4;
            assertArrayEquals(new byte[]{0,0,0,(byte)255},new byte[]{b.get(m),b.get(m+1),b.get(m+2),b.get(m+3)});
        }
    }

    @Test public void survivesNaNAndWildInput(){
        for(Supplier<?> f:ALL){
            Simulation s=(Simulation)f.get();
            s.step(DT,Float.NaN,Float.NaN,Float.NaN,Float.NaN,Float.NaN);
            s.step(DT,0,-1,1,Float.NaN,.5f);
            s.step(DT,0,-1,1,.5f,Float.NaN);
            s.step(.1f,5,-5,1,-3,9);
            s.step(0,0,-1,0,.5f,.5f);
            s.step(Float.NaN,0,-1,0,.5f,.5f);
            for(int i=0;i<30;i++)s.step(DT,0,-1,1,.5f,.5f);
            s.fill(ByteBuffer.allocateDirect(Simulation.W*Simulation.H*4));
        }
    }

    @Test public void snowConservesFlakesAndPilesOnTheGravitySide(){
        SnowGlobe s=new SnowGlobe();
        settle(s,1,0,40);
        assertEquals(SnowGlobe.N,s.settledCount());
        float mean=0;for(int i=0;i<SnowGlobe.N;i++)mean+=s.x[i];mean/=SnowGlobe.N;
        assertTrue("snow should heap on the right, mean x "+mean,mean>Simulation.W*.85f);
        int piled=0;for(byte p:s.pile)piled+=p;assertEquals(SnowGlobe.N,piled);
        // Turn the phone upright: the heap slides to the bottom.
        settle(s,0,-1,40);
        mean=0;for(int i=0;i<SnowGlobe.N;i++)mean+=s.y[i];mean/=SnowGlobe.N;
        assertTrue("snow should heap at the bottom, mean y "+mean,mean<Simulation.H*.1f);
        // A hard shake throws flakes back into the air.
        for(int i=0;i<10;i++)s.step(DT,0,-1,1,Float.NaN,Float.NaN);
        assertTrue(s.settledCount()<SnowGlobe.N/2);
        assertTrue(s.step(DT,0,-1,0,Float.NaN,Float.NaN));
    }

    @Test public void sandConservesGrainsPoursTowardGravityAndDigs(){
        Sand s=new Sand();int grains=s.grains();
        assertEquals(Simulation.W*Sand.FILL_ROWS,grains);
        settle(s,-1,0,40);
        assertEquals(grains,s.grains());
        // The slab collapses against the left wall: the left half ends up holding far more sand than the right.
        int left=0;for(int c=0;c<Simulation.W*Simulation.H;c++)if(s.grain[c]&&c%Simulation.W<Simulation.W/2)left++;
        assertTrue("sand should pour to the left, left half "+left+" of "+grains,left>grains*.55f);
        assertTrue(s.grain[(Simulation.H*3/5)*Simulation.W]);// climbs high up the left wall
        // Rest at roughly the angle of repose: no grain hangs over an empty cell toward gravity.
        for(int y=0;y<Simulation.H;y++)for(int x=1;x<Simulation.W;x++)
            if(s.grain[y*Simulation.W+x])assertTrue(s.grain[y*Simulation.W+x-1]);
        // Lying flat, a touch digs a hole and keeps every grain.
        assertFalse(s.step(DT,0,0,0,Float.NaN,Float.NaN));
        // Touch just below the heap's surface: grains there are pushed out over the surface, leaving a pit.
        int cx=10,top=0;for(int y=0;y<Simulation.H;y++)if(s.grain[y*Simulation.W+cx])top=y;
        int cy=top-2;float tx=(cx+.5f)/Simulation.W,ty=(cy+.5f)/Simulation.H;
        assertTrue(s.grain[cy*Simulation.W+cx]);
        assertTrue(s.step(DT,0,0,0,tx,ty));
        assertFalse(s.grain[cy*Simulation.W+cx]);
        assertEquals(grains,s.grains());
    }

    @Test public void heavyDropsRunDownAndLightOnesCling(){
        Droplets s=new Droplets();s.count=0;
        s.add(20,120,3);s.add(50,120,.6f);
        float t=0;while(t<1.5f){s.step(DT,0,-1,0,Float.NaN,Float.NaN);t+=DT;}
        int big=0,small=-1;for(int i=0;i<s.count;i++){if(s.r[i]>s.r[big])big=i;if(Math.abs(s.x[i]-50)<.01f&&Math.abs(s.y[i]-120)<.01f)small=i;}
        assertTrue("the light drop clings",small>=0);
        assertTrue("the heavy drop runs down, y "+s.y[big],s.y[big]<110);
        // Sideways gravity sends a heavy drop sideways.
        s=new Droplets();s.count=0;s.add(30,80,3);
        for(int i=0;i<30;i++)s.step(DT,1,0,0,Float.NaN,Float.NaN);
        assertTrue(s.x[0]>33);
        // Two touching drops become one with the same area.
        s=new Droplets();s.count=0;s.add(30,80,1);s.add(31,80,1);
        s.step(DT,0,0,0,Float.NaN,Float.NaN);
        assertEquals(1,s.count);assertEquals(Math.sqrt(2),s.r[0],1e-4);
    }
}
