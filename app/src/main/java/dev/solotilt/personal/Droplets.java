package dev.solotilt.personal;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Droplets: rain drops cling to fogged glass until they are heavy enough for the current tilt, then run down along
 * real gravity on a wandering path, swallow the drops they touch, shed tiny beads and wipe a clear trail that fogs
 * over again. A few new drops condense while anything is running; once every drop holds still the simulation sleeps.
 * The Drops slider sets how many drops the glass holds: moving it condenses new ones or lets small ones evaporate.
 *
 * Field: every cell within a drop's radius plus one stores the drop that stands tallest there, so the shader can draw
 * exact round lenses from its 3x3 neighbourhood: R,G the drop centre relative to the cell centre (128 + 16 per cell),
 * B the radius (32 per cell, 0 = no drop), A how clear the fog is (trails; sampled bilinearly).
 * The top-right texel is a marker (0,0,0,255) telling a live field from the empty one previews get.
 */
final class Droplets implements Simulation {
    static final int MAX=220,MARKER=W*H-1;static final float DROPS=.65f;
    static final float FLAT=.15f,SLIDE=1.7f,MAX_R=6f,STILL=.5f,FOG_BACK=14f;
    final float[] x=new float[MAX],y=new float[MAX],r=new float[MAX],vx=new float[MAX],vy=new float[MAX],travel=new float[MAX],phase=new float[MAX];
    final boolean[] sliding=new boolean[MAX];
    final float[] clear=new float[W*H],height=new float[W*H];
    final int[] owner=new int[W*H];
    int count,seed=0x3C6EF372,target,change;float still,spawn,drops=-1;

    Droplets(){scatter(target(DROPS));}
    /** Drops on the glass for a Drops slider value. */
    static int target(float drops){return Math.round(12+148*Math.max(0,Math.min(1,drops)));}
    void scatter(int n){count=0;target=n;for(int i=0;i<n;i++)add(.5f+random()*(W-1),.5f+random()*(H-1),randomRadius());}
    float randomRadius(){return .35f+2.5f*(float)Math.pow(random(),2.6);}
    @Override public void configure(float a,float b,boolean toggle){
        if(a==drops)return;
        // The first value scatters the glass outright; later changes condense or evaporate drops a few per frame.
        if(drops<0)scatter(target(a));else{int t=target(a);change=t-count;target=t;still=0;}
        drops=a;
    }

    float random(){seed^=seed<<13;seed^=seed>>>17;seed^=seed<<5;return (seed>>>8)*(1f/16777216f);}
    /** Adds a resting drop (cell units); ignored when full. */
    void add(float px,float py,float radius){
        if(count>=MAX)return;
        int i=count++;x[i]=px;y[i]=py;r[i]=Math.min(MAX_R,radius);vx[i]=vy[i]=0;travel[i]=0;phase[i]=random()*6.283f;sliding[i]=false;
    }
    void remove(int i){
        int last=--count;
        x[i]=x[last];y[i]=y[last];r[i]=r[last];vx[i]=vx[last];vy[i]=vy[last];travel[i]=travel[last];phase[i]=phase[last];sliding[i]=sliding[last];
    }

    @Override public boolean step(float dt,float gx,float gy,float shake,float touchX,float touchY){
        if(!(dt>0))return still<STILL;
        dt=Math.min(dt,.1f);
        if(!Float.isFinite(gx)||!Float.isFinite(gy)){gx=0;gy=0;}
        float m=(float)Math.hypot(gx,gy),eff=Math.max(0,Math.min(1,(m-FLAT)/(1-FLAT)));
        float ux=m>1e-4f?gx/m:0,uy=m>1e-4f?gy/m:-1,px=-uy,py=ux;
        boolean moving=false;
        for(int i=0;i<count;i++){
            // Static friction: a drop starts to run once its weight beats the glass, and stops a little below that.
            float pull=r[i]*eff;
            if(!sliding[i]&&pull>SLIDE){sliding[i]=true;travel[i]=0;}
            else if(sliding[i]&&pull<SLIDE*.8f)sliding[i]=false;
            if(sliding[i]){
                float speed=Math.min(42,7*(pull-SLIDE*.8f)+3);
                // Runs wander: the sideways component changes with distance travelled, like water finding a path.
                float wander=(float)(Math.sin(travel[i]*.22f+phase[i])*.16+Math.sin(travel[i]*.9f+phase[i]*2.1f)*.07);
                float tx=(ux+px*wander)*speed,ty=(uy+py*wander)*speed;
                float k=1-(float)Math.exp(-dt*8);vx[i]+=(tx-vx[i])*k;vy[i]+=(ty-vy[i])*k;
                // Water left on the glass: the drop shrinks a little and drops a bead now and then.
                float step=(float)Math.hypot(vx[i],vy[i])*dt,before=travel[i];travel[i]+=step;
                r[i]=(float)Math.sqrt(Math.max(.05f,r[i]*r[i]-step*.035f));
                if((int)(travel[i]/2.6f)!=(int)(before/2.6f)&&random()<.55f&&count<MAX)
                    add(x[i]-ux*(r[i]+.8f)+px*(random()-.5f)*r[i],y[i]-uy*(r[i]+.8f)+py*(random()-.5f)*r[i],.3f+.3f*random());
                wipe(x[i],y[i],r[i]*.6f);
                moving=true;
            }else{
                float k=(float)Math.exp(-dt*14);vx[i]*=k;vy[i]*=k;
                if(Math.abs(vx[i])+Math.abs(vy[i])>.2f)moving=true;else vx[i]=vy[i]=0;
            }
            x[i]+=vx[i]*dt;y[i]+=vy[i]*dt;
        }
        // Drops that touch merge into one with the same total area, at their area-weighted centre.
        for(int i=0;i<count;i++)for(int j=i+1;j<count;j++){
            float dx=x[j]-x[i],dy=y[j]-y[i],reach=(r[i]+r[j])*.85f;
            if(dx*dx+dy*dy>=reach*reach)continue;
            float ai=r[i]*r[i],aj=r[j]*r[j],a=ai+aj;
            x[i]=(x[i]*ai+x[j]*aj)/a;y[i]=(y[i]*ai+y[j]*aj)/a;vx[i]=(vx[i]*ai+vx[j]*aj)/a;vy[i]=(vy[i]*ai+vy[j]*aj)/a;
            sliding[i]|=sliding[j];travel[i]=Math.max(travel[i],travel[j]);r[i]=Math.min(MAX_R,(float)Math.sqrt(a));
            remove(j);j=i;moving=true;
        }
        // After the slider moved: condense new drops, or let the smallest resting ones evaporate. Only a slider change
        // does this, so merging (which lowers the count) never keeps the simulation awake.
        for(int k=0;k<3&&change!=0;k++){
            if(change>0){add(.5f+random()*(W-1),.5f+random()*(H-1),randomRadius());change--;}
            else{int small=-1;for(int i=0;i<count;i++)if(!sliding[i]&&(small<0||r[i]<r[small]))small=i;if(small<0){change=0;break;}remove(small);change++;}
            moving=true;
        }
        for(int i=count-1;i>=0;i--)if(x[i]<-r[i]-1||x[i]>W+r[i]+1||y[i]<-r[i]-1||y[i]>H+r[i]+1){remove(i);moving=true;}
        if(moving){
            // The fog creeps back over trails, and fresh drops condense while the glass is alive.
            float k=(float)Math.exp(-dt/FOG_BACK);for(int c=0;c<W*H;c++)clear[c]*=k;
            spawn+=dt;
            if(spawn>.4f){spawn=0;if(count<target+20)add(.5f+random()*(W-1),.5f+random()*(H-1),.4f+1.2f*(float)Math.pow(random(),2));}
        }
        still=moving?0:still+dt;
        return still<STILL;
    }
    void wipe(float cx,float cy,float radius){
        int x0=Math.max(0,(int)(cx-radius)),x1=Math.min(W-1,(int)(cx+radius)),y0=Math.max(0,(int)(cy-radius)),y1=Math.min(H-1,(int)(cy+radius));
        for(int yy=y0;yy<=y1;yy++)for(int xx=x0;xx<=x1;xx++){
            float dx=xx+.5f-cx,dy=yy+.5f-cy,d=(float)Math.sqrt(dx*dx+dy*dy);
            float v=Math.max(0,Math.min(1,(radius+.5f-d)));int c=yy*W+xx;if(v>clear[c])clear[c]=v;
        }
    }

    @Override public void fill(ByteBuffer rgba){
        Arrays.fill(owner,-1);Arrays.fill(height,-1e9f);
        for(int i=0;i<count;i++){
            float reach=r[i]+1.2f;
            int x0=Math.max(0,(int)Math.floor(x[i]-reach)),x1=Math.min(W-1,(int)Math.floor(x[i]+reach)),y0=Math.max(0,(int)Math.floor(y[i]-reach)),y1=Math.min(H-1,(int)Math.floor(y[i]+reach));
            for(int yy=y0;yy<=y1;yy++)for(int xx=x0;xx<=x1;xx++){
                float dx=xx+.5f-x[i],dy=yy+.5f-y[i],d2=dx*dx+dy*dy;
                if(d2>reach*reach)continue;
                // Inside: the cap height; in the margin ring: a small negative score so real drops win the cell.
                float h=d2<r[i]*r[i]?(float)Math.sqrt(r[i]*r[i]-d2):-(float)Math.sqrt(d2)+r[i]-.001f;
                int c=yy*W+xx;if(h>height[c]){height[c]=h;owner[c]=i;}
            }
        }
        rgba.position(0);
        for(int c=0;c<W*H;c++){
            if(c==MARKER){rgba.put((byte)0).put((byte)0).put((byte)0).put((byte)255);continue;}
            int a=Math.min(255,Math.round(clear[c]*255)),i=owner[c];
            if(i<0){rgba.put((byte)128).put((byte)128).put((byte)0).put((byte)a);continue;}
            int cx=c%W,cy=c/W;
            int ox=Math.max(0,Math.min(255,Math.round(128+(x[i]-cx-.5f)*16))),oy=Math.max(0,Math.min(255,Math.round(128+(y[i]-cy-.5f)*16)));
            rgba.put((byte)ox).put((byte)oy).put((byte)Math.max(1,Math.min(255,Math.round(r[i]*32)))).put((byte)a);
        }
    }
}
