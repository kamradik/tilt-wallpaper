package dev.solotilt.personal;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.*;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.List;

/**
 * Home screen widget: the home screen's current effect, Next effect and Pause/Resume. The buttons go to
 * ParallaxWidget.Action, a receiver other apps cannot reach; refresh() is called whenever the settings or the pause
 * change, so the widget never polls (updatePeriodMillis 0).
 */
public final class ParallaxWidget extends AppWidgetProvider {
    static final String NEXT="dev.solotilt.personal.widget.NEXT",PAUSE="dev.solotilt.personal.widget.PAUSE";

    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){for(int id:ids)manager.updateAppWidget(id,views(c));}

    /** Redraws every placed widget; cheap when none is placed. */
    static void refresh(Context c){
        try{
            AppWidgetManager m=AppWidgetManager.getInstance(c);if(m==null)return;
            int[] ids=m.getAppWidgetIds(new ComponentName(c,ParallaxWidget.class));if(ids.length==0)return;
            RemoteViews v=views(c);for(int id:ids)m.updateAppWidget(id,v);
        }catch(RuntimeException ignored){} // no widget host (tests, some launchers)
    }
    static RemoteViews views(Context c){
        WallpaperConfig config=WallpaperConfig.load(c);boolean en=config.en(),paused=ImageStore.prefs(c).getBoolean(PerformanceController.KEY_PAUSED,false);
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget);
        Effects.Effect e=config.home.effect();Effects.Effect top=config.home.overlayEffect();
        v.setTextViewText(R.id.widget_effect,e.name(en)+(top==null?"":" + "+top.name(en)));
        v.setTextViewText(R.id.widget_title,paused?(en?"Parallaxia · paused":"Parallaxia · пауза"):"Parallaxia");
        v.setImageViewResource(R.id.widget_pause,paused?R.drawable.widget_play:R.drawable.widget_pause);
        v.setContentDescription(R.id.widget_pause,paused?(en?"Resume wallpaper":"Продолжить обои"):(en?"Pause wallpaper":"Пауза обоев"));
        v.setContentDescription(R.id.widget_next,en?"Next effect":"Следующий эффект");
        v.setOnClickPendingIntent(R.id.widget_next,action(c,NEXT,1));v.setOnClickPendingIntent(R.id.widget_pause,action(c,PAUSE,2));
        Intent open=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,3,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
        return v;
    }
    static PendingIntent action(Context c,String what,int code){
        return PendingIntent.getBroadcast(c,code,new Intent(what).setClass(c,Action.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** The order Next walks: the favorites when there are at least two, else the whole catalog. */
    static List<String> cycle(WallpaperConfig config){
        List<String> ids=new ArrayList<>();
        if(config.favorites.size()>=2)ids.addAll(config.favorites);else for(Effects.Effect e:Effects.ALL)ids.add(e.id);
        return ids;
    }
    /** The effect after current in the cycle (the first one when current is not in it). Pure, unit-tested. */
    static String next(List<String> cycle,String current){
        if(cycle.isEmpty())return current;int i=cycle.indexOf(current);return cycle.get((i+1)%cycle.size());
    }

    /** Not exported: only the widget's own pending intents reach it. */
    public static final class Action extends BroadcastReceiver {
        @Override public void onReceive(Context c,Intent i){
            if(NEXT.equals(i.getAction())){
                WallpaperConfig config=WallpaperConfig.load(c);String id=next(cycle(config),config.home.effect);
                config.home.effect=id;config.used(id);config.save(c);
            }else if(PAUSE.equals(i.getAction())){
                SharedPreferences p=ImageStore.prefs(c);p.edit().putBoolean(PerformanceController.KEY_PAUSED,!p.getBoolean(PerformanceController.KEY_PAUSED,false)).apply();
            }
            refresh(c);
        }
    }
}
