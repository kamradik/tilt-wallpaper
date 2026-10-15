package dev.solotilt.personal;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;
import java.util.Random;

/** Invisible target of the "Random effect" app shortcut: picks another effect for the home profile, saves, confirms. */
public final class ShortcutActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        WallpaperConfig config=WallpaperConfig.load(this);
        String next=pick(config.home.effect,new Random());config.home.effect=next;config.used(next);config.save(this);
        Effects.Effect e=Effects.get(next);
        Toast.makeText(getApplicationContext(),(config.en()?"Effect: ":"Эффект: ")+e.name(config.en()),Toast.LENGTH_SHORT).show();
        finish();
    }
    /** Any catalog effect except the current one. */
    static String pick(String current,Random random){
        Effects.Effect[] all=Effects.ALL;if(all.length<2)return all[0].id;
        String id;do id=all[random.nextInt(all.length)].id;while(id.equals(current));return id;
    }
    @Override public void finish(){super.finish();overridePendingTransition(0,0);}
}
