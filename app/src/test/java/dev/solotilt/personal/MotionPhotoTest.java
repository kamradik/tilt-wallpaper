package dev.solotilt.personal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class MotionPhotoTest {
    /** A minimal MP4 start: a 24-byte ftyp box and some payload. */
    static byte[] mp4(){byte[] b=new byte[200];b[3]=24;b[4]='f';b[5]='t';b[6]='y';b[7]='p';b[8]='m';b[9]='p';b[10]='4';b[11]='2';return b;}
    static byte[] jpeg(String xmp,byte[] trailer,byte[] video)throws Exception{
        ByteArrayOutputStream o=new ByteArrayOutputStream();
        o.write(new byte[]{(byte)0xFF,(byte)0xD8});o.write(xmp.getBytes(StandardCharsets.ISO_8859_1));
        o.write(new byte[]{1,2,3,(byte)0xFF,(byte)0xD9});if(trailer!=null)o.write(trailer);if(video!=null)o.write(video);
        return o.toByteArray();
    }
    @Test public void googleContainerItem()throws Exception{
        byte[] v=mp4();byte[] f=jpeg("<Container:Item Item:Mime=\"video/mp4\" Item:Semantic=\"MotionPhoto\" Item:Length=\""+v.length+"\"/>",null,v);
        assertEquals(f.length-v.length,MotionPhoto.locate(f));
    }
    @Test public void microVideoOffset()throws Exception{
        byte[] v=mp4();byte[] f=jpeg("GCamera:MicroVideo=\"1\" GCamera:MicroVideoOffset=\""+v.length+"\"",null,v);
        assertEquals(f.length-v.length,MotionPhoto.locate(f));
    }
    @Test public void samsungMarker()throws Exception{
        byte[] v=mp4();byte[] f=jpeg("",MotionPhoto.SAMSUNG,v);
        assertEquals(f.length-v.length,MotionPhoto.locate(f));
    }
    @Test public void plainPhotosAndWrongOffsetsAreRejected()throws Exception{
        assertEquals(-1,MotionPhoto.locate(jpeg("<x:xmpmeta/>",null,null)));
        assertEquals(-1,MotionPhoto.locate("not a jpeg at all".getBytes()));
        // A length that does not point at an ftyp box is ignored; the trailer search then finds the real clip.
        byte[] v=mp4();byte[] f=jpeg("Item:Mime=\"video/mp4\" Item:Length=\"7\"",null,v);
        assertEquals(f.length-v.length,MotionPhoto.locate(f));
    }
}
