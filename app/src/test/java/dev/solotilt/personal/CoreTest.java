package dev.solotilt.personal;

import org.junit.Test;

public class CoreTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Test public void configBoundsRoundtripAndSensorMailbox()throws Exception{
        WallpaperConfig c=WallpaperConfig.parse("broken");check(c.effect==0&&c.touch,"legacy/corrupt fallback");
        c=WallpaperConfig.parse("{\"version\":9,\"effectId\":\"focus\"}");check(c.effect==0,"future config fallback");
        c=WallpaperConfig.parse("{\"effectId\":\"missing\",\"qualityMode\":99,\"cropX\":-8,\"scale\":999,\"sensorSensitivity\":-2}");
        check(c.effect==0&&c.quality==3&&c.cropX==0&&c.scale==2.5f&&c.sensitivity==.1f,"bounds");
        for(int i=0;i<5;i++){c.effect=i;c.a[i]=.37f;c.b[i]=.91f;c.language="en";c.reduceMotion=true;c.cropY=.73f;WallpaperConfig copy=WallpaperConfig.parse(c.json());check(copy.effect==i&&copy.a[i]==.37f&&copy.b[i]==.91f&&copy.en()&&copy.reduceMotion&&copy.cropY==.73f,"roundtrip "+i);copy.resetEffect();check(copy.a[i]==Effects.DEFAULT_A[i],"reset "+i);}
        check(WallpaperConfig.safe(Double.NaN,.4f,0,1)==.4f,"NaN");
        RenderState box=new RenderState();RenderState.Frame frame=new RenderState.Frame();
        check(!box.set(.01f,0,1)&&!box.set(.02f,0,2)&&box.set(.05f,0,3),"accumulated wake threshold");
        box.read(frame);check(frame.x==.05f&&frame.count==3&&frame.timestamp==3,"latest value");
        Thread producer=new Thread(()->{for(int i=4;i<=100003;i++)box.set(i,-i,i);});producer.start();
        while(producer.isAlive()){box.read(frame);if(frame.timestamp>=4)check(frame.x==-frame.y&&frame.x==frame.timestamp,"atomic mailbox");}
        producer.join();box.read(frame);check(frame.count==100003&&frame.timestamp==100003,"no lost events");
    }
}
