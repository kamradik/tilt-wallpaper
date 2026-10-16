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
}
