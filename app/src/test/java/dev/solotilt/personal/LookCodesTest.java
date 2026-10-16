package dev.solotilt.personal;

import org.junit.Test;
import static org.junit.Assert.*;

public class LookCodesTest {
    @Test public void codesRoundTripWithoutTheId()throws Exception{
        WallpaperConfig.Profile p=new WallpaperConfig.Profile();p.effect="glass";p.overlay="snowglobe";p.mods[2]=.7f;
        Looks.Look mine=Looks.Look.custom("abc12345","Зима",p);
        String code=LookCodes.encode(mine);
        assertTrue(code.startsWith(LookCodes.PREFIX));assertTrue(code.length()<LookCodes.MAX_TEXT);assertFalse(code.contains("abc12345"));
        Looks.Look back=LookCodes.decode(code);
        assertNotNull(back);assertEquals("glass",back.effect);assertEquals("snowglobe",back.overlay);assertEquals(.7f,back.mods[2],1e-6);assertEquals("Зима",back.name(false));
        assertNotEquals("abc12345",back.id);assertTrue(WallpaperConfig.validId(back.id));
    }
    @Test public void foreignTextIsRejected(){
        assertNull(LookCodes.decode(null));assertNull(LookCodes.decode("https://example.com"));
        assertNull(LookCodes.decode(LookCodes.PREFIX+"!!!not base64"));
        assertNull(LookCodes.decode(LookCodes.PREFIX+java.util.Base64.getUrlEncoder().encodeToString("{\"effect\":\"nope\"}".getBytes())));
        StringBuilder huge=new StringBuilder(LookCodes.PREFIX);for(int i=0;i<2000;i++)huge.append('A');assertNull(LookCodes.decode(huge.toString()));
    }
    @Test public void builtInLooksEncodeToo()throws Exception{
        for(Looks.Look l:Looks.ALL){Looks.Look back=LookCodes.decode(LookCodes.encode(l));assertNotNull(l.id,back);assertEquals(l.effect,back.effect);assertEquals(l.overlay,back.overlay);}
    }
}
