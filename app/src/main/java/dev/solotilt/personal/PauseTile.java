package dev.solotilt.personal;

import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: pauses and resumes the wallpaper (pref "paused", read by PerformanceController).
 *  Active while the wallpaper runs. A long press opens the app (QS_TILE_PREFERENCES on MainActivity). */
public final class PauseTile extends TileService {
    @Override public void onStartListening(){super.onStartListening();update();}
    @Override public void onClick(){
        super.onClick();SharedPreferences p=ImageStore.prefs(this);
        p.edit().putBoolean(PerformanceController.KEY_PAUSED,!p.getBoolean(PerformanceController.KEY_PAUSED,false)).apply();update();
    }
    void update(){
        Tile tile=getQsTile();if(tile==null)return;
        boolean paused=ImageStore.prefs(this).getBoolean(PerformanceController.KEY_PAUSED,false),en=WallpaperConfig.load(this).en();
        tile.setState(paused?Tile.STATE_INACTIVE:Tile.STATE_ACTIVE);tile.setLabel("Tilt");
        String sub=paused?(en?"Paused":"Пауза"):(en?"Running":"Работает");tile.setSubtitle(sub);
        tile.setStateDescription(sub);tile.setContentDescription(en?"Tilt wallpaper":"Обои Tilt");tile.updateTile();
    }
}
