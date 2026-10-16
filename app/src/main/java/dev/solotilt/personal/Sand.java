package dev.solotilt.personal;

import java.nio.ByteBuffer;

/**
 * Sand: a falling-sand cellular automaton on the field grid. Each grain falls one cell per pass toward real screen
 * gravity (picked between the two nearest of eight directions, so any tilt angle pours smoothly), slides diagonally
 * off a heap (a 45 degree angle of repose), and carries the photo coordinate it started from, so photo-coloured sand
 * looks like the picture crumbling. A touch pushes grains out of a small disc, digging a hole without losing any.
 *
 * Sand holds on the glass like real sand: a heap starts to pour only once the phone is tilted past about 38 degrees
 * (in-plane gravity beats friction) and stops again below about 30, so small tilts leave it where it lies. The Amount
 * slider adds grains on top of the heap or takes them off its surface, a few rows a frame.
 *
 * Field: R occupancy (255 grain, 0 empty), G the grain's place in its column of sand from the floor up (0 at the
 * floor, 255 at the top surface; empty cells 255),
 * B,A the field cell the grain started in (its piece of the photo). The top-right texel is a marker (0,0,0,255) telling a live field
 * from the empty one previews get.
 */
final class Sand implements Simulation {
    static final int MARKER=W*H-1;
    /** In-plane gravity (sine of the tilt from face-up) at which a resting heap starts to pour, and below which a
     *  pouring one comes to rest: about 38 and 30 degrees. */
    static final float START=.62f,STOP=.5f,RATE=70f,STILL=.4f,DIG=5f,AMOUNT=.6f;
    static final int[] DX={1,1,0,-1,-1,-1,0,1},DY={0,1,1,1,0,-1,-1,-1};
    final boolean[] grain=new boolean[W*H];
    final char[] origin=new char[W*H];
    final int[] stamp=new int[W*H],below=new int[W*H],above=new int[W*H];
    final int[] surface=new int[W*H];
    int pass,seed=0x6A09E667,main=6,count,target;float budget,still,steady,lastUx,lastUy=-1,flow,amount=-1;boolean pouring;

    Sand(){level(rows(AMOUNT));}
    /** Rows of a level heap for an Amount slider value: from a thin layer to most of the screen. */
    static int rows(float amount){return Math.round(H*(.08f+.52f*Math.max(0,Math.min(1,amount))));}
    void level(int rows){
        java.util.Arrays.fill(grain,false);
        for(int y=0;y<rows;y++)for(int x=0;x<W;x++){int c=y*W+x;grain[c]=true;origin[c]=(char)(x<<8|y);}
        count=target=W*rows;
    }
    @Override public void configure(float a,float b,boolean toggle){
        if(a==amount)return;
        // The first value sets the heap outright (nothing is on screen yet); later changes grow or shrink it.
        if(amount<0)level(rows(a));else{target=W*rows(a);still=0;}
        amount=a;
    }

    float random(){seed^=seed<<13;seed^=seed>>>17;seed^=seed<<5;return (seed>>>8)*(1f/16777216f);}

    @Override public boolean step(float dt,float gx,float gy,float shake,float touchX,float touchY){
        if(!(dt>0))return still<STILL;
        dt=Math.min(dt,.1f);
        if(!Float.isFinite(gx)||!Float.isFinite(gy)){gx=0;gy=0;}
        float m=(float)Math.hypot(gx,gy);
        // Static friction with hysteresis: a heap at rest needs a steep tilt to break loose, a pouring one keeps going
        // down to a shallower one. Once pouring, the speed grows with the tilt as before.
        pouring=m>(pouring?STOP:START);
        float eff=pouring?Math.max(.05f,Math.min(1,(m-STOP)/(1-STOP))):0;
        int moved=0;boolean touching=Float.isFinite(touchX)&&Float.isFinite(touchY);
        if(touching)moved+=dig(touchX*W,touchY*H);
        if(count!=target)moved+=adjust();
        int n=0;
        if(eff>0&&!(still>=STILL&&steady>0&&Math.abs(gx/m-lastUx)+Math.abs(gy/m-lastUy)<=.06f)){
            float angle=(float)Math.atan2(gy,gx),sector=angle/(float)(Math.PI/4);if(sector<0)sector+=8;
            int d0=(int)Math.floor(sector)%8;float frac=sector-(float)Math.floor(sector);
            main=frac<.5f?d0:(d0+1)%8;
            budget+=RATE*(.35f+.65f*eff)*dt;n=Math.min(3,(int)budget);budget-=n;if(budget>1)budget=1;
            if(n==0&&still>=STILL)n=1;  // waking from rest: look at once, or a slow first frame would read as rest
            float ux=gx/m,uy=gy/m;
            for(int p=0;p<n;p++)moved+=pass(d0,frac,ux,uy);
        }
        float ux=m>1e-4f?gx/m:0,uy=m>1e-4f?gy/m:-1;
        if(Math.abs(ux-lastUx)+Math.abs(uy-lastUy)>.06f||touching){lastUx=ux;lastUy=uy;steady=0;}else steady+=dt;
        // Once gravity has been steady for a while, a trickle of grains creeping along a slope counts as rest, and
        // after 12 s of steady gravity the heap freezes outright until the phone turns or is touched (battery).
        if(n>0)flow+=(moved/(float)n-flow)*.05f;
        boolean trickle=(steady>4&&flow<5||steady>12)&&count==target;
        if(moved>0&&!trickle)still=0;else if(n>0||eff==0)still+=dt;
        if(still>=STILL&&!touching)return false;
        return still<STILL;
    }

    /** One pass: every grain moves at most one cell. Cells nearest the floor go first so whole columns fall together. */
    int pass(int d0,float frac,float ux,float uy){
        pass++;int moved=0;
        int sx=DX[main],sy=DY[main];
        boolean flipX=sx==0?(pass&1)==0:sx>0,flipY=sy==0?(pass&2)==0:sy>0;
        for(int j=0;j<H;j++){
            int y=flipY?H-1-j:j;
            for(int i=0;i<W;i++){
                int x=flipX?W-1-i:i,c=y*W+x;
                if(!grain[c]||stamp[c]==pass)continue;
                int k=random()<frac?(d0+1)%8:d0;
                int to=target(x,y,k);
                if(to<0){
                    // Blocked straight down: slide to one of the neighbouring directions, if it still leads downhill.
                    // Rough grains only slide where they would keep falling, so heaps get an uneven, steeper face.
                    boolean left=random()<.5f,rough=rough(origin[c]);
                    for(int t=0;t<2&&to<0;t++){
                        int kk=(k+((t==0)==left?1:7))%8;
                        if(DX[kk]*ux+DY[kk]*uy>.17f*(DX[kk]!=0&&DY[kk]!=0?1.4142f:1)){
                            to=target(x,y,kk);
                            if(to>=0&&rough&&target(x+DX[kk],y+DY[kk],k)<0)to=-1;
                        }
                    }
                }
                if(to<0)continue;
                grain[to]=true;origin[to]=origin[c];grain[c]=false;stamp[to]=pass;moved++;
            }
        }
        return moved;
    }
    /** Moves the grain count toward the Amount target: adds grains on top of the heap (in the cell above a surface
     *  grain, "above" meaning against gravity) or lifts surface grains off, at most one per surface cell a frame. */
    int adjust(){
        int ux=-DX[main],uy=-DY[main],n=0;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int c=y*W+x;if(!grain[c]||c==MARKER)continue;
            int nx=x+ux,ny=y+uy;boolean open=nx<0||nx>=W||ny<0||ny>=H?count>target:!grain[ny*W+nx];
            if(open)surface[n++]=c;
        }
        if(n==0&&count<target){for(int c=0;c<W&&count<target;c++)if(!grain[c]){grain[c]=true;origin[c]=(char)(c<<8);count++;}return 1;}
        int changed=0;
        for(int k=n-1;k>=0&&count!=target;k--){
            int pick=(int)(random()*(k+1));int c=surface[pick];surface[pick]=surface[k];
            if(count>target){grain[c]=false;count--;changed++;continue;}
            int x=c%W+ux,y=c/W+uy;if(x<0||x>=W||y<0||y>=H)continue;
            int to=y*W+x;if(grain[to]||to==MARKER)continue;
            grain[to]=true;origin[to]=(char)(x<<8|y);count++;changed++;
        }
        return changed;
    }
    /** A third of the grains (fixed by where they came from) are rough. */
    static boolean rough(char o){return (o*0x9E3779B1>>>28)<5;}
    int target(int x,int y,int k){
        int nx=x+DX[k],ny=y+DY[k];
        if(nx<0||nx>=W||ny<0||ny>=H)return -1;
        int c=ny*W+nx;return grain[c]?-1:c;
    }

    /** Pushes grains out of a disc around the touch to the first free cell further out along the same ray. */
    int dig(float cx,float cy){
        int moved=0,r=(int)Math.ceil(DIG);
        int x0=Math.max(0,(int)cx-r),x1=Math.min(W-1,(int)cx+r),y0=Math.max(0,(int)cy-r),y1=Math.min(H-1,(int)cy+r);
        for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
            int c=y*W+x;if(!grain[c])continue;
            float dx=x+.5f-cx,dy=y+.5f-cy,d=(float)Math.sqrt(dx*dx+dy*dy);if(d>=DIG)continue;
            if(d<1e-3f){dx=0;dy=1;d=1;}
            dx/=d;dy/=d;
            for(float t=DIG+.5f;t<DIG+8;t+=.7f){
                int tx=(int)Math.floor(cx+dx*t),ty=(int)Math.floor(cy+dy*t);
                if(tx<0||tx>=W||ty<0||ty>=H)break;
                int to=ty*W+tx;
                if(!grain[to]){grain[to]=true;origin[to]=origin[c];grain[c]=false;moved++;break;}
            }
        }
        return moved;
    }

    @Override public void fill(ByteBuffer rgba){
        // Run lengths along the gravity direction: grains between this one and the floor, and up to the surface.
        int sx=DX[main],sy=DY[main];
        boolean flipX=sx>0,flipY=sy>0;
        for(int j=0;j<H;j++){int y=flipY?H-1-j:j;for(int i=0;i<W;i++){int x=flipX?W-1-i:i,c=y*W+x;
            if(!grain[c]){below[c]=0;continue;}
            int nx=x+sx,ny=y+sy;below[c]=1+(nx>=0&&nx<W&&ny>=0&&ny<H?below[ny*W+nx]:0);}}
        for(int j=H-1;j>=0;j--){int y=flipY?H-1-j:j;for(int i=W-1;i>=0;i--){int x=flipX?W-1-i:i,c=y*W+x;
            if(!grain[c]){above[c]=0;continue;}
            int nx=x-sx,ny=y-sy;above[c]=1+(nx>=0&&nx<W&&ny>=0&&ny<H?above[ny*W+nx]:0);}}
        rgba.position(0);
        for(int c=0;c<W*H;c++){
            if(c==MARKER){rgba.put((byte)0).put((byte)0).put((byte)0).put((byte)255);continue;}
            if(!grain[c]){rgba.put((byte)0).put((byte)255).put((byte)0).put((byte)0);continue;}
            int run=below[c]+above[c]-1;float place=(below[c]-.5f)/run;
            rgba.put((byte)255).put((byte)Math.min(255,(int)(place*256))).put((byte)(origin[c]>>8)).put((byte)origin[c]);
        }
    }

    int grains(){int n=0;for(boolean b:grain)if(b)n++;return n;}
}
