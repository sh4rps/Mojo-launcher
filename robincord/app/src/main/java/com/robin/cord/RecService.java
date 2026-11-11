package com.robin.cord;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

/** Foreground service that owns the projection + recorder. */
public class RecService extends Service {
    static final String A_START = "com.robin.cord.START", A_PAUSE = "com.robin.cord.PAUSE",
            A_RESUME = "com.robin.cord.RESUME", A_STOP = "com.robin.cord.STOP",
            A_STATE = "com.robin.cord.STATE";
    static final int IDLE = 0, COUNTDOWN = 1, REC = 2, PAUSED = 3;
    private static final String CH = "rec";

    static volatile int state = IDLE;
    private static volatile long recStart, pausedMs, pauseFrom;

    static long elapsedMs() {
        if (state == REC) return SystemClock.elapsedRealtime() - recStart - pausedMs;
        if (state == PAUSED) return pauseFrom - recStart - pausedMs;
        return 0;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaProjection mp;
    private Recorder rec;
    private Cfg cfg;
    private boolean busy;
    private MediaProjection.Callback pcb;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        String a = i == null ? null : i.getAction();
        if (A_START.equals(a)) start(i);
        else if (A_PAUSE.equals(a)) pauseRec();
        else if (A_RESUME.equals(a)) resumeRec();
        else if (A_STOP.equals(a)) stopRec();
        else if (state == IDLE) stopSelf();
        return START_NOT_STICKY;
    }

    private void setState(int s) {
        state = s;
        sendBroadcast(new Intent(A_STATE).setPackage(getPackageName()));
    }

    private void start(Intent i) {
        if (state != IDLE || busy) return;
        cfg = Cfg.load(this);
        busy = true;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH, "Recording", NotificationManager.IMPORTANCE_LOW));
        int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
        if (Build.VERSION.SDK_INT >= 30 && cfg.mic
                && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        }
        try {
            startForeground(1, note("Starting...", false, false), type);
        } catch (Throwable t) {
            toast("Cannot start recording service: " + t.getMessage());
            busy = false; stopSelf(); return;
        }
        MediaProjectionManager pm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        try {
            mp = pm.getMediaProjection(i.getIntExtra("code", 0), (Intent) i.getParcelableExtra("data"));
            pcb = new MediaProjection.Callback() {
                @Override public void onStop() { main.post(new Runnable() { public void run() { stopRec(); } }); }
            };
            mp.registerCallback(pcb, main);
        } catch (Throwable t) {
            toast("Screen capture permission failed: " + t.getMessage());
            busy = false; finishService(); return;
        }
        int cd = cfg.countdown;
        begin(cd);
    }

    private void countdown(final int n) {
        if (state != COUNTDOWN || rec == null) return;
        if (n <= 0) { rec.resume(); enterRec(); return; }
        notify1(note("Recording starts in " + n + "...", false, false));
        main.postDelayed(new Runnable() { public void run() { countdown(n - 1); } }, 1000);
    }

    private void enterRec() {
        recStart = SystemClock.elapsedRealtime();
        pausedMs = 0;
        setState(REC);
        notify1(note(rec.summary, true, false));
        for (String w : rec.warnings) toast(w);
    }

    /** The recorder (and its microphone) starts immediately, while the app is still on screen; the countdown only gates the output. */
    private void begin(final int cd) {
        new Thread(new Runnable() {
            public void run() {
                final Recorder r = new Recorder(RecService.this, cfg, mp);
                try {
                    r.start();
                } catch (final Throwable t) {
                    main.post(new Runnable() { public void run() {
                        toast("Recording failed: " + t.getMessage());
                        finishService();
                    } });
                    return;
                }
                main.post(new Runnable() { public void run() {
                    if (!busy) { new Thread(new Runnable() { public void run() { r.stop(); } }).start(); return; }
                    rec = r;
                    if (cd > 0) {
                        r.pause();
                        setState(COUNTDOWN);
                        countdown(cd);
                    } else {
                        enterRec();
                    }
                } });
            }
        }, "rc-start").start();
    }

    private void pauseRec() {
        if (state != REC || rec == null) return;
        rec.pause();
        pauseFrom = SystemClock.elapsedRealtime();
        setState(PAUSED);
        notify1(note("Paused", false, true));
    }

    private void resumeRec() {
        if (state != PAUSED || rec == null) return;
        pausedMs += SystemClock.elapsedRealtime() - pauseFrom;
        rec.resume();
        setState(REC);
        notify1(note(rec.summary, true, false));
    }

    private void stopRec() {
        if (state == IDLE && !busy) { finishService(); return; }
        if (rec == null) { finishService(); return; }
        final Recorder r = rec;
        rec = null;
        notify1(note("Saving...", false, false));
        new Thread(new Runnable() {
            public void run() {
                final Out o = r.stop();
                Cfg.sp(RecService.this).edit().putString("lastinfo", r.report + "  |  Accessibility " + (a11yOn() ? "ON" : "OFF")).apply();
                main.post(new Runnable() { public void run() {
                    if (o != null) {
                        Cfg.addRecent(RecService.this, o.uri.toString(), o.name, o.size);
                        toast("Saved: " + o.name);
                        saved(o);
                    } else {
                        toast("Nothing was recorded");
                    }
                    finishService();
                } });
            }
        }, "rc-stop").start();
    }

    private void saved(Out o) {
        Intent v = new Intent(Intent.ACTION_VIEW).setDataAndType(o.uri, "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 5, v, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CH).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Recording saved").setContentText(o.name).setContentIntent(pi).setAutoCancel(true).build();
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(2, n);
    }

    private void finishService() {
        main.removeCallbacksAndMessages(null);
        try { if (mp != null) { if (pcb != null) mp.unregisterCallback(pcb); mp.stop(); } } catch (Throwable ignore) { }
        mp = null; rec = null; busy = false;
        setState(IDLE);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        if (rec != null) { try { rec.stop(); } catch (Throwable ignore) { } rec = null; }
        state = IDLE;
        super.onDestroy();
    }

    private boolean a11yOn() {
        try {
            String e = android.provider.Settings.Secure.getString(getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return e != null && e.contains(getPackageName() + "/");
        } catch (Throwable t) { return false; }
    }

    private void toast(String s) { Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show(); }

    private void notify1(Notification n) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, n);
    }

    private PendingIntent act(String a, int rc) {
        return PendingIntent.getService(this, rc, new Intent(this, RecService.class).setAction(a),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification note(String text, boolean chrono, boolean paused) {
        Notification.Builder b = new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(paused ? "Robincord - paused" : "Robincord")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE));
        if (chrono) {
            b.setUsesChronometer(true).setWhen(System.currentTimeMillis() - elapsedMs());
        }
        if (state == REC || state == PAUSED || chrono || paused) {
            if (paused) b.addAction(new Notification.Action.Builder((android.graphics.drawable.Icon) null, "Resume", act(A_RESUME, 2)).build());
            else b.addAction(new Notification.Action.Builder((android.graphics.drawable.Icon) null, "Pause", act(A_PAUSE, 1)).build());
            b.addAction(new Notification.Action.Builder((android.graphics.drawable.Icon) null, "Stop", act(A_STOP, 3)).build());
        } else {
            b.addAction(new Notification.Action.Builder((android.graphics.drawable.Icon) null, "Cancel", act(A_STOP, 3)).build());
        }
        return b.build();
    }
}
