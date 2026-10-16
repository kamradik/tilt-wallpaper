package dev.solotilt.personal;

import org.junit.Test;
import static org.junit.Assert.*;

public class LayersTest {
    @Test public void onlyLayerEffectsGoOnTopAndOnlyOnePhysics(){
        Effects.Effect classic=Effects.get("classic"),snow=Effects.get("snowglobe"),drops=Effects.get("droplets"),sand=Effects.get("sand"),film=Effects.get("film");
        assertTrue(Effects.layerable(classic,snow));assertTrue(Effects.layerable(classic,film));
        assertFalse(Effects.layerable(classic,Effects.get("depth")));  // not a layer effect
        assertFalse(Effects.layerable(film,film));                      // not over itself
        assertFalse(Effects.layerable(sand,drops));                     // two simulations
        assertTrue(Effects.layerable(sand,film));
        assertNull(Effects.find("nope"));assertEquals("holo",Effects.find("foil").id);
    }
    @Test public void overlayRoundTripsAndIsDroppedWhenInvalid()throws Exception{
        WallpaperConfig c=new WallpaperConfig();c.home.effect="depth";c.home.overlay="snowglobe";c.home.params("snowglobe")[0]=.25f;
        WallpaperConfig back=WallpaperConfig.parse(c.json());
        assertEquals("snowglobe",back.home.overlay);assertEquals(.25f,back.home.params("snowglobe")[0],1e-6);
        assertEquals("snowglobe",back.home.overlayEffect().id);
        back.home.effect="sand";assertNull("sand and snow both simulate",back.home.overlayEffect());
        WallpaperConfig bad=WallpaperConfig.parse(c.json().replace("\"snowglobe\",","\"depth\",").replace("\"overlay\":\"snowglobe\"","\"overlay\":\"depth\""));
        assertNull(bad.home.overlay);
        WallpaperConfig.Profile copy=new WallpaperConfig.Profile();copy.copyFrom(c.home);assertEquals("snowglobe",copy.overlay);
    }
}
