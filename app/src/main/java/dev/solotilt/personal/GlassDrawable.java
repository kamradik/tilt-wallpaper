package dev.solotilt.personal;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Cheap prefiltered photo backdrop; does not read back the GL surface every frame. */
final class GlassDrawable extends Drawable {
    final View host;final Bitmap backdrop;final boolean primary;final Paint paint=new Paint(3);
    final RectF rect=new RectF();final Path clip=new Path();final Matrix matrix=new Matrix();final int[] position=new int[2];
    final float radius;final int tint;
    GlassDrawable(View host,Bitmap backdrop,float radius,boolean primary,boolean bright){this.host=host;this.backdrop=backdrop;this.radius=radius;this.primary=primary;tint=bright?0xe022282d:0xd0141920;}
    @Override protected void onBoundsChange(Rect b){rect.set(b.left+.5f,b.top+.5f,b.right-.5f,b.bottom-.5f);clip.reset();clip.addRoundRect(rect,radius,radius,Path.Direction.CW);}
    @Override public void draw(Canvas c){
        int save=c.save();c.clipPath(clip);
        paint.setColor(primary?0xe8f1f2f3:tint);c.drawRect(rect,paint);
        if(backdrop!=null&&!backdrop.isRecycled()&&!primary){
            host.getLocationOnScreen(position);float w=host.getRootView().getWidth(),h=host.getRootView().getHeight();float scale=Math.max(w/backdrop.getWidth(),h/backdrop.getHeight());
            matrix.setScale(scale,scale);matrix.postTranslate((w-backdrop.getWidth()*scale)/2-position[0],(h-backdrop.getHeight()*scale)/2-position[1]);paint.setAlpha(36);c.drawBitmap(backdrop,matrix,paint);
        }
        paint.setColor(primary?0x55ffffff:0x10ffffff);c.drawRect(rect.left,rect.top,rect.right,rect.top+rect.height()*.42f,paint);c.restoreToCount(save);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(primary?0xccffffff:0x55ffffff);c.drawRoundRect(rect,radius,radius,paint);paint.setStyle(Paint.Style.FILL);
    }
    @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
