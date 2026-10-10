package dev.solotilt.personal;

import android.animation.*;
import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

/** iOS-style tokens, motion and controls drawn with the platform canvas, so the APK stays dependency-free. */
final class Ios {
    // Dark-mode system colors from Apple's Human Interface Guidelines.
    static final int LABEL=0xffffffff,SECONDARY=0x99ebebf5,TERTIARY=0x4debebf5,FILL=0x5c787880,SEPARATOR=0x5c545458,
        BLUE=0xff0a84ff,GREEN=0xff30d158,ORANGE=0xffff9f0a,ALERT=0xf0262628,SHEET=0xc4141416;
    static final int REGULAR=400,MEDIUM=500,SEMIBOLD=600,BOLD=700;

    static float px(Context c,float dp){return dp*c.getResources().getDisplayMetrics().density;}
    static int dp(Context c,float dp){return Math.round(px(c,dp));}
    static Typeface font(int weight){return Typeface.create(Typeface.SANS_SERIF,weight,false);}
    static int mix(int a,int b,float t){return (int)new ArgbEvaluator().evaluate(Math.max(0,Math.min(1,t)),a,b);}

    /** Text with SF-like tracking: tight for large titles, neutral for captions. */
    static TextView text(Context c,CharSequence value,float sp,int weight,int color){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);t.setTypeface(font(weight));t.setTextColor(color);
        t.setLetterSpacing(sp>=28?-.015f:sp>=15?-.01f:0);return t;
    }
    /** Crossfades a label instead of snapping it, like UIKit's transition on text changes. */
    static void swap(TextView t,CharSequence value){
        if(value.toString().contentEquals(t.getText()))return;
        t.animate().cancel();t.animate().alpha(0).setDuration(110).setStartDelay(0).withEndAction(()->{t.setText(value);t.animate().alpha(1).setDuration(180).setInterpolator(SMOOTH);}).start();
    }

    /** Damped spring in SwiftUI terms: response is the period in seconds, damping the fraction of critical. */
    static final class Spring implements TimeInterpolator {
        final double omega,zeta;final long duration;
        Spring(double response,double damping){omega=2*Math.PI/response;zeta=Math.min(.99,damping);duration=Math.round(1000*Math.min(1.4,6/(zeta*omega)));}
        @Override public float getInterpolation(float t){
            if(t>=1)return 1;double s=t*duration/1000.0,wd=omega*Math.sqrt(1-zeta*zeta);
            return (float)(1-Math.exp(-zeta*omega*s)*(Math.cos(wd*s)+zeta*omega/wd*Math.sin(wd*s)));
        }
    }
    static final Spring SMOOTH=new Spring(.5,.9),BOUNCY=new Spring(.42,.68),SNAPPY=new Spring(.26,.92);
    static ViewPropertyAnimator spring(View v,Spring s){v.animate().cancel();return v.animate().setInterpolator(s).setDuration(s.duration).setStartDelay(0);}
    /** Staggered entrance: fade and settle upward, like a sheet's content arriving. */
    static void rise(View v,int order){v.setAlpha(0);v.setTranslationY(px(v.getContext(),18));spring(v,SMOOTH).alpha(1).translationY(0).setStartDelay(90+55L*order);}

    /** Pressed state of iOS 26 controls: a quick shrink and dim, released on a bouncy spring. */
    static <T extends View> T pressable(T v){
        v.setOnTouchListener((view,e)->{
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN)spring(view,SNAPPY).scaleX(.95f).scaleY(.95f).alpha(.78f);
            else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)spring(view,BOUNCY).scaleX(1).scaleY(1).alpha(view.isEnabled()?1:.45f);
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

    /** Filled continuous-corner shape; glass() adds the specular sheen and gradient hairline of iOS materials. */
    static final class Shape extends Drawable {
        final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),stroke=new Paint(Paint.ANTI_ALIAS_FLAG),sheen=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path=new Path();final RectF box=new RectF();final float radius;boolean glass;
        Shape(int color,float radius){fill.setColor(color);this.radius=radius;stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(0);}
        Shape stroke(float width,int color){stroke.setStrokeWidth(width);stroke.setColor(color);return this;}
        Shape glass(float width){glass=true;stroke.setStrokeWidth(width);stroke.setColor(LABEL);return this;}
        void setColor(int color){fill.setColor(color);invalidateSelf();}
        @Override protected void onBoundsChange(Rect b){
            float inset=stroke.getStrokeWidth()/2;box.set(b.left+inset,b.top+inset,b.right-inset,b.bottom-inset);squircle(path,box,radius);
            if(glass&&!b.isEmpty()){
                stroke.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,new int[]{0x66ffffff,0x14ffffff,0x0affffff,0x38ffffff},new float[]{0,.35f,.65f,1},Shader.TileMode.CLAMP));
                sheen.setShader(new LinearGradient(0,b.top,0,b.top+Math.min(b.height(),b.width())*.6f,0x1cffffff,0x00ffffff,Shader.TileMode.CLAMP));
            }
        }
        @Override public void draw(Canvas c){c.drawPath(path,fill);if(glass)c.drawPath(path,sheen);if(stroke.getStrokeWidth()>0)c.drawPath(path,stroke);}
        @Override public void getOutline(Outline o){Rect b=getBounds();if(radius<0)o.setRoundRect(b,Math.min(b.width(),b.height())/2f);else if(!path.isEmpty())o.setPath(path);}
        @Override public void setAlpha(int a){fill.setAlpha(a);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter f){fill.setColorFilter(f);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    static Shape glass(Context c,float radiusDp){return new Shape(0x29ffffff,radiusDp<0?-1:px(c,radiusDp)).glass(Math.max(1,px(c,.7f)));}

    /** Line icons in the spirit of SF Symbols, drawn on a unit grid. */
    static final class Glyph extends Drawable {
        static final int PLUS=0,BACK=1,LINK=2,PLAY=3,PAUSE=4,PHOTO=5,INFO=6,SEARCH=7,HEART=8,HEART_FILL=9,GEAR=10,CHEVRON=11,CLOSE=12,VIDEO=13;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path();final int kind,size;
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
                case INFO->{path.moveTo(.5f,.46f);path.lineTo(.5f,.76f);c.drawCircle(.5f,.27f,.075f,fillPaint());paint.setStrokeWidth(.12f);}
                case SEARCH->{path.addCircle(.44f,.44f,.24f,Path.Direction.CW);path.moveTo(.62f,.62f);path.lineTo(.82f,.82f);paint.setStrokeWidth(.1f);}
                case HEART,HEART_FILL->{path.moveTo(.5f,.82f);path.cubicTo(.08f,.56f,.12f,.18f,.34f,.2f);path.cubicTo(.43f,.2f,.48f,.27f,.5f,.32f);path.cubicTo(.52f,.27f,.57f,.2f,.66f,.2f);path.cubicTo(.88f,.18f,.92f,.56f,.5f,.82f);path.close();paint.setStrokeWidth(.08f);if(kind==HEART_FILL)paint.setStyle(Paint.Style.FILL_AND_STROKE);}
                case GEAR->{for(int i=0;i<8;i++){double t=i*Math.PI/4;path.moveTo(.5f+(float)Math.cos(t)*.3f,.5f+(float)Math.sin(t)*.3f);path.lineTo(.5f+(float)Math.cos(t)*.4f,.5f+(float)Math.sin(t)*.4f);}path.addCircle(.5f,.5f,.25f,Path.Direction.CW);path.addCircle(.5f,.5f,.09f,Path.Direction.CW);paint.setStrokeWidth(.09f);}
                case CHEVRON->{path.moveTo(.38f,.2f);path.lineTo(.66f,.5f);path.lineTo(.38f,.8f);paint.setStrokeWidth(.11f);}
                case CLOSE->{path.moveTo(.28f,.28f);path.lineTo(.72f,.72f);path.moveTo(.72f,.28f);path.lineTo(.28f,.72f);paint.setStrokeWidth(.1f);}
                case VIDEO->{path.addRoundRect(.1f,.26f,.66f,.74f,.1f,.1f,Path.Direction.CW);path.moveTo(.68f,.44f);path.lineTo(.9f,.32f);path.lineTo(.9f,.68f);path.lineTo(.68f,.56f);paint.setStrokeWidth(.075f);}
            }
            c.drawPath(path,paint);c.restore();
        }
        Paint fillPaint(){Paint p=new Paint(paint);p.setStyle(Paint.Style.FILL);return p;}
        @Override public void setAlpha(int a){paint.setAlpha(a);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    /** Capsule button: prominent is the white fill of iOS 26 primary actions, otherwise a glass capsule. */
    static TextView capsule(Context c,String title,boolean prominent){
        TextView b=text(c,title,16,SEMIBOLD,prominent?0xff0b0b0d:LABEL);b.setGravity(Gravity.CENTER);b.setMaxLines(1);
        b.setAutoSizeTextTypeUniformWithConfiguration(12,16,1,TypedValue.COMPLEX_UNIT_SP);b.setPadding(dp(c,14),0,dp(c,14),0);
        b.setBackground(prominent?new Shape(0xfff5f5f7,-1):glass(c,-1));b.setClickable(true);b.setFocusable(true);
        b.setOutlineProvider(ViewOutlineProvider.BACKGROUND);return pressable(b);
    }
    /** Round glass button holding one glyph, like the toolbar buttons of iOS 26. */
    static ImageView circle(Context c,int glyph,String description){
        ImageView v=new ImageView(c);v.setImageDrawable(new Glyph(c,glyph,LABEL,20));v.setScaleType(ImageView.ScaleType.CENTER);
        v.setBackground(glass(c,-1));v.setContentDescription(description);v.setClickable(true);v.setFocusable(true);return pressable(v);
    }
    static View hairline(Context c){View v=new View(c);v.setBackgroundColor(SEPARATOR);return v;}

    /** UISwitch: 51x31 pt track, thumb on a spring, stretched while held. */
    static final class Toggle extends View {
        interface Listener{void changed(boolean on);}
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF r=new RectF();boolean on;float progress,stretch;Listener listener;ValueAnimator move,hold;
        Toggle(Context c){super(c);setClickable(true);setFocusable(true);}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(dp(getContext(),51)+getPaddingLeft()+getPaddingRight(),dp(getContext(),31)+getPaddingTop()+getPaddingBottom());}
        void setOn(boolean value,boolean animate){
            on=value;if(move!=null)move.cancel();
            if(!animate){progress=on?1:0;invalidate();return;}
            move=ValueAnimator.ofFloat(progress,on?1:0);move.setInterpolator(BOUNCY);move.setDuration(BOUNCY.duration);move.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});move.start();
        }
        @Override public void setPressed(boolean pressed){
            boolean changed=pressed!=isPressed();super.setPressed(pressed);if(!changed)return;if(hold!=null)hold.cancel();
            hold=ValueAnimator.ofFloat(stretch,pressed?1:0);hold.setInterpolator(SNAPPY);hold.setDuration(SNAPPY.duration);hold.addUpdateListener(a->{stretch=(float)a.getAnimatedValue();invalidate();});hold.start();
        }
        @Override public boolean performClick(){setOn(!on,true);performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);if(listener!=null)listener.changed(on);return super.performClick();}
        @Override protected void onDraw(Canvas c){
            float d=px(getContext(),1),left=getPaddingLeft(),top=getPaddingTop(),w=51*d,h=31*d;
            paint.clearShadowLayer();paint.setColor(mix(FILL,GREEN,Math.min(1,progress)));r.set(left,top,left+w,top+h);c.drawRoundRect(r,h/2,h/2,paint);
            float knob=27*d,width=knob+7*d*stretch,x=left+2*d+(w-4*d-width)*progress;
            paint.setShadowLayer(3*d,0,1.5f*d,0x45000000);paint.setColor(LABEL);r.set(x,top+2*d,x+width,top+2*d+knob);c.drawRoundRect(r,knob/2,knob/2,paint);
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(Switch.class.getName());info.setCheckable(true);info.setChecked(on);}
    }

    /** UISlider with a bipolar fill from zero and haptic detents at 0 and the ends. */
    static final class Slider extends View {
        interface Listener{void changed(int value);}
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF r=new RectF();final int min,max;int value;Listener listener;
        Slider(Context c,int min,int max){super(c);this.min=min;this.max=max;setFocusable(true);}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),dp(getContext(),44));}
        float thumb(){return px(getContext(),14);}
        float xOf(int v){float t=thumb();return t+(getWidth()-2*t)*(v-min)/(float)(max-min);}
        void set(int next,boolean user){
            next=Math.max(min,Math.min(max,next));if(next==value)return;
            if(user&&(next==0||next==min||next==max||(value<0)!=(next<0)))performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            value=next;invalidate();if(listener!=null)listener.changed(value);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN)getParent().requestDisallowInterceptTouchEvent(true);
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE){float t=thumb();set(Math.round(min+(max-min)*(e.getX()-t)/Math.max(1,getWidth()-2*t)),true);return true;}
            return action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL;
        }
        @Override protected void onDraw(Canvas c){
            float d=px(getContext(),1),cy=getHeight()/2f,t=thumb();
            paint.clearShadowLayer();paint.setColor(0x47787880);r.set(t,cy-2*d,getWidth()-t,cy+2*d);c.drawRoundRect(r,2*d,2*d,paint);
            float zero=xOf(Math.max(min,Math.min(max,0))),x=xOf(value);paint.setColor(LABEL);r.set(Math.min(zero,x),cy-2*d,Math.max(zero,x),cy+2*d);c.drawRoundRect(r,2*d,2*d,paint);
            paint.setShadowLayer(4*d,0,2*d,0x4d000000);c.drawCircle(x,cy,t,paint);
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(info);info.setClassName(SeekBar.class.getName());
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,min,max,value));
            if(value<max)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);if(value>min)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
        @Override public boolean performAccessibilityAction(int action,Bundle args){
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD){set(value+10,false);return true;}
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){set(value-10,false);return true;}
            return super.performAccessibilityAction(action,args);
        }
    }

    /** UIAlertController look: 270 pt card, centered title and message, one blue action; grows in from 1.15x. */
    static void alert(Activity a,String title,String message,String action){
        Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setCanceledOnTouchOutside(false);
        FrameLayout frame=new FrameLayout(a);LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);card.setBackground(new Shape(ALERT,px(a,16)));
        frame.addView(card,new FrameLayout.LayoutParams(dp(a,280),-2,Gravity.CENTER));
        TextView head=text(a,title,17,SEMIBOLD,LABEL);head.setGravity(Gravity.CENTER);head.setPadding(dp(a,18),dp(a,20),dp(a,18),dp(a,4));card.addView(head);
        TextView body=text(a,message,13,REGULAR,0xd9ffffff);body.setGravity(Gravity.CENTER);body.setLineSpacing(px(a,2),1);body.setPadding(dp(a,18),dp(a,2),dp(a,18),dp(a,18));
        ScrollView scroll=new ScrollView(a);scroll.addView(body);card.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));
        card.addView(hairline(a),new LinearLayout.LayoutParams(-1,Math.max(1,dp(a,.5f))));
        TextView button=text(a,action,17,SEMIBOLD,BLUE);button.setGravity(Gravity.CENTER);button.setClickable(true);button.setFocusable(true);button.setOnClickListener(v->dialog.dismiss());
        card.addView(pressable(button),new LinearLayout.LayoutParams(-1,dp(a,48)));
        dialog.setContentView(frame);
        Window w=dialog.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(-1,-2);w.setDimAmount(.45f);w.setWindowAnimations(android.R.style.Animation_Toast);
        dialog.setOnShowListener(x->{card.setScaleX(1.15f);card.setScaleY(1.15f);card.setAlpha(0);spring(card,SMOOTH).scaleX(1).scaleY(1).alpha(1);});
        dialog.show();
    }
    /** UISegmentedControl: tinted track, a white thumb that springs between segments. */
    static final class Segmented extends View {
        interface Listener{void selected(int index);}
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF r=new RectF();
        String[] items;int selected;float position;Listener listener;ValueAnimator move;
        Segmented(Context c,String... items){
            super(c);this.items=items;setBackground(new Shape(0x3d767680,-1));setClickable(true);setFocusable(true);
            text.setTypeface(font(SEMIBOLD));text.setTextSize(px(c,13));text.setTextAlign(Paint.Align.CENTER);
        }
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),dp(getContext(),34));}
        void select(int index,boolean animate){
            if(index<0||index>=items.length)return;selected=index;if(move!=null)move.cancel();
            if(!animate){position=index;invalidate();return;}
            move=ValueAnimator.ofFloat(position,index);move.setInterpolator(SMOOTH);move.setDuration(SMOOTH.duration);move.addUpdateListener(a->{position=(float)a.getAnimatedValue();invalidate();});move.start();
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getActionMasked()==MotionEvent.ACTION_UP){int i=(int)Math.max(0,Math.min(items.length-1,e.getX()/getWidth()*items.length));
                if(i!=selected){select(i,true);performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);if(listener!=null)listener.selected(i);}performClick();}
            return true;
        }
        @Override protected void onDraw(Canvas c){
            float d=px(getContext(),1),seg=getWidth()/(float)items.length;
            r.set(position*seg+2*d,2*d,(position+1)*seg-2*d,getHeight()-2*d);paint.setColor(0xfff5f5f7);paint.setShadowLayer(3*d,0,d,0x33000000);c.drawRoundRect(r,r.height()/2,r.height()/2,paint);
            for(int i=0;i<items.length;i++){float near=Math.max(0,1-Math.abs(position-i));text.setColor(mix(LABEL,0xff0b0b0d,near));
                c.drawText(items[i],seg*i+seg/2,getHeight()/2f-(text.descent()+text.ascent())/2,text);}
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setText(items[selected]);}
    }
    /** UISearchBar: rounded fill, magnifier, clear button. Fills the given container and returns its field. */
    static EditText searchField(Context c,String hint,LinearLayout container){
        container.setGravity(Gravity.CENTER_VERTICAL);container.setBackground(new Shape(0x3d767680,px(c,12)));container.setPadding(dp(c,10),0,dp(c,6),0);
        ImageView icon=new ImageView(c);icon.setImageDrawable(new Glyph(c,Glyph.SEARCH,SECONDARY,18));container.addView(icon);
        EditText field=new EditText(c);field.setHint(hint);field.setHintTextColor(SECONDARY);field.setTextColor(LABEL);field.setTextSize(TypedValue.COMPLEX_UNIT_SP,17);field.setTypeface(font(REGULAR));
        field.setSingleLine(true);field.setBackground(null);field.setPadding(dp(c,8),0,dp(c,8),0);field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);container.addView(field,new LinearLayout.LayoutParams(0,-1,1));
        ImageView clear=new ImageView(c);clear.setImageDrawable(new Glyph(c,Glyph.CLOSE,SECONDARY,16));clear.setContentDescription("Очистить");clear.setVisibility(View.GONE);clear.setPadding(dp(c,6),0,dp(c,6),0);container.addView(clear,new LinearLayout.LayoutParams(-2,-1));
        clear.setOnClickListener(v->field.setText(""));
        field.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int n){}public void onTextChanged(CharSequence s,int a,int b,int n){clear.setVisibility(s.length()>0?View.VISIBLE:View.GONE);}public void afterTextChanged(android.text.Editable s){}});
        return field;
    }
    /** Filter chip; a selected chip is filled white like the iOS 26 capsules. */
    static TextView chip(Context c,String title){
        TextView t=text(c,title,15,MEDIUM,LABEL);t.setGravity(Gravity.CENTER);t.setPadding(dp(c,14),0,dp(c,14),0);t.setBackground(glass(c,-1));t.setClickable(true);t.setFocusable(true);return pressable(t);
    }
    static void chipSelected(TextView chip,boolean on){chip.setSelected(on);chip.setTextColor(on?0xff0b0b0d:LABEL);chip.setBackground(on?new Shape(0xfff5f5f7,-1):glass(chip.getContext(),-1));}
    /** Inset grouped section of a UITableView: caption above, a rounded card, hairlines between rows. */
    static LinearLayout group(Context c,LinearLayout parent,String caption){
        if(caption!=null){TextView t=text(c,caption.toUpperCase(java.util.Locale.ROOT),13,REGULAR,SECONDARY);t.setPadding(dp(c,16),dp(c,22),dp(c,16),dp(c,7));parent.addView(t);}
        LinearLayout card=new LinearLayout(c);card.setOrientation(LinearLayout.VERTICAL);card.setBackground(new Shape(0xff1c1c1e,px(c,14)));card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);card.setClipToOutline(true);
        parent.addView(card,new LinearLayout.LayoutParams(-1,-2));return card;
    }
    static void footer(Context c,LinearLayout parent,String note){TextView t=text(c,note,13,REGULAR,SECONDARY);t.setPadding(dp(c,16),dp(c,7),dp(c,16),0);parent.addView(t);}
    static void separate(Context c,LinearLayout card){
        if(card.getChildCount()==0)return;View line=hairline(c);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Math.max(1,dp(c,.5f)));lp.leftMargin=dp(c,16);card.addView(line,lp);
    }
    /** A table row: title on the left, an accessory (switch, value, control) on the right. */
    static LinearLayout row(Context c,LinearLayout card,String title,View accessory){
        separate(c,card);
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(c,50));row.setPadding(dp(c,16),dp(c,6),dp(c,14),dp(c,6));
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
        Slider s=new Slider(c,0,100);s.value=Math.round(value*100);s.setContentDescription(title);box.addView(s);
        s.listener=v->{number.setText(v+"%");listener.changed(v);};return s;
    }

    private Ios(){}
}
