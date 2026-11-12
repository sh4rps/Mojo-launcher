package com.robin.setting;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.text.Collator;
import java.util.*;

/** Pick a refresh rate per app. */
public class AppsActivity extends Activity {
    static class Row { String pkg, label; Drawable icon; }
    private final List<Row> all = new ArrayList<>(), shown = new ArrayList<>();
    private BaseAdapter ad;
    private String q = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UI.BG);
        int p = UI.dp(this, 16);
        root.setPadding(p, UI.dp(this, 36), p, 0);
        root.addView(UI.text(this, "Per-app refresh rate", 22, UI.ACC, true));
        TextView hint = UI.text(this, "Tap an app and choose the Hz it should always run at. Needs the Robin Setting accessibility service ON.", 13, UI.SUB, false);
        hint.setPadding(0, UI.dp(this, 4), 0, UI.dp(this, 10));
        root.addView(hint);
        EditText search = new EditText(this);
        search.setHint("Search apps");
        search.setHintTextColor(UI.OFF); search.setTextColor(UI.TXT); search.setSingleLine();
        search.setBackground(UI.box(this, UI.CARD, UI.EDGE, 10));
        search.setPadding(UI.dp(this, 14), UI.dp(this, 10), UI.dp(this, 14), UI.dp(this, 10));
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));
        root.addView(UI.gap(this, 10));
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setClipToPadding(false);
        lv.setPadding(0, 0, 0, UI.dp(this, 40));
        root.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        ad = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup g) {
                Row r = shown.get(i);
                LinearLayout row = new LinearLayout(AppsActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(UI.box(AppsActivity.this, UI.CARD, UI.EDGE, 10));
                int pp = UI.dp(AppsActivity.this, 10);
                row.setPadding(pp, pp, pp, pp);
                AbsListView.LayoutParams lp = new AbsListView.LayoutParams(-1, -2);
                row.setLayoutParams(lp);
                ImageView ic = new ImageView(AppsActivity.this);
                ic.setImageDrawable(r.icon);
                row.addView(ic, new LinearLayout.LayoutParams(UI.dp(AppsActivity.this, 40), UI.dp(AppsActivity.this, 40)));
                LinearLayout mid = new LinearLayout(AppsActivity.this);
                mid.setOrientation(LinearLayout.VERTICAL);
                mid.setPadding(UI.dp(AppsActivity.this, 12), 0, 0, 0);
                mid.addView(UI.text(AppsActivity.this, r.label, 16, UI.TXT, true));
                mid.addView(UI.text(AppsActivity.this, r.pkg, 11, UI.SUB, false));
                row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
                int hz = Prefs.app(AppsActivity.this, r.pkg);
                TextView tag = UI.text(AppsActivity.this, hz == 0 ? "Default" : hz + " Hz", 14, hz == 0 ? UI.SUB : UI.ACC, true);
                row.addView(tag);
                // spacing between rows
                FrameLayout fl = new FrameLayout(AppsActivity.this);
                fl.setPadding(0, 0, 0, UI.dp(AppsActivity.this, 8));
                fl.addView(row);
                fl.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
                return fl;
            }
        };
        lv.setAdapter(ad);
        lv.setOnItemClickListener((a, v, i, id) -> pick(shown.get(i)));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { q = s.toString().trim().toLowerCase(Locale.ROOT); filter(); }
            public void afterTextChanged(Editable s) { }
        });
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent li = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<Row> tmp = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : pm.queryIntentActivities(li, 0)) {
                String pk = ri.activityInfo.packageName;
                if (!seen.add(pk)) continue;
                Row r = new Row();
                r.pkg = pk; r.label = String.valueOf(ri.loadLabel(pm)); r.icon = ri.loadIcon(pm);
                tmp.add(r);
            }
            final Collator col = Collator.getInstance();
            Collections.sort(tmp, (x, y) -> col.compare(x.label, y.label));
            runOnUiThread(() -> { all.clear(); all.addAll(tmp); filter(); });
        }).start();
    }

    @Override protected void onResume() { super.onResume(); if (ad != null) ad.notifyDataSetChanged(); }

    private void filter() {
        shown.clear();
        for (Row r : all) if (q.isEmpty() || r.label.toLowerCase(Locale.ROOT).contains(q) || r.pkg.contains(q)) shown.add(r);
        ad.notifyDataSetChanged();
    }

    private void pick(final Row r) {
        final List<String> names = new ArrayList<>();
        final List<Integer> vals = new ArrayList<>();
        names.add("Default (don't force)"); vals.add(0);
        for (int hz : HzCtl.RATES) {
            names.add(hz + " Hz" + (HzCtl.supported(this, hz) ? "" : "  (not listed by this display)"));
            vals.add(hz);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(r.label)
            .setItems(names.toArray(new String[0]), (d, which) -> {
                Prefs.app(this, r.pkg, vals.get(which));
                ad.notifyDataSetChanged();
                if (HzService.inst == null) Toast.makeText(this, "Turn ON the Robin Setting accessibility service on the main screen", Toast.LENGTH_LONG).show();
            }).show();
    }
}
