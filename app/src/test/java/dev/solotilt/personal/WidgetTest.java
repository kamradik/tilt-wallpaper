package dev.solotilt.personal;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WidgetTest {
    @Test public void nextWalksTheCycleAndWraps(){
        List<String> c=Arrays.asList("a","b","c");
        assertEquals("b",ParallaxWidget.next(c,"a"));assertEquals("a",ParallaxWidget.next(c,"c"));
        assertEquals("a",ParallaxWidget.next(c,"zzz"));  // not in the cycle: start at the first
        assertEquals("x",ParallaxWidget.next(new ArrayList<>(),"x"));
    }
    @Test public void favoritesFormTheCycleOnlyFromTwo(){
        WallpaperConfig config=new WallpaperConfig();
        assertEquals(Effects.ALL.length,ParallaxWidget.cycle(config).size());
        config.favorites.add("ice");assertEquals(Effects.ALL.length,ParallaxWidget.cycle(config).size());
        config.favorites.add("film");assertEquals(Arrays.asList("ice","film"),ParallaxWidget.cycle(config));
    }
}
