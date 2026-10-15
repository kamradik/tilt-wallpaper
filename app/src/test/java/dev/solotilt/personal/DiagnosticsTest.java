package dev.solotilt.personal;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiagnosticsTest {
    @Test public void crashLogRotatesAndKeepsTwoFiles()throws Exception{
        File dir=Files.createTempDirectory("crashlog").toFile();
        String entry=CrashLog.entry("NOTE",0,"device","main",new String(new char[300]).replace('\0','x'));
        for(int i=0;i<20;i++)CrashLog.append(dir,entry,1000);
        File log=new File(dir,CrashLog.NAME),old=new File(dir,CrashLog.NAME+".1");
        assertTrue(log.isFile()&&old.isFile());
        assertTrue(log.length()<=1000+entry.length());assertTrue(old.length()<=1000+entry.length());
        assertEquals(2,dir.listFiles().length);
    }
    @Test public void reportHasHeaderCauseAndIsCapped(){
        RuntimeException e=new RuntimeException("outer",new IllegalStateException("inner"));
        String r=CrashLog.report("CRASH",0,CrashLog.deviceLine("2.0",4,"Google","Pixel","15",35),"SoloRenderer",e);
        assertTrue(r.startsWith("=== CRASH · "));assertTrue(r.contains("Tilt 2.0 (4) · Google Pixel · Android 15 (API 35)"));
        assertTrue(r.contains("Thread: SoloRenderer"));assertTrue(r.contains("Caused by: java.lang.IllegalStateException: inner"));
        Throwable deep=deep(200);
        String capped=CrashLog.report("CRASH",0,"d","t",deep);
        assertTrue(capped.split("\n").length<CrashLog.MAX_STACK_LINES+10);assertTrue(capped.contains("…"));
    }
    static Throwable deep(int n){if(n==0)return new RuntimeException("bottom");try{throw deep(n-1);}catch(Throwable t){return new RuntimeException("level "+n,t);}}
    @Test public void sharingKeepsTheNewestText(){
        String log="old-old-old|new";assertEquals(log,CrashLog.forSharing(log,100));
        String cut=CrashLog.forSharing(new String(new char[500]).replace('\0','a')+"END",100);
        assertEquals(100,cut.length());assertTrue(cut.endsWith("END"));assertTrue(cut.startsWith("[…"));
    }
    @Test public void pauseRules(){
        assertTrue(PerformanceController.pauseFor(true,false,100,true));                 // the tile always wins
        assertTrue(PerformanceController.pauseFor(false,true,15,false));                 // low and unplugged
        assertFalse(PerformanceController.pauseFor(false,true,15,true));                 // charging
        assertFalse(PerformanceController.pauseFor(false,true,16,false));
        assertFalse(PerformanceController.pauseFor(false,false,3,false));                // setting off
    }
    @Test public void effectStatsMergeSummarizeAndKeepAWeek(){
        JSONObject h=new JSONObject();
        Map<String,double[]> a=new HashMap<>();a.put("classic",new double[]{10,50});a.put("aurora",new double[]{5,100});
        for(int d=1;d<=9;d++)PerformanceController.EffectStats.merge(h,String.format(Locale.ROOT,"2026-10-%02d",d),a,7);
        assertEquals(7,h.length());assertFalse(h.has("2026-10-02"));assertTrue(h.has("2026-10-03"));
        PerformanceController.EffectStats.merge(h,"2026-10-09",a,7);
        List<PerformanceController.EffectStats.Row> today=PerformanceController.EffectStats.summarize(h,"2026-10-09");
        assertEquals("aurora",today.get(0).effect);assertEquals(200,today.get(0).busyMs,1e-9);assertEquals(20,today.get(1).frames);
        List<PerformanceController.EffectStats.Row> week=PerformanceController.EffectStats.summarize(h,null);
        assertEquals(8*100,week.get(0).busyMs,1e-9);
        assertEquals(0,PerformanceController.EffectStats.summarize(PerformanceController.EffectStats.parse("not json"),null).size());
    }
    @Test public void randomShortcutNeverRepeatsTheCurrentEffect(){
        Random r=new Random(7);for(int i=0;i<200;i++){String id=ShortcutActivity.pick("classic",r);assertNotEquals("classic",id);assertEquals(id,Effects.get(id).id);}
    }
}
