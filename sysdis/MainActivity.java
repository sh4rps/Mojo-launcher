package com.sysdis.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.*;

public class MainActivity extends Activity {
    // Commonly safe-to-disable bloat on Xiaomi/HyperOS (Redmi). Only shown if installed.
    static final String[] SUGGESTED = {
        "com.miui.analytics", "com.miui.msa.global", "com.miui.bugreport", "com.miui.hybrid",
        "com.miui.hybrid.accessory", "com.miui.yellowpage", "com.xiaomi.mipicks", "com.xiaomi.discover",
        "com.miui.systemAdSolution", "com.miui.cloudbackup", "com.miui.videoplayer", "com.miui.player",
        "com.mi.globalbrowser", "com.xiaomi.glgm", "com.miui.fm", "com.miui.notes", "com.miui.compass",
        "com.miui.weather2", "com.miui.miservice", "com.xiaomi.payment", "com.mipay.wallet.in",
        "com.facebook.appmanager", "com.facebook.services", "com.facebook.system",
        "com.netflix.partner.activation", "com.google.android.apps.tachyon", "com.google.android.videos",
        "com.google.android.music", "com.google.android.apps.magazines", "com.google.android.apps.podcasts",
        "com.google.android.apps.youtube.music", "com.google.android.projection.gearhead"
    };
    // Disabling these can brick the UI / calling / boot. Strong warning.
    static final String[] CRITICAL = {
        "com.android.systemui", "com.miui.home", "com.mi.android.globallauncher", "com.android.settings",
        "com.android.phone", "com.android.providers.telephony", "com.android.providers.contacts",
        "com.android.providers.settings", "com.android.providers.media", "com.android.providers.downloads",
        "com.android.inputmethod.latin", "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.google.android.gms", "com.google.android.gsf", "com.android.vending", "com.miui.securitycenter",
        "com.miui.system", "com.android.server.telecom", "com.android.bluetooth", "com.android.nfc",
        "com.android.mms", "com.android.contacts", "com.android.incallui", "com.android.shell",
        "com.android.webview", "com.google.android.webview", "com.android.permissioncontroller",
        "com.google.android.permissioncontroller", "com.android.externalstorage", "android",
        "com.miui.core", "com.xiaomi.xmsf", "com.android.keychain", "com.android.cellbroadcastreceiver"
    };

    final Set<String> sugg = new HashSet<>(Arrays.asList(SUGGESTED));
    final Set<String> crit = new HashSet<>(Arrays.asList(CRITICAL));
    final List<Row> all = new ArrayList<>();
    final List<Row> shown = new ArrayList<>();
    PackageManager pm;
    ListView list;
    EditText search;
    TextView count;
    Adapter adapter;
    int filter = 0; // 0 all 1 system 2 user 3 disabled 4 suggested
    Button[] chips;

    static class Row {
        String label, pkg; boolean system, enabled;
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        getWindow().setStatusBarColor(0xFF0F1320);
        getWindow().setNavigationBarColor(0xFF0F1320);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F1320);
        root.setPadding(dp(14), dp(40), dp(14), 0);

        TextView title = new TextView(this);
        title.setText("System App Disabler");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        count = new TextView(this);
        count.setTextColor(0xFF8F9BB3);
        count.setTextSize(12);
        count.setPadding(0, dp(2), 0, dp(8));
        root.addView(count);

        search = new EditText(this);
        search.setHint("Search app or package");
        search.setHintTextColor(0xFF6B7690);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setBackgroundColor(0xFF1A2033);
        search.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) { apply(); }
        });

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"All", "System", "User", "Disabled", "Suggested"};
        chips = new Button[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            Button c = new Button(this);
            c.setText(names[i]);
            c.setAllCaps(false);
            c.setTextSize(13);
            c.setMinHeight(0); c.setMinimumHeight(0); c.setMinWidth(0); c.setMinimumWidth(0);
            c.setPadding(dp(16), dp(8), dp(16), dp(8));
            c.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { filter = idx; apply(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
            lp.setMargins(0, dp(8), dp(8), dp(8));
            chipRow.addView(c, lp);
            chips[i] = c;
        }
        hs.addView(chipRow);
        root.addView(hs);

        list = new ListView(this);
        list.setDivider(null);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) { actions(shown.get(pos)); }
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button bulk = new Button(this);
        bulk.setText("Copy ADB script for all Suggested");
        bulk.setAllCaps(false);
        bulk.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { bulkScript(); }
        });
        root.addView(bulk, new LinearLayout.LayoutParams(-1, -2));
        root.setPadding(dp(14), dp(40), dp(14), dp(10));

        setContentView(root);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!all.isEmpty()) load(); // refresh states after returning from App Info
    }

    void load() {
        new Thread(new Runnable() {
            public void run() {
                final List<Row> tmp = new ArrayList<>();
                List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.MATCH_UNINSTALLED_PACKAGES);
                for (ApplicationInfo ai : apps) {
                    if ((ai.flags & ApplicationInfo.FLAG_INSTALLED) == 0) continue;
                    Row r = new Row();
                    r.pkg = ai.packageName;
                    CharSequence l = pm.getApplicationLabel(ai);
                    r.label = l == null ? ai.packageName : l.toString();
                    r.system = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                    int st = pm.getApplicationEnabledSetting(ai.packageName);
                    r.enabled = ai.enabled && st != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                            && st != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                    tmp.add(r);
                }
                Collections.sort(tmp, new Comparator<Row>() {
                    public int compare(Row a, Row b) { return a.label.compareToIgnoreCase(b.label); }
                });
                runOnUiThread(new Runnable() { public void run() { all.clear(); all.addAll(tmp); apply(); } });
            }
        }).start();
    }

    void apply() {
        String q = search.getText().toString().trim().toLowerCase();
        shown.clear();
        for (Row r : all) {
            if (filter == 1 && !r.system) continue;
            if (filter == 2 && r.system) continue;
            if (filter == 3 && r.enabled) continue;
            if (filter == 4 && !sugg.contains(r.pkg)) continue;
            if (!q.isEmpty() && !r.label.toLowerCase().contains(q) && !r.pkg.toLowerCase().contains(q)) continue;
            shown.add(r);
        }
        for (int i = 0; i < chips.length; i++) {
            chips[i].setBackgroundColor(i == filter ? 0xFF2F6BFF : 0xFF1A2033);
            chips[i].setTextColor(i == filter ? Color.WHITE : 0xFFB6C0D8);
        }
        int dis = 0; for (Row r : all) if (!r.enabled) dis++;
        count.setText(all.size() + " apps  •  " + dis + " disabled  •  showing " + shown.size());
        adapter.notifyDataSetChanged();
    }

    void copy(String s, String msg) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("cmd", s));
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    void openInfo(Row r) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + r.pkg));
        try { startActivity(i); } catch (Throwable t) { Toast.makeText(this, "Can't open App info", Toast.LENGTH_SHORT).show(); }
    }

    void actions(final Row r) {
        boolean c = crit.contains(r.pkg);
        String msg = r.pkg + "\n" + (r.system ? "System app" : "User app") + " • " + (r.enabled ? "Enabled" : "Disabled");
        if (c) msg += "\n\n⚠ CRITICAL: disabling this can break the phone (no UI / calls / boot loop). Don't.";
        else if (sugg.contains(r.pkg)) msg += "\n\n✓ Commonly disabled bloat on Xiaomi phones.";
        String[] items = {
            r.enabled ? "Open App info (tap Disable there)" : "Open App info (tap Enable there)",
            "Copy ADB disable command",
            "Copy ADB enable command",
            "Copy package name"
        };
        new AlertDialog.Builder(this).setTitle(r.label).setMessage(msg)
            .setPositiveButton("App info", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { openInfo(r); }
            })
            .setNeutralButton("Copy ADB cmd", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    copy("pm " + (r.enabled ? "disable-user --user 0 " : "enable ") + r.pkg, "Command copied");
                }
            })
            .setNegativeButton("Close", null).show();
    }

    void bulkScript() {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Row r : all) {
            if (sugg.contains(r.pkg) && r.enabled && !crit.contains(r.pkg)) {
                sb.append("pm disable-user --user 0 ").append(r.pkg).append("\n");
                n++;
            }
        }
        if (n == 0) { Toast.makeText(this, "No suggested apps left to disable", Toast.LENGTH_SHORT).show(); return; }
        copy(sb.toString(), n + " commands copied (run in adb shell)");
    }

    class Adapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int pos, View cv, ViewGroup parent) {
            LinearLayout row; ImageView ic; TextView t1, t2, tag;
            if (cv == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(10), dp(8), dp(10));
                ic = new ImageView(MainActivity.this);
                row.addView(ic, new LinearLayout.LayoutParams(dp(40), dp(40)));
                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(dp(12), 0, dp(8), 0);
                t1 = new TextView(MainActivity.this); t1.setTextColor(Color.WHITE); t1.setTextSize(15); t1.setSingleLine(true);
                t2 = new TextView(MainActivity.this); t2.setTextColor(0xFF8F9BB3); t2.setTextSize(11); t2.setSingleLine(true);
                col.addView(t1); col.addView(t2);
                row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
                tag = new TextView(MainActivity.this); tag.setTextSize(11); tag.setTypeface(Typeface.DEFAULT_BOLD);
                row.addView(tag);
                row.setTag(new View[]{ic, t1, t2, tag});
            } else row = (LinearLayout) cv;
            View[] v = (View[]) row.getTag();
            Row r = shown.get(pos);
            try { ((ImageView) v[0]).setImageDrawable(pm.getApplicationIcon(r.pkg)); } catch (Throwable t) { ((ImageView) v[0]).setImageDrawable(null); }
            ((TextView) v[1]).setText(r.label);
            ((TextView) v[2]).setText(r.pkg);
            TextView tg = (TextView) v[3];
            if (!r.enabled) { tg.setText("DISABLED"); tg.setTextColor(0xFFFF6B6B); }
            else if (crit.contains(r.pkg)) { tg.setText("CRITICAL"); tg.setTextColor(0xFFFFB020); }
            else if (sugg.contains(r.pkg)) { tg.setText("BLOAT"); tg.setTextColor(0xFF4ADE80); }
            else { tg.setText(r.system ? "SYSTEM" : ""); tg.setTextColor(0xFF6B7690); }
            row.setAlpha(r.enabled ? 1f : 0.6f);
            return row;
        }
    }
}
