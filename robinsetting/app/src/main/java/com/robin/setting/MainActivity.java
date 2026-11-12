package com.robin.setting;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout content;
    private String q = "";
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView curTxt, report;
    private boolean pendingAll;
    private String lastAsked = "";

    // ---- permissions -------------------------------------------------------------------------------
    private interface Check { boolean ok(); }
    private static class Perm {
        String name, why; Check ok; Runnable ask;
        Perm(String n, String w, Check c, Runnable a) { name = n; why = w; ok = c; ask = a; }
    }
    private List<Perm> perms() {
        final String pkg = getPackageName();
        final Uri u = Uri.parse("package:" + pkg);
        List<Perm> l = new ArrayList<>();
        l.add(new Perm("Modify system settings", "Lets the app write the refresh-rate settings.",
            () -> Settings.System.canWrite(this),
            () -> startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, u))));
        l.add(new Perm("Accessibility service", "Needed for per-app Hz and the display-mode request.",
            this::a11yOn, () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        l.add(new Perm("Display over other apps", "Backup way to hold a display mode.",
            () -> Settings.canDrawOverlays(this),
            () -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, u))));
        l.add(new Perm("Notifications", "Status messages.",
            () -> Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0,
            () -> requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1)));
        l.add(new Perm("All files access", "Full storage access.",
            () -> Environment.isExternalStorageManager(),
            () -> startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, u))));
        l.add(new Perm("Usage access", "See which app is in use.",
            this::usageOn, () -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))));
        l.add(new Perm("Battery: unrestricted", "Keeps the Hz service from being killed.",
            () -> getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(pkg),
            () -> startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, u))));
        l.add(new Perm("Write secure settings (ADB grant)", "Optional. Makes the Hz switch much more reliable on HyperOS.",
            () -> checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == 0,
            this::secureDialog));
        return l;
    }

    private boolean a11yOn() {
        if (HzService.inst != null) return true;
        try {
            String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains(getPackageName() + "/");
        } catch (Throwable t) { return false; }
    }

    private boolean usageOn() {
        try {
            AppOpsManager a = getSystemService(AppOpsManager.class);
            return a.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) { return false; }
    }

    private void secureDialog() {
        final String cmd = "pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS";
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Write secure settings")
            .setMessage("Android only allows this through ADB. Using Wireless debugging (Developer options > Wireless debugging) with any on-phone ADB app, run:\n\n" + cmd + "\n\nIt is optional: the app also works without it.")
            .setPositiveButton("Copy command", (d, w) -> {
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("adb", cmd));
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Close", null).show();
    }

    // ---- UI ------------------------------------------------------------------------------------------
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UI.BG);
        int p = UI.dp(this, 16);
        root.setPadding(p, UI.dp(this, 36), p, 0);
        TextView title = UI.text(this, "Robin Setting", 26, UI.ACC, true);
        title.setPadding(0, 0, 0, UI.dp(this, 10));
        root.addView(title);
        EditText search = new EditText(this);
        search.setHint("Search settings");
        search.setHintTextColor(UI.OFF); search.setTextColor(UI.TXT); search.setSingleLine();
        search.setBackground(UI.box(this, UI.CARD, UI.EDGE, 10));
        search.setPadding(UI.dp(this, 14), UI.dp(this, 10), UI.dp(this, 14), UI.dp(this, 10));
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));
        root.addView(UI.gap(this, 12));
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, UI.dp(this, 40));
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            public void onTextChanged(CharSequence s, int a, int b2, int c) { q = s.toString().trim().toLowerCase(Locale.ROOT); render(); }
            public void afterTextChanged(Editable s) { }
        });
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        render();
        if (pendingAll) continueAll();
    }

    private boolean matches(String hay) {
        if (q.isEmpty()) return true;
        hay = hay.toLowerCase(Locale.ROOT);
        for (String t : q.split("\\s+")) if (!hay.contains(t)) return false;
        return true;
    }

    private void render() {
        content.removeAllViews();
        if (matches("refresh rate hz 60 90 120 144 display force")) content.addView(hzCard());
        if (matches("permission permissions access grant allow")) content.addView(permCard());
        int n = 0;
        LinearLayout list = UI.card(this);
        list.setPadding(UI.dp(this, 6), UI.dp(this, 6), UI.dp(this, 6), UI.dp(this, 6));
        for (final String[] s : SettingsList.ALL) {
            if (!matches(s[0] + " " + s[1])) continue;
            n++;
            TextView t = UI.text(this, s[0], 16, UI.TXT, false);
            t.setPadding(UI.dp(this, 10), UI.dp(this, 13), UI.dp(this, 10), UI.dp(this, 13));
            t.setOnClickListener(v -> open(s[2]));
            list.addView(t);
        }
        if (!q.isEmpty()) {
            TextView t = UI.text(this, "Search \"" + q + "\" in apps (set Hz per app)", 15, UI.ACC, true);
            t.setPadding(UI.dp(this, 10), UI.dp(this, 13), UI.dp(this, 10), UI.dp(this, 13));
            t.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
            list.addView(t);
        }
        if (n > 0 || !q.isEmpty()) {
            if (q.isEmpty()) content.addView(UI.text(this, "All settings", 14, UI.SUB, true));
            content.addView(list);
        }
    }

    private void open(String action) {
        try { startActivity(new Intent(action)); }
        catch (Throwable t) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Throwable t2) { Toast.makeText(this, "Can't open this page on your phone", Toast.LENGTH_SHORT).show(); }
        }
    }

    private View hzCard() {
        LinearLayout c = UI.card(this);
        c.addView(UI.text(this, "Refresh rate", 18, UI.TXT, true));
        curTxt = UI.text(this, "", 14, UI.SUB, false);
        curTxt.setPadding(0, UI.dp(this, 4), 0, UI.dp(this, 10));
        c.addView(curTxt);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int sel = Prefs.global(this);
        for (final int hz : HzCtl.RATES) {
            TextView btn = UI.button(this, hz + " Hz", sel == hz);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(UI.dp(this, 3), 0, UI.dp(this, 3), 0);
            row.addView(btn, lp);
            btn.setOnClickListener(v -> setGlobal(hz));
        }
        c.addView(row);
        c.addView(UI.gap(this, 8));
        TextView auto = UI.button(this, "Auto (stop forcing)", sel == 0);
        auto.setOnClickListener(v -> setGlobal(0));
        c.addView(auto);
        c.addView(UI.gap(this, 8));
        TextView apps = UI.button(this, "Set Hz for each app", false);
        apps.setTextColor(UI.ACC);
        apps.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        c.addView(apps);
        report = UI.text(this, "", 12, UI.SUB, false);
        report.setPadding(0, UI.dp(this, 10), 0, 0);
        c.addView(report);
        updateCur();
        return c;
    }

    private void setGlobal(int hz) {
        Prefs.global(this, hz);
        String r;
        if (hz == 0) {
            HzCtl.writeAuto(this);
            HzService s = HzService.inst;
            if (s != null) s.release();
            r = "Back to automatic.";
        } else {
            r = HzCtl.writeSettings(this, hz);
            if (HzService.inst != null) HzService.refresh(this);
            else r += " Accessibility service is OFF, so only the settings write was tried.";
        }
        final String rr = r;
        render();
        if (report != null) report.setText(rr);
        h.postDelayed(this::updateCur, 900);
        h.postDelayed(this::updateCur, 2500);
    }

    private void updateCur() {
        if (curTxt == null) return;
        StringBuilder s = new StringBuilder("Display now: " + HzCtl.current(this) + " Hz. Supported: ");
        for (int[] m : HzCtl.modes(this)) s.append(m[1]).append(' ');
        curTxt.setText(s.toString().trim());
    }

    private View permCard() {
        LinearLayout c = UI.card(this);
        c.addView(UI.text(this, "Permissions", 18, UI.TXT, true));
        c.addView(UI.gap(this, 6));
        for (final Perm p : perms()) {
            boolean ok = p.ok.ok();
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setPadding(0, UI.dp(this, 7), 0, UI.dp(this, 7));
            r.addView(UI.text(this, (ok ? "●  " : "○  ") + p.name, 15, ok ? UI.OK : UI.TXT, true));
            r.addView(UI.text(this, p.why, 12, UI.SUB, false));
            r.setOnClickListener(v -> { try { p.ask.run(); } catch (Throwable t) { Toast.makeText(this, "Can't open this page", Toast.LENGTH_SHORT).show(); } });
            c.addView(r);
        }
        TextView all = UI.button(this, "Grant all", true);
        all.setOnClickListener(v -> { pendingAll = true; lastAsked = ""; continueAll(); });
        c.addView(UI.gap(this, 6));
        c.addView(all);
        TextView note = UI.text(this, "Camera, mic, contacts and the like are not requested because nothing here uses them.", 11, UI.SUB, false);
        note.setPadding(0, UI.dp(this, 8), 0, 0);
        c.addView(note);
        return c;
    }

    private void continueAll() {
        for (Perm p : perms()) {
            if (p.ok.ok() || p.name.startsWith("Write secure")) continue;
            if (p.name.equals(lastAsked)) { pendingAll = false; return; }   // user skipped it
            lastAsked = p.name;
            try { p.ask.run(); } catch (Throwable t) { pendingAll = false; }
            return;
        }
        pendingAll = false;
        Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show();
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] r) { render(); if (pendingAll) continueAll(); }
}
