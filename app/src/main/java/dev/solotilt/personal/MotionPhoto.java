package dev.solotilt.personal;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motion photos (Samsung "Motion photo", Google "Motion Photo"): a JPEG whose file goes on, after the picture,
 * with a short MP4 clip. Where the clip starts is found the way the formats describe it, in order:
 *  1. Google Motion Photo XMP: a Container:Directory item with Mime video/mp4 and Length N = the last N bytes;
 *  2. the older GCamera:MicroVideoOffset="N" = the last N bytes;
 *  3. Samsung's "MotionPhoto_Data" marker, the clip right after it;
 *  4. the first MP4 "ftyp" box after the JPEG's end-of-image marker.
 * Every candidate must start with an MP4 "ftyp" box, so a stray number never yields garbage.
 */
final class MotionPhoto {
    static final int MAX_BYTES=64<<20;
    static final Pattern ITEM=Pattern.compile("Item:Mime=\"video/mp4\"[^>]*?Item:Length=\"(\\d+)\"|Item:Length=\"(\\d+)\"[^>]*?Item:Mime=\"video/mp4\"");
    static final Pattern MICRO=Pattern.compile("MicroVideoOffset=\"(\\d+)\"");
    static final byte[] SAMSUNG="MotionPhoto_Data".getBytes(StandardCharsets.US_ASCII);

    /** Offset of the embedded MP4 in the file, or -1 when it is a plain photo. Pure, unit-tested. */
    static int locate(byte[] b){
        if(b.length<16||(b[0]&255)!=0xFF||(b[1]&255)!=0xD8)return -1;   // not a JPEG
        // XMP lives near the start; 256 KB is plenty and keeps the regex cheap on big files.
        String head=new String(b,0,Math.min(b.length,256*1024),StandardCharsets.ISO_8859_1);
        Matcher m=ITEM.matcher(head);
        while(m.find()){int n=Integer.parseInt(m.group(1)!=null?m.group(1):m.group(2));int at=b.length-n;if(n>0&&isMp4(b,at))return at;}
        m=MICRO.matcher(head);
        if(m.find()){int n=Integer.parseInt(m.group(1));int at=b.length-n;if(n>0&&isMp4(b,at))return at;}
        int marker=indexOf(b,SAMSUNG,0);
        if(marker>=0&&isMp4(b,marker+SAMSUNG.length))return marker+SAMSUNG.length;
        // Last resort: a box size + "ftyp" after the end of the JPEG.
        for(int i=2;i<b.length-1;i++)if((b[i]&255)==0xFF&&(b[i+1]&255)==0xD9){
            for(int j=i+2;j<b.length-8;j++)if(b[j+4]=='f'&&b[j+5]=='t'&&b[j+6]=='y'&&b[j+7]=='p'&&isMp4(b,j))return j;
            break;
        }
        return -1;
    }
    /** An MP4 starts with a box whose type is "ftyp" and whose size is plausible. */
    static boolean isMp4(byte[] b,int at){
        if(at<0||at+12>b.length)return false;
        int size=(b[at]&255)<<24|(b[at+1]&255)<<16|(b[at+2]&255)<<8|(b[at+3]&255);
        return b[at+4]=='f'&&b[at+5]=='t'&&b[at+6]=='y'&&b[at+7]=='p'&&size>=8&&size<=4096;
    }
    static int indexOf(byte[] b,byte[] needle,int from){
        outer:for(int i=from;i<=b.length-needle.length;i++){for(int k=0;k<needle.length;k++)if(b[i+k]!=needle[k])continue outer;return i;}
        return -1;
    }

    /** The clip of a motion photo written to target, or false when the picture has none. Off the main thread. */
    static boolean extract(Context c,Uri photo,File target)throws IOException{
        byte[] b;
        try(InputStream in=c.getContentResolver().openInputStream(photo)){
            if(in==null)return false;ByteArrayOutputStream out=new ByteArrayOutputStream(8<<20);byte[] buf=new byte[1<<16];int n;
            while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>MAX_BYTES)return false;}
            b=out.toByteArray();
        }
        int at=locate(b);if(at<0)return false;
        try(OutputStream out=new FileOutputStream(target)){out.write(b,at,b.length-at);}
        return true;
    }
    private MotionPhoto(){}
}
