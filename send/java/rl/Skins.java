package rl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Send Launcher skin + cape manager. Entry point: Skins.hook(view) wires the "robin_skins" menu button. */
public class Skins extends Activity {
    static final int BG = 0xff0e0b1f, CARD = 0xff1b1436, EDGE = 0xff3b2a73, ACC = 0xff8b5cf6, ACC_D = 0xff6d28d9,
            TXT = 0xfff5f3ff, SUB = 0xffc4b5fd, OK = 0xff45c27a, BAD = 0xfff07a68;

    /** Called from the launcher main menu (smali). */
    public static void hook(View root) {
        try {
            View b = root.findViewWithTag("robin_skins");
            if (b == null) return;
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { v.getContext().startActivity(new Intent(v.getContext(), Skins.class)); }
            });
        } catch (Throwable t) { }
    }

    // ---- state ----
    private SkinView3D view;
    private TextView status, acct, variantBtn;
    private LinearLayout capeRow, libRow;
    private String token, name, uuid;
    private boolean microsoft, slim;
    private String capeId;                 // selected cape (null = none)
    private String activeCapeId;           // what the account has now
    private JSONArray capes = new JSONArray();
    private Bitmap pending;                // skin shown in the preview
    private File libDir;

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable box(int fill, int stroke, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(r)); g.setStroke(dp(2), stroke);
        return g;
    }

    private TextView btn(String s, boolean primary) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(14); t.setGravity(Gravity.CENTER); t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(primary ? Color.WHITE : SUB);
        t.setBackground(box(primary ? ACC_D : EDGE, primary ? ACC : 0xff5b4a99, 8));
        t.setPadding(dp(10), dp(10), dp(10), dp(10));
        return t;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); return r;
    }

    private void add(LinearLayout r, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        r.addView(v, lp);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        loadAccount();
        libDir = libraryDir();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(32), dp(16), 0);
        TextView title = new TextView(this);
        title.setText("Skins & Capes"); title.setTextSize(24); title.setTextColor(ACC); title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        acct = new TextView(this);
        acct.setTextSize(13); acct.setTextColor(SUB); acct.setPadding(0, dp(2), 0, dp(8));
        acct.setText(name == null ? "No account selected" : name + (microsoft ? "  (Microsoft)" : "  (offline / other)"));
        root.addView(acct);

        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, dp(30));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        view = new SkinView3D(this);
        view.setBackground(box(CARD, EDGE, 10));
        col.addView(view, new LinearLayout.LayoutParams(-1, dp(300)));
        col.addView(space(8));

        LinearLayout r1 = row();
        variantBtn = btn("Model: Classic", false);
        variantBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            slim = !slim; variantBtn.setText(slim ? "Model: Slim (Alex)" : "Model: Classic (Steve)");
            if (pending != null) view.setSkin(pending, slim);
        }});
        add(r1, variantBtn);
        col.addView(r1);
        col.addView(space(6));

        LinearLayout r2 = row();
        TextView pick = btn("Choose PNG", false);
        pick.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/png");
            startActivityForResult(i, 7);
        }});
        TextView player = btn("From player name", false);
        player.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { askPlayer(); } });
        add(r2, pick); add(r2, player);
        col.addView(r2);
        col.addView(space(6));

        TextView apply = btn("Apply skin to my account", true);
        apply.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { applySkin(); } });
        col.addView(apply, new LinearLayout.LayoutParams(-1, -2));
        col.addView(space(14));

        col.addView(label("Capes"));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        capeRow = new LinearLayout(this); capeRow.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(capeRow);
        col.addView(hs);
        col.addView(space(6));
        TextView applyCape = btn("Apply cape to my account", true);
        applyCape.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { applyCape(); } });
        col.addView(applyCape, new LinearLayout.LayoutParams(-1, -2));
        col.addView(space(14));

        col.addView(label("Saved skins"));
        HorizontalScrollView hs2 = new HorizontalScrollView(this);
        libRow = new LinearLayout(this); libRow.setOrientation(LinearLayout.HORIZONTAL);
        hs2.addView(libRow);
        col.addView(hs2);
        status = new TextView(this);
        status.setTextSize(12); status.setTextColor(SUB); status.setPadding(0, dp(12), 0, 0);
        col.addView(status);
        setContentView(root);

        if (!microsoft) status.setText("This is not a Microsoft account, so skins can only be previewed here. Real skin / cape changes need a Microsoft account (offline accounts show a skin only through a skin mod or server).");
        refreshLibrary();
        showCapes();
        if (microsoft && token != null) loadProfile();
        else view.setSkin(defaultSkin(), false);
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(16); t.setTextColor(TXT); t.setTypeface(Typeface.DEFAULT_BOLD); t.setPadding(0, 0, 0, dp(6));
        return t;
    }

    private View space(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(h))); return v; }

    private void msg(final String s, final boolean ok) {
        runOnUiThread(new Runnable() { public void run() { status.setTextColor(ok ? OK : BAD); status.setText(s); } });
    }

    // ---- account (read straight from the launcher) ----
    private void loadAccount() {
        try {
            Class<?> c = Class.forName("git.artdeell.mojo.authenticator.accounts.Accounts");
            Object a = c.getMethod("getCurrent").invoke(null);
            if (a == null) return;
            Class<?> ac = a.getClass();
            token = (String) ac.getField("accessToken").get(a);
            name = (String) ac.getField("username").get(a);
            uuid = (String) ac.getField("profileId").get(a);
            microsoft = ac.getField("isMicrosoft").getBoolean(a);
        } catch (Throwable t) { }
    }

    private File libraryDir() {
        File d = null;
        try {
            Class<?> c = Class.forName("git.artdeell.mojo.Tools");
            Field f = c.getField("DIR_GAME_HOME");
            String p = (String) f.get(null);
            if (p != null) d = new File(p, "skins");
        } catch (Throwable t) { }
        if (d == null) d = new File(getExternalFilesDir(null), "skins");
        d.mkdirs();
        return d;
    }

    private Bitmap defaultSkin() {
        // plain grey placeholder so the preview works before anything is loaded
        Bitmap b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        b.eraseColor(0xff8b5cf6);
        return b;
    }

    // ---- profile ----
    private void loadProfile() {
        new Thread(new Runnable() { public void run() {
            try {
                JSONObject p = Mojang.profile(token);
                JSONArray skins = p.optJSONArray("skins");
                capes = p.optJSONArray("capes") == null ? new JSONArray() : p.optJSONArray("capes");
                for (int i = 0; i < capes.length(); i++) if ("ACTIVE".equals(capes.getJSONObject(i).optString("state"))) activeCapeId = capes.getJSONObject(i).getString("id");
                capeId = activeCapeId;
                if (skins != null && skins.length() > 0) {
                    JSONObject s = skins.getJSONObject(0);
                    for (int i = 0; i < skins.length(); i++) if ("ACTIVE".equals(skins.getJSONObject(i).optString("state"))) s = skins.getJSONObject(i);
                    final boolean sl = "SLIM".equalsIgnoreCase(s.optString("variant"));
                    final Bitmap bm = Mojang.bitmap(s.getString("url"));
                    runOnUiThread(new Runnable() { public void run() { setPreview(bm, sl); } });
                }
                final JSONArray cp = capes;
                runOnUiThread(new Runnable() { public void run() { showCapes(); } });
                for (int i = 0; i < cp.length(); i++) {
                    final JSONObject c = cp.getJSONObject(i);
                    final Bitmap cb = Mojang.bitmap(c.getString("url"));
                    capeBitmaps.put(c.getString("id"), cb);
                    runOnUiThread(new Runnable() { public void run() { showCapes(); selectCape(capeId, false); } });
                }
            } catch (final Mojang.Err e) {
                msg(e.code == 401 ? "Your login expired. Go back, tap the account at the top of the launcher (or press Play once) so it refreshes, then open Skins again." : "Mojang error " + e.code + ": " + e.getMessage(), false);
            } catch (Throwable t) { msg("Can't reach Mojang: " + t.getMessage(), false); }
        }}).start();
    }

    private final java.util.HashMap<String, Bitmap> capeBitmaps = new java.util.HashMap<>();

    private void setPreview(Bitmap b, boolean sl) {
        pending = b; slim = sl;
        variantBtn.setText(slim ? "Model: Slim (Alex)" : "Model: Classic (Steve)");
        view.setSkin(b, sl);
    }

    private void showCapes() {
        capeRow.removeAllViews();
        addCapeItem(null, "No cape");
        for (int i = 0; i < capes.length(); i++) {
            JSONObject c = capes.optJSONObject(i);
            if (c != null) addCapeItem(c.optString("id"), c.optString("alias", "Cape"));
        }
        if (capes.length() == 0 && microsoft) {
            TextView t = new TextView(this); t.setText("  (this account owns no capes)"); t.setTextColor(SUB); t.setTextSize(12);
            capeRow.addView(t);
        }
    }

    private void addCapeItem(final String id, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean sel = (id == null && capeId == null) || (id != null && id.equals(capeId));
        item.setBackground(box(sel ? ACC_D : CARD, sel ? ACC : EDGE, 8));
        item.setPadding(dp(10), dp(8), dp(10), dp(8));
        ImageView iv = new ImageView(this);
        Bitmap cb = id == null ? null : capeBitmaps.get(id);
        if (cb != null && cb.getWidth() >= 11 && cb.getHeight() >= 17) {
            Bitmap face = Bitmap.createBitmap(cb, 1, 1, 10, 16);
            iv.setImageBitmap(Bitmap.createScaledBitmap(face, 40, 64, false));
        }
        item.addView(iv, new LinearLayout.LayoutParams(dp(30), dp(48)));
        TextView t = new TextView(this); t.setText(label); t.setTextSize(11); t.setTextColor(TXT); t.setGravity(Gravity.CENTER);
        item.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.setMargins(0, 0, dp(8), 0);
        item.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { selectCape(id, true); } });
        capeRow.addView(item, lp);
    }

    private void selectCape(String id, boolean redraw) {
        capeId = id;
        view.setCape(id == null ? null : capeBitmaps.get(id));
        if (redraw) showCapes();
    }

    // ---- actions ----
    private byte[] png(Bitmap b) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, o);
        return o.toByteArray();
    }

    private void applySkin() {
        if (pending == null) { msg("Pick a skin first.", false); return; }
        if (!microsoft || token == null) { msg("Skin upload needs a Microsoft account.", false); return; }
        final byte[] data = png(SkinView3D.normalize(pending));
        final boolean sl = slim;
        msg("Uploading…", true);
        new Thread(new Runnable() { public void run() {
            try { Mojang.uploadSkin(token, data, sl); msg("Skin applied. It shows in game after the next launch (Mojang can take a minute).", true); }
            catch (Mojang.Err e) { msg(e.code == 401 ? "Login expired. Press Play once / re-select the account to refresh it." : "Mojang refused (" + e.code + "): " + e.getMessage(), false); }
            catch (Throwable t) { msg("Upload failed: " + t.getMessage(), false); }
        }}).start();
    }

    private void applyCape() {
        if (!microsoft || token == null) { msg("Capes need a Microsoft account.", false); return; }
        final String id = capeId;
        msg("Updating cape…", true);
        new Thread(new Runnable() { public void run() {
            try { Mojang.setCape(token, id); activeCapeId = id; msg(id == null ? "Cape hidden." : "Cape applied.", true); }
            catch (Mojang.Err e) { msg(e.code == 401 ? "Login expired. Press Play once / re-select the account to refresh it." : "Mojang refused (" + e.code + "): " + e.getMessage(), false); }
            catch (Throwable t) { msg("Failed: " + t.getMessage(), false); }
        }}).start();
    }

    private void askPlayer() {
        final EditText et = new EditText(this);
        et.setHint("Minecraft username"); et.setSingleLine();
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Copy a player's skin").setView(et)
            .setPositiveButton("Load", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { loadPlayer(et.getText().toString()); }
            }).setNegativeButton("Cancel", null).show();
    }

    private void loadPlayer(final String n) {
        if (n.trim().isEmpty()) return;
        msg("Looking up " + n + "…", true);
        new Thread(new Runnable() { public void run() {
            try {
                String[] r = Mojang.playerSkin(n);
                final Bitmap b = Mojang.bitmap(r[0]);
                final boolean sl = "slim".equals(r[1]);
                save(b, n.trim());
                runOnUiThread(new Runnable() { public void run() { setPreview(b, sl); refreshLibrary(); } });
                msg("Loaded " + n + "'s skin. Tap Apply to use it.", true);
            } catch (Throwable t) { msg("Couldn't find that player.", false); }
        }}).start();
    }

    private void save(Bitmap b, String name) {
        try {
            FileOutputStream o = new FileOutputStream(new File(libDir, name.replaceAll("[^A-Za-z0-9_.-]", "_") + ".png"));
            b.compress(Bitmap.CompressFormat.PNG, 100, o); o.close();
        } catch (Throwable t) { }
    }

    private void refreshLibrary() {
        libRow.removeAllViews();
        File[] fs = libDir.listFiles();
        if (fs == null || fs.length == 0) {
            TextView t = new TextView(this); t.setText("Skins you load are saved in games/SendLauncher/skins"); t.setTextColor(SUB); t.setTextSize(12);
            libRow.addView(t); return;
        }
        for (final File f : fs) {
            if (!f.getName().endsWith(".png")) continue;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b == null || b.getWidth() < 64) continue;
            final Bitmap full = b;
            Bitmap head = Bitmap.createBitmap(b, 8, 8, 8, 8);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setBackground(box(CARD, EDGE, 8)); item.setPadding(dp(8), dp(8), dp(8), dp(6));
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(Bitmap.createScaledBitmap(head, 64, 64, false));
            item.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView t = new TextView(this); t.setText(f.getName().replace(".png", "")); t.setTextSize(10); t.setTextColor(TXT);
            t.setMaxWidth(dp(60)); t.setSingleLine();
            item.addView(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.setMargins(0, 0, dp(8), 0);
            item.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { setPreview(full, slim); } });
            libRow.addView(item, lp);
        }
    }

    @Override protected void onActivityResult(int rq, int rc, Intent d) {
        super.onActivityResult(rq, rc, d);
        if (rq != 7 || rc != RESULT_OK || d == null || d.getData() == null) return;
        try {
            Uri u = d.getData();
            InputStream in = getContentResolver().openInputStream(u);
            Bitmap b = BitmapFactory.decodeStream(in); in.close();
            if (b == null || b.getWidth() != 64 || (b.getHeight() != 64 && b.getHeight() != 32)) { msg("A Minecraft skin must be a 64x64 (or 64x32) PNG.", false); return; }
            setPreview(SkinView3D.normalize(b), slim);
            save(pending, "skin_" + (System.currentTimeMillis() / 1000));
            refreshLibrary();
            msg("Skin loaded. Tap Apply to use it.", true);
        } catch (Throwable t) { msg("Couldn't read that file.", false); }
    }
}
