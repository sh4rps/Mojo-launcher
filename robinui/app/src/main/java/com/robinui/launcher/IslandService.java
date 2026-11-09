package com.robinui.launcher;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Always-on Dynamic-Island style pill. Purely event driven: no polling, no wake locks. */
public class IslandService extends Service {
    static IslandService inst;
    static final int IDLE = 0, COMPACT = 1, EXPANDED = 2;
    static final int NONE = 0, MEDIA = 1, NOTIF = 2, CHARGE = 3, LOWBAT = 4;

    WindowManager wm;
    FrameLayout host, pill;
    LinearLayout col, topRow, controls;
    ImageView art;
    TextView title, sub, tail;
    ImageButton bPrev, bPlay, bNext;
    GradientDrawable pillBg;
    WindowManager.LayoutParams wlp;
    final Handler h = new Handler(Looper.getMainLooper());
    int state = IDLE, content = NONE, curW, curH;
    float curR;
    ValueAnimator anim;
    GestureDetector gd;

    // temp event
    int tempType = NONE;
    CharSequence tTitle, tSub;
    Drawable tIcon;
    PendingIntent tIntent;
    // media
    MediaSessionManager msm;
    MediaController mc;
    MediaSessionManager.OnActiveSessionsChangedListener sessL;
    // battery
    boolean plugged, lowShown, batInit;
    int level;

    public static void ensure(Context c) {
        try {
            if (inst != null) return;
            if (!Prefs.b(c, "island", true) || !Settings.canDrawOverlays(c)) return;
            c.getApplicationContext().startForegroundService(new Intent(c, IslandService.class));
        } catch (Throwable t) { }
    }

    public static void stop(Context c) { c.getApplicationContext().stopService(new Intent(c, IslandService.class)); }
    public static void notif(StatusBarNotification s) { if (inst != null) inst.onNotif(s); }
    public static void mediaAttach() { if (inst != null) inst.attachMedia(); }
    public static void relayout() { if (inst != null) inst.applyWindow(); }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        inst = this;
        NotificationChannel ch = new NotificationChannel("robinui_island", "Island", NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        startForeground(7, new Notification.Builder(this, "robinui_island")
                .setSmallIcon(android.R.drawable.ic_menu_info_details).setContentTitle("RobinUI island is active")
                .setOngoing(true).build());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildView();
        registerReceiver(batRx, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        attachMedia();
        onConfigurationChanged(getResources().getConfiguration());
    }

    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (host != null) host.setVisibility(c.orientation == Configuration.ORIENTATION_PORTRAIT ? View.VISIBLE : View.GONE);
    }

    @Override public void onDestroy() {
        inst = null;
        try { unregisterReceiver(batRx); } catch (Throwable t) { }
        try { if (sessL != null) msm.removeOnActiveSessionsChangedListener(sessL); } catch (Throwable t) { }
        try { if (mc != null) mc.unregisterCallback(mcCb); } catch (Throwable t) { }
        try { wm.removeView(host); } catch (Throwable t) { }
        super.onDestroy();
    }

    float scale() { return Prefs.i(this, "island_scale", 100) / 100f; }
    int dpx(float v) { return Math.round(U.dp(this, v) * scale()); }

    // ---------- view ----------
    void buildView() {
        host = new FrameLayout(this);
        pill = new FrameLayout(this);
        pillBg = U.rr(Color.BLACK, 100);
        pill.setBackground(pillBg);
        pill.setClipToOutline(true);
        pill.setElevation(U.dp(this, 3));
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(U.dp(this, 12), 0, U.dp(this, 12), 0);
        topRow = new LinearLayout(this);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setSingleLine();
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sub = new TextView(this);
        sub.setTextColor(0xB3FFFFFF);
        sub.setTextSize(12);
        sub.setMaxLines(2);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tc.addView(title);
        tc.addView(sub);
        tail = new TextView(this);
        tail.setTextColor(U.RED);
        tail.setTextSize(13);
        tail.setTypeface(Typeface.DEFAULT_BOLD);
        topRow.addView(art);
        topRow.addView(tc, new LinearLayout.LayoutParams(0, -2, 1f));
        topRow.addView(tail);
        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        bPrev = btn(android.R.drawable.ic_media_previous);
        bPlay = btn(android.R.drawable.ic_media_pause);
        bNext = btn(android.R.drawable.ic_media_next);
        controls.addView(bPrev);
        controls.addView(bPlay);
        controls.addView(bNext);
        col.addView(topRow, new LinearLayout.LayoutParams(-1, -2));
        col.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        pill.addView(col, new FrameLayout.LayoutParams(-1, -1));
        host.addView(pill, new FrameLayout.LayoutParams(dpx(88), dpx(26), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        curW = dpx(88); curH = dpx(26); curR = curH / 2f;
        pillBg.setCornerRadius(curR);
        col.setAlpha(0f);
        col.setVisibility(View.GONE);

        bPrev.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (mc != null) mc.getTransportControls().skipToPrevious(); resetAuto(); } });
        bNext.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (mc != null) mc.getTransportControls().skipToNext(); resetAuto(); } });
        bPlay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mc == null) return;
                PlaybackState ps = mc.getPlaybackState();
                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) mc.getTransportControls().pause(); else mc.getTransportControls().play();
                resetAuto();
            }
        });

        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) { onTap(); return true; }
            @Override public void onLongPress(MotionEvent e) { if (state == EXPANDED) collapse(); else if (content != NONE) show(EXPANDED); }
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (vy > 500 && Math.abs(vy) > Math.abs(vx)) { ControlCenter.toggle(IslandService.this); return true; }
                if (vy < -500 && state == EXPANDED) { collapse(); return true; }
                return false;
            }
        });
        pill.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) h.removeCallbacks(autoCollapse);
                else if (e.getAction() == MotionEvent.ACTION_UP && state == EXPANDED) resetAuto();
                return gd.onTouchEvent(e);
            }
        });

        wlp = new WindowManager.LayoutParams(curW + U.dp(this, 20), curH + U.dp(this, 14),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        wlp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        wlp.y = U.dp(this, Prefs.i(this, "island_y", 6));
        wm.addView(host, wlp);
    }

    ImageButton btn(int res) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setColorFilter(Color.WHITE);
        b.setBackground(null);
        b.setLayoutParams(new LinearLayout.LayoutParams(U.dp(this, 64), U.dp(this, 36)));
        return b;
    }

    void applyWindow() {
        if (host == null) return;
        wlp.y = U.dp(this, Prefs.i(this, "island_y", 6));
        syncWindow(curW, curH);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pill.getLayoutParams();
        int[] t = target(state);
        lp.width = t[0]; lp.height = t[1];
        pill.setLayoutParams(lp);
        curW = t[0]; curH = t[1];
        syncWindow(curW, curH);
    }

    void syncWindow(int w, int hh) {
        wlp.width = w + U.dp(this, 20);
        wlp.height = hh + U.dp(this, 14);
        try { wm.updateViewLayout(host, wlp); } catch (Throwable t) { }
    }

    // ---------- state machine ----------
    int[] target(int st) {
        if (st == IDLE || content == NONE) return new int[]{dpx(88), dpx(26), dpx(13)};
        if (st == COMPACT) return new int[]{dpx(content == CHARGE || content == LOWBAT ? 150 : 196), dpx(34), dpx(17)};
        int hh = content == MEDIA ? 124 : content == NOTIF ? 88 : 68;
        return new int[]{dpx(content == MEDIA || content == NOTIF ? 348 : 250), dpx(hh), dpx(30)};
    }

    void configure(int st) {
        boolean exp = st == EXPANDED;
        col.setVisibility(st == IDLE || content == NONE ? View.GONE : View.VISIBLE);
        int artS = dpx(exp ? 52 : 22);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(artS, artS);
        al.rightMargin = dpx(10);
        art.setLayoutParams(al);
        GradientDrawable ab = U.rr(0xFF333338, dpx(exp ? 12 : 6));
        art.setBackground(ab);
        art.setClipToOutline(true);
        title.setTextSize(exp ? 15 : 12);
        sub.setVisibility(exp ? View.VISIBLE : View.GONE);
        controls.setVisibility(exp && content == MEDIA ? View.VISIBLE : View.GONE);
        tail.setVisibility(!exp && content == MEDIA ? View.VISIBLE : View.GONE);
        tail.setText("♫");
        PlaybackState ps = mc == null ? null : mc.getPlaybackState();
        bPlay.setImageResource(ps != null && ps.getState() == PlaybackState.STATE_PLAYING ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        fillContent();
    }

    void fillContent() {
        if (content == MEDIA && mc != null) {
            MediaMetadata md = mc.getMetadata();
            CharSequence t = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_TITLE);
            CharSequence a = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_ARTIST);
            title.setText(t == null ? "Playing" : t);
            sub.setText(a == null ? "" : a);
            Bitmap b = null;
            if (md != null) {
                b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            }
            art.setVisibility(View.VISIBLE);
            art.setImageBitmap(b);
        } else if (content == NOTIF) {
            title.setText(tTitle);
            sub.setText(tSub);
            art.setVisibility(View.VISIBLE);
            art.setImageDrawable(tIcon);
            art.setColorFilter(null);
        } else if (content == CHARGE || content == LOWBAT) {
            art.setVisibility(View.GONE);
            title.setText(content == CHARGE ? "⚡ " + level + "%" : "Battery low " + level + "%");
            title.setTextColor(content == CHARGE ? 0xFF7CFC7C : 0xFFFF6B6B);
            sub.setText(content == CHARGE ? "Charging" : "Connect a charger");
        }
        if (content != CHARGE && content != LOWBAT) title.setTextColor(Color.WHITE);
    }

    void show(int next) {
        if (host == null) return;
        if (content == NONE) next = IDLE;
        h.removeCallbacks(autoCollapse);
        final int[] to = target(next);
        final int[] from = {curW, curH, Math.round(curR)};
        final boolean grow = to[0] > curW || to[1] > curH;
        final int st = next;
        if (grow) syncWindow(Math.max(to[0], curW), Math.max(to[1], curH));
        col.animate().cancel();
        col.setAlpha(0f);
        configure(next);
        state = next;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(next == IDLE ? 260 : 360);
        anim.setInterpolator(grow ? new OvershootInterpolator(0.85f) : new DecelerateInterpolator(1.4f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float f = (Float) a.getAnimatedValue();
                int w = Math.round(from[0] + (to[0] - from[0]) * f);
                int hh = Math.round(from[1] + (to[1] - from[1]) * f);
                float r = from[2] + (to[2] - from[2]) * f;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pill.getLayoutParams();
                lp.width = Math.max(w, 1); lp.height = Math.max(hh, 1);
                pill.setLayoutParams(lp);
                pillBg.setCornerRadius(Math.max(r, 1f));
                curW = lp.width; curH = lp.height; curR = r;
            }
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            public void onAnimationEnd(android.animation.Animator a) {
                curW = to[0]; curH = to[1]; curR = to[2];
                if (!grow) syncWindow(to[0], to[1]);
                if (st != IDLE) col.animate().alpha(1f).setDuration(180).start();
                if (st == EXPANDED) resetAuto();
            }
        });
        anim.start();
    }

    final Runnable autoCollapse = new Runnable() { public void run() { collapse(); } };
    void resetAuto() { h.removeCallbacks(autoCollapse); h.postDelayed(autoCollapse, 7000); }

    void collapse() { show(content == NONE ? IDLE : COMPACT); }

    void onTap() {
        if (state == IDLE) { if (content != NONE) show(EXPANDED); return; }
        if (state == COMPACT) { show(EXPANDED); return; }
        try {
            if (content == NOTIF && tIntent != null) tIntent.send();
            else if (content == MEDIA && mc != null && mc.getSessionActivity() != null) mc.getSessionActivity().send();
        } catch (Throwable t) { }
        collapse();
    }

    // ---------- content sources ----------
    void setBase() {
        if (tempType != NONE) return;
        boolean playing = mc != null && mc.getPlaybackState() != null && (mc.getPlaybackState().getState() == PlaybackState.STATE_PLAYING || mc.getPlaybackState().getState() == PlaybackState.STATE_BUFFERING);
        if (playing && Prefs.b(this, "island_media", true)) {
            content = MEDIA;
            show(state == IDLE ? COMPACT : state);
        } else {
            content = NONE;
            show(IDLE);
        }
    }

    final Runnable revert = new Runnable() { public void run() { tempType = NONE; setBase(); } };

    void temp(int type, int ms) {
        tempType = type;
        content = type;
        h.removeCallbacks(revert);
        show(COMPACT);
        h.postDelayed(revert, ms);
    }

    void onNotif(StatusBarNotification s) {
        try {
            if (!Prefs.b(this, "island_notif", true)) return;
            Notification n = s.getNotification();
            if (s.getPackageName().equals(getPackageName())) return;
            if ((n.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_GROUP_SUMMARY | Notification.FLAG_FOREGROUND_SERVICE)) != 0) return;
            if (Notification.CATEGORY_TRANSPORT.equals(n.category) || Notification.CATEGORY_PROGRESS.equals(n.category) || Notification.CATEGORY_SERVICE.equals(n.category)) return;
            CharSequence t = n.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence x = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (t == null && x == null) return;
            tTitle = t == null ? x : t;
            tSub = t == null ? "" : x;
            tIntent = n.contentIntent;
            tIcon = null;
            try {
                Context pc = createPackageContext(s.getPackageName(), 0);
                tIcon = pc.getPackageManager().getApplicationIcon(s.getPackageName());
            } catch (Throwable e) { }
            temp(NOTIF, 4500);
        } catch (Throwable t) { }
    }

    final BroadcastReceiver batRx = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            int lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0), sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            level = Math.round(lv * 100f / sc);
            boolean pl = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            boolean was = plugged;
            plugged = pl;
            if (!batInit) { batInit = true; return; }
            if (!Prefs.b(IslandService.this, "island_charge", true)) return;
            if (pl && !was) temp(CHARGE, 3500);
            else if (!pl && level <= 15 && !lowShown) { lowShown = true; temp(LOWBAT, 4000); }
            else if (level > 20) lowShown = false;
        }
    };

    final MediaController.Callback mcCb = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState st) { setBase(); if (state != IDLE && content == MEDIA) configure(state); }
        @Override public void onMetadataChanged(MediaMetadata m) { if (content == MEDIA) fillContent(); }
    };

    void attachMedia() {
        try {
            ComponentName cn = new ComponentName(this, NotifListener.class);
            if (sessL == null) {
                sessL = new MediaSessionManager.OnActiveSessionsChangedListener() {
                    public void onActiveSessionsChanged(List<MediaController> l) { pick(l); }
                };
                msm.addOnActiveSessionsChangedListener(sessL, cn, h);
            }
            pick(msm.getActiveSessions(cn));
        } catch (Throwable t) { }
    }

    void pick(List<MediaController> l) {
        MediaController best = null;
        if (l != null) {
            for (MediaController c : l) {
                PlaybackState ps = c.getPlaybackState();
                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) { best = c; break; }
                if (best == null) best = c;
            }
        }
        if (mc != null) { try { mc.unregisterCallback(mcCb); } catch (Throwable t) { } }
        mc = best;
        if (mc != null) mc.registerCallback(mcCb, h);
        setBase();
    }
}
