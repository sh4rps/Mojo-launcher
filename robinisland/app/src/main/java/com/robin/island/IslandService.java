package com.robin.island;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class IslandService extends Service {
    public static boolean running;
    private WindowManager wm;
    private TextView view;
    private WindowManager.LayoutParams lp;
    private boolean expanded;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable clock = new Runnable() {
        @Override public void run() { updateText(); h.postDelayed(this, 1000); }
    };

    public static void start(Context c) {
        Intent i = new Intent(c, IslandService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        startForeground(1, notif());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        view = new TextView(this);
        view.setTextColor(0xFFFFFFFF);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setOnClickListener(v -> { expanded = !expanded; apply(); });
        lp = new WindowManager.LayoutParams(-2, -2,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        try { wm.addView(view, lp); } catch (Exception e) { stopSelf(); return; }
        apply();
        h.post(clock);
    }

    @Override public int onStartCommand(Intent i, int f, int id) {
        if (view != null) apply();
        return START_STICKY;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void apply() {
        android.content.SharedPreferences p = getSharedPreferences("island", 0);
        int w = p.getInt("w", 120), hh = p.getInt("h", 32), x = p.getInt("x", 0), y = p.getInt("y", 6), ts = p.getInt("t", 13);
        float k = expanded ? 1.6f : 1f;
        lp.width = dp((int) (w * (expanded ? 1.4f : 1f)));
        lp.height = dp((int) (hh * k));
        lp.x = dp(x); lp.y = dp(y);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF000000);
        g.setCornerRadius(dp(hh) * k);
        view.setBackground(g);
        view.setTextSize(ts);
        updateText();
        try { wm.updateViewLayout(view, lp); } catch (Exception ignored) { }
    }

    private void updateText() {
        if (view == null) return;
        Date d = new Date();
        String t = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int bat = -1;
        if (b != null) { int l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), s = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100); if (l >= 0) bat = l * 100 / s; }
        String s = t + (bat >= 0 ? "  " + bat + "%" : "");
        if (expanded) s = new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(d) + "  " + s;
        view.setText(s);
    }

    private Notification notif() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("island", "Island", NotificationManager.IMPORTANCE_MIN);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "island") : new Notification.Builder(this);
        return nb.setContentTitle("ROBINISLAND active").setSmallIcon(android.R.drawable.ic_menu_info_details).setOngoing(true).build();
    }

    @Override public void onDestroy() {
        running = false;
        h.removeCallbacksAndMessages(null);
        try { if (view != null) wm.removeView(view); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
