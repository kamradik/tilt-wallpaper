package dev.solotilt.personal;
import android.content.Context;
import android.hardware.*;
import android.view.Display;

final class Motion implements SensorEventListener {
    interface Listener { void onTilt(float x,float y); }
    final SensorManager manager; final Sensor sensor; final Display display; final Listener listener;
    final float[] filtered=new float[3]; long timestamp; boolean started;
    Motion(Context c, Display d, Listener l){
        manager=c.getSystemService(SensorManager.class);display=d;listener=l;
        Sensor gravity=manager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        sensor=gravity!=null?gravity:manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }
    void start(){if(!started&&sensor!=null){timestamp=0;started=manager.registerListener(this,sensor,33333);}}
    void stop(){manager.unregisterListener(this);started=false;timestamp=0;}
    public void onAccuracyChanged(Sensor s,int a){}
    public void onSensorChanged(SensorEvent e){
        if(!started)return;
        float alpha=timestamp==0?1:1-(float)Math.exp(-Math.min(.2,(e.timestamp-timestamp)*1e-9)/.18);
        for(int i=0;i<3;i++) filtered[i]=sensor.getType()==Sensor.TYPE_GRAVITY?e.values[i]:filtered[i]+alpha*(e.values[i]-filtered[i]);
        timestamp=e.timestamp;
        float[] angles=FoldMath.gravity(filtered[0],filtered[1],filtered[2],display==null?0:display.getRotation());
        if(angles!=null)listener.onTilt(angles[0],angles[1]);
    }
}
