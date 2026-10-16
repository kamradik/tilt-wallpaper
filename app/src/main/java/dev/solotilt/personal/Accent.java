package dev.solotilt.personal;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * The interface tint taken from the wallpaper: the most present vivid hue of the photo, lifted to a bright,
 * readable tone on the dark interface. A grey or nearly colourless photo keeps the system blue. Remembered across
 * launches; Settings can turn it off.
 */
final class Accent {
    static final String KEY="accent",ON="accent_photo";
    static boolean enabled(Context c){return ImageStore.prefs(c).getBoolean(ON,true);}
    static void setEnabled(Context c,boolean on){ImageStore.prefs(c).edit().putBoolean(ON,on).apply();Ios.accent=on?ImageStore.prefs(c).getInt(KEY,Ios.BLUE):Ios.BLUE;}
    /** The remembered tint, or the system blue. */
    static void load(Context c){Ios.accent=enabled(c)?ImageStore.prefs(c).getInt(KEY,Ios.BLUE):Ios.BLUE;}
    /** Works out the tint for this photo and remembers it; true when it changed. Any thread. */
    static boolean update(Context c,Bitmap photo){
        if(photo==null||photo.isRecycled())return false;
        int w=photo.getWidth(),h=photo.getHeight(),step=Math.max(1,Math.max(w,h)/64);int n=((w+step-1)/step)*((h+step-1)/step),k=0;int[] px=new int[n];
        for(int y=0;y<h;y+=step)for(int x=0;x<w;x+=step)if(k<n)px[k++]=photo.getPixel(x,y);
        int tint=pick(java.util.Arrays.copyOf(px,k));
        if(ImageStore.prefs(c).getInt(KEY,Ios.BLUE)==tint&&(!enabled(c)||Ios.accent==tint))return false;
        ImageStore.prefs(c).edit().putInt(KEY,tint).apply();if(enabled(c))Ios.accent=tint;return enabled(c);
    }
    /** The tint for these pixels: hue histogram in 24 bins weighted by saturation and brightness. Pure, unit-tested. */
    static int pick(int[] pixels){
        float[] weight=new float[24],sumX=new float[24],sumY=new float[24];float[] hsv=new float[3];float total=0;
        for(int p:pixels){
            hsv(p,hsv);if(hsv[1]<.28f||hsv[2]<.22f)continue;
            float wgt=hsv[1]*hsv[2];int bin=Math.min(23,(int)(hsv[0]/15));weight[bin]+=wgt;total+=wgt;
            double a=Math.toRadians(hsv[0]);sumX[bin]+=Math.cos(a)*wgt;sumY[bin]+=Math.sin(a)*wgt;
        }
        // Too little colour in the photo: keep the system blue.
        if(total<pixels.length*.04f)return Ios.BLUE;
        int best=0;for(int i=1;i<24;i++)if(weight[i]+.5f*(weight[(i+1)%24]+weight[(i+23)%24])>weight[best]+.5f*(weight[(best+1)%24]+weight[(best+23)%24]))best=i;
        float hue=(float)((Math.toDegrees(Math.atan2(sumY[best],sumX[best]))+360)%360);
        // Bright and vivid enough for text on near-black; yellows a little less saturated so they do not glare.
        float sat=hue>40&&hue<75?.62f:.72f;
        return rgb(hue,sat,1f);
    }
    /** RGB to hue (degrees), saturation and value, as Color.RGBToHSV, without the framework (unit tests). */
    static void hsv(int p,float[] out){
        float r=(p>>16&255)/255f,g=(p>>8&255)/255f,b=(p&255)/255f,max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min;
        float h=d==0?0:max==r?((g-b)/d)%6:max==g?(b-r)/d+2:(r-g)/d+4;h*=60;if(h<0)h+=360;
        out[0]=h;out[1]=max==0?0:d/max;out[2]=max;
    }
    static int rgb(float h,float s,float v){
        float c=v*s,x=c*(1-Math.abs((h/60)%2-1)),m=v-c,r,g,b;int i=(int)(h/60)%6;
        switch(i){case 0->{r=c;g=x;b=0;}case 1->{r=x;g=c;b=0;}case 2->{r=0;g=c;b=x;}case 3->{r=0;g=x;b=c;}case 4->{r=x;g=0;b=c;}default->{r=c;g=0;b=x;}}
        return 0xff000000|Math.round((r+m)*255)<<16|Math.round((g+m)*255)<<8|Math.round((b+m)*255);
    }
    private Accent(){}
}
