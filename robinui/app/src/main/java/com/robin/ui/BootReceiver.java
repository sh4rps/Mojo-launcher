package com.robin.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Prefs.init(c);
        if (Prefs.island() && Settings.canDrawOverlays(c)) IslandService.start(c);
    }
}
