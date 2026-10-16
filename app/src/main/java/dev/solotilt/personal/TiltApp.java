package dev.solotilt.personal;

import android.app.Application;

/** Process entry: installs the offline crash log before any activity, service or wallpaper engine starts. */
public final class TiltApp extends Application {
    @Override public void onCreate(){super.onCreate();CrashLog.install(this);Ios.fonts(this);Accent.load(this);}
}
