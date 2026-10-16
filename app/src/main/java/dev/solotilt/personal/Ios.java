package dev.solotilt.personal;

import android.animation.*;
import android.app.*;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** iOS-style tokens, motion and controls drawn with the platform canvas, so the APK stays dependency-free. */
final class Ios {
    // Dark-mode system colors from Apple's Human Interface Guidelines.
    static final int LABEL=0xffffffff,SECONDARY=0x99ebebf5,TERTIARY=0x4debebf5,FILL=0x5c787880,SEPARATOR=0x5c545458,
        BLUE=0xff0a84ff,GREEN=0xff30d158,ORANGE=0xffff9f0a,ALERT=0xf0262628,SHEET=0x8c101014;
    static final int REGULAR=400,MEDIUM=500,SEMIBOLD=600,BOLD=700;
    // One corner scale for the whole app: controls are capsules, cards 14-18, grouped lists 20, sheets 32.
    static final float CARD=14,GROUPED=20,SHEET_RADIUS=32;
    static final Handler MAIN=new Handler(Looper.getMainLooper());

    static float px(Context c,float dp){return dp*c.getResources().getDisplayMetrics().density;}
    static int dp(Context c,float dp){return Math.round(px(c,dp));}
    static Typeface font(int weight){return Typeface.create(Typeface.SANS_SERIF,weight,false);}
    static int mix(int a,int b,float t){return (int)new ArgbEvaluator().evaluate(Math.max(0,Math.min(1,t)),a,b);}
    static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    /** UIScrollView's rubber band: the further past the edge, the less the content follows the finger. */
    static float rubber(float offset,float dimension){float d=Math.max(1e-3f,dimension),x=Math.abs(offset);return Math.signum(offset)*(1-1/(x*.55f/d+1))*d;}

    /** Text with SF-like tracking: tight for large titles, neutral for captions. */
    static TextView text(Context c,CharSequence value,float sp,int weight,int color){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);t.setTypeface(font(weight));t.setTextColor(color);
        t.setLetterSpacing(sp>=28?-.015f:sp>=15?-.01f:0);return t;
    }
    /** A soft contact shadow keeps white labels readable where glass sits over a bright patch of the photo. */
    static <T extends TextView> T legible(T t){t.setShadowLayer(px(t.getContext(),3),0,px(t.getContext(),.5f),0x59000000);return t;}
    /** Crossfades a label instead of snapping it, like UIKit's transition on text changes. */
    static void swap(TextView t,CharSequence value){
        if(value.toString().contentEquals(t.getText()))return;
        t.animate().cancel();t.animate().alpha(0).setDuration(110).setStartDelay(0).withEndAction(()->{t.setText(value);t.animate().alpha(1).setDuration(180).setInterpolator(SMOOTH);}).start();
    }

    // ---------------------------------------------------------------- motion
    /** Damped spring in SwiftUI terms: response is the period in seconds, damping the fraction of critical. */
    static final class Spring implements TimeInterpolator {
        final double omega,zeta;final long duration;
        Spring(double response,double damping){omega=2*Math.PI/response;zeta=Math.min(.99,damping);duration=Math.round(1000*Math.min(1.4,6/(zeta*omega)));}
        @Override public float getInterpolation(float t){
            if(t>=1)return 1;double s=t*duration/1000.0,wd=omega*Math.sqrt(1-zeta*zeta);
            return (float)(1-Math.exp(-zeta*omega*s)*(Math.cos(wd*s)+zeta*omega/wd*Math.sin(wd*s)));
        }
    }
    static final Spring SMOOTH=new Spring(.5,.9),BOUNCY=new Spring(.42,.68),SNAPPY=new Spring(.26,.92),POP=new Spring(.36,.5),CALM=new Spring(.3,.99);
    /** Reduce Motion (the app setting, mirrored here by each screen) or animator scale 0 in developer options. */
    static volatile boolean reduce;
    static void reduceMotion(boolean on){reduce=on;}
    static boolean still(){return reduce||!ValueAnimator.areAnimatorsEnabled();}
    /** Without motion every spring becomes a short critically damped ease: no overshoot, nothing bouncing. */
    static Spring ease(Spring s){return still()?CALM:s;}
    static ViewPropertyAnimator spring(View v,Spring s){Spring e=ease(s);v.animate().cancel();return v.animate().setInterpolator(e).setDuration(e.duration).setStartDelay(0);}
    /** Staggered entrance: fade and settle upward, like a sheet's content arriving. */
    static void rise(View v,int order){v.setAlpha(0);if(!still())v.setTranslationY(px(v.getContext(),18));spring(v,SMOOTH).alpha(1).translationY(0).setStartDelay(90+55L*order);}
    /** Grid items arriving one after another on a soft spring, slightly scaled down, as in iOS sheets. */
    static void appear(View v,int order,boolean quick){
        v.setAlpha(0);if(!still()){v.setTranslationY(px(v.getContext(),quick?8:22));v.setScaleX(.94f);v.setScaleY(.94f);}
        spring(v,SMOOTH).alpha(1).translationY(0).scaleX(1).scaleY(1).setStartDelay((quick?0:70)+Math.min(order,12)*(quick?16L:34L));
    }

    /** Pressed state of iOS 26 controls. Glass grows toward the finger with a bloom under it (.interactive());
     *  filled controls shrink and dim. Both are released on a bouncy spring. */
    static <T extends View> T pressable(T v){
        v.setOnTouchListener((view,e)->{
            int action=e.getActionMasked();Shape glass=view.getBackground() instanceof Shape&&((Shape)view.getBackground()).glass?(Shape)view.getBackground():null;
            float w=Math.max(1,view.getWidth()),h=Math.max(1,view.getHeight()),grow=px(view.getContext(),7);
            if(action==MotionEvent.ACTION_DOWN){
                if(glass==null)spring(view,SNAPPY).scaleX(.95f).scaleY(.95f).alpha(.78f);
                else{glass.press(e.getX(),e.getY(),true);if(!still())spring(view,SNAPPY).scaleX(1+Math.min(.06f,grow/w)).scaleY(1+Math.min(.06f,grow/h));}
            }else if(action==MotionEvent.ACTION_MOVE&&glass!=null){
                glass.follow(e.getX(),e.getY());
                // The lens leans after the finger a little, like a drop of liquid held by surface tension.
                // Measured from the untranslated frame, so the lean does not feed back into the next event.
                if(!still()){view.setTranslationX(rubber(e.getX()+view.getTranslationX()-w/2,grow*3)*.35f);view.setTranslationY(rubber(e.getY()+view.getTranslationY()-h/2,grow*3)*.35f);}
            }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                if(glass!=null)glass.press(e.getX(),e.getY(),false);
                spring(view,BOUNCY).scaleX(1).scaleY(1).translationX(0).translationY(0).alpha(view.isEnabled()?1:.45f);
            }
            return false;
        });
        return v;
    }

    /** Continuous ("squircle") corners: the curve starts earlier and ends tighter than a circular arc. radius<0 is a capsule. */
    static Path squircle(Path p,RectF r,float radius){
        p.reset();float w=r.width(),h=r.height(),half=Math.min(w,h)/2;
        if(radius<0||radius>=half){p.addRoundRect(r,half,half,Path.Direction.CW);return p;}
        float l=Math.min(radius*1.52f,half),k=.874f*l,L=r.left,T=r.top,R=r.right,B=r.bottom;
        p.moveTo(L+l,T);p.lineTo(R-l,T);p.cubicTo(R-l+k,T,R,T+l-k,R,T+l);
        p.lineTo(R,B-l);p.cubicTo(R,B-l+k,R-l+k,B,R-l,B);
        p.lineTo(L+l,B);p.cubicTo(L+l-k,B,L,B-l+k,L,B-l);
        p.lineTo(L,T+l);p.cubicTo(L,T+l-k,L+l-k,T,L+l,T);p.close();return p;
    }

    // ---------------------------------------------------------------- liquid glass
    // The GL wallpaper cannot be read back cheaply, so glass refracts a small pre-blurred copy of the same photo,
    // placed exactly where the wallpaper shows it. One copy serves every glass surface of the hosting window.
    static Bitmap backdrop;static WeakReference<View> backdropRoot;static float zoom=1.08f,cropX=.5f,cropY=.5f;
    static float lightDeg=-115;static volatile float lightTarget=-115;static volatile boolean lightPending;static long lightTime;
    static final Set<Shape> GLASS=Collections.newSetFromMap(new WeakHashMap<>());
    static final float LENS=1.07f,EDGE=1.2f;
    static final Paint IMAGE=new Paint(Paint.FILTER_BITMAP_FLAG|Paint.ANTI_ALIAS_FLAG),EDGE_IMAGE=new Paint(Paint.FILTER_BITMAP_FLAG|Paint.ANTI_ALIAS_FLAG),
        SCRIM=new Paint(),SPEC=new Paint(Paint.ANTI_ALIAS_FLAG),SHEEN=new Paint(Paint.ANTI_ALIAS_FLAG),GLOW=new Paint(Paint.ANTI_ALIAS_FLAG);
    // Unit-space gradients, placed per surface through their local matrix: light side first, a faint bounce opposite.
    static final Shader SPECULAR=new LinearGradient(-1,0,1,0,new int[]{0xe6ffffff,0x33ffffff,0x0dffffff,0x14ffffff,0x73ffffff},new float[]{0,.28f,.5f,.75f,1},Shader.TileMode.CLAMP),
        SHEEN_SHADER=new LinearGradient(-1,0,1,0,new int[]{0x2effffff,0x00ffffff,0x00ffffff,0x0fffffff},new float[]{0,.5f,.8f,1},Shader.TileMode.CLAMP),
        GLOW_SHADER=new RadialGradient(0,0,1,new int[]{0x73ffffff,0x29ffffff,0x00ffffff},new float[]{0,.45f,1},Shader.TileMode.CLAMP);
    static final Matrix SPEC_MATRIX=new Matrix(),GLOW_MATRIX=new Matrix();
    static{
        ColorMatrix vivid=new ColorMatrix();vivid.setSaturation(1.45f);IMAGE.setColorFilter(new ColorMatrixColorFilter(vivid));
        // The refracted rim gathers more light than the body: more saturated and lifted.
        ColorMatrix rim=new ColorMatrix();rim.setSaturation(1.7f);rim.postConcat(new ColorMatrix(new float[]{1.12f,0,0,0,22,0,1.12f,0,0,22,0,0,1.12f,0,22,0,0,0,1,0}));EDGE_IMAGE.setColorFilter(new ColorMatrixColorFilter(rim));
        SCRIM.setColor(Color.BLACK);SPEC.setStyle(Paint.Style.STROKE);SPEC.setShader(SPECULAR);SHEEN.setShader(SHEEN_SHADER);GLOW.setShader(GLOW_SHADER);
    }
    static void ui(Runnable r){if(Looper.myLooper()==Looper.getMainLooper())r.run();else MAIN.post(r);}

    /** Sets the photo that glass inside root's window refracts. Blurs on the calling thread (call it off the UI thread);
     *  photo is only read, never kept or recycled. null returns glass to its plain tinted look. */
    static void backdrop(View root,Bitmap photo){
        Bitmap blurred=photo==null||photo.isRecycled()?null:blur(photo);
        ui(()->{backdropRoot=new WeakReference<>(root);backdrop=blurred;refract();});
    }
    /** The wallpaper's framing (zoom 1.02-2.5, crop 0..1), so the glass shows what is really behind it. UI thread. */
    static void backdropFrame(float zoomValue,float cropXValue,float cropYValue){
        if(zoom==zoomValue&&cropX==cropXValue&&cropY==cropYValue)return;zoom=Math.max(1,zoomValue);cropX=cropXValue;cropY=cropYValue;refract();
    }
    static final Runnable SHINE=()->{lightPending=false;lightDeg=lightTarget;refract();};
    /** Device tilt in degrees moves the specular highlight on every glass edge. Any thread; coalesced to at most ~30 Hz. */
    static void light(float tiltX,float tiltY){
        if(reduce)return;long now=SystemClock.uptimeMillis();if(lightPending||now-lightTime<33)return;
        float deg=(float)Math.toDegrees(Math.atan2(-1+clamp(tiltY,-60,60)/90f,-.45f+clamp(tiltX,-60,60)/45f));
        if(Math.abs(deg-lightTarget)<.75f)return;lightTime=now;lightTarget=deg;lightPending=true;MAIN.post(SHINE);
    }
    /** Redraws visible glass that refracts the backdrop (after a scroll, a new photo or new light). UI thread. */
    static void refract(){for(Shape s:GLASS)if(s.host()!=null&&((View)s.getCallback()).isShown())s.invalidateSelf();}

    /** About 48 px on the short side and three box passes each way: close to a Gaussian, a few ms on any phone. */
    static Bitmap blur(Bitmap src){
        Bitmap source=src.getConfig()==Bitmap.Config.HARDWARE?src.copy(Bitmap.Config.ARGB_8888,false):src;
        float s=Math.min(1,48f/Math.max(1,Math.min(source.getWidth(),source.getHeight())));
        int w=Math.max(4,Math.round(source.getWidth()*s)),h=Math.max(4,Math.round(source.getHeight()*s));
        Bitmap small=Bitmap.createScaledBitmap(source,w,h,true);int[] a=new int[w*h],t=new int[w*h];small.getPixels(a,0,w,0,0,w,h);
        if(small!=src)small.recycle();if(source!=src&&source!=small)source.recycle();
        for(int pass=0;pass<3;pass++){box(a,t,w,h,2,true);box(t,a,w,h,2,false);}
        return Bitmap.createBitmap(a,w,h,Bitmap.Config.ARGB_8888);
    }
    static void box(int[] in,int[] out,int w,int h,int r,boolean horizontal){
        int n=horizontal?w:h,lines=horizontal?h:w,step=horizontal?1:w,stride=horizontal?w:1,div=2*r+1;
        for(int line=0;line<lines;line++){
            int base=line*stride,sa=0,sr=0,sg=0,sb=0;
            for(int i=-r;i<=r;i++){int p=in[base+Math.max(0,Math.min(n-1,i))*step];sa+=p>>>24;sr+=p>>16&255;sg+=p>>8&255;sb+=p&255;}
            for(int i=0;i<n;i++){
                out[base+i*step]=sa/div<<24|sr/div<<16|sg/div<<8|sb/div;
                int add=in[base+Math.min(n-1,i+r+1)*step],rem=in[base+Math.max(0,i-r)*step];
                sa+=(add>>>24)-(rem>>>24);sr+=(add>>16&255)-(rem>>16&255);sg+=(add>>8&255)-(rem>>8&255);sb+=(add&255)-(rem&255);
            }
        }
    }
    /** Relative luminance with gamma 2 instead of the sRGB curve: close enough to pick a scrim. */
    static float luminance(int c){float r=(c>>16&255)/255f,g=(c>>8&255)/255f,b=(c&255)/255f;return .2126f*r*r+.7152f*g*g+.0722f*b*b;}

    /** Continuous-corner shape. glass() turns it into iOS 26 Liquid Glass: over the backdrop it refracts the photo
     *  (lens in the body, stronger at the rim), darkens just enough for white text, and catches a tilt-driven highlight;
     *  elsewhere it is a frosted fill with a gradient hairline. */
    static final class Shape extends Drawable {
        final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),stroke=new Paint(Paint.ANTI_ALIAS_FLAG),sheen=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path=new Path(),inner=new Path();final RectF box=new RectF(),edge=new RectF();final Matrix place=new Matrix(),lens=new Matrix(),inverse=new Matrix();final float[] probe=new float[10];
        final float radius;boolean glass;float legible=.18f,bloom,touchX,touchY;int color,strokeColor,alpha=255;ValueAnimator press;
        Shape(int color,float radius){this.color=color;fill.setColor(color);this.radius=radius;stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(0);}
        Shape stroke(float width,int color){stroke.setStrokeWidth(width);strokeColor=color;applyAlpha();return this;}
        Shape glass(float width){glass=true;stroke.setStrokeWidth(width);strokeColor=LABEL;applyAlpha();GLASS.add(this);return this;}
        /** Highest luminance the glass lets through behind its content: .18 suits white semibold labels, less for secondary text. */
        Shape legible(float maxLuminance){legible=maxLuminance;return this;}
        void setColor(int value){color=value;applyAlpha();invalidateSelf();}
        void applyAlpha(){fill.setColor(color);fill.setAlpha(Color.alpha(color)*alpha/255);stroke.setColor(strokeColor);stroke.setAlpha(Color.alpha(strokeColor)*alpha/255);}
        /** Bloom of light under the finger (interactive glass). */
        void press(float x,float y,boolean down){
            touchX=x;touchY=y;if(press!=null)press.cancel();
            press=ValueAnimator.ofFloat(bloom,down?1:0);press.setDuration(down?140:560);press.setInterpolator(new DecelerateInterpolator(down?1:1.6f));
            press.addUpdateListener(a->{bloom=(float)a.getAnimatedValue();invalidateSelf();});press.start();
        }
        void follow(float x,float y){touchX=x;touchY=y;if(bloom>0)invalidateSelf();}
        @Override protected void onBoundsChange(Rect b){
            float inset=stroke.getStrokeWidth()/2;box.set(b.left+inset,b.top+inset,b.right-inset,b.bottom-inset);squircle(path,box,radius);
            float rim=Math.min(Math.min(box.width(),box.height())*.16f,7*Resources.getSystem().getDisplayMetrics().density);
            edge.set(box.left+rim,box.top+rim,box.right-rim,box.bottom-rim);squircle(inner,edge,radius<0?-1:Math.max(0,radius-rim));
            if(glass&&!b.isEmpty()){
                stroke.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,new int[]{0x66ffffff,0x14ffffff,0x0affffff,0x38ffffff},new float[]{0,.35f,.65f,1},Shader.TileMode.CLAMP));
                sheen.setShader(new LinearGradient(0,b.top,0,b.top+Math.min(b.height(),b.width())*.6f,0x1cffffff,0x00ffffff,Shader.TileMode.CLAMP));
            }
        }
        /** The view this glass belongs to, when it sits in the window whose wallpaper the backdrop shows. */
        View host(){
            Callback cb=getCallback();View root=backdropRoot==null?null:backdropRoot.get();
            if(!glass||backdrop==null||root==null||!(cb instanceof View))return null;View v=(View)cb;
            return v.isAttachedToWindow()&&v.getRootView()==root.getRootView()?v:null;
        }
        @Override public void draw(Canvas c){
            View host=host();
            if(host==null){c.drawPath(path,fill);if(glass)c.drawPath(path,sheen);glow(c);if(stroke.getStrokeWidth()>0)c.drawPath(path,stroke);return;}
            Bitmap b=backdrop;View root=backdropRoot.get();locate(host,root,b);float t=transmission(b),cx=box.centerX(),cy=box.centerY();
            int save=c.save();c.clipPath(path);
            lens.set(place);lens.postScale(LENS,LENS,cx,cy);c.drawBitmap(b,lens,IMAGE);SCRIM.setAlpha(Math.round(255*(1-t)*alpha/255f));c.drawPaint(SCRIM);
            // Refracted rim: the band near the edge samples the photo with a stronger lens, so content bends at the border.
            c.save();c.clipOutPath(inner);lens.set(place);lens.postScale(EDGE,EDGE,cx,cy);c.drawBitmap(b,lens,EDGE_IMAGE);SCRIM.setAlpha(Math.round(190*(1-t)*alpha/255f));c.drawPaint(SCRIM);c.restore();
            boolean frost=luminance(color)>.5f;if(frost)fill.setAlpha(Math.round(Color.alpha(color)*.4f*alpha/255f));c.drawPath(path,fill);if(frost)fill.setAlpha(Color.alpha(color)*alpha/255);
            float r=(float)Math.hypot(box.width(),box.height())/2,w=Math.max(1,stroke.getStrokeWidth());
            SPEC_MATRIX.setScale(r,r);SPEC_MATRIX.postRotate(lightDeg+180);SPEC_MATRIX.postTranslate(cx,cy);SPECULAR.setLocalMatrix(SPEC_MATRIX);SHEEN_SHADER.setLocalMatrix(SPEC_MATRIX);
            SHEEN.setAlpha(alpha);c.drawPath(path,SHEEN);glow(c);
            SPEC.setStrokeWidth(w*5);SPEC.setAlpha(60*alpha/255);c.drawPath(path,SPEC);  // soft inner light, clipped to the body
            c.restoreToCount(save);
            SPEC.setStrokeWidth(w*1.2f);SPEC.setAlpha(alpha);c.drawPath(path,SPEC);
        }
        /** Bitmap-to-drawable matrix: the shader's cover-fit with crop and zoom, offset by the host's layout position.
         *  Transforms are ignored on purpose, so a moving or pressed surface carries its glass instead of re-recording. */
        void locate(View host,View root,Bitmap b){
            float x=0,y=0;View cur=host;
            while(cur!=root){x+=cur.getLeft();y+=cur.getTop();ViewParent p=cur.getParent();if(!(p instanceof View))break;cur=(View)p;x-=cur.getScrollX();y-=cur.getScrollY();}
            float W=Math.max(1,root.getWidth()),H=Math.max(1,root.getHeight()),bw=b.getWidth(),bh=b.getHeight(),cover=W/H/(bw/bh);
            float sx=Math.min(1,cover)/zoom,sy=Math.min(1,1/cover)/zoom,ox=sx/2+(1-sx)*cropX,oy=sy/2+(1-sy)*(1-cropY);
            place.setScale(W/(bw*sx),H/(bh*sy));place.postTranslate(W*(.5f-ox/sx)-x,H*(.5f-oy/sy)-y);
        }
        /** Fraction of the photo to let through so the brightest of five probes stays under the legible luminance
         *  after the tint (a dark fill) or frost (a light fill) is added on top. */
        float transmission(Bitmap b){
            if(!place.invert(inverse))return .5f;
            float cx=box.centerX(),cy=box.centerY(),qx=box.width()/4,qy=box.height()/4;
            probe[0]=cx;probe[1]=cy;probe[2]=cx-qx;probe[3]=cy;probe[4]=cx+qx;probe[5]=cy;probe[6]=cx;probe[7]=cy-qy;probe[8]=cx;probe[9]=cy+qy;inverse.mapPoints(probe);
            float most=0;for(int i=0;i<5;i++)most=Math.max(most,luminance(b.getPixel((int)clamp(probe[2*i],0,b.getWidth()-1),(int)clamp(probe[2*i+1],0,b.getHeight()-1))));
            boolean frost=luminance(color)>.5f;float a=Color.alpha(color)/255f*(frost?.4f:1),lf=luminance(color);
            return clamp((legible-a*lf)/(Math.max(.02f,most*1.15f)*Math.max(.05f,1-a)),.1f,.9f);
        }
        void glow(Canvas c){
            if(bloom<.01f)return;float r=Math.max(box.width(),box.height())*.8f;
            GLOW_MATRIX.setScale(r,r);GLOW_MATRIX.postTranslate(touchX,touchY);GLOW_SHADER.setLocalMatrix(GLOW_MATRIX);GLOW.setAlpha(Math.round(255*bloom*alpha/255f));
            int save=c.save();c.clipPath(path);c.drawPath(path,GLOW);c.restoreToCount(save);
        }
        @Override public void getOutline(Outline o){
            Rect b=getBounds();if(radius<0)o.setRoundRect(b,Math.min(b.width(),b.height())/2f);
            else if(Build.VERSION.SDK_INT>=33&&!path.isEmpty())o.setPath(path);  // path outlines clip only from Android 13
            else o.setRoundRect(b,radius);
        }
        @Override public void setAlpha(int a){alpha=a;applyAlpha();invalidateSelf();}
        @Override public int getAlpha(){return alpha;}
        @Override public void setColorFilter(ColorFilter f){fill.setColorFilter(f);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    static Shape glass(Context c,float radiusDp){return new Shape(0x29ffffff,radiusDp<0?-1:px(c,radiusDp)).glass(Math.max(1,px(c,.7f)));}

    /** Line icons in the spirit of SF Symbols, drawn on a unit grid. */
    static final class Glyph extends Drawable {
        static final int PLUS=0,BACK=1,LINK=2,PLAY=3,PAUSE=4,PHOTO=5,INFO=6,SEARCH=7,HEART=8,HEART_FILL=9,GEAR=10,CHEVRON=11,CLOSE=12,VIDEO=13;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG),dot=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path();final int kind,size;
        Glyph(Context c,int kind,int color,float sizeDp){this.kind=kind;size=dp(c,sizeDp);paint.setColor(color);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);setBounds(0,0,size,size);}
        @Override public int getIntrinsicWidth(){return size;}
        @Override public int getIntrinsicHeight(){return size;}
        @Override public void draw(Canvas c){
            Rect b=getBounds();float s=Math.min(b.width(),b.height()),x=b.exactCenterX()-s/2,y=b.exactCenterY()-s/2;
            c.save();c.translate(x,y);c.scale(s,s);path.reset();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.1f);
            switch(kind){
                case PLUS->{path.moveTo(.5f,.2f);path.lineTo(.5f,.8f);path.moveTo(.2f,.5f);path.lineTo(.8f,.5f);paint.setStrokeWidth(.11f);}
                case BACK->{path.moveTo(.62f,.16f);path.lineTo(.3f,.5f);path.lineTo(.62f,.84f);paint.setStrokeWidth(.12f);}
                case LINK->{path.moveTo(.27f,.73f);path.lineTo(.72f,.28f);path.moveTo(.36f,.28f);path.lineTo(.72f,.28f);path.lineTo(.72f,.64f);}
                case PLAY->{path.moveTo(.32f,.2f);path.lineTo(.8f,.5f);path.lineTo(.32f,.8f);path.close();paint.setStyle(Paint.Style.FILL_AND_STROKE);paint.setStrokeWidth(.08f);}
                case PAUSE->{path.addRoundRect(.25f,.2f,.41f,.8f,.05f,.05f,Path.Direction.CW);path.addRoundRect(.59f,.2f,.75f,.8f,.05f,.05f,Path.Direction.CW);paint.setStyle(Paint.Style.FILL);}
                case PHOTO->{path.addRoundRect(.12f,.2f,.88f,.8f,.12f,.12f,Path.Direction.CW);path.moveTo(.2f,.7f);path.lineTo(.42f,.48f);path.lineTo(.58f,.62f);path.lineTo(.68f,.54f);path.lineTo(.82f,.68f);path.addCircle(.66f,.36f,.06f,Path.Direction.CW);paint.setStrokeWidth(.075f);}
                case INFO->{path.moveTo(.5f,.46f);path.lineTo(.5f,.76f);dot.set(paint);dot.setStyle(Paint.Style.FILL);c.drawCircle(.5f,.27f,.075f,dot);paint.setStrokeWidth(.12f);}
                case SEARCH->{path.addCircle(.44f,.44f,.24f,Path.Direction.CW);path.moveTo(.62f,.62f);path.lineTo(.82f,.82f);paint.setStrokeWidth(.1f);}
                case HEART,HEART_FILL->{path.moveTo(.5f,.82f);path.cubicTo(.08f,.56f,.12f,.18f,.34f,.2f);path.cubicTo(.43f,.2f,.48f,.27f,.5f,.32f);path.cubicTo(.52f,.27f,.57f,.2f,.66f,.2f);path.cubicTo(.88f,.18f,.92f,.56f,.5f,.82f);path.close();paint.setStrokeWidth(.08f);if(kind==HEART_FILL)paint.setStyle(Paint.Style.FILL_AND_STROKE);}
                case GEAR->{for(int i=0;i<8;i++){double t=i*Math.PI/4;path.moveTo(.5f+(float)Math.cos(t)*.3f,.5f+(float)Math.sin(t)*.3f);path.lineTo(.5f+(float)Math.cos(t)*.4f,.5f+(float)Math.sin(t)*.4f);}path.addCircle(.5f,.5f,.25f,Path.Direction.CW);path.addCircle(.5f,.5f,.09f,Path.Direction.CW);paint.setStrokeWidth(.09f);}
                case CHEVRON->{path.moveTo(.38f,.2f);path.lineTo(.66f,.5f);path.lineTo(.38f,.8f);paint.setStrokeWidth(.11f);}
                case CLOSE->{path.moveTo(.28f,.28f);path.lineTo(.72f,.72f);path.moveTo(.72f,.28f);path.lineTo(.28f,.72f);paint.setStrokeWidth(.1f);}
                case VIDEO->{path.addRoundRect(.1f,.26f,.66f,.74f,.1f,.1f,Path.Direction.CW);path.moveTo(.68f,.44f);path.lineTo(.9f,.32f);path.lineTo(.9f,.68f);path.lineTo(.68f,.56f);paint.setStrokeWidth(.075f);}
            }
            c.drawPath(path,paint);c.restore();
        }
        @Override public void setAlpha(int a){paint.setAlpha(a);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    /** Capsule button: prominent is the white fill of iOS 26 primary actions, otherwise a glass capsule. */
    static TextView capsule(Context c,String title,boolean prominent){
        TextView b=text(c,title,16,SEMIBOLD,prominent?0xff0b0b0d:LABEL);b.setGravity(Gravity.CENTER);b.setMaxLines(1);
        b.setAutoSizeTextTypeUniformWithConfiguration(12,16,1,TypedValue.COMPLEX_UNIT_SP);b.setPadding(dp(c,16),0,dp(c,16),0);
        b.setBackground(prominent?new Shape(0xfff5f5f7,-1):glass(c,-1));if(!prominent)legible(b);b.setClickable(true);b.setFocusable(true);
        b.setOutlineProvider(ViewOutlineProvider.BACKGROUND);b.setAccessibilityDelegate(BUTTON);return reach(pressable(b));
    }
    /** Round glass button holding one glyph, like the toolbar buttons of iOS 26. */
    static ImageView circle(Context c,int glyph,String description){
        ImageView v=new ImageView(c);v.setImageDrawable(new Glyph(c,glyph,LABEL,20));v.setScaleType(ImageView.ScaleType.CENTER);
        v.setBackground(glass(c,-1));v.setContentDescription(description);v.setClickable(true);v.setFocusable(true);v.setAccessibilityDelegate(BUTTON);return reach(pressable(v));
    }
    // ---------------------------------------------------------------- accessibility
    /** Announces a custom-drawn clickable view as a button (TalkBack: "..., button"). */
    static final View.AccessibilityDelegate BUTTON=new View.AccessibilityDelegate(){
        @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());}
    };
    /** Minimum touch target (Material and WCAG 2.5.8 guidance): 48 dp. */
    static final float MIN_TARGET=48;
    /** Lets a control smaller than 48 dp be hit from a 48 dp area through its parent, without changing how it looks.
     *  Several small controls may share one parent; a touch goes to the nearest one whose area holds it. */
    static <T extends View> T reach(T v){v.addOnLayoutChangeListener(REACH);return v;}
    static final View.OnLayoutChangeListener REACH=(v,l,t,r,b,ol,ot,or,ob)->{
        if(!(v.getParent() instanceof ViewGroup))return;ViewGroup g=(ViewGroup)v.getParent();
        int min=dp(v.getContext(),MIN_TARGET),dx=Math.max(0,(min-(r-l)+1)/2),dy=Math.max(0,(min-(b-t)+1)/2);
        TouchDelegate existing=g.getTouchDelegate();Reach reach=existing instanceof Reach?(Reach)existing:null;
        if(reach==null){if(existing!=null||dx==0&&dy==0)return;reach=new Reach(g);g.setTouchDelegate(reach);}
        if(dx==0&&dy==0){reach.areas.remove(v);return;}
        reach.areas.put(v,new Rect(l-dx,t-dy,r+dx,b+dy));
    };
    static final class Reach extends TouchDelegate {
        final ViewGroup parent;final Map<View,Rect> areas=new WeakHashMap<>();View target;
        Reach(ViewGroup parent){super(new Rect(),parent);this.parent=parent;}
        @Override public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked();float x=e.getX()+parent.getScrollX(),y=e.getY()+parent.getScrollY();
            if(action==MotionEvent.ACTION_DOWN){
                target=null;float best=Float.MAX_VALUE;
                for(Map.Entry<View,Rect> a:areas.entrySet()){View v=a.getKey();
                    if(v.getParent()!=parent||v.getVisibility()!=View.VISIBLE||!v.isEnabled()||!a.getValue().contains((int)x,(int)y))continue;
                    float d=(float)Math.hypot(x-(v.getLeft()+v.getRight())/2f,y-(v.getTop()+v.getBottom())/2f);if(d<best){best=d;target=v;}
                }
            }
            View v=target;if(v==null)return false;
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)target=null;
            // Clamped into the control, so it treats the touch as its own (pressed state, click on release).
            MotionEvent copy=MotionEvent.obtain(e);copy.setLocation(clamp(x-v.getLeft(),0,Math.max(0,v.getWidth()-1)),clamp(y-v.getTop(),0,Math.max(0,v.getHeight()-1)));
            try{return v.dispatchTouchEvent(copy);}finally{copy.recycle();}
        }
        @Override public AccessibilityNodeInfo.TouchDelegateInfo getTouchDelegateInfo(){
            Map<Region,View> map=new android.util.ArrayMap<>();for(Map.Entry<View,Rect> a:areas.entrySet())map.put(new Region(a.getValue()),a.getKey());
            return map.isEmpty()?super.getTouchDelegateInfo():new AccessibilityNodeInfo.TouchDelegateInfo(map);
        }
    }

    static View hairline(Context c){View v=new View(c);v.setBackgroundColor(SEPARATOR);return v;}

    /** A one-value spring animator on the given curve; under Reduce Motion it eases without overshoot. */
    static ValueAnimator animate(ValueAnimator old,float from,float to,Spring s,ValueAnimator.AnimatorUpdateListener l){
        if(old!=null)old.cancel();Spring e=ease(s);ValueAnimator a=ValueAnimator.ofFloat(from,to);a.setInterpolator(e);a.setDuration(e.duration);a.addUpdateListener(l);a.start();return a;
    }

    /** UISwitch: 51x31 pt track, thumb on a spring, stretched while held. */
    static final class Toggle extends View {
        interface Listener{void changed(boolean on);}
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF r=new RectF();boolean on;float progress,stretch;Listener listener;ValueAnimator move,hold;
        Toggle(Context c){super(c);setClickable(true);setFocusable(true);reach(this);}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(dp(getContext(),51)+getPaddingLeft()+getPaddingRight(),dp(getContext(),31)+getPaddingTop()+getPaddingBottom());}
        void setOn(boolean value,boolean animate){
            on=value;if(move!=null)move.cancel();
            if(!animate){progress=on?1:0;invalidate();return;}
            move=Ios.animate(move,progress,on?1:0,BOUNCY,a->{progress=(float)a.getAnimatedValue();invalidate();});
        }
        @Override public void setPressed(boolean pressed){
            boolean changed=pressed!=isPressed();super.setPressed(pressed);if(!changed)return;
            hold=Ios.animate(hold,stretch,pressed?1:0,SNAPPY,a->{stretch=(float)a.getAnimatedValue();invalidate();});
        }
        @Override public boolean performClick(){setOn(!on,true);performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);if(listener!=null)listener.changed(on);return super.performClick();}
        @Override protected void onDraw(Canvas c){
            float d=px(getContext(),1),left=getPaddingLeft(),top=getPaddingTop(),w=51*d,h=31*d;
            paint.clearShadowLayer();paint.setColor(mix(FILL,GREEN,Math.min(1,progress)));r.set(left,top,left+w,top+h);c.drawRoundRect(r,h/2,h/2,paint);
            float knob=27*d,width=knob+7*d*stretch,x=left+2*d+(w-4*d-width)*progress;
            paint.setShadowLayer(3*d,0,1.5f*d,0x45000000);paint.setColor(LABEL);r.set(x,top+2*d,x+width,top+2*d+knob);c.drawRoundRect(r,knob/2,knob/2,paint);
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(Switch.class.getName());info.setCheckable(true);info.setChecked(on);}
        // The click event TalkBack announces after a toggle carries the new state, as CompoundButton's does.
        @Override public void onInitializeAccessibilityEvent(AccessibilityEvent event){super.onInitializeAccessibilityEvent(event);event.setClassName(Switch.class.getName());event.setChecked(on);}
    }

    /** UISlider with a bipolar fill from zero and haptic detents at 0 and the ends; the thumb swells into a lens while held. */
    static final class Slider extends View {
        interface Listener{void changed(int value);}
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF r=new RectF();final int min,max;int value;float held;Listener listener;ValueAnimator hold;
        /** Spoken after the value ("%" for the unit sliders); the drawn readout is separate. */
        String unit="";
        Slider(Context c,int min,int max){super(c);this.min=min;this.max=max;setFocusable(true);reach(this);}
        /** Accessibility step: a tenth of the range. */
        int step(){return Math.max(1,(max-min)/10);}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),dp(getContext(),44));}
        float thumb(){return px(getContext(),14);}
        float xOf(int v){float t=thumb()*1.3f;return t+(getWidth()-2*t)*(v-min)/(float)(max-min);}
        void set(int next,boolean user){
            next=Math.max(min,Math.min(max,next));if(next==value)return;
            if(user&&(next==0||next==min||next==max||(value<0)!=(next<0)))performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            value=next;invalidate();setStateDescription(value+unit);if(listener!=null)listener.changed(value);
        }
        void grip(boolean on){hold=Ios.animate(hold,held,on?1:0,on?SNAPPY:BOUNCY,a->{held=(float)a.getAnimatedValue();invalidate();});}
        @Override public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){getParent().requestDisallowInterceptTouchEvent(true);grip(true);}
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE){float t=thumb()*1.3f;set(Math.round(min+(max-min)*(e.getX()-t)/Math.max(1,getWidth()-2*t)),true);return true;}
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){grip(false);return true;}
            return false;
        }
        @Override protected void onDraw(Canvas c){
            float d=px(getContext(),1),cy=getHeight()/2f,t=thumb(),edge=t*1.3f;
            paint.clearShadowLayer();paint.setColor(0x47787880);r.set(edge,cy-2*d,getWidth()-edge,cy+2*d);c.drawRoundRect(r,2*d,2*d,paint);
            float zero=xOf(Math.max(min,Math.min(max,0))),x=xOf(value);paint.setColor(LABEL);r.set(Math.min(zero,x),cy-2*d,Math.max(zero,x),cy+2*d);c.drawRoundRect(r,2*d,2*d,paint);
            // iOS 26: the knob widens into a capsule and turns to clear glass while it is dragged.
            float hw=t*(1+.3f*held),hh=t*(1+.08f*held);r.set(x-hw,cy-hh,x+hw,cy+hh);
            paint.setShadowLayer(4*d,0,2*d,0x4d000000);paint.setColor(mix(LABEL,0xd9f5f5f7,held));c.drawRoundRect(r,hh,hh,paint);
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(info);info.setClassName(SeekBar.class.getName());
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,min,max,value));info.setStateDescription(value+unit);
            if(value<max)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);if(value>min)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            if(isEnabled())info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
        }
        @Override public boolean performAccessibilityAction(int action,Bundle args){
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==android.R.id.accessibilityActionScrollRight){set(value+step(),false);return true;}
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD||action==android.R.id.accessibilityActionScrollLeft){set(value-step(),false);return true;}
            if(action==android.R.id.accessibilityActionSetProgress&&args!=null&&args.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)){
                set(Math.round(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)),false);return true;}
            return super.performAccessibilityAction(action,args);
        }
    }

    /** UIAlertController look: 280 pt card, centered title and message, one blue action; grows in from 1.15x. */
    static void alert(Activity a,String title,String message,String action){
        Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setCanceledOnTouchOutside(false);
        FrameLayout frame=new FrameLayout(a);LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);card.setBackground(new Shape(ALERT,px(a,GROUPED)));
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);card.setClipToOutline(true);
        frame.addView(card,new FrameLayout.LayoutParams(dp(a,280),-2,Gravity.CENTER));
        TextView head=text(a,title,17,SEMIBOLD,LABEL);head.setGravity(Gravity.CENTER);head.setPadding(dp(a,18),dp(a,20),dp(a,18),dp(a,4));card.addView(head);
        TextView body=text(a,message,13,REGULAR,0xd9ffffff);body.setGravity(Gravity.CENTER);body.setLineSpacing(px(a,2),1);body.setPadding(dp(a,18),dp(a,2),dp(a,18),dp(a,18));
        ScrollView scroll=new ScrollView(a);scroll.addView(body);card.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));
        card.addView(hairline(a),new LinearLayout.LayoutParams(-1,Math.max(1,dp(a,.5f))));
        TextView button=text(a,action,17,SEMIBOLD,BLUE);button.setGravity(Gravity.CENTER);button.setClickable(true);button.setFocusable(true);button.setAccessibilityDelegate(BUTTON);button.setOnClickListener(v->dialog.dismiss());
        card.addView(pressable(button),new LinearLayout.LayoutParams(-1,dp(a,48)));
        dialog.setContentView(frame);
        Window w=dialog.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(-1,-2);w.setDimAmount(.45f);w.setWindowAnimations(android.R.style.Animation_Toast);
        dialog.setOnShowListener(x->{card.setScaleX(1.15f);card.setScaleY(1.15f);card.setAlpha(0);spring(card,SMOOTH).scaleX(1).scaleY(1).alpha(1);});
        dialog.show();
    }

    /** iOS 26 segmented control: a glass track and a liquid thumb. The thumb is simulated as a damped spring, so it keeps
     *  its velocity when let go, stretches along its travel and settles with a wobble; it can be dragged, and swells
     *  into a clear lens while held. */
    static final class Segmented extends View {
        interface Listener{void selected(int index);}
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF r=new RectF();final int slop;
        String[] items;int selected,hovered;float position,velocity,target,held,downX,lastX;long frame,lastTime;boolean settling,onThumb,dragging;Listener listener;ValueAnimator hold;
        Segmented(Context c,String... items){
            super(c);this.items=items;setBackground(glass(c,-1));setClickable(true);setFocusable(true);slop=ViewConfiguration.get(c).getScaledTouchSlop();
            // 13 sp: follows the system font size, capped at 1.3x so the labels stay inside the capsule.
            text.setTypeface(font(SEMIBOLD));text.setTextSize(px(c,13)*Math.max(1,Math.min(1.3f,c.getResources().getConfiguration().fontScale)));text.setTextAlign(Paint.Align.CENTER);
            setStateDescription(items.length>0?items[0]:null);reach(this);
        }
        @Override protected void onMeasure(int w,int h){Paint.FontMetrics m=text.getFontMetrics();setMeasuredDimension(MeasureSpec.getSize(w),Math.max(dp(getContext(),36),Math.round(m.descent-m.ascent+px(getContext(),16))));}
        float segment(){return getWidth()/(float)Math.max(1,items.length);}
        void select(int index,boolean animate){
            if(index<0||index>=items.length)return;selected=hovered=index;target=index;setStateDescription(items[index]);
            if(!animate||still()){position=index;velocity=0;settling=false;invalidate();return;}
            settle();
        }
        void settle(){if(!settling){settling=true;frame=0;}postInvalidateOnAnimation();}
        /** Semi-implicit Euler at 4 substeps per frame: response .42 s, damping .62, a visible but short wobble. */
        void step(){
            long now=AnimationUtils.currentAnimationTimeMillis();float dt=frame==0?1/60f:Math.min(.034f,(now-frame)/1000f);frame=now;
            double omega=2*Math.PI/.42,k=omega*omega,c=2*.62*omega;float h=dt/4;
            for(int i=0;i<4;i++){velocity+=(float)(-k*(position-target)-c*velocity)*h;position+=velocity*h;}
            if(Math.abs(position-target)<.002f&&Math.abs(velocity)<.02f){position=target;velocity=0;settling=false;}
        }
        void grip(boolean on){hold=Ios.animate(hold,held,on?1:0,on?SNAPPY:BOUNCY,a->{held=(float)a.getAnimatedValue();invalidate();});}
        void commit(int i){
            i=Math.max(0,Math.min(items.length-1,i));target=i;
            if(i!=selected){selected=hovered=i;performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);setStateDescription(items[i]);if(listener!=null)listener.selected(i);}
            if(still()){position=i;velocity=0;invalidate();}else settle();
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            float seg=segment(),x=e.getX();
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN->{downX=lastX=x;lastTime=e.getEventTime();dragging=false;onThumb=Math.abs(x-(position+.5f)*seg)<seg/2;
                    if(onThumb){getParent().requestDisallowInterceptTouchEvent(true);if(!still())grip(true);}}
                case MotionEvent.ACTION_MOVE->{
                    if(onThumb&&!dragging&&Math.abs(x-downX)>slop)dragging=true;
                    if(dragging){
                        // Past either end the thumb follows on a rubber band; velocity is smoothed for the stretch.
                        float p=x/seg-.5f,last=items.length-1;p=p<0?rubber(p,.6f):p>last?last+rubber(p-last,.6f):p;
                        float dt=Math.max(1,e.getEventTime()-lastTime)/1000f;velocity=.6f*(p-position)/dt+.4f*velocity;position=p;lastX=x;lastTime=e.getEventTime();settling=false;
                        int near=Math.round(clamp(p,0,last));if(near!=hovered){hovered=near;performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);}invalidate();
                    }
                }
                case MotionEvent.ACTION_UP->{grip(false);commit(dragging?Math.round(clamp(position+velocity*.08f,0,items.length-1)):(int)clamp(x/seg,0,items.length-1));dragging=false;performClick();}
                case MotionEvent.ACTION_CANCEL->{grip(false);dragging=false;commit(selected);}
            }
            return true;
        }
        @Override public boolean performClick(){return super.performClick();}
        @Override protected void onDraw(Canvas c){
            if(settling)step();
            if(dragging&&Math.abs(velocity)>.01f)velocity*=.82f;  // a finger at rest lets the stretch relax
            float d=px(getContext(),1),seg=segment(),speed=Math.min(1,Math.abs(velocity)/6f),stretch=.38f*speed;
            float w=(seg-4*d)*(1+stretch)*(1+.06f*held),h=(getHeight()-4*d)*(1-.22f*speed)+3*d*held,cx=(position+.5f)*seg,cy=getHeight()/2f;
            w=Math.min(w,getWidth()-2*d);r.set(cx-w/2,cy-h/2,cx+w/2,cy+h/2);
            paint.setStyle(Paint.Style.FILL);paint.setColor(mix(0xfff5f5f7,0x3dffffff,held));paint.setShadowLayer(3*d,0,d,mix(0x33000000,0x00000000,held));c.drawRoundRect(r,r.height()/2,r.height()/2,paint);
            if(held>.01f){paint.clearShadowLayer();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);paint.setColor(mix(0x00ffffff,0x8cffffff,held));c.drawRoundRect(r,r.height()/2,r.height()/2,paint);paint.setStyle(Paint.Style.FILL);}
            for(int i=0;i<items.length;i++){float near=Math.max(0,1-Math.abs(position-i))*(1-held);text.setColor(mix(LABEL,0xff0b0b0d,near));
                c.drawText(items[i],seg*i+seg/2,cy-(text.descent()+text.ascent())/2,text);}
            if(settling||dragging&&Math.abs(velocity)>.01f)postInvalidateOnAnimation();
        }
        /** TalkBack reads the choices and the selected one; double tap moves to the next, the actions menu or a scroll gesture picks one. */
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(info);info.setText(String.join(" / ",items));info.setStateDescription(items[selected]);
            if(selected<items.length-1)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);if(selected>0)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            for(int i=0;i<items.length&&i<SEGMENT_ACTIONS.length;i++)if(i!=selected)info.addAction(new AccessibilityNodeInfo.AccessibilityAction(SEGMENT_ACTIONS[i],items[i]));
        }
        @Override public boolean performAccessibilityAction(int action,Bundle args){
            if(action==AccessibilityNodeInfo.ACTION_CLICK){commit((selected+1)%items.length);return true;}
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD&&selected<items.length-1){commit(selected+1);return true;}
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD&&selected>0){commit(selected-1);return true;}
            for(int i=0;i<items.length&&i<SEGMENT_ACTIONS.length;i++)if(action==SEGMENT_ACTIONS[i]){commit(i);return true;}
            return super.performAccessibilityAction(action,args);
        }
    }
    static final int[] SEGMENT_ACTIONS={R.id.a11y_segment_0,R.id.a11y_segment_1,R.id.a11y_segment_2,R.id.a11y_segment_3,R.id.a11y_segment_4,R.id.a11y_segment_5};
    /** UISearchBar: capsule fill, magnifier, clear button. Fills the given container and returns its field. */
    static EditText searchField(Context c,String hint,LinearLayout container){
        container.setGravity(Gravity.CENTER_VERTICAL);container.setBackground(new Shape(0x3d767680,-1));container.setPadding(dp(c,12),0,dp(c,6),0);
        ImageView icon=new ImageView(c);icon.setImageDrawable(new Glyph(c,Glyph.SEARCH,SECONDARY,18));container.addView(icon);
        EditText field=new EditText(c);field.setHint(hint);field.setHintTextColor(SECONDARY);field.setTextColor(LABEL);field.setTextSize(TypedValue.COMPLEX_UNIT_SP,17);field.setTypeface(font(REGULAR));
        field.setSingleLine(true);field.setBackground(null);field.setPadding(dp(c,8),0,dp(c,8),0);field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);container.addView(field,new LinearLayout.LayoutParams(0,-1,1));
        ImageView clear=new ImageView(c);clear.setImageDrawable(new Glyph(c,Glyph.CLOSE,SECONDARY,16));clear.setContentDescription(hint!=null&&hint.matches("(?s).*[А-Яа-яЁё].*")?"Очистить":"Clear");  // the hint is already in the UI language
        clear.setAccessibilityDelegate(BUTTON);clear.setFocusable(true);clear.setVisibility(View.GONE);clear.setPadding(dp(c,6),0,dp(c,6),0);container.addView(clear,new LinearLayout.LayoutParams(-2,-1));
        clear.setOnClickListener(v->field.setText(""));
        field.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int n){}public void onTextChanged(CharSequence s,int a,int b,int n){clear.setVisibility(s.length()>0?View.VISIBLE:View.GONE);}public void afterTextChanged(android.text.Editable s){}});
        return field;
    }
    /** Filter chip; a selected chip is filled white like the iOS 26 capsules. */
    static TextView chip(Context c,String title){
        TextView t=text(c,title,15,MEDIUM,LABEL);t.setGravity(Gravity.CENTER);t.setPadding(dp(c,14),0,dp(c,14),0);t.setBackground(glass(c,-1));legible(t);t.setClickable(true);t.setFocusable(true);t.setAccessibilityDelegate(BUTTON);return reach(pressable(t));
    }
    static void chipSelected(TextView chip,boolean on){
        if(on==chip.isSelected()&&chip.getBackground()!=null)return;
        chip.setSelected(on);chip.setTextColor(on?0xff0b0b0d:LABEL);if(on)chip.setShadowLayer(0,0,0,0);else legible(chip);chip.setBackground(on?new Shape(0xfff5f5f7,-1):glass(chip.getContext(),-1));
    }
    /** Inset grouped section of a UITableView: caption above, a rounded card, hairlines between rows. */
    static LinearLayout group(Context c,LinearLayout parent,String caption){
        if(caption!=null){TextView t=text(c,caption.toUpperCase(java.util.Locale.ROOT),13,REGULAR,SECONDARY);t.setPadding(dp(c,16),dp(c,22),dp(c,16),dp(c,7));parent.addView(t);}
        LinearLayout card=new LinearLayout(c);card.setOrientation(LinearLayout.VERTICAL);card.setBackground(new Shape(0xff1c1c1e,px(c,GROUPED)));card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);card.setClipToOutline(true);
        parent.addView(card,new LinearLayout.LayoutParams(-1,-2));return card;
    }
    static void footer(Context c,LinearLayout parent,String note){TextView t=text(c,note,13,REGULAR,SECONDARY);t.setPadding(dp(c,16),dp(c,7),dp(c,16),0);parent.addView(t);}
    static void separate(Context c,LinearLayout card){
        if(card.getChildCount()==0)return;View line=hairline(c);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Math.max(1,dp(c,.5f)));lp.leftMargin=dp(c,16);card.addView(line,lp);
    }
    /** A table row: title on the left, an accessory (switch, value, control) on the right. */
    static LinearLayout row(Context c,LinearLayout card,String title,View accessory){
        separate(c,card);
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(c,52));row.setPadding(dp(c,16),dp(c,6),dp(c,14),dp(c,6));
        if(title!=null)row.addView(text(c,title,17,REGULAR,LABEL),new LinearLayout.LayoutParams(0,-2,1));
        if(accessory!=null)row.addView(accessory,new LinearLayout.LayoutParams(title==null?0:-2,-2,title==null?1:0));
        card.addView(row);return row;
    }
    /** Labelled 0..1 slider row with a percentage readout. */
    static Slider unitSlider(Context c,LinearLayout card,String title,float value,Slider.Listener listener){
        separate(c,card);
        LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(c,16),dp(c,10),dp(c,14),dp(c,2));card.addView(box);
        LinearLayout head=new LinearLayout(c);box.addView(head);head.addView(text(c,title,15,REGULAR,LABEL),new LinearLayout.LayoutParams(0,-2,1));
        TextView number=text(c,Math.round(value*100)+"%",15,REGULAR,SECONDARY);number.setFontFeatureSettings("tnum");head.addView(number);
        Slider s=new Slider(c,0,100);s.value=Math.round(value*100);s.unit="%";s.setStateDescription(s.value+"%");s.setContentDescription(title);box.addView(s);
        s.listener=v->{number.setText(v+"%");listener.changed(v);};return s;
    }

    private Ios(){}
}
