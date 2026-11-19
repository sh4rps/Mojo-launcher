package com.robin.island;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {
    private SharedPreferences p;
    private TextView status;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String s, int sp, int c) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(c); return t; }

    private TextView btn(String s, boolean prim, Runnable r) {
        TextView t = text(s, 14, 0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable g = new GradientDrawable(); g.setColor(prim ? 0xFF8B5CF6 : 0xFF2A2A38); g.setCornerRadius(dp(12));
        t.setBackground(g);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences("island", 0);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(0xFF14141C);
        col.setPadding(dp(18), dp(28), dp(18), dp(18));
        col.addView(text("ROBINISLAND", 24, 0xFFF2F2F7));
        status = text("", 13, 0xFF9A9AAE);
        col.addView(status);

        LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(-1, -2); m.setMargins(0, dp(12), 0, 0);
        col.addView(btn("1. Allow display over other apps", false, this::askOverlay), m);
        col.addView(btn("2. Start / Stop island", true, this::toggle), m);
        col.addView(btn("3. Battery: set ROBINISLAND to 'No restrictions'", false, () -> {
            try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) { }
        }), m);

        addSlider(col, "Width (dp)", "w", 60, 400, 120);
        addSlider(col, "Height (dp)", "h", 20, 80, 32);
        addSlider(col, "Top offset (dp)", "y", 0, 120, 6);
        addSlider(col, "Left / right shift (dp)", "x", -150, 150, 0);
        addSlider(col, "Text size", "t", 8, 24, 13);
        col.addView(text("Tap the island to expand/collapse it.", 12, 0xFF9A9AAE), m);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF14141C);
        sv.addView(col);
        setContentView(sv);
    }

    private void addSlider(LinearLayout col, String label, String key, int min, int max, int def) {
        TextView t = text("", 13, 0xFFF2F2F7);
        SeekBar s = new SeekBar(this);
        s.setMax(max - min);
        s.setProgress(p.getInt(key, def) - min);
        t.setText(label + ": " + p.getInt(key, def));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int v, boolean user) {
                p.edit().putInt(key, v + min).apply();
                t.setText(label + ": " + (v + min));
                if (user && IslandService.running) IslandService.start(MainActivity.this);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(-1, -2); m.setMargins(0, dp(14), 0, 0);
        col.addView(t, m); col.addView(s);
    }

    private boolean canOverlay() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this); }

    private void askOverlay() {
        if (canOverlay()) { Toast.makeText(this, "Already allowed", Toast.LENGTH_SHORT).show(); return; }
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }

    private void toggle() {
        if (IslandService.running) {
            stopService(new Intent(this, IslandService.class));
            p.edit().putBoolean("on", false).apply();
        } else {
            if (!canOverlay()) { Toast.makeText(this, "Allow overlay permission first (step 1)", Toast.LENGTH_LONG).show(); return; }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0)
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
            p.edit().putBoolean("on", true).apply();
            IslandService.start(this);
        }
        status.postDelayed(this::refresh, 400);
    }

    private void refresh() { status.setText("Overlay permission: " + (canOverlay() ? "OK" : "needed") + "   Island: " + (IslandService.running ? "running" : "stopped")); }

    @Override protected void onResume() { super.onResume(); refresh(); }
}
