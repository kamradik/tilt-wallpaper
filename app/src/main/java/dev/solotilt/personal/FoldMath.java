package dev.solotilt.personal;

/** Physical model reconstructed from the reference Android renderer. No Android dependencies. */
public final class FoldMath {
    public static float clamp(float x, float lo, float hi) { return Math.max(lo, Math.min(hi, x)); }
    public static float[] gravity(float gx, float gy, float gz, int rotation) {
        float[] out=new float[2];return gravity(gx,gy,gz,rotation,out)?out:null;
    }
    public static boolean gravity(float gx,float gy,float gz,int rotation,float[] out) {
        if (!Float.isFinite(gx+gy+gz) || Math.sqrt(gx*gx+gy*gy+gz*gz)<.5) return false;
        float x=gx,y=gy;
        if(rotation==1){x=-gy;y=gx;} else if(rotation==2){x=-gx;y=-gy;} else if(rotation==3){x=gy;y=-gx;}
        double length=Math.hypot(x,y), angle=Math.toDegrees(Math.atan2(length,gz));
        if(angle>120.00001||length<.0001){out[0]=out[1]=0;return true;}
        out[0]=(float)(x*angle/length);out[1]=(float)(y*angle/length);return true;
    }
    private static float envelope(float angle,float gain,float start,float end,float maxDegrees) {
        return gain*(float)Math.sin(Math.toRadians(maxDegrees)*clamp((Math.abs(angle)-start)/(end-start),0,1));
    }
    /** projection X/Y/Z, geometric gap and optical gap, each a vec3. */
    public static float[] projection(float x,float y,float widthMm,float aspect) {
        float[] out=new float[15];projection(x,y,widthMm,aspect,out);return out;
    }
    public static void projection(float x,float y,float widthMm,float aspect,float[] out) {
        if(!Float.isFinite(x+y+widthMm+aspect)||widthMm<=0||aspect<=0) throw new IllegalArgumentException("Invalid dimensions");
        x=clamp(x,-120,120)*.75f;y=clamp(y,-120,120)*.75f;
        float sign=x<0?-1:1, angle=(float)Math.toRadians(Math.min(90,Math.abs(x)));
        float c=(float)Math.cos(angle), s=(float)Math.sin(angle);
        float gapX=sign*s*widthMm, gapC=.5f*s*widthMm;
        float blurX=envelope(x,.68f,14,100,90)*widthMm;
        float blurY=Math.copySign(envelope(y,.28f,25,100,25)*widthMm,y);
        out[0]=c;out[1]=0;out[2]=sign*.5f*(c-1);out[3]=0;out[4]=1;out[5]=0;
        out[6]=-gapX/320;out[7]=0;out[8]=1-gapC/320;out[9]=gapX;out[10]=0;out[11]=gapC;
        out[12]=Math.copySign(blurX,x);out[13]=blurY;out[14]=.5f*blurX+.5f*Math.abs(blurY)/aspect;
    }
    private FoldMath() {}
}
