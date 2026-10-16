package dev.solotilt.personal;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import static dev.solotilt.personal.Ios.*;

/**
 * The app's signature moments, drawn over the live preview: the depth scan while the network reads a photo, the
 * check mark after the wallpaper is set, and the opening title. Each is a self-contained overlay that removes
 * itself when done; under Reduce Motion each shows its message without the movement.
 */
final class Moments {
    static final int CYAN=0xff64d2ff;

    /**
     * Depth scan: while the depth network works, a bright line sweeps down the photo leaving a faint grid of depth
     * points behind it, under a glass label. finish(true) sends the line to the bottom at once and lets three frames
     * spread out from the middle, the photo "coming apart into layers"; finish(false) just fades.
     */
    static final class Scan extends FrameLayout {
        final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG),glow=new Paint(Paint.ANTI_ALIAS_FLAG),dots=new Paint(Paint.ANTI_ALIAS_FLAG),frame=new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r=new RectF();final TextView label;final long born=SystemClock.uptimeMillis();
        Shader trail;float trailHeight;long finishedAt=-1;boolean success;Runnable gone;
        Scan(Context c,String text){
            super(c);setWillNotDraw(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            frame.setStyle(Paint.Style.STROKE);frame.setStrokeWidth(px(c,1.5f));dots.setColor(CYAN);
            label=legible(text(c,text,15,SEMIBOLD,LABEL));label.setGravity(Gravity.CENTER);label.setBackground(glass(c,-1));label.setPadding(dp(c,18),0,dp(c,18),0);
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,dp(c,38),Gravity.CENTER_HORIZONTAL|Gravity.TOP);addView(label,lp);
            setAlpha(0);animate().alpha(1).setDuration(still()?0:260).setStartDelay(180).start();  // a cached map is ready before this shows
            label.announceForAccessibility(text);
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r2,int b){
            super.onLayout(changed,l,t,r2,b);label.setTranslationY((b-t)*.3f);
        }
        void finish(boolean ok,Runnable after){
            if(finishedAt>=0)return;success=ok;gone=after;finishedAt=SystemClock.uptimeMillis();
            if(ok)haptic(this,CONFIRM);
            label.animate().alpha(0).setDuration(200).start();
            animate().cancel();animate().alpha(0).setStartDelay(ok&&!still()?620:0).setDuration(320).withEndAction(this::remove).start();
            invalidate();
        }
        void remove(){ViewParent p=getParent();if(p instanceof ViewGroup)((ViewGroup)p).removeView(this);if(gone!=null)gone.run();}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),d=px(getContext(),1);if(w==0||h==0)return;
            c.drawColor(0x33000000);
            if(still())return;
            long now=SystemClock.uptimeMillis();float t=(now-born)/1000f;
            float y;float done=finishedAt<0?0:Math.min(1,(now-finishedAt)/520f);
            float sweep=(t*.5f)%1.25f;y=sweep*h;
            if(finishedAt>=0){y=y+(h*1.1f-y)*(1-(1-done)*(1-done));}
            // The trail: a cyan wash fading upward, with depth points that light up as the line passes.
            float th=h*.32f;if(trail==null||trailHeight!=th){trail=new LinearGradient(0,0,0,th,0x0064d2ff,0x4064d2ff,Shader.TileMode.CLAMP);trailHeight=th;}
            c.save();c.translate(0,y-th);glow.setShader(trail);c.drawRect(0,0,w,th,glow);glow.setShader(null);c.restore();
            float step=22*d;
            for(float gy=step/2;gy<Math.min(y,h);gy+=step){
                float age=(y-gy)/h;if(age>.6f)continue;
                for(float gx=step/2;gx<w;gx+=step){
                    float hsh=(float)(Math.sin(gx*12.9898+gy*78.233)*43758.5453);hsh-=Math.floor(hsh);
                    float a=(1-age/.6f)*(.25f+.75f*hsh);dots.setAlpha(Math.round(200*a));c.drawCircle(gx,gy,(.9f+1.3f*hsh)*d,dots);
                }
            }
            if(y<h){
                glow.setColor(CYAN);glow.setAlpha(110);c.drawRect(0,y-6*d,w,y+6*d,glow);
                line.setColor(Color.WHITE);c.drawRect(0,y-1.2f*d,w,y+1.2f*d,line);
            }
            // Success: three frames spread out from the middle, the photo coming apart into layers.
            if(success&&done>0){
                for(int i=0;i<3;i++){
                    float k=Math.max(0,Math.min(1,done*1.4f-i*.18f));if(k<=0)continue;float e=1-(1-k)*(1-k)*(1-k);
                    float inset=(.3f-e*.34f+i*.04f)*Math.min(w,h);r.set(inset,inset*h/w,w-inset,h-inset*h/w);
                    frame.setColor(i==1?CYAN:Color.WHITE);frame.setAlpha(Math.round(200*(1-k)));c.drawRoundRect(r,28*d,28*d,frame);
                }
            }
            postInvalidateOnAnimation();
        }
    }

    /** "Set": a glass disc grows in, a check mark draws itself and a ring of light spreads out, then all of it fades. */
    static final class Done extends View {
        final Paint ring=new Paint(Paint.ANTI_ALIAS_FLAG),check=new Paint(Paint.ANTI_ALIAS_FLAG),fill=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path=new Path(),part=new Path();final PathMeasure measure=new PathMeasure();float progress;
        Done(Context c){
            super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            ring.setStyle(Paint.Style.STROKE);check.setStyle(Paint.Style.STROKE);check.setStrokeCap(Paint.Cap.ROUND);check.setStrokeJoin(Paint.Join.ROUND);check.setColor(Color.WHITE);
        }
        void play(Runnable after){
            haptic(this,CONFIRM);
            ValueAnimator a=ValueAnimator.ofFloat(0,1);a.setDuration(still()?900:1500);a.setInterpolator(null);
            a.addUpdateListener(x->{progress=(float)x.getAnimatedValue();invalidate();});
            a.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator x){ViewParent p=getParent();if(p instanceof ViewGroup)((ViewGroup)p).removeView(Done.this);if(after!=null)after.run();}});
            a.start();
        }
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),d=px(getContext(),1),cx=w/2,cy=h*.42f,R=46*d;
            float in=Math.min(1,progress/.25f),out=progress<.75f?1:Math.max(0,1-(progress-.75f)/.25f);
            float pop=still()?1:BOUNCY.getInterpolation(in);float s=.6f+.4f*pop;
            c.drawColor(Color.argb(Math.round(70*out),0,0,0));
            fill.setColor(0x52ffffff);fill.setAlpha(Math.round(82*out*Math.min(1,in*2)));c.drawCircle(cx,cy,R*s,fill);
            ring.setStrokeWidth(d);ring.setColor(0x80ffffff);ring.setAlpha(Math.round(160*out*Math.min(1,in*2)));c.drawCircle(cx,cy,R*s,ring);
            // The spreading ring of light.
            if(!still()&&progress>.12f){float k=Math.min(1,(progress-.12f)/.6f);ring.setStrokeWidth((1-k)*6*d+d);ring.setAlpha(Math.round(180*(1-k)));c.drawCircle(cx,cy,R*(1+k*2.4f),ring);}
            // The check draws itself between 15% and 45% of the run.
            float k=still()?1:Math.max(0,Math.min(1,(progress-.15f)/.3f));
            if(k>0){path.reset();path.moveTo(cx-R*.38f,cy+R*.02f);path.lineTo(cx-R*.1f,cy+R*.3f);path.lineTo(cx+R*.42f,cy-R*.28f);
                measure.setPath(path,false);part.reset();measure.getSegment(0,measure.getLength()*k,part,true);
                check.setStrokeWidth(7*d*s);check.setAlpha(Math.round(255*out));c.drawPath(part,check);}
        }
    }

    /**
     * Opening title on a cold start: three cards of a photo, stacked flat, part in depth as a tilted phone would show
     * them, the name settles under them, then everything lifts away from the live preview. A tap skips it.
     */
    static final class Intro extends View {
        final Paint card=new Paint(Paint.ANTI_ALIAS_FLAG),edge=new Paint(Paint.ANTI_ALIAS_FLAG),word=new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r=new RectF();final Path path=new Path();final long born=SystemClock.uptimeMillis();static final long LENGTH=1250;
        final int[][] colors={{0xff5e5ce6,0xffbf5af2},{0xff0a84ff,0xff64d2ff},{0xffff375f,0xffff9f0a}};boolean leaving;Runnable gone;final Shader[] shaders=new Shader[3];
        Intro(Context c,Runnable gone){
            super(c);this.gone=gone;setClickable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            edge.setStyle(Paint.Style.STROKE);edge.setStrokeWidth(px(c,1));edge.setColor(0x66ffffff);
            word.setColor(Color.WHITE);word.setTextAlign(Paint.Align.CENTER);word.setTypeface(display(BOLD));word.setTextSize(px(c,26));word.setLetterSpacing(.02f);
            setOnClickListener(v->leave());
            postDelayed(this::leave,LENGTH);
        }
        void leave(){
            if(leaving)return;leaving=true;
            animate().alpha(0).scaleX(1.06f).scaleY(1.06f).setDuration(380).setInterpolator(new DecelerateInterpolator(1.6f)).withEndAction(()->{ViewParent p=getParent();if(p instanceof ViewGroup)((ViewGroup)p).removeView(this);if(gone!=null)gone.run();}).start();
        }
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),d=px(getContext(),1),t=Math.min(1,(SystemClock.uptimeMillis()-born)/(float)LENGTH);
            c.drawColor(Color.BLACK);
            float cw=118*d,ch=196*d,cx=w/2,cy=h*.44f;
            // Separation on a spring over the first 70%: back card up and left, front card down and right.
            float k=SMOOTH.getInterpolation(Math.min(1,t/.7f));
            for(int i=0;i<3;i++){
                float o=(i-1)*k,dx=o*20*d,dy=o*16*d,s=1-(2-i)*.05f*k;
                c.save();c.translate(cx+dx,cy+dy);c.rotate(o*3f);c.scale(s,s);
                r.set(-cw/2,-ch/2,cw/2,ch/2);squircle(path,r,24*d);
                if(shaders[i]==null)shaders[i]=new LinearGradient(-cw/2,-ch/2,cw/2,ch/2,colors[i][0],colors[i][1],Shader.TileMode.CLAMP);card.setShader(shaders[i]);card.setAlpha(Math.round(255*(.55f+.45f*(i==2?1:k*.7f))));
                c.drawPath(path,card);card.setShader(null);c.drawPath(path,edge);c.restore();
            }
            // The name rises and opens its tracking a touch as it settles.
            float n=SMOOTH.getInterpolation(Math.max(0,Math.min(1,(t-.25f)/.6f)));
            word.setAlpha(Math.round(255*n));word.setLetterSpacing(.02f+.06f*(1-n));
            c.drawText("Parallaxia",cx,cy+ch/2+64*d+(1-n)*14*d,word);
            if(!leaving)postInvalidateOnAnimation();
        }
    }
    private Moments(){}
}
