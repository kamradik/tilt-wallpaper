package dev.solotilt.personal;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Alternative app icons: each is a launcher activity-alias of MainActivity with its own icon (AndroidManifest), and
 * exactly one alias is enabled. Switching enables the new one before disabling the old, so the app never has no
 * launcher entry; the launcher may take a few seconds to show the new icon.
 */
final class AppIcons {
    static final String[] ALIASES={".IconDusk",".IconOcean",".IconSunset",".IconGraphite"};
    static final String[] RU={"Сумерки","Океан","Закат","Графит"},EN={"Dusk","Ocean","Sunset","Graphite"};

    static ComponentName component(Context c,int i){return new ComponentName(c.getPackageName(),c.getPackageName()+ALIASES[i]);}
    /** The enabled icon; the manifest default (Dusk) when nothing was changed. */
    static int current(Context c){
        PackageManager pm=c.getPackageManager();
        for(int i=0;i<ALIASES.length;i++){
            int state=pm.getComponentEnabledSetting(component(c,i));
            if(state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED||state==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT&&i==0)return i;
        }
        return 0;
    }
    static void set(Context c,int index){
        PackageManager pm=c.getPackageManager();
        pm.setComponentEnabledSetting(component(c,index),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
        for(int i=0;i<ALIASES.length;i++)if(i!=index)pm.setComponentEnabledSetting(component(c,i),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
    }
    private AppIcons(){}
}
