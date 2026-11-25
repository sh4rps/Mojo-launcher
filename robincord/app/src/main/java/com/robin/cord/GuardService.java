package com.robin.cord;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

/**
 * Keeps Robincord's process alive (so battery savers are less likely to kill it and switch the accessibility
 * service off) and watches the accessibility setting. If the service gets switched off it
 *  1. turns it back on by itself when WRITE_SECURE_SETTINGS has been granted, otherwise
 *  2. shows a notification that opens the Accessibility settings in one tap.
 */
public class GuardService extends Service {
    private static final String CH_KEEP = "guard", CH_ALERT = "guard_alert";
    private final Handler h = new Handler(Looper.getMainLooper());
    private ContentObserver obs;

    public static void start(Context c) {
        try {
            Intent i = new Intent(c, GuardService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Throwable ignored) { }
    }

    private String component() { return new ComponentName(this, RobinA11y.class).flattenToString(); }

    private boolean on() {
        try {
            String e = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (e == null) return false;
            for (String s : e.split(":")) if (s.equalsIgnoreCase(component()) || s.startsWith(getPackageName() + "/")) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH_KEEP, "Call recording fix (keeps running)", NotificationManager.IMPORTANCE_MIN));
        nm.createNotificationChannel(new NotificationChannel(CH_ALERT, "Accessibility turned off", NotificationManager.IMPORTANCE_HIGH));
        Notification n = new Notification.Builder(this, CH_KEEP).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Robincord call fix is active").setOngoing(true).build();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(7, n);
        } catch (Throwable t) { stopSelf(); return; }
        obs = new ContentObserver(h) { @Override public void onChange(boolean s) { check(); } };
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, obs);
        check();
    }

    private void check() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (on()) { nm.cancel(8); return; }
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED) {
            try {
                String e = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                String v = (e == null || e.isEmpty()) ? component() : e + ":" + component();
                Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, v);
                Settings.Secure.putString(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, "1");
                return;
            } catch (Throwable ignored) { }
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CH_ALERT).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Robincord: Accessibility got turned off")
                .setContentText("Tap to turn it back on (Call recording fix)")
                .setContentIntent(pi).setAutoCancel(true).build();
        nm.notify(8, n);
    }

    @Override public int onStartCommand(Intent i, int f, int id) { check(); return START_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        if (obs != null) getContentResolver().unregisterContentObserver(obs);
        super.onDestroy();
    }
}
