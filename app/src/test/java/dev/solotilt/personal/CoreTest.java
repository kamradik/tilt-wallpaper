package dev.solotilt.personal;

import java.io.File;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CoreTest {
    @Test public void corruptAndFutureConfigFallBackToDefaults(){
        WallpaperConfig c=WallpaperConfig.parse("broken");
        assertEquals("classic",c.home.effect);assertTrue(c.touch);assertFalse(c.separateLock);
        c=WallpaperConfig.parse("{\"version\":9,\"home\":{\"effect\":\"focus\"}}");
        assertEquals("classic",c.home.effect);
    }
    @Test public void valuesAreClamped(){
        WallpaperConfig c=WallpaperConfig.parse("{\"version\":3,\"qualityMode\":99,\"fps\":7,\"sensorSensitivity\":-2,\"home\":{\"effect\":\"missing\",\"cropX\":-8,\"scale\":999,\"params\":{\"light\":[5,-1,0]}}}");
        assertEquals(3,c.quality);assertEquals(2,c.fps);assertEquals(.1f,c.sensitivity,0);
        assertEquals("classic",c.home.effect);assertEquals(0,c.home.cropX,0);assertEquals(2.5f,c.home.scale,0);
        assertArrayEquals(new float[]{1,0,0},c.home.params("light"),0);
        assertEquals(.4f,WallpaperConfig.safe(Double.NaN,.4f,0,1),0);
    }
    @Test public void roundTripKeepsProfilesFavoritesAndRecents(){
        WallpaperConfig c=new WallpaperConfig();c.separateLock=true;c.language="en";c.reduceMotion=true;c.fps=WallpaperConfig.FPS_30;
        c.home.effect="prism";c.home.params("prism")[0]=.37f;c.home.params("prism")[2]=0;c.home.cropY=.73f;
        c.lock.effect="aurora";c.lock.params("aurora")[1]=.91f;
        c.toggleFavorite("holo");c.toggleFavorite("pond");c.toggleFavorite("holo");c.used("glass");c.used("pond");c.used("glass");
        WallpaperConfig copy=WallpaperConfig.parse(c.json());
        assertTrue(copy.separateLock&&copy.en()&&copy.reduceMotion);assertEquals(WallpaperConfig.FPS_30,copy.fps);
        assertEquals("prism",copy.profile(WallpaperConfig.HOME).effect);assertEquals("aurora",copy.profile(WallpaperConfig.LOCK).effect);
        assertEquals(.37f,copy.home.current()[0],0);assertFalse(copy.home.switchOn());assertEquals(.73f,copy.home.cropY,0);assertEquals(.91f,copy.lock.current()[1],0);
        assertEquals(List.of("pond"),new ArrayList<>(copy.favorites));assertEquals(List.of("glass","pond"),new ArrayList<>(copy.recents));
        copy.home.reset("prism");assertArrayEquals(Effects.get("prism").defaults(),copy.home.params("prism"),0);
    }
    @Test public void lockMirrorsHomeUntilSeparated(){
        WallpaperConfig c=new WallpaperConfig();c.home.effect="golden";c.lock.effect="ice";
        assertSame(c.home,c.profile(WallpaperConfig.LOCK));
        c.separateLock=true;assertSame(c.lock,c.profile(WallpaperConfig.LOCK));
        c.lock.copyFrom(c.home);c.home.params("golden")[0]=.1f;
        assertNotEquals(.1f,c.lock.params("golden")[0],0); // a copy, not shared arrays
    }
    @Test public void migratesBetaAndFortyNineEffectConfigs(){
        // 2.0 beta: one flat profile with string id and two-value parameters.
        WallpaperConfig beta=WallpaperConfig.parse("{\"version\":2,\"effectId\":\"focus\",\"cropX\":0.2,\"scale\":1.3,\"effectParameters\":{\"focus\":[0.3,0.8],\"classic\":[0.6,0.4]}}");
        assertEquals("focus",beta.home.effect);assertEquals(.2f,beta.home.cropX,0);assertArrayEquals(new float[]{.3f,.8f,1},beta.home.current(),0);
        assertEquals("focus",beta.lock.effect);assertFalse(beta.separateLock);
        // 49-effect builds: renamed and removed ids, favorites, a separate lock profile.
        WallpaperConfig wip=WallpaperConfig.parse("{\"effectId\":\"foil\",\"favorites\":[\"matrix\",\"pond\"],\"separateLock\":true,\"lockEffectId\":\"nebula\",\"lockCropX\":0.9,"
            +"\"effectParameters\":{\"foil\":[0.2,0.3],\"matrix\":[0.9,0.9]},\"lockEffectParameters\":{\"nebula\":[0.4,0.5]}}");
        assertEquals("holo",wip.home.effect);assertArrayEquals(new float[]{.2f,.3f,1},wip.home.current(),0);assertFalse(wip.home.params.containsKey("matrix"));
        assertTrue(wip.separateLock);assertEquals("nebula",wip.lock.effect);assertEquals(.9f,wip.lock.cropX,0);assertArrayEquals(new float[]{.4f,.5f,1},wip.lock.current(),0);
        assertTrue(wip.isFavorite("pond"));
        // Builds before 2.0 stored the effect as an index.
        assertEquals("light",WallpaperConfig.parse("{\"effectId\":2}").home.effect);
    }
    @Test public void catalogMatchesShaderFiles(){
        File dir=new File("src/main/assets/fx");if(!dir.isDirectory())dir=new File("app/src/main/assets/fx");
        Set<String> ids=new HashSet<>();
        for(Effects.Effect e:Effects.ALL){
            assertTrue("duplicate id "+e.id,ids.add(e.id));
            assertTrue("missing shader for "+e.id,new File(dir,e.id+".glsl").isFile());
            assertTrue(e.a>=0&&e.a<=1&&e.b>=0&&e.b<=1);assertEquals(e.switchRu==null,e.switchEn==null);
            assertFalse(e.has(Effects.ANIMATED_SWITCH)&&!e.hasSwitch());
        }
        for(String f:Objects.requireNonNull(dir.list()))if(!f.equals("common.glsl")&&!f.equals("main.glsl"))assertTrue("shader without catalog entry: "+f,ids.contains(f.replace(".glsl","")));
        assertEquals(26,Effects.ALL.length);assertSame(Effects.ALL[0],Effects.get("nope"));assertEquals("holo",Effects.get("sheen").id);
        assertEquals("bokeh",Effects.get("diamond").id);
    }
    @Test public void searchIgnoresCaseYoAndLanguage(){
        assertEquals("ice",Effects.search("ИНЕЙ").get(0).id);
        assertTrue(Effects.search("свет").stream().anyMatch(e->e.id.equals("light")));
        assertTrue(Effects.search("Плёнка").stream().anyMatch(e->e.id.equals("film")));
        assertTrue(Effects.search("пленка").stream().anyMatch(e->e.id.equals("film")));
        assertTrue(Effects.search("water").stream().anyMatch(e->e.id.equals("caustic")));
        assertTrue(Effects.search("3d").stream().allMatch(e->e.has(Effects.DEPTH)));
        assertEquals(Effects.ALL.length,Effects.search("  ").size());assertTrue(Effects.search("zzzz").isEmpty());
    }
    @Test public void sensorMailboxKeepsLatestValueAcrossThreads()throws Exception{
        RenderState box=new RenderState();RenderState.Frame frame=new RenderState.Frame();
        assertTrue(!box.set(.01f,0,1)&&!box.set(.02f,0,2)&&box.set(.05f,0,3));
        box.read(frame);assertTrue(frame.x==.05f&&frame.count==3&&frame.timestamp==3);
        Thread producer=new Thread(()->{for(int i=4;i<=100003;i++)box.set(i,-i,i);});producer.start();
        while(producer.isAlive()){box.read(frame);if(frame.timestamp>=4)assertTrue(frame.x==-frame.y&&frame.x==frame.timestamp);}
        producer.join();box.read(frame);assertTrue(frame.count==100003&&frame.timestamp==100003);
    }
}
