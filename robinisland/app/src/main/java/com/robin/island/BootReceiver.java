package com.robin.island;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        try {
            if (c.getSharedPreferences("island", 0).getBoolean("on", false) && Settings.canDrawOverlays(c)) IslandService.start(c);
        } catch (Exception ignored) { }
    }
}
