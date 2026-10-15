package dev.solotilt.personal;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Snow Globe: a few hundred snowflakes and glitter flakes fall along real screen gravity with drag and a little
 * turbulence, heap up on whichever edge is "down", slide off when the phone turns, and fly up again when it is shaken.
 *
 * Field (one texel per cell, row 0 at the bottom):
 *  R  density of settled flakes, bilinear-splatted (160 = one flake's worth), drawn as the settled drift
 *  G,B sub-cell position of the one flake this cell draws (0..255 = 0..1 of the cell)
 *  A  that flake: bit 7 glitter, bit 6 airborne, bits 0..5 rank 1..63 (0 = no flake); the shader shows a flake only
 *     when its rank is under the Amount / Glitter slider, so the sliders thin the scene without touching the physics.
 * The top-right texel is a marker (0,0,0,255) so the shader can tell a live field from the empty one previews get.
 */
final class SnowGlobe implements Simulation {
    static final int N=1000,CAP=1,MARKER=W*H-1;
    static final float GRAVITY=70f,SNOW_DRAG=2.4f,GLITTER_DRAG=1.6f,FLAT=.12f,STILL=.5f,TOUCH_RADIUS=9f;
    final float[] x=new float[N],y=new float[N],vx=new float[N],vy=new float[N],downX=new float[N],downY=new float[N];
    final boolean[] settled=new boolean[N];
    final byte[] kind=new byte[N];
    /** Slides off a heap since gravity last turned; capped so lattice rounding can never keep a heap shuffling. */
    final byte[] slides=new byte[N];
    float lastUx,lastUy=-1,steady;int busy;
    /** Settled flakes per cell; airborne flakes treat a full cell as solid. */
    final byte[] pile=new byte[W*H];
    final float[] density=new float[W*H];
    final int[] shown=new int[W*H];
    int seed=0x2545F491;float time,still;

    SnowGlobe(){
        for(int i=0;i<N;i++){
            x[i]=.5f+random()*(W-1);y[i]=.5f+random()*(H-1);vx[i]=(random()-.5f)*4;vy[i]=(random()-.5f)*4;
            boolean glitter=i%5==0;int rank=1+(glitter?(i/5)*29:i*37)%63;
            kind[i]=(byte)((glitter?0x80:0)|rank);
        }
    }

    float random(){seed^=seed<<13;seed^=seed>>>17;seed^=seed<<5;return (seed>>>8)*(1f/16777216f);}
    /** Cell index at a position in cell units, or -1 outside the field (a wall). */
    static int cell(float px,float py){
        if(!(px>=0&&px<W&&py>=0&&py<H))return -1;
        return (int)py*W+(int)px;
    }
    boolean free(int c){return c>=0&&pile[c]<CAP;}

    @Override public boolean step(float dt,float gx,float gy,float shake,float touchX,float touchY){
        if(!(dt>0))return still<STILL;
        dt=Math.min(dt,.1f);time+=dt;
        if(!Float.isFinite(gx)||!Float.isFinite(gy)){gx=0;gy=0;}
        if(!Float.isFinite(shake))shake=0;
        float m=(float)Math.hypot(gx,gy),ux=m>1e-4f?gx/m:0,uy=m>1e-4f?gy/m:-1;
        // Lying almost flat, flakes rest on the glass; below the speed that reads as falling, gravity is ignored.
        float eff=Math.max(0,Math.min(1,(m-FLAT)/(1-FLAT)));if(GRAVITY*eff/SNOW_DRAG<1.5f)eff=0;
        float s=Math.max(0,Math.min(1,(shake-.3f)/.7f));
        boolean touching=Float.isFinite(touchX)&&Float.isFinite(touchY);float tx=touchX*W,ty=touchY*H;
        boolean moving=false;
        float px=-uy,py=ux; // perpendicular to gravity, for sliding off a heap
        if(Math.abs(ux-lastUx)+Math.abs(uy-lastUy)>.1f||s>0||touching){java.util.Arrays.fill(slides,(byte)0);lastUx=ux;lastUy=uy;steady=0;}
        else steady+=dt;
        // Watchdog: with gravity steady for a while and only a few stragglers left jostling on a heap's lattice,
        // the scene counts as settled and freezes until the phone turns, shakes or is touched (battery first).
        if(steady>8&&busy<N/40){still=STILL;return false;}
        busy=0;
        for(int i=0;i<N;i++){
            float dx=x[i]-tx,dy=y[i]-ty,touchD=touching?(float)Math.sqrt(dx*dx+dy*dy):1e9f;
            if(settled[i]){
                boolean release=s>0&&random()<s*dt*12||touchD<TOUCH_RADIUS;
                if(!release&&eff>0){
                    // The phone turned under the heap, or the cell beneath emptied: the flake slides again.
                    if(downX[i]*ux+downY[i]*uy<.82f)release=true;
                    else{
                        int c=cell(x[i],y[i]),b=cell(x[i]+ux,y[i]+uy);
                        if(b!=c&&free(b))release=true;
                        // A flake on top of a steep stack slides off (angle of repose), so heaps never form towers.
                        else if(slides[i]<8)for(int sd=-1;sd<=1&&!release;sd+=2){
                            int beside=cell(x[i]+px*sd,y[i]+py*sd),diagonal=cell(x[i]+ux+px*sd,y[i]+uy+py*sd);
                            if(beside!=c&&diagonal!=c&&free(beside)&&free(diagonal)){release=true;slides[i]++;}
                        }
                    }
                }
                if(!release)continue;
                settled[i]=false;int c=cell(x[i],y[i]);if(c>=0&&pile[c]>0)pile[c]--;
                vx[i]=(random()-.5f)*2;vy[i]=(random()-.5f)*2;moving=true;
            }
            boolean glitter=kind[i]<0;float drag=glitter?GLITTER_DRAG:SNOW_DRAG;
            float ax=ux*GRAVITY*eff,ay=uy*GRAVITY*eff;
            // A smooth swirl that drifts with time keeps falling snow from moving in lockstep; shaking stirs it hard.
            float swirl=6f*eff+s*240f;
            if(swirl>0){
                float fx=(float)(Math.sin(y[i]*.13f+time*1.7f+i*.37f)+Math.sin((x[i]+y[i])*.07f-time*1.1f));
                float fy=(float)(Math.cos(x[i]*.11f-time*1.3f+i*.21f)+Math.sin((x[i]-y[i])*.09f+time*.9f));
                ax+=fx*swirl*.5f;ay+=fy*swirl*.5f;
            }
            if(s>0){ax+=(random()-.5f)*900*s-ux*480*s;ay+=(random()-.5f)*900*s-uy*480*s;}
            if(touchD<TOUCH_RADIUS){float k=(1-touchD/TOUCH_RADIUS)*420/Math.max(.5f,touchD);ax+=dx*k;ay+=dy*k;}
            vx[i]+=(ax-drag*vx[i])*dt;vy[i]+=(ay-drag*vy[i])*dt;
            float speed=(float)Math.hypot(vx[i],vy[i]);if(speed>90){vx[i]*=90/speed;vy[i]*=90/speed;speed=90;}
            float nx=x[i]+vx[i]*dt,ny=y[i]+vy[i]*dt;
            if(nx<.5f){nx=.5f;vx[i]*=-.2f;}else if(nx>W-.5f){nx=W-.5f;vx[i]*=-.2f;}
            if(ny<.5f){ny=.5f;vy[i]*=-.2f;}else if(ny>H-.5f){ny=H-.5f;vy[i]*=-.2f;}
            int c=cell(nx,ny);
            boolean hit=eff>0&&c!=cell(x[i],y[i])&&!free(c);
            if(hit){nx=x[i];ny=y[i];c=cell(nx,ny);vx[i]=vy[i]=0;speed=0;}// ran into the heap: treat as blocked below
            x[i]=nx;y[i]=ny;
            if(speed>.5f){moving=true;busy++;}
            if(eff>0&&touchD>=TOUCH_RADIUS){
                int b=cell(x[i]+ux,y[i]+uy);
                if(b<0||hit||b!=c&&!free(b)){
                    // Blocked below: roll off a peak toward a free side, otherwise come to rest (angle of repose).
                    float side=random()<.5f?1:-1;
                    if(slides[i]<12&&(rolled(i,c,side,ux,uy,px,py)||rolled(i,c,-side,ux,uy,px,py))){moving=true;slides[i]++;}
                    else{settled[i]=true;pile[c]++;downX[i]=ux;downY[i]=uy;vx[i]=vy[i]=0;moving=true;}
                }
            }
        }
        still=moving?0:still+dt;
        return still<STILL;
    }

    /** Rolls flake i toward one side when both the cell beside it and the one below that are free. */
    boolean rolled(int i,int c,float side,float ux,float uy,float px,float py){
        int beside=cell(x[i]+px*side,y[i]+py*side),diagonal=cell(x[i]+ux+px*side,y[i]+uy+py*side);
        if(beside==c||diagonal==c||!free(beside)||!free(diagonal))return false;
        x[i]+=px*side*.5f;y[i]+=py*side*.5f;x[i]=Math.max(.5f,Math.min(W-.5f,x[i]));y[i]=Math.max(.5f,Math.min(H-.5f,y[i]));
        vx[i]=px*side*3+ux*3;vy[i]=py*side*3+uy*3;return true;
    }

    @Override public void fill(ByteBuffer rgba){
        Arrays.fill(density,0);Arrays.fill(shown,-1);
        for(int i=0;i<N;i++){
            float fx=x[i]-.5f,fy=y[i]-.5f;int ix=(int)Math.floor(fx),iy=(int)Math.floor(fy);float ax=fx-ix,ay=fy-iy;
            if(settled[i]){splat(ix,iy,(1-ax)*(1-ay));splat(ix+1,iy,ax*(1-ay));splat(ix,iy+1,(1-ax)*ay);splat(ix+1,iy+1,ax*ay);}
            int c=cell(x[i],y[i]);
            if(c>=0&&c!=MARKER&&(shown[c]<0||!settled[i]&&settled[shown[c]]))shown[c]=i;
        }
        rgba.position(0);
        for(int c=0;c<W*H;c++){
            if(c==MARKER){rgba.put((byte)0).put((byte)0).put((byte)0).put((byte)255);continue;}
            int r=Math.min(255,Math.round(density[c]*160)),i=shown[c];
            if(i<0){rgba.put((byte)r).put((byte)0).put((byte)0).put((byte)0);continue;}
            int ox=Math.min(255,(int)((x[i]-(float)Math.floor(x[i]))*256)),oy=Math.min(255,(int)((y[i]-(float)Math.floor(y[i]))*256));
            rgba.put((byte)r).put((byte)ox).put((byte)oy).put((byte)((kind[i]&0xBF)|(settled[i]?0:0x40)));
        }
    }
    void splat(int ix,int iy,float w){if(ix>=0&&ix<W&&iy>=0&&iy<H)density[iy*W+ix]+=w;}

    /** Flakes resting on a heap (for tests). */
    int settledCount(){int n=0;for(boolean b:settled)if(b)n++;return n;}
}
