package dev.solotilt.personal;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LooksTest {
    @Test public void everyLookIsAValidCombination(){
        Set<String> ids=new HashSet<>();
        for(Looks.Look l:Looks.ALL){
            assertTrue("duplicate id "+l.id,ids.add(l.id));
            Effects.Effect base=Effects.find(l.effect);assertNotNull(l.id+": unknown effect",base);assertEquals(3,l.base.length);
            if(l.overlay!=null){assertTrue(l.id+": "+l.overlay+" cannot lie over "+l.effect,Effects.layerable(base,Effects.find(l.overlay)));assertEquals(3,l.top.length);}
            for(float v:l.base)assertTrue(v>=0&&v<=1);
            assertFalse(l.name(false).isEmpty());assertFalse(l.name(true).isEmpty());
        }
    }
    @Test public void applyingKeepsTheFramingAndSetsTheLayer(){
        WallpaperConfig c=new WallpaperConfig();c.home.cropX=.2f;c.home.scale=1.6f;
        Looks.Look l=Looks.ALL[0];Looks.put(c.home,l);
        assertEquals(l.effect,c.home.effect);assertEquals(l.overlay,c.home.overlay);assertEquals(l.overlay,c.home.overlayEffect().id);
        assertEquals(.2f,c.home.cropX,0);assertEquals(1.6f,c.home.scale,0);
        assertArrayEquals(l.top,c.home.params(l.overlay),0);
        c.home.params(l.effect)[0]=0;assertNotEquals(0,l.base[0],0);  // the look's own values are not shared
    }
    @Test public void customLooksKeepTheConstructorAndRoundTrip()throws Exception{
        WallpaperConfig c=new WallpaperConfig();c.home.effect="glass";c.home.overlay="film";c.home.mods[0]=.4f;c.home.mods[3]=.8f;c.home.params("glass")[0]=.33f;
        Looks.Look mine=Looks.Look.custom(WallpaperConfig.newId(),"Мой",c.home);c.looks.add(mine);
        WallpaperConfig back=WallpaperConfig.parse(c.json());
        assertEquals(1,back.looks.size());Looks.Look l=back.looks.get(0);
        assertEquals("glass",l.effect);assertEquals("film",l.overlay);assertEquals(.4f,l.mods[0],1e-6);assertEquals(.8f,l.mods[3],1e-6);assertEquals(.33f,l.base[0],1e-6);
        WallpaperConfig.Profile p=new WallpaperConfig.Profile();Looks.put(p,l);
        assertEquals(.4f,p.mods[0],1e-6);assertEquals("film",p.overlayEffect().id);
        // Built-in looks reset the finishing to neutral.
        Looks.put(p,Looks.ALL[1]);assertFalse(p.hasMods());
        // Modifiers persist with the profile, and neutral ones are not written.
        assertTrue(WallpaperConfig.parse(c.json()).home.hasMods());
        assertFalse(new WallpaperConfig().json().contains("mods"));
    }
}
