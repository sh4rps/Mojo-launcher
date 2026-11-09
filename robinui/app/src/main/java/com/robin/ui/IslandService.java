package com.robin.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Always-on "dynamic island": event-driven overlay (no polling, no timers while idle). */
public class IslandService extends Service implements Bus.Listener {
    static final int IDLE = 0, COMPACT = 1, EXPANDED = 2;
    WindowManager wm;
    FrameLayout host;
    LinearLayout pill, row2;
    ImageView icon;
    TextView title, sub, playBtn;
    GradientDrawable bg;
    WindowManager.LayoutParams wlp;
    final Handler h = new Handler(Looper.getMainLooper());
    int mode = IDLE, curW, curH;
    boolean mediaUi;            // current content is media
    Bus.Note note;
    ValueAnimator anim;
    ControlCenter cc;
    final Runnable backRun = new Runnable() { public void run() { back(); } };

    public static void start(Context c) {
        try { c.startForegroundService(new Intent(c, IslandService.class)); } catch (Throwable t) { }
    }

    public static void send(Context c, String action) {
        try { c.startForegroundService(new Intent(c, IslandService.class).setAction(action)); } catch (Throwable t) { }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("island", "RobinUI Island", NotificationManager.IMPORTANCE_MIN));
        startForeground(7, fgNotif());
        if (!android.provider.Settings.canDrawOverlays(this)) { stopSelf(); return; }
        wm = getSystemService(WindowManager.class);
        build();
        cc = new ControlCenter(this, wm);
        Bus.listener = this;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_BATTERY_LOW);
        registerReceiver(rx, f);
        back();
    }

    Notification fgNotif() {
        PendingIntent cc = PendingIntent.getService(this, 1, new Intent(this, IslandService.class).setAction("cc"), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "island")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("RobinUI Island is active")
                .setContentText("Tap for Control Center")
                .setContentIntent(cc)
                .setOngoing(true).build();
    }

    @Override public int onStartCommand(Intent i, int f, int id) {
        String a = i == null ? null : i.getAction();
        if ("cc".equals(a) && cc != null) cc.toggle();
        else if ("refresh".equals(a)) { back(); }
        else if ("stop".equals(a)) stopSelf();
        return START_STICKY;
    }

    final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_POWER_CONNECTED.equals(i.getAction())) {
                Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int pct = b == null ? 0 : b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / Math.max(1, b.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
                transient1("Charging", pct + "%", null, null, 3500);
            } else {
                transient1("Battery low", "Connect a charger", null, null, 4000);
            }
        }
    };

    void build() {
        host = new FrameLayout(this);
        pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        bg = U.round(0xFF000000, 20);
        pill.setBackground(bg);
        pill.setPadding(U.dp(12), U.dp(4), U.dp(12), U.dp(4));
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row1.addView(icon, new LinearLayout.LayoutParams(U.dp(24), U.dp(24)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        title = U.tv(this, "", 13, 0xFFFFFFFF, true);
        title.setSingleLine(); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub = U.tv(this, "", 11, 0xFFB5B5BC, false);
        sub.setSingleLine(); sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(title); col.addView(sub);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.leftMargin = U.dp(10);
        row1.addView(col, cl);
        pill.addView(row1, new LinearLayout.LayoutParams(-1, -2));
        row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        row2.addView(ctl("⏮", new View.OnClickListener() { public void onClick(View v) { tc(0); } }));
        playBtn = ctl("⏯", new View.OnClickListener() { public void onClick(View v) { tc(1); } });
        row2.addView(playBtn);
        row2.addView(ctl("⏭", new View.OnClickListener() { public void onClick(View v) { tc(2); } }));
        row2.setVisibility(View.GONE);
        pill.addView(row2, new LinearLayout.LayoutParams(-1, 0, 1f));
        host.addView(pill, new FrameLayout.LayoutParams(U.dp(88), U.dp(26)));
        curW = U.dp(88); curH = U.dp(26);

        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapUp(MotionEvent e) { tap(); return true; }
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (Math.abs(vy) > Math.abs(vx)) {
                    if (vy > 0) cc.show(); else if (mode == EXPANDED) back();
                    return true;
                }
                return false;
            }
            @Override public boolean onDown(MotionEvent e) { return true; }
        });
        pill.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) { return gd.onTouchEvent(e); }
        });

        wlp = new WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        wlp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        wlp.y = U.dp(Prefs.i("island_y", 8));
        wlp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        wlp.setTitle("RobinUI Island");
        Hz.apply(wlp, this, false);
        wm.addView(host, wlp);
    }

    TextView ctl(String s, View.OnClickListener l) {
        TextView t = U.tv(this, s, 22, 0xFFFFFFFF, false);
        t.setPadding(U.dp(22), U.dp(4), U.dp(22), U.dp(4));
        t.setOnClickListener(l);
        return t;
    }

    void tc(int k) {
        MediaController m = Bus.media;
        if (m == null) return;
        try {
            MediaController.TransportControls t = m.getTransportControls();
            if (k == 0) t.skipToPrevious(); else if (k == 2) t.skipToNext();
            else { PlaybackState s = m.getPlaybackState(); if (s != null && s.getState() == PlaybackState.STATE_PLAYING) t.pause(); else t.play(); }
        } catch (Throwable e) { }
        h.removeCallbacks(backRun);
        h.postDelayed(backRun, 6000);
    }

    // ---------------- state machine ----------------
    boolean playing() {
        MediaController m = Bus.media;
        if (m == null) return false;
        PlaybackState s = m.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING && m.getMetadata() != null;
    }

    void back() {
        h.removeCallbacks(backRun);
        note = null;
        if (playing()) { mediaUi = true; fillMedia(); go(COMPACT); }
        else { mediaUi = false; go(IDLE); }
    }

    void fillMedia() {
        MediaController m = Bus.media;
        if (m == null || m.getMetadata() == null) return;
        MediaMetadata md = m.getMetadata();
        String t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String a = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        title.setText(t == null ? "Playing" : t);
        sub.setText(a == null ? "" : a);
        Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art != null) icon.setImageBitmap(art); else icon.setImageDrawable(null);
        PlaybackState s = m.getPlaybackState();
        playBtn.setText(s != null && s.getState() == PlaybackState.STATE_PLAYING ? "⏸" : "▶");
    }

    void transient1(String t, String s, Drawable ic, Bus.Note n, long ms) {
        title.setText(t);
        sub.setText(s);
        icon.setImageDrawable(ic);
        note = n;
        mediaUi = false;
        go(COMPACT);
        h.removeCallbacks(backRun);
        h.postDelayed(backRun, ms);
    }

    void tap() {
        if (note != null) {
            try { if (note.open != null) note.open.send(); } catch (Throwable e) { }
            back();
            return;
        }
        if (mediaUi) {
            if (mode == EXPANDED) { back(); } else { fillMedia(); go(EXPANDED); h.removeCallbacks(backRun); h.postDelayed(backRun, 6000); }
        }
    }

    @Override public void onNote(Bus.Note n) {
        transient1(n.call ? "📞 " + n.title : n.title, n.text.isEmpty() ? n.app : n.text, n.icon, n, n.call ? 9000 : 4500);
    }

    @Override public void onMedia() {
        if (note != null || (mode == COMPACT && !mediaUi && title.getText().toString().startsWith("Charging"))) return;
        if (mode == EXPANDED && mediaUi) { fillMedia(); return; }
        back();
    }

    void go(int m) {
        mode = m;
        int w, hh;
        boolean show = true;
        if (m == IDLE) { w = U.dp(88); hh = U.dp(26); show = Prefs.idlePill(); }
        else if (m == COMPACT) { w = U.dp(214); hh = U.dp(42); }
        else { w = U.dp(344); hh = U.dp(mediaUi ? 124 : 96); }
        row2.setVisibility(m == EXPANDED && mediaUi ? View.VISIBLE : View.GONE);
        if (m == IDLE) { title.setText(""); sub.setText(""); icon.setImageDrawable(null); }
        pill.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        animate(w, hh);
    }

    void animate(final int tw, final int th) {
        if (anim != null) anim.cancel();
        final int sw = curW, sh = curH;
        Hz.apply(wlp, this, true);
        try { wm.updateViewLayout(host, wlp); } catch (Throwable e) { }
        pill.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(420);
        anim.setInterpolator(U.SPRING);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float f = (Float) a.getAnimatedValue();
                curW = Math.max(U.dp(40), Math.round(sw + (tw - sw) * f));
                curH = Math.max(U.dp(20), Math.round(sh + (th - sh) * f));
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pill.getLayoutParams();
                lp.width = curW; lp.height = curH;
                pill.setLayoutParams(lp);
                bg.setCornerRadius(Math.min(curH / 2f, U.dp(30)));
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                pill.setLayerType(View.LAYER_TYPE_NONE, null);
                Hz.apply(wlp, IslandService.this, false);
                try { wm.updateViewLayout(host, wlp); } catch (Throwable e) { }
            }
        });
        anim.start();
    }

    @Override public void onDestroy() {
        try { unregisterReceiver(rx); } catch (Throwable e) { }
        if (Bus.listener == this) Bus.listener = null;
        try { if (cc != null) cc.hide(); } catch (Throwable e) { }
        try { if (host != null) wm.removeView(host); } catch (Throwable e) { }
        super.onDestroy();
    }
}
