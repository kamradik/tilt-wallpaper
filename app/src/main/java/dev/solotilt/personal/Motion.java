package dev.solotilt.personal;
import android.content.Context;
import android.hardware.*;
import android.os.*;
import android.view.Display;

final class Motion implements SensorEventListener {
    interface Listener { void onTilt(float x,float y,long sensorNanos); }
    final SensorManager manager; volatile Sensor sensor; final Display display; final Listener listener;
    final HandlerThread thread=new HandlerThread("SoloSensors",android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE);
    final Handler handler;
    final float[] filtered=new float[3],matrix=new float[9],angles=new float[2];
    long timestamp; volatile boolean started; boolean closed;
    /** Shake input: receives how hard the phone is jolted (m/s², gravity removed). Rotation sensors hardly see a
     *  shake, which is mostly a quick back-and-forth move, so it comes from the accelerometer, only while set. */
    volatile java.util.function.DoubleConsumer jolt; Sensor accel; final float[] still=new float[3]; long joltStamp;
    Motion(Context c,Display d,Listener l){
        manager=c.getSystemService(SensorManager.class);display=d;listener=l;
        Sensor selected=null;
        for(int type:new int[]{Sensor.TYPE_GAME_ROTATION_VECTOR,Sensor.TYPE_ROTATION_VECTOR,Sensor.TYPE_GRAVITY,Sensor.TYPE_ACCELEROMETER}){
            selected=manager.getDefaultSensor(type);if(selected!=null)break;
        }
        sensor=selected;thread.start();handler=new Handler(thread.getLooper());
    }
    void start(){if(closed||started||sensor==null)return;started=true;handler.post(()->{timestamp=0;if(started){boolean registered=false;for(int type:new int[]{Sensor.TYPE_GAME_ROTATION_VECTOR,Sensor.TYPE_ROTATION_VECTOR,Sensor.TYPE_GRAVITY,Sensor.TYPE_ACCELEROMETER}){Sensor candidate=manager.getDefaultSensor(type);if(candidate!=null&&manager.registerListener(this,candidate,16667,0,handler)){sensor=candidate;registered=true;break;}}if(registered&&jolt!=null){Sensor a=manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);if(a==null)a=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);joltStamp=0;if(a!=null&&manager.registerListener(this,a,SensorManager.SENSOR_DELAY_GAME,0,handler))accel=a;}if(!started)manager.unregisterListener(this);else started=registered;}});}
    void stop(){started=false;manager.unregisterListener(this);handler.post(()->{if(!started)manager.unregisterListener(this);});}
    void close(){closed=true;stop();thread.quitSafely();}
    String description(){return sensor==null?"unavailable":sensor.getName()+" (type "+sensor.getType()+")";}
    public void onAccuracyChanged(Sensor s,int accuracy){}
    public void onSensorChanged(SensorEvent e){
        if(e.sensor==accel&&e.sensor!=sensor){onJolt(e);return;}
        if(!started||e.timestamp<=timestamp)return;
        int type=sensor.getType();
        if(type==Sensor.TYPE_GAME_ROTATION_VECTOR||type==Sensor.TYPE_ROTATION_VECTOR){
            SensorManager.getRotationMatrixFromVector(matrix,e.values);
            filtered[0]=matrix[6]*9.81f;filtered[1]=matrix[7]*9.81f;filtered[2]=matrix[8]*9.81f;
        }else{
            float a=timestamp==0?1:1-(float)Math.exp(-Math.min(.2,(e.timestamp-timestamp)*1e-9)/.055);
            for(int i=0;i<3;i++)filtered[i]=type==Sensor.TYPE_GRAVITY?e.values[i]:filtered[i]+a*(e.values[i]-filtered[i]);
        }
        timestamp=e.timestamp;
        if(FoldMath.gravity(filtered[0],filtered[1],filtered[2],display==null?0:display.getRotation(),angles))listener.onTilt(angles[0],angles[1],timestamp);
    }
    void onJolt(SensorEvent e){
        java.util.function.DoubleConsumer out=jolt;if(!started||out==null)return;
        float x=e.values[0],y=e.values[1],z=e.values[2];
        if(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER){
            // No linear-acceleration sensor: remove gravity with a slow low-pass of the raw reading.
            float k=joltStamp==0?1:1-(float)Math.exp(-Math.min(.2,(e.timestamp-joltStamp)*1e-9)/.4);
            for(int i=0;i<3;i++)still[i]+=k*(e.values[i]-still[i]);x-=still[0];y-=still[1];z-=still[2];
        }
        joltStamp=e.timestamp;out.accept(Math.sqrt(x*x+y*y+z*z));
    }
}
