package dev.solotilt.personal;

/** Latest-value mailbox. Lock holds only primitive copies, never GPU/disk work. */
final class RenderState {
    private float x,y,wakeX,wakeY; private long timestamp,count;
    synchronized boolean set(float x,float y,long timestamp){this.x=x;this.y=y;this.timestamp=timestamp;count++;boolean changed=Math.abs(x-wakeX)>=.04f||Math.abs(y-wakeY)>=.04f;if(changed){wakeX=x;wakeY=y;}return changed;}
    synchronized void read(Frame out){out.x=x;out.y=y;out.timestamp=timestamp;out.count=count;}
    static final class Frame { float x,y;long timestamp,count; }
}
