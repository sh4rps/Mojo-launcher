package com.robin.ui;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        U.dens = getResources().getDisplayMetrics().density;
        Apps.px = U.dp(58);
        Apps.reload(this);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String p = i.getData() == null ? "" : i.getData().getSchemeSpecificPart();
                Apps.icons.evictAll();
                Apps.reload(App.this);
            }
        }, f);
        if (Prefs.island() && android.provider.Settings.canDrawOverlays(this)) IslandService.start(this);
    }
}
