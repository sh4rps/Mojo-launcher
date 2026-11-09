package com.robin.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** ColorOS-style control center overlay: opens from the island (swipe down) or the persistent notification. */
public class ControlCenter {
    final Context c;
    final WindowManager wm;
    FrameLayout root;
    LinearLayout panel;
    boolean shown, torchOn;
    WindowManager.LayoutParams lp;
    CameraManager cam;
    CameraManager.TorchCallback torchCb;
    final List<Tile> tiles = new ArrayList<>();
    ValueAnimator anim;

    class Tile {
        String glyph, label;
        Runnable click;
        TextView box;
        boolean on() { return false; }
    }

    abstract class T extends Tile { abstract boolean state(); @Override boolean on() { return state(); } }

    ControlCenter(Context c, WindowManager wm) { this.c = c; this.wm = wm; }

    void toggle() { if (shown) hide(); else show(); }

    void open(Intent i) {
        try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable e) { }
        hide();
    }

    void show() {
        if (shown) return;
        shown = true;
        build();
        lp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        Hz.apply(lp, c, true);
        if (Build.VERSION.SDK_INT >= 31) {
            lp.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            lp.setBlurBehindRadius(U.dp(18));
        }
        wm.addView(root, lp);
        registerTorch();
        panel.setTranslationY(-6000);
        panel.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                panel.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (shown) slide(true);
            }
        });
    }

    void hide() {
        if (!shown) return;
        shown = false;
        unregisterTorch();
        slide(false);
    }

    void slide(final boolean in) {
        if (anim != null) anim.cancel();
        panel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        final float from = in ? -panel.getHeight() : panel.getTranslationY();
        final float to = in ? 0 : -panel.getHeight();
        anim = ValueAnimator.ofFloat(0, 1);
        anim.setDuration(in ? 420 : 260);
        anim.setInterpolator(in ? U.SPRING : new android.view.animation.AccelerateInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float f = (Float) a.getAnimatedValue();
                panel.setTranslationY(from + (to - from) * f);
                float p = 1 - Math.min(1, Math.max(0, -panel.getTranslationY() / Math.max(1, panel.getHeight())));
                root.setBackgroundColor(((int) (0x66 * p) << 24));
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                panel.setLayerType(View.LAYER_TYPE_NONE, null);
                if (!in) { try { wm.removeView(root); } catch (Throwable e) { } }
            }
        });
        anim.start();
    }

    // ------------------------------------------------------------------
    void build() {
        tiles.clear();
        final int tx = U.text(c), sb = U.sub(c);
        root = new FrameLayout(c);
        root.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { hide(); } });
        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(U.round(U.glass(c), 30));
        panel.setPadding(U.dp(18), U.dp(46), U.dp(18), U.dp(18));
        panel.setClickable(true);
        final android.view.GestureDetector gd = new android.view.GestureDetector(c, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (vy < -800 && Math.abs(vy) > Math.abs(vx)) { hide(); return true; }
                return false;
            }
        });
        panel.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) { return gd.onTouchEvent(e); }
        });
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        pl.setMargins(U.dp(8), 0, U.dp(8), 0);
        root.addView(panel, pl);

        // header
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        TextClock tc = new TextClock(c);
        tc.setFormat24Hour("HH:mm"); tc.setFormat12Hour("h:mm");
        tc.setTextSize(34); tc.setTextColor(tx); tc.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        TextClock dc = new TextClock(c);
        dc.setFormat24Hour("EEE, d MMM"); dc.setFormat12Hour("EEE, d MMM");
        dc.setTextSize(14); dc.setTextColor(sb);
        dc.setPadding(U.dp(12), 0, 0, U.dp(6));
        head.addView(tc); head.addView(dc);
        panel.addView(head);

        // tiles
        final WifiManager wf = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        cam = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
        final AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        final NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);

        add(new T() { boolean state() { try { return wf.isWifiEnabled(); } catch (Throwable e) { return false; } } }, "📶", "Wi-Fi", new Runnable() { public void run() { open(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)); } });
        add(new T() { boolean state() { try { BluetoothAdapter b = BluetoothAdapter.getDefaultAdapter(); return b != null && b.isEnabled(); } catch (Throwable e) { return false; } } }, "🔷", "Bluetooth", new Runnable() { public void run() { open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); } });
        add(new T() { boolean state() { return false; } }, "📡", "Mobile data", new Runnable() { public void run() { open(new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)); } });
        add(new T() { boolean state() { return Settings.Global.getInt(c.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1; } }, "✈", "Airplane", new Runnable() { public void run() { open(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)); } });
        add(new T() { boolean state() { return torchOn; } }, "🔦", "Flashlight", new Runnable() { public void run() { torch(); } });
        add(new T() { boolean state() { return Settings.System.getInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1; } }, "🔄", "Auto-rotate", new Runnable() { public void run() { rotate(); } });
        add(new T() { boolean state() { try { return nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL; } catch (Throwable e) { return false; } } }, "🌙", "Do not disturb", new Runnable() { public void run() { dnd(nm); } });
        add(new T() { boolean state() { return false; } }, "📍", "Location", new Runnable() { public void run() { open(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); } });
        add(new T() { boolean state() { return Prefs.hz(); } }, "⚡", "Adaptive 120Hz", new Runnable() {
            public void run() {
                Prefs.setB("hz", !Prefs.hz());
                Toast.makeText(c, Prefs.hz() ? "Adaptive 120 Hz on" : "RobinUI locked to 60 Hz (saves battery)", Toast.LENGTH_SHORT).show();
                refresh();
            }
        });
        add(new T() { boolean state() { return Prefs.island(); } }, "🏝", "Island", new Runnable() {
            public void run() {
                Prefs.setB("island", !Prefs.island());
                if (!Prefs.island()) { IslandService.send(c, "stop"); } 
                refresh();
            }
        });
        add(new T() { boolean state() { try { return ((PowerManager) c.getSystemService(Context.POWER_SERVICE)).isPowerSaveMode(); } catch (Throwable e) { return false; } } }, "🔋", "Battery saver", new Runnable() { public void run() { open(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)); } });
        add(new T() { boolean state() { return false; } }, "⚙", "Settings", new Runnable() { public void run() { open(new Intent(Settings.ACTION_SETTINGS)); } });

        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.topMargin = U.dp(12);
                panel.addView(row, rl);
            }
            row.addView(cell(tiles.get(i)), new LinearLayout.LayoutParams(0, -2, 1f));
        }
        refresh();

        // sliders
        panel.addView(slider("☀", true, am), sliderLp());
        panel.addView(slider("🔊", false, am), sliderLp());

        // media
        MediaController m = Bus.media;
        if (m != null && m.getMetadata() != null) panel.addView(mediaCard(m), sliderLp());
    }

    LinearLayout.LayoutParams sliderLp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = U.dp(14);
        return l;
    }

    void add(Tile t, String g, String l, Runnable r) { t.glyph = g; t.label = l; t.click = r; tiles.add(t); }

    View cell(final Tile t) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView box = new TextView(c);
        box.setGravity(Gravity.CENTER);
        box.setTextSize(24);
        box.setText(t.glyph);
        t.box = box;
        box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) {
                try { t.click.run(); } catch (Throwable e) { }
                refresh();
            }
        });
        box.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View x, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) x.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start();
                else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) x.animate().scaleX(1f).scaleY(1f).setInterpolator(U.SPRING).setDuration(320).start();
                return false;
            }
        });
        v.addView(box, new LinearLayout.LayoutParams(U.dp(64), U.dp(64)));
        TextView l = U.tv(c, t.label, 11, U.sub(c), false);
        l.setGravity(Gravity.CENTER);
        l.setSingleLine();
        l.setPadding(0, U.dp(4), 0, 0);
        v.addView(l, new LinearLayout.LayoutParams(U.dp(78), -2));
        return v;
    }

    void refresh() {
        for (Tile t : tiles) {
            boolean on = false;
            try { on = t.on(); } catch (Throwable e) { }
            t.box.setBackground(U.round(on ? U.ACCENT : U.tile(c), 22));
            t.box.setTextColor(on ? 0xFFFFFFFF : U.text(c));
        }
    }

    // ---- actions ----
    void registerTorch() {
        try {
            torchCb = new CameraManager.TorchCallback() {
                @Override public void onTorchModeChanged(String id, boolean enabled) { torchOn = enabled; if (shown) refresh(); }
            };
            cam.registerTorchCallback(torchCb, null);
        } catch (Throwable e) { }
    }

    void unregisterTorch() { try { if (cam != null && torchCb != null) cam.unregisterTorchCallback(torchCb); } catch (Throwable e) { } }

    void torch() {
        try {
            for (String id : cam.getCameraIdList()) {
                Boolean flash = cam.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) { cam.setTorchMode(id, !torchOn); return; }
            }
        } catch (Throwable e) { Toast.makeText(c, "Flashlight unavailable", Toast.LENGTH_SHORT).show(); }
    }

    boolean needWrite() {
        if (Settings.System.canWrite(c)) return false;
        open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.getPackageName())));
        return true;
    }

    void rotate() {
        if (needWrite()) return;
        int cur = Settings.System.getInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
        Settings.System.putInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, cur == 1 ? 0 : 1);
    }

    void dnd(NotificationManager nm) {
        if (!nm.isNotificationPolicyAccessGranted()) { open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); return; }
        nm.setInterruptionFilter(nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_ALL
                ? NotificationManager.INTERRUPTION_FILTER_PRIORITY : NotificationManager.INTERRUPTION_FILTER_ALL);
    }

    View slider(String glyph, final boolean bright, final AudioManager am) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(U.round(U.tile(c), 22));
        r.setPadding(U.dp(16), U.dp(6), U.dp(16), U.dp(6));
        r.addView(U.tv(c, glyph, 18, U.text(c), false));
        SeekBar s = new SeekBar(c);
        s.setProgressTintList(ColorStateList.valueOf(U.ACCENT));
        s.setThumbTintList(ColorStateList.valueOf(U.ACCENT));
        if (bright) {
            s.setMax(255);
            s.setProgress(Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128));
        } else {
            s.setMax(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            s.setProgress(am.getStreamVolume(AudioManager.STREAM_MUSIC));
        }
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean user) {
                if (!user) return;
                if (bright) {
                    if (!Settings.System.canWrite(c)) return;
                    Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(1, p));
                } else am.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0);
            }
            public void onStartTrackingTouch(SeekBar b) { if (bright) needWrite(); }
            public void onStopTrackingTouch(SeekBar b) { }
        });
        r.addView(s, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    View mediaCard(final MediaController m) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(U.round(U.tile(c), 22));
        card.setPadding(U.dp(16), U.dp(10), U.dp(8), U.dp(10));
        MediaMetadata md = m.getMetadata();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        String t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String a = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        TextView tt = U.tv(c, t == null ? "Media" : t, 14, U.text(c), true);
        tt.setSingleLine(); tt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView aa = U.tv(c, a == null ? "" : a, 12, U.sub(c), false);
        aa.setSingleLine(); aa.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(tt); col.addView(aa);
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(mc("⏮", m, 0));
        final TextView p = mc(playing(m) ? "⏸" : "▶", m, 1);
        card.addView(p);
        card.addView(mc("⏭", m, 2));
        return card;
    }

    boolean playing(MediaController m) { PlaybackState s = m.getPlaybackState(); return s != null && s.getState() == PlaybackState.STATE_PLAYING; }

    TextView mc(String g, final MediaController m, final int k) {
        final TextView t = U.tv(c, g, 22, U.text(c), false);
        t.setPadding(U.dp(12), U.dp(4), U.dp(12), U.dp(4));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    MediaController.TransportControls tc = m.getTransportControls();
                    if (k == 0) tc.skipToPrevious(); else if (k == 2) tc.skipToNext();
                    else if (playing(m)) { tc.pause(); t.setText("▶"); } else { tc.play(); t.setText("⏸"); }
                } catch (Throwable e) { }
            }
        });
        return t;
    }
}
