package dev.solotilt.personal;

import android.graphics.Bitmap;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * Looks shared as QR codes (ZXing, Apache-2.0). A code carries only the look's text (effect, settings, second
 * layer, finishing), never a photo: PREFIX + URL-safe Base64 of its JSON, about 300 characters. Reading takes an
 * image from the gallery (a screenshot or a photo of a code), so no camera permission is needed.
 */
final class LookCodes {
    static final String PREFIX="PARALLAXIA-LOOK1:";
    static final int MAX_TEXT=1500;

    /** The text a QR code carries for a look. Pure, unit-tested. */
    static String encode(Looks.Look look)throws org.json.JSONException{
        org.json.JSONObject o=look.json();o.remove("id");
        return PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(o.toString().getBytes(StandardCharsets.UTF_8));
    }
    /** A look from a code's text, with a fresh id; null for anything that is not a Parallaxia look. Pure, unit-tested. */
    static Looks.Look decode(String text){
        if(text==null||text.length()>MAX_TEXT||!text.startsWith(PREFIX))return null;
        try{
            String json=new String(Base64.getUrlDecoder().decode(text.substring(PREFIX.length()).trim()),StandardCharsets.UTF_8);
            org.json.JSONObject o=new org.json.JSONObject(json);o.put("id",WallpaperConfig.newId());
            return Looks.Look.parse(o);
        }catch(IllegalArgumentException|org.json.JSONException e){return null;}
    }

    /** A black-on-white QR code, side x side pixels, with a quiet border. */
    static Bitmap qr(String text,int side)throws WriterException{
        Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M);hints.put(EncodeHintType.MARGIN,2);hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");
        BitMatrix m=new QRCodeWriter().encode(text,BarcodeFormat.QR_CODE,side,side,hints);
        int w=m.getWidth(),h=m.getHeight();int[] px=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)px[y*w+x]=m.get(x,y)?0xff000000:0xffffffff;
        return Bitmap.createBitmap(px,w,h,Bitmap.Config.ARGB_8888);
    }
    /** The text of the QR code in a picture, or null. Large pictures are scaled down first. Off the main thread. */
    static String read(Bitmap picture){
        Bitmap b=picture;float s=Math.min(1,1600f/Math.max(picture.getWidth(),picture.getHeight()));
        if(s<1)b=Bitmap.createScaledBitmap(picture,Math.round(picture.getWidth()*s),Math.round(picture.getHeight()*s),true);
        int w=b.getWidth(),h=b.getHeight();int[] px=new int[w*h];b.getPixels(px,0,w,0,0,w,h);if(b!=picture)b.recycle();
        Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS,java.util.Collections.singletonList(BarcodeFormat.QR_CODE));hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);
        try{return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w,h,px))),hints).getText();}
        catch(NotFoundException e){return null;}
    }
    private LookCodes(){}
}
