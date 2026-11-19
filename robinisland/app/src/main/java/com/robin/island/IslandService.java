package com.robin.island;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Island that appears only when a notification arrives, hidden in landscape (games / video). */
public class IslandService extends Service {
    public static boolean running;
    private static IslandService inst;
    private WindowManager wm;
    private LinearLayout view;
    private ImageView icon;
    private TextView title, body;
    private GradientDrawable bg;
    private WindowManager.LayoutParams lp;
    private boolean attached;
    private PendingIntent target;
    private boolean expanded;
    private ValueAnimator anim;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable hideTask = this::collapse;
    private DisplayManager.DisplayListener dl;

    public static void start(Context c) {
        Intent i = new Intent(c, IslandService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    public static void show(Drawable ic, String t, String b, PendingIntent pi) { show(ic, "", t, b, pi); }

    public static void show(Drawable ic, String label, String t, String b, PendingIntent pi) {
        IslandService s = inst;
        if (s != null) s.h.post(() -> s.display(ic, label, t, b, pi));
    }

    @Override public IBinder onBind(Intent i) { return null; }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onCreate() {
        super.onCreate();
        running = true; inst = this;
        startForeground(1, notif());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(14), 0, dp(18), 0);
        bg = new GradientDrawable(); bg.setColor(0xFF000000);
        view.setBackground(bg);
        icon = new ImageView(this);
        view.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(12), 0, 0, 0);
        title = new TextView(this); title.setTextColor(0xFFFFFFFF); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        body = new TextView(this); body.setTextColor(0xFFB8B8C2); body.setSingleLine(true); body.setEllipsize(TextUtils.TruncateAt.END);
        tx.addView(title); tx.addView(body);
        view.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        view.setOnClickListener(v -> {
            if (target != null) { try { target.send(); } catch (Exception ignored) { } }
            if (expanded) collapse();
        });
        lp = new WindowManager.LayoutParams(-2, -2,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        dl = new DisplayManager.DisplayListener() {
            @Override public void onDisplayAdded(int id) { }
            @Override public void onDisplayRemoved(int id) { }
            @Override public void onDisplayChanged(int id) { if (landscape()) hideNow(); else if (!attached) attachIdle(); }
        };
        dm.registerDisplayListener(dl, h);
        if (!landscape()) attachIdle();
    }

    @Override public int onStartCommand(Intent i, int f, int id) {
        if (!landscape()) { if (!attached) attachIdle(); else if (!expanded) applyIdle(); }
        return START_STICKY;
    }

    private void applyIdle() {
        android.content.SharedPreferences p = getSharedPreferences("island", 0);
        lp.width = dp(p.getInt("w", 150)); lp.height = dp(p.getInt("h", 34));
        lp.x = dp(p.getInt("x", 0)); lp.y = dp(p.getInt("y", 8));
        bg.setCornerRadius(lp.height / 2f);
        icon.setAlpha(0f); title.setAlpha(0f); body.setAlpha(0f);
        view.setAlpha(1f);
        if (attached) { try { wm.updateViewLayout(view, lp); } catch (Exception ignored) { } }
    }

    private void attachIdle() {
        if (attached) return;
        applyIdle();
        try { wm.addView(view, lp); attached = true; expanded = false; } catch (Exception ignored) { }
    }

    private boolean landscape() {
        int r = wm.getDefaultDisplay().getRotation();
        return r == Surface.ROTATION_90 || r == Surface.ROTATION_270;
    }

    private void display(Drawable ic, String label, String t, String b, PendingIntent pi) {
        if (landscape()) return;
        android.content.SharedPreferences p = getSharedPreferences("island", 0);
        final int w = dp(p.getInt("w", 150)), hh = dp(p.getInt("h", 34));
        int sw = getResources().getDisplayMetrics().widthPixels;
        final int ew = Math.min(sw - dp(24), Math.max((int) (w * 2.3f), dp(300)));
        final int eh = dp(68);
        int ts = p.getInt("t", 14);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, ts);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(8, ts - 2));
        String head = label.isEmpty() ? t : (t.isEmpty() ? label : label + "  \u2022  " + t);
        title.setText(head.isEmpty() ? " " : head); body.setText(b);
        body.setVisibility(b.isEmpty() ? View.GONE : View.VISIBLE);
        icon.setImageDrawable(ic);
        target = pi;
        lp.x = dp(p.getInt("x", 0)); lp.y = dp(p.getInt("y", 8));
        h.removeCallbacks(hideTask);
        if (anim != null) anim.cancel();
        if (!attached) { attachIdle(); if (!attached) return; }
        expanded = true;
        final int sw0 = lp.width, sh0 = lp.height;
        animate(sw0, sh0, ew, eh, w, hh, true, () -> h.postDelayed(hideTask, 5000));
    }

    private void animate(int fw, int fh, int tw, int th, int cw, int ch, boolean show, Runnable end) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(show ? 380 : 260);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        final float r0 = ch / 2f, r1 = dp(30);
        anim.addUpdateListener(a -> {
            if (!attached) return;
            float f = (float) a.getAnimatedValue();
            lp.width = (int) (fw + (tw - fw) * f);
            lp.height = (int) (fh + (th - fh) * f);
            float k = (float) lp.height / Math.max(1, eh());
            bg.setCornerRadius(Math.min(lp.height / 2f, r0 + (r1 - r0) * Math.min(1f, k)));
            float e = show ? f : 1f - f;
            view.setAlpha(1f);
            float c = show ? Math.max(0f, (f - 0.45f) * 1.8f) : Math.max(0f, 1f - f * 2.2f);
            icon.setAlpha(c); title.setAlpha(c); body.setAlpha(c);
            try { wm.updateViewLayout(view, lp); } catch (Exception ignored) { }
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator a) { if (!cancelled && end != null) end.run(); }
        });
        anim.start();
    }

    private int eh() { return dp(68); }

    private void collapse() {
        if (!attached) return;
        android.content.SharedPreferences p = getSharedPreferences("island", 0);
        int w = dp(p.getInt("w", 150)), hh = dp(p.getInt("h", 34));
        animate(lp.width, lp.height, w, hh, w, hh, false, () -> { expanded = false; applyIdle(); });
    }

    private void hideNow() {
        h.removeCallbacks(hideTask);
        if (anim != null) anim.cancel();
        expanded = false;
        if (attached) { try { wm.removeView(view); } catch (Exception ignored) { } attached = false; }
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
        running = false; inst = null;
        hideNow();
        try { ((DisplayManager) getSystemService(DISPLAY_SERVICE)).unregisterDisplayListener(dl); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
