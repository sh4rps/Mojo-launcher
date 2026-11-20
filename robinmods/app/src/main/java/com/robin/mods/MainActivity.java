package com.robin.mods;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int RC_TREE = 21;
    private static final int BG = 0xFF16181C, CARD = 0xFF26292F, ACC = 0xFF1BD96A, TXT = 0xFFF2F3F5, MUT = 0xFF9AA0AA, RED = 0xFFE5484D;
    private static final String[] TYPES = {"mod", "modpack", "shader", "resourcepack"};
    private static final String[] TYPE_LABELS = {"Mods", "Modpacks", "Shaders", "Resource packs"};
    private static final String[] LOADERS = {"fabric", "forge", "neoforge", "quilt"};

    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> cache = new LruCache<>(60);
    private FrameLayout body;
    private List<Store.Inst> insts;
    private List<String> mcVersions = new ArrayList<>(Arrays.asList("1.21.4", "1.21.1", "1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.16.5", "1.12.2", "1.8.9"));
    private int tab, typeIdx, instIdx = -1, offset;
    private String query = "";
    private LinearLayout results;
    private TextView more;
    // pending actions that need a folder pick
    private String pName, pMc, pLoader;
    private File pPack;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        insts = Store.load(this);
        if (!insts.isEmpty()) instIdx = 0;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(CARD);
        bar.addView(tabBtn("Discover", 0), new LinearLayout.LayoutParams(0, dp(52), 1f));
        bar.addView(tabBtn("Instances", 1), new LinearLayout.LayoutParams(0, dp(52), 1f));
        root.addView(bar);
        setContentView(root);
        show();
        pool.execute(() -> { try { List<String> v = Api.gameVersions(); if (!v.isEmpty()) mcVersions = v; } catch (Exception ignored) { } });
    }

    // ------------------------------------------------------------ helpers
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView text(String s, int sp, int c) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(c); return t; }

    private TextView btn(String s, int color, Runnable r) {
        TextView t = text(s, 13, color == ACC ? 0xFF06260F : TXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(9), dp(14), dp(9));
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(10));
        t.setBackground(g);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private TextView tabBtn(String s, int idx) {
        TextView t = text(s, 14, TXT);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(v -> { tab = idx; show(); });
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout col() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(24), dp(14), dp(8));
        return c;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void show() {
        body.removeAllViews();
        body.addView(tab == 0 ? discover() : instances(), new FrameLayout.LayoutParams(-1, -1));
    }

    // ----------------------------------------------------------- discover
    private View discover() {
        LinearLayout c = col();
        c.addView(text("Robin Mods", 22, TXT));
        c.addView(text("Search Modrinth, install into your instance", 12, MUT), lp(-2, -2, 0, 0, 0, 8));

        Spinner sp = new Spinner(this);
        List<String> names = new ArrayList<>();
        names.add("No instance (browse only)");
        for (Store.Inst i : insts) names.add(i.name + "  -  " + i.mc + " " + i.loader);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        sp.setSelection(instIdx + 1);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (instIdx != pos - 1) { instIdx = pos - 1; search(true); }
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        c.addView(sp);

        LinearLayout types = new LinearLayout(this);
        for (int i = 0; i < TYPES.length; i++) {
            final int k = i;
            TextView t = btn(TYPE_LABELS[i], i == typeIdx ? ACC : CARD, () -> { typeIdx = k; show(); });
            types.addView(t, lp(-2, -2, 0, 0, 6, 0));
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(types);
        c.addView(hs, lp(-1, -2, 0, 8, 0, 0));

        LinearLayout row = new LinearLayout(this);
        EditText q = new EditText(this);
        q.setTextColor(TXT); q.setHintTextColor(MUT); q.setHint("Search"); q.setSingleLine(true); q.setText(query);
        q.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        q.setOnEditorActionListener((v, a, e) -> { query = q.getText().toString(); search(true); return true; });
        row.addView(q, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(btn("Search", ACC, () -> { query = q.getText().toString(); search(true); }), lp(-2, -2, 8, 0, 0, 0));
        c.addView(row);

        ScrollView sv = new ScrollView(this);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        sv.addView(results);
        c.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        search(true);
        return c;
    }

    private Store.Inst cur() { return instIdx >= 0 && instIdx < insts.size() ? insts.get(instIdx) : null; }

    private void search(boolean reset) {
        if (results == null) return;
        if (reset) { offset = 0; results.removeAllViews(); }
        final Store.Inst in = cur();
        final String type = TYPES[typeIdx], q = query;
        final int off = offset;
        if (more != null) { results.removeView(more); more = null; }
        TextView loading = text("Loading...", 13, MUT);
        results.addView(loading);
        pool.execute(() -> {
            try {
                JSONArray hits = Api.search(q, type, in == null ? null : in.loader, in == null ? null : in.mc, off);
                runOnUiThread(() -> {
                    if (off != offset || results == null) return;
                    results.removeView(loading);
                    for (int i = 0; i < hits.length(); i++) results.addView(card(hits.optJSONObject(i), type), lp(-1, -2, 0, 0, 0, 8));
                    offset += hits.length();
                    if (hits.length() == 0 && off == 0) results.addView(text("Nothing found.", 13, MUT));
                    if (hits.length() >= 20) {
                        more = btn("Load more", CARD, () -> search(false));
                        results.addView(more);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> { results.removeView(loading); results.addView(text("Failed: " + e.getMessage(), 13, RED)); });
            }
        });
    }

    private View card(JSONObject h, String type) {
        LinearLayout c = new LinearLayout(this);
        c.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable g = new GradientDrawable(); g.setColor(CARD); g.setCornerRadius(dp(12));
        c.setBackground(g);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        c.addView(iv, new LinearLayout.LayoutParams(dp(60), dp(60)));
        loadIcon(iv, h.optString("icon_url", ""));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(10), 0, 0, 0);
        t.addView(text(h.optString("title"), 15, TXT));
        t.addView(text("by " + h.optString("author") + "  -  " + fmt(h.optLong("downloads")) + " downloads", 11, MUT));
        TextView d = text(h.optString("description"), 12, MUT);
        d.setMaxLines(2); d.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(d);
        c.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        c.addView(btn("Install", ACC, () -> install(h.optString("project_id"), h.optString("title"), type)), lp(-2, -2, 6, 0, 0, 0));
        return c;
    }

    private String fmt(long n) { return n >= 1000000 ? String.format(Locale.US, "%.1fM", n / 1e6) : n >= 1000 ? String.format(Locale.US, "%.1fK", n / 1e3) : "" + n; }

    private void loadIcon(ImageView iv, String url) {
        if (url.isEmpty()) return;
        Bitmap b = cache.get(url);
        if (b != null) { iv.setImageBitmap(b); return; }
        pool.execute(() -> {
            try {
                byte[] d = Api.bytes(url);
                BitmapFactory.Options o = new BitmapFactory.Options(); o.inSampleSize = 2;
                Bitmap bm = BitmapFactory.decodeByteArray(d, 0, d.length, o);
                if (bm != null) { cache.put(url, bm); runOnUiThread(() -> iv.setImageBitmap(bm)); }
            } catch (Exception ignored) { }
        });
    }

    // ------------------------------------------------------------ install
    private ProgressDialog busy(String msg) {
        ProgressDialog p = new ProgressDialog(this);
        p.setMessage(msg); p.setCancelable(false); p.show();
        return p;
    }

    private void install(String pid, String title, String type) {
        if (type.equals("modpack")) { installPack(pid, title); return; }
        Store.Inst in = cur();
        if (in == null) { toast("Create an instance (Instances tab) and select it first"); return; }
        ProgressDialog pd = busy("Installing " + title + "...");
        pool.execute(() -> {
            String res;
            try {
                Installer.installProject(getContentResolver(), in, pid, type, s -> runOnUiThread(() -> pd.setMessage(s)));
                res = "Installed " + title + " into " + in.name;
            } catch (Exception e) { res = "Failed: " + e.getMessage(); }
            final String m = res;
            runOnUiThread(() -> { pd.dismiss(); new AlertDialog.Builder(this).setMessage(m).setPositiveButton("OK", null).show(); });
        });
    }

    private void installPack(String pid, String title) {
        Store.Inst in = cur();
        ProgressDialog pd = busy("Fetching " + title + "...");
        pool.execute(() -> {
            try {
                JSONArray vs = Api.versions(pid, "modpack", null, in == null ? null : in.mc);
                if (vs.length() == 0) throw new Exception("No version found");
                JSONObject f = Installer.primary(vs.getJSONObject(0));
                File tmp = new File(getCacheDir(), "pack.mrpack");
                Api.downloadTo(f.getString("url"), tmp);
                String[] info = Installer.mrpackInfo(tmp);
                runOnUiThread(() -> {
                    pd.dismiss();
                    pPack = tmp; pName = info[2]; pMc = info[0]; pLoader = info[1];
                    new AlertDialog.Builder(this).setTitle(pName)
                            .setMessage("Minecraft " + pMc + " / " + pLoader + "\n\nNext: choose an empty folder where this modpack's files (mods, configs) will be saved. Point your launcher's game directory at that folder.")
                            .setPositiveButton("Choose folder", (d, w) -> pickTree())
                            .setNegativeButton("Cancel", (d, w) -> pPack = null).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { pd.dismiss(); toast("Failed: " + e.getMessage()); });
            }
        });
    }

    // ---------------------------------------------------------- instances
    private View instances() {
        LinearLayout c = col();
        c.addView(text("Instances", 22, TXT));
        c.addView(text("An instance = a game folder + Minecraft version + loader", 12, MUT), lp(-2, -2, 0, 0, 0, 8));
        c.addView(btn("+ New instance", ACC, this::newInstanceDialog), lp(-1, -2, 0, 4, 0, 10));
        ScrollView sv = new ScrollView(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        sv.addView(l);
        c.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (insts.isEmpty()) l.addView(text("No instances yet. Create one, then install mods from Discover.", 13, MUT));
        for (Store.Inst i : insts) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            GradientDrawable g = new GradientDrawable(); g.setColor(CARD); g.setCornerRadius(dp(12));
            card.setBackground(g);
            card.addView(text(i.name, 16, TXT));
            card.addView(text("Minecraft " + i.mc + "  -  " + i.loader, 12, MUT));
            card.setOnClickListener(v -> { instIdx = insts.indexOf(i); tab = 0; show(); });
            card.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setMessage("Remove \"" + i.name + "\" from the list? (Files stay on your phone.)")
                        .setPositiveButton("Remove", (d, w) -> { insts.remove(i); Store.save(this, insts); instIdx = insts.isEmpty() ? -1 : 0; show(); })
                        .setNegativeButton("Cancel", null).show();
                return true;
            });
            l.addView(card, lp(-1, -2, 0, 0, 0, 8));
        }
        return c;
    }

    private void newInstanceDialog() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(8), dp(20), 0);
        EditText n = new EditText(this); n.setHint("Instance name"); n.setSingleLine(true); n.setInputType(InputType.TYPE_CLASS_TEXT);
        Spinner mc = new Spinner(this); mc.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, mcVersions));
        Spinner ld = new Spinner(this); ld.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, LOADERS));
        l.addView(n); l.addView(text("Minecraft version", 12, MUT)); l.addView(mc); l.addView(text("Loader", 12, MUT)); l.addView(ld);
        new AlertDialog.Builder(this).setTitle("New instance").setView(l)
                .setPositiveButton("Choose game folder", (d, w) -> {
                    pName = n.getText().toString().trim().isEmpty() ? "My instance" : n.getText().toString().trim();
                    pMc = (String) mc.getSelectedItem(); pLoader = (String) ld.getSelectedItem(); pPack = null;
                    pickTree();
                }).setNegativeButton("Cancel", null).show();
    }

    private void pickTree() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, RC_TREE);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != RC_TREE || res != RESULT_OK || data == null || data.getData() == null) { return; }
        Uri tree = data.getData();
        try { getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (Exception ignored) { }
        Store.Inst in = new Store.Inst();
        in.name = pName; in.mc = pMc; in.loader = pLoader; in.tree = tree.toString();
        insts.add(in); Store.save(this, insts); instIdx = insts.size() - 1;
        if (pPack != null) {
            File pack = pPack; pPack = null;
            ProgressDialog pd = busy("Installing modpack...");
            pool.execute(() -> {
                String m;
                try { Installer.installMrpack(getContentResolver(), tree, pack, s -> runOnUiThread(() -> pd.setMessage(s))); m = "Modpack installed. Set your launcher's game folder to the folder you picked."; }
                catch (Exception e) { m = "Failed: " + e.getMessage(); }
                final String fm = m;
                runOnUiThread(() -> { pd.dismiss(); new AlertDialog.Builder(this).setMessage(fm).setPositiveButton("OK", null).show(); show(); });
            });
        } else { tab = 1; show(); toast("Instance created"); }
    }
}
