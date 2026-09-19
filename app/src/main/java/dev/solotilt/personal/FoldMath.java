package dev.solotilt.personal;

/** Physical model reconstructed from the reference Android renderer. No Android dependencies. */
public final class FoldMath {
    public static float clamp(float x, float lo, float hi) { return Math.max(lo, Math.min(hi, x)); }
    public static float[] gravity(float gx, float gy, float gz, int rotation) {
        if (!Float.isFinite(gx+gy+gz) || Math.sqrt(gx*gx+gy*gy+gz*gz)<.5) return null;
        float x=gx,y=gy;
        if(rotation==1){x=-gy;y=gx;} else if(rotation==2){x=-gx;y=-gy;} else if(rotation==3){x=gy;y=-gx;}
        double length=Math.hypot(x,y), angle=Math.toDegrees(Math.atan2(length,gz));
        if(angle>120.00001) return new float[]{0,0};
        if(length<.0001) return new float[]{0,0};
        return new float[]{(float)(x*angle/length),(float)(y*angle/length)};
    }
    private static float envelope(float angle,float gain,float start,float end,float maxDegrees) {
        return gain*(float)Math.sin(Math.toRadians(maxDegrees)*clamp((Math.abs(angle)-start)/(end-start),0,1));
    }
    /** projection X/Y/Z, geometric gap and optical gap, each a vec3. */
    public static float[] projection(float x,float y,float widthMm,float aspect) {
        if(!Float.isFinite(x+y+widthMm+aspect)||widthMm<=0||aspect<=0) throw new IllegalArgumentException("Invalid dimensions");
        x=clamp(x,-120,120)*.75f;y=clamp(y,-120,120)*.75f;
        float sign=x<0?-1:1, angle=(float)Math.toRadians(Math.min(90,Math.abs(x)));
        float c=(float)Math.cos(angle), s=(float)Math.sin(angle);
        float gapX=sign*s*widthMm, gapC=.5f*s*widthMm;
        float blurX=envelope(x,.68f,14,100,90)*widthMm;
        float blurY=Math.copySign(envelope(y,.28f,25,100,25)*widthMm,y);
        return new float[]{c,0,sign*.5f*(c-1), 0,1,0, -gapX/320,0,1-gapC/320,
            gapX,0,gapC, Math.copySign(blurX,x),blurY,.5f*blurX+.5f*Math.abs(blurY)/aspect};
    }
    private FoldMath() {}
}
