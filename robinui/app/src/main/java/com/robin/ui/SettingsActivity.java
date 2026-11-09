package com.robin.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class SettingsActivity extends Activity {
    LinearLayout box;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(U.night(this) ? 0xFF0E0F12 : 0xFFF2F2F6));
        getWindow().setDecorFitsSystemWindows(true);
        ScrollView sv = new ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(U.dp(18), U.dp(36), U.dp(18), U.dp(30));
        sv.addView(box);
        setContentView(sv);
    }

    @Override protected void onResume() { super.onResume(); render(); }

    void render() {
        box.removeAllViews();
        int tx = U.text(this), sb = U.sub(this);
        box.addView(U.tv(this, "RobinUI", 30, tx, true));
        TextView s = U.tv(this, "Set up the island, control center and refresh rate", 13, sb, false);
        s.setPadding(0, 0, 0, U.dp(14));
        box.addView(s);

        head("Dynamic island");
        sw("Island always active", "island", true, true);
        sw("Show idle pill", "idle", true, false);
        final SeekBar y = new SeekBar(this);
        y.setMax(40);
        y.setProgress(Prefs.i("island_y", 8));
        y.setProgressTintList(android.content.res.ColorStateList.valueOf(U.ACCENT));
        y.setThumbTintList(android.content.res.ColorStateList.valueOf(U.ACCENT));
        y.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar a, int p, boolean u) { }
            public void onStartTrackingTouch(SeekBar a) { }
            public void onStopTrackingTouch(SeekBar a) {
                Prefs.setI("island_y", a.getProgress());
                IslandService.send(SettingsActivity.this, "stop");
                a.postDelayed(new Runnable() { public void run() { IslandService.start(SettingsActivity.this); } }, 500);
            }
        });
        box.addView(U.tv(this, "Island height from top", 13, sb, false));
        box.addView(y);

        head("Performance");
        sw("Adaptive 120 Hz (60 Hz when idle). Off = RobinUI stays at 60 Hz", "hz", true, false);

        head("Home");
        sw("Show app names", "labels", true, false);
        sw("Drawer mode (home keeps only apps you add)", "drawer_mode", false, false);
        btn("Show all hidden apps", new Runnable() { public void run() { Prefs.list("hidden", new java.util.ArrayList<String>()); Toast.makeText(SettingsActivity.this, "Done", Toast.LENGTH_SHORT).show(); } });

        head("Permissions");
        perm("Display over other apps (island + control center)", Settings.canDrawOverlays(this),
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        String l = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        perm("Notification access (music + notifications in island)", l != null && l.contains(getPackageName()),
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        perm("Modify system settings (brightness, auto-rotate)", Settings.System.canWrite(this),
                new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())));
        perm("Make RobinUI your default launcher", false, new Intent(Settings.ACTION_HOME_SETTINGS));
        TextView n = U.tv(this, "Control Center: swipe down on the island, swipe down from the top-right of the home screen, or tap the RobinUI notification.", 12, sb, false);
        n.setPadding(0, U.dp(16), 0, 0);
        box.addView(n);
    }

    void head(String t) {
        TextView v = U.tv(this, t, 13, U.ACCENT, true);
        v.setPadding(0, U.dp(22), 0, U.dp(8));
        box.addView(v);
    }

    void sw(String label, final String key, boolean def, final boolean island) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(U.text(this));
        s.setTextSize(15);
        s.setChecked(Prefs.b(key, def));
        s.setPadding(0, U.dp(8), 0, U.dp(8));
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                Prefs.setB(key, on);
                if (island) { if (on) IslandService.start(SettingsActivity.this); else IslandService.send(SettingsActivity.this, "stop"); }
                else if (key.equals("idle")) IslandService.send(SettingsActivity.this, "refresh");
            }
        });
        box.addView(s);
    }

    void btn(String t, final Runnable r) {
        TextView v = U.tv(this, t, 14, 0xFFFFFFFF, true);
        v.setBackground(U.round(U.ACCENT, 16));
        v.setGravity(Gravity.CENTER);
        v.setPadding(U.dp(14), U.dp(11), U.dp(14), U.dp(11));
        v.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { r.run(); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = U.dp(8);
        box.addView(v, lp);
    }

    void perm(String t, boolean ok, final Intent i) {
        TextView v = U.tv(this, (ok ? "✓  " : "○  ") + t, 14, ok ? U.ACCENT : U.text(this), false);
        v.setBackground(U.round(U.tile(this), 16));
        v.setPadding(U.dp(14), U.dp(13), U.dp(14), U.dp(13));
        v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) { try { startActivity(i); } catch (Throwable e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); } }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = U.dp(8);
        box.addView(v, lp);
    }
}
