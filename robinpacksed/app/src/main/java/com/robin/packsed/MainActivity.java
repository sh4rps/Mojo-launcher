package com.robin.packsed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT = 1, REQ_PACK_ZIP = 2, REQ_TEX_PNG = 3, REQ_TEX_ZIP = 4, REQ_REPLACE = 5;
    private static final int[] PALETTE = {
            0xFF000000, 0xFF3A3A3A, 0xFF7F7F7F, 0xFFBFBFBF, 0xFFFFFFFF, 0xFF5B3A1E, 0xFF8B5A2B, 0xFFC08A4B,
            0xFFE4C98A, 0xFF7A1F1F, 0xFFD62828, 0xFFFF6B3D, 0xFFF4A261, 0xFFFFD23F, 0xFFFFF3A0, 0xFF1B5E20,
            0xFF2E8B3A, 0xFF7BC74D, 0xFFB5E48C, 0xFF0B3D4F, 0xFF1D7FA6, 0xFF3B82F6, 0xFF7DD3FC, 0xFF3B2A6B,
            0xFF8B5CF6, 0xFFC4A1FF, 0xFFEC4899, 0xFFFFB3D1, 0xFF2B2B2B, 0xFF8C8C8C, 0xFFD9D9D9, 0x00000000};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int screen = 0;                 // 0 packs, 1 pack, 2 editor
    private Model.Pack pack;
    private Model.Tex tex;
    private FrameLayout root;

    // editor state
    private PixelView pv;
    private LinearLayout frameStrip, palRow;
    private TextView title, swatch, playBtn, ticksLbl, smoothBtn;
    private TextView[] toolBtns;
    private TextView mirrorBtn, gridBtn, onionBtn;
    private boolean playing;
    private int tick;
    private final List<Integer> recent = new ArrayList<>();
    private int curColor = 0xFF000000;
    private final Runnable player = new Runnable() {
        @Override public void run() {
            if (!playing || tex == null) return;
            int n = tex.frames.size(), ft = Math.max(1, tex.frametime);
            int t = tick % (n * ft);
            int a = t / ft;
            pv.setPlayback(true, a, (a + 1) % n, (t % ft) / (float) ft);
            tick++;
            handler.postDelayed(this, 50);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Model.load(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        showPacks();
    }

    @Override protected void onPause() { super.onPause(); stopPlay(); Model.save(this); }

    @Override public void onBackPressed() {
        if (screen == 2) { stopPlay(); Model.save(this); showPack(pack); }
        else if (screen == 1) { Model.save(this); showPacks(); }
        else super.onBackPressed();
    }

    private void setScreen(View v) { root.removeAllViews(); root.addView(v, new FrameLayout.LayoutParams(-1, -1)); }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(Ui.BG);
        return l;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ------------------------------------------------------------ packs
    private void showPacks() {
        screen = 0; pack = null; tex = null;
        LinearLayout col = column();
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 12));
        col.addView(Ui.text(this, "ROBINPACKSED", 24, Ui.TEXT));
        TextView sub = Ui.text(this, "Minecraft texture & animation pack editor", 12, Ui.MUTED);
        col.addView(sub);
        col.addView(Ui.btn(this, "+ New pack", true, this::newPackDialog), Ui.margin(Ui.lp(-1, -2), this, 0, 14, 0, 10));
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (Model.packs.isEmpty()) list.addView(Ui.text(this, "No packs yet. Create one to start making textures.", 13, Ui.MUTED));
        for (Model.Pack p : Model.packs) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
            card.setBackground(Ui.round(Ui.CARD, 12, this));
            card.addView(Ui.text(this, p.name, 16, Ui.TEXT));
            card.addView(Ui.text(this, p.texs.size() + " textures  -  " + Model.VERSIONS[p.version], 12, Ui.MUTED));
            card.setOnClickListener(v -> showPack(p));
            card.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setTitle(p.name).setItems(new String[]{"Delete pack"}, (d, w) ->
                        new AlertDialog.Builder(this).setMessage("Delete \"" + p.name + "\" and all its textures?")
                                .setPositiveButton("Delete", (d2, w2) -> { Model.packs.remove(p); Model.save(this); showPacks(); })
                                .setNegativeButton("Cancel", null).show()).show();
                return true;
            });
            list.addView(card, Ui.margin(Ui.lp(-1, -2), this, 0, 0, 0, 10));
        }
        setScreen(col);
    }

    private void newPackDialog() {
        EditText name = edit("My Pack", "Pack name");
        new AlertDialog.Builder(this).setTitle("New pack").setView(pad(name))
                .setPositiveButton("Create", (d, w) -> {
                    Model.Pack p = new Model.Pack();
                    String n = name.getText().toString().trim();
                    if (!n.isEmpty()) p.name = n;
                    Model.packs.add(p); Model.save(this); showPack(p);
                }).setNegativeButton("Cancel", null).show();
    }

    private EditText edit(String text, String hint) {
        EditText e = new EditText(this);
        e.setText(text); e.setHint(hint); e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        return e;
    }

    private View pad(View v) {
        FrameLayout f = new FrameLayout(this);
        f.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        f.addView(v, new FrameLayout.LayoutParams(-1, -2));
        return f;
    }

    // -------------------------------------------------------------- pack
    private void showPack(Model.Pack p) {
        screen = 1; pack = p; tex = null;
        LinearLayout col = column();
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 12));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.btn(this, "<", false, () -> onBackPressed()));
        TextView t = Ui.text(this, p.name, 18, Ui.TEXT);
        t.setPadding(Ui.dp(this, 12), 0, 0, 0);
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(Ui.btn(this, "Settings", false, () -> packSettingsDialog(p)));
        col.addView(top);
        LinearLayout row = new LinearLayout(this);
        row.addView(Ui.btn(this, "+ Texture", true, this::newTexDialog), new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(Ui.btn(this, "Import PNG", false, () -> pickPng(REQ_IMPORT)), Ui.margin(new LinearLayout.LayoutParams(0, -2, 1f), this, 8, 0, 0, 0));
        row.addView(Ui.btn(this, "Export ZIP", false, () -> {
            if (p.texs.isEmpty()) { toast("Add a texture first"); return; }
            createDoc(REQ_PACK_ZIP, "application/zip", Ui.clean(p.name) + ".zip");
        }), Ui.margin(new LinearLayout.LayoutParams(0, -2, 1f), this, 8, 0, 0, 0));
        col.addView(row, Ui.margin(Ui.lp(-1, -2), this, 0, 12, 0, 10));
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (p.texs.isEmpty()) list.addView(Ui.text(this, "No textures. Tap + Texture (name it like the vanilla file, e.g. stone, diamond_sword) or import a PNG.", 13, Ui.MUTED));
        for (Model.Tex x : p.texs) {
            LinearLayout card = new LinearLayout(this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
            card.setBackground(Ui.round(Ui.CARD, 12, this));
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(thumb(x.frames.get(0), x.size, 56));
            iv.setBackgroundColor(0xFF2C2C38);
            card.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(Ui.dp(this, 12), 0, 0, 0);
            info.addView(Ui.text(this, x.name, 15, Ui.TEXT));
            info.addView(Ui.text(this, x.category + "/  -  " + x.size + "x" + x.size + (x.animated() ? "  -  " + x.frames.size() + " frames" : ""), 12, Ui.MUTED));
            card.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            card.setOnClickListener(v -> showEditor(x));
            card.setOnLongClickListener(v -> { texOptions(x); return true; });
            list.addView(card, Ui.margin(Ui.lp(-1, -2), this, 0, 0, 0, 8));
        }
        setScreen(col);
    }

    private BitmapDrawable thumb(int[] px, int size, int dpSize) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, size, 0, 0, size, size);
        int s = Ui.dp(this, dpSize);
        Bitmap sc = Bitmap.createScaledBitmap(b, s, s, false);
        b.recycle();
        return new BitmapDrawable(getResources(), sc);
    }

    private void texOptions(Model.Tex x) {
        new AlertDialog.Builder(this).setTitle(x.name).setItems(new String[]{"Duplicate", "Delete"}, (d, w) -> {
            if (w == 0) {
                Model.Tex c = new Model.Tex(x.name + "_copy", x.category, x.size);
                c.frames.clear();
                for (int[] f : x.frames) c.frames.add(f.clone());
                c.frametime = x.frametime; c.interpolate = x.interpolate;
                pack.texs.add(pack.texs.indexOf(x) + 1, c);
                Model.save(this); showPack(pack);
            } else {
                new AlertDialog.Builder(this).setMessage("Delete texture \"" + x.name + "\"?")
                        .setPositiveButton("Delete", (d2, w2) -> { pack.texs.remove(x); Model.save(this); showPack(pack); })
                        .setNegativeButton("Cancel", null).show();
            }
        }).show();
    }

    private void packSettingsDialog(Model.Pack p) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        EditText n = edit(p.name, "Pack name"), d = edit(p.desc, "Description");
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Model.VERSIONS));
        sp.setSelection(p.version);
        l.addView(n); l.addView(d);
        l.addView(Ui.text(this, "Target Minecraft version", 12, Ui.MUTED));
        l.addView(sp);
        new AlertDialog.Builder(this).setTitle("Pack settings").setView(pad(l))
                .setPositiveButton("Save", (dd, w) -> {
                    String nn = n.getText().toString().trim();
                    if (!nn.isEmpty()) p.name = nn;
                    p.desc = d.getText().toString();
                    p.version = sp.getSelectedItemPosition();
                    Model.save(this); showPack(p);
                }).setNegativeButton("Cancel", null).show();
    }

    private void newTexDialog() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        EditText n = edit("", "Texture name (e.g. stone)"), c = edit("block", "Folder (block, item, entity/chest...)");
        Spinner sp = new Spinner(this);
        String[] sizes = {"16", "32", "64", "128", "256"};
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sizes));
        l.addView(n); l.addView(c);
        l.addView(Ui.text(this, "Size (pixels)", 12, Ui.MUTED));
        l.addView(sp);
        new AlertDialog.Builder(this).setTitle("New texture").setView(pad(l))
                .setPositiveButton("Create", (dd, w) -> {
                    String nm = Ui.clean(n.getText().toString().trim());
                    Model.Tex t = new Model.Tex(nm, Ui.clean(c.getText().toString().trim().isEmpty() ? "block" : c.getText().toString().trim()), Integer.parseInt(sizes[sp.getSelectedItemPosition()]));
                    pack.texs.add(t); Model.save(this); showEditor(t);
                }).setNegativeButton("Cancel", null).show();
    }

    // --------------------------------------------------------------- IO
    private void pickPng(int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, req);
    }

    private void createDoc(int req, String mime, String name) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, req);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            switch (req) {
                case REQ_IMPORT -> importPng(u, null);
                case REQ_REPLACE -> importPng(u, tex);
                case REQ_PACK_ZIP -> {
                    try (OutputStream os = getContentResolver().openOutputStream(u)) { Export.packZip(pack, os); }
                    toast("Pack exported");
                }
                case REQ_TEX_PNG -> {
                    try (OutputStream os = getContentResolver().openOutputStream(u)) { os.write(Export.png(tex)); }
                    toast("PNG exported (" + tex.frames.size() + " frame" + (tex.animated() ? "s, vertical strip" : "") + ")");
                }
                case REQ_TEX_ZIP -> {
                    try (OutputStream os = getContentResolver().openOutputStream(u)) { Export.texZip(tex, os); }
                    toast("ZIP exported");
                }
                default -> { }
            }
        } catch (Exception e) {
            toast("Failed: " + e.getMessage());
        }
    }

    private String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) { String n = c.getString(i); int d = n.lastIndexOf('.'); return d > 0 ? n.substring(0, d) : n; }
            }
        } catch (Exception ignored) { }
        return "imported";
    }

    private void importPng(Uri u, Model.Tex into) throws Exception {
        Bitmap bm;
        try (InputStream in = getContentResolver().openInputStream(u)) { bm = BitmapFactory.decodeStream(in); }
        if (bm == null) { toast("Not a readable image"); return; }
        int w = bm.getWidth(), h = bm.getHeight();
        int size = Math.min(w, 256);
        int frames;
        if (h >= w && h % w == 0 && w <= 256) frames = h / w;      // vertical animation strip
        else { frames = 1; bm = Bitmap.createScaledBitmap(bm, size, size, false); h = size; w = size; }
        if (w > 256) { toast("Image too large (max 256 px wide)"); return; }
        Model.Tex t = into != null ? into : new Model.Tex(Ui.clean(displayName(u)), "block", size);
        t.size = w; t.frames.clear();
        for (int i = 0; i < frames; i++) {
            int[] px = new int[w * w];
            bm.getPixels(px, 0, w, 0, i * w, w, w);
            t.frames.add(px);
        }
        if (into == null) pack.texs.add(t);
        Model.save(this);
        if (into != null) showEditor(t); else showPack(pack);
        toast(frames > 1 ? "Imported " + frames + " frames" : "Imported");
    }

    // ------------------------------------------------------------ editor
    private void showEditor(Model.Tex t) {
        screen = 2; tex = t; playing = false;
        LinearLayout col = column();

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 4));
        bar.addView(Ui.btn(this, "<", false, this::onBackPressed));
        title = Ui.text(this, "", 14, Ui.TEXT);
        title.setPadding(Ui.dp(this, 10), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(Ui.btn(this, "Undo", false, () -> pv.undo()));
        bar.addView(Ui.btn(this, "Redo", false, () -> pv.redo()), Ui.margin(Ui.lp(-2, -2), this, 6, 0, 6, 0));
        bar.addView(Ui.btn(this, "Menu", true, this::editorMenu));
        col.addView(bar);

        pv = new PixelView(this);
        pv.listener = new PixelView.Listener() {
            @Override public void onEdited() { buildFrames(); }
            @Override public void onPicked(int c) { setColor(c, false); }
        };
        pv.color = curColor;
        col.addView(pv, new LinearLayout.LayoutParams(-1, 0, 1f));

        // tools
        LinearLayout tools = new LinearLayout(this);
        String[] names = {"Pen", "Eraser", "Fill", "Pick", "Line"};
        toolBtns = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            toolBtns[i] = Ui.btn(this, names[i], false, () -> selectTool(k));
            tools.addView(toolBtns[i], Ui.margin(Ui.lp(-2, -2), this, 0, 0, 6, 0));
        }
        mirrorBtn = Ui.btn(this, "Mirror", false, () -> { pv.mirror = !pv.mirror; Ui.setSelected(mirrorBtn, pv.mirror); pv.invalidate(); });
        gridBtn = Ui.btn(this, "Grid", true, () -> { pv.grid = !pv.grid; Ui.setSelected(gridBtn, pv.grid); pv.invalidate(); });
        onionBtn = Ui.btn(this, "Onion", true, () -> { pv.onion = !pv.onion; Ui.setSelected(onionBtn, pv.onion); pv.invalidate(); });
        tools.addView(mirrorBtn, Ui.margin(Ui.lp(-2, -2), this, 8, 0, 6, 0));
        tools.addView(gridBtn, Ui.margin(Ui.lp(-2, -2), this, 0, 0, 6, 0));
        tools.addView(onionBtn);
        col.addView(hscroll(tools), Ui.margin(Ui.lp(-1, -2), this, 8, 4, 8, 0));

        // colors
        LinearLayout colors = new LinearLayout(this);
        colors.setGravity(Gravity.CENTER_VERTICAL);
        swatch = Ui.text(this, "", 10, Color.WHITE);
        swatch.setGravity(Gravity.CENTER);
        swatch.setOnClickListener(v -> colorDialog());
        colors.addView(swatch, Ui.margin(Ui.lp(Ui.dp(this, 46), Ui.dp(this, 46)), this, 0, 0, 8, 0));
        palRow = new LinearLayout(this);
        colors.addView(hscroll(palRow), new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(colors, Ui.margin(Ui.lp(-1, -2), this, 8, 6, 8, 0));

        // frames
        frameStrip = new LinearLayout(this);
        col.addView(hscroll(frameStrip), Ui.margin(Ui.lp(-1, -2), this, 8, 6, 8, 0));
        LinearLayout ctl = new LinearLayout(this);
        ctl.setGravity(Gravity.CENTER_VERTICAL);
        ctl.addView(Ui.btn(this, "+ Frame", true, () -> { stopPlay(); t.frames.add(frameIndex() + 1, new int[t.size * t.size]); pv.setFrame(frameIndex() + 1); buildFrames(); }), Ui.margin(Ui.lp(-2, -2), this, 0, 0, 6, 0));
        ctl.addView(Ui.btn(this, "Copy", false, () -> { stopPlay(); t.frames.add(frameIndex() + 1, t.frames.get(frameIndex()).clone()); pv.setFrame(frameIndex() + 1); buildFrames(); }), Ui.margin(Ui.lp(-2, -2), this, 0, 0, 6, 0));
        ctl.addView(Ui.btn(this, "Del", false, this::deleteFrame), Ui.margin(Ui.lp(-2, -2), this, 0, 0, 6, 0));
        ctl.addView(Ui.btn(this, "Move <", false, () -> moveFrame(-1)), Ui.margin(Ui.lp(-2, -2), this, 0, 0, 6, 0));
        ctl.addView(Ui.btn(this, "Move >", false, () -> moveFrame(1)), Ui.margin(Ui.lp(-2, -2), this, 0, 0, 10, 0));
        playBtn = Ui.btn(this, "Play", true, this::togglePlay);
        ctl.addView(playBtn, Ui.margin(Ui.lp(-2, -2), this, 0, 0, 6, 0));
        ctl.addView(Ui.btn(this, "-", false, () -> { t.frametime = Math.max(1, t.frametime - 1); updateTicks(); }), Ui.margin(Ui.lp(-2, -2), this, 0, 0, 4, 0));
        ticksLbl = Ui.text(this, "", 12, Ui.TEXT);
        ctl.addView(ticksLbl);
        ctl.addView(Ui.btn(this, "+", false, () -> { t.frametime = Math.min(200, t.frametime + 1); updateTicks(); }), Ui.margin(Ui.lp(-2, -2), this, 4, 0, 6, 0));
        smoothBtn = Ui.btn(this, "Smooth", false, () -> { t.interpolate = !t.interpolate; Ui.setSelected(smoothBtn, t.interpolate); Model.save(this); });
        ctl.addView(smoothBtn);
        col.addView(hscroll(ctl), Ui.margin(Ui.lp(-1, -2), this, 8, 6, 8, 10));

        setScreen(col);
        pv.setTex(t, 0);
        selectTool(PixelView.PEN);
        Ui.setSelected(mirrorBtn, pv.mirror); Ui.setSelected(gridBtn, pv.grid); Ui.setSelected(onionBtn, pv.onion);
        Ui.setSelected(smoothBtn, t.interpolate);
        setColor(curColor, false);
        buildPalette();
        buildFrames();
        updateTicks();
    }

    private HorizontalScrollView hscroll(View v) {
        HorizontalScrollView h = new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(v);
        return h;
    }

    private int frameIndex() { return pv.frame; }

    private void selectTool(int k) {
        stopPlay();
        pv.tool = k;
        for (int i = 0; i < toolBtns.length; i++) Ui.setSelected(toolBtns[i], i == k);
    }

    private void setColor(int c, boolean remember) {
        curColor = c; pv.color = c;
        swatch.setBackground(Ui.round(c, 8, this));
        swatch.setText((c >>> 24) == 0 ? "clear" : "");
        if (remember) {
            recent.remove((Integer) c); recent.add(0, c);
            while (recent.size() > 12) recent.remove(recent.size() - 1);
            buildPalette();
        }
    }

    private void buildPalette() {
        palRow.removeAllViews();
        List<Integer> all = new ArrayList<>(recent);
        for (int c : PALETTE) all.add(c);
        for (int c : all) {
            View v = new View(this);
            v.setBackground(Ui.round((c >>> 24) == 0 ? 0xFF3A3A48 : c, 6, this));
            v.setOnClickListener(x -> setColor(c, false));
            palRow.addView(v, Ui.margin(Ui.lp(Ui.dp(this, 30), Ui.dp(this, 30)), this, 0, 0, 5, 0));
        }
    }

    private void buildFrames() {
        frameStrip.removeAllViews();
        for (int i = 0; i < tex.frames.size(); i++) {
            final int k = i;
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(thumb(tex.frames.get(i), tex.size, 48));
            boolean sel = i == pv.frame;
            iv.setBackground(Ui.round(sel ? Ui.ACCENT : 0xFF2C2C38, 6, this));
            iv.setPadding(Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3));
            iv.setOnClickListener(v -> { stopPlay(); pv.setFrame(k); buildFrames(); });
            frameStrip.addView(iv, Ui.margin(Ui.lp(Ui.dp(this, 54), Ui.dp(this, 54)), this, 0, 0, 6, 0));
        }
        title.setText(tex.name + "  " + tex.size + "x" + tex.size + "  f" + (pv.frame + 1) + "/" + tex.frames.size());
    }

    private void updateTicks() {
        ticksLbl.setText(tex.frametime + "t (" + (tex.frametime * 50) + "ms)");
        Model.save(this);
    }

    private void deleteFrame() {
        stopPlay();
        if (tex.frames.size() <= 1) { toast("A texture needs at least one frame"); return; }
        int i = pv.frame;
        tex.frames.remove(i);
        pv.clearHistory();
        pv.setFrame(Math.max(0, i - 1));
        buildFrames();
    }

    private void moveFrame(int dir) {
        stopPlay();
        int i = pv.frame, j = i + dir;
        if (j < 0 || j >= tex.frames.size()) return;
        int[] a = tex.frames.get(i);
        tex.frames.set(i, tex.frames.get(j)); tex.frames.set(j, a);
        pv.clearHistory();
        pv.setFrame(j);
        buildFrames();
    }

    private void togglePlay() {
        if (playing) { stopPlay(); return; }
        if (!tex.animated()) { toast("Add more frames to preview an animation"); return; }
        playing = true; tick = 0;
        playBtn.setText("Stop");
        handler.post(player);
    }

    private void stopPlay() {
        if (!playing) return;
        playing = false;
        handler.removeCallbacks(player);
        if (pv != null) { pv.setPlayback(false, 0, 0, 0); pv.refresh(); pv.invalidate(); }
        if (playBtn != null) playBtn.setText("Play");
    }

    private void colorDialog() {
        float[] hsv = new float[3];
        Color.colorToHSV(curColor, hsv);
        int[] alpha = {curColor >>> 24};
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        View prev = new View(this);
        EditText hex = edit(String.format("#%08X", curColor), "#AARRGGBB");
        SeekBar h = seek(360, (int) hsv[0]), s = seek(100, (int) (hsv[1] * 100)), v = seek(100, (int) (hsv[2] * 100)), a = seek(255, alpha[0]);
        boolean[] busy = {false};
        Runnable upd = () -> {
            if (busy[0]) return;
            busy[0] = true;
            int c = Color.HSVToColor(alpha[0], new float[]{h.getProgress(), s.getProgress() / 100f, v.getProgress() / 100f});
            prev.setBackground(Ui.round(c, 8, this));
            hex.setText(String.format("#%08X", c));
            busy[0] = false;
        };
        SeekBar.OnSeekBarChangeListener ol = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) { if (sb == a) alpha[0] = p; upd.run(); }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        };
        h.setOnSeekBarChangeListener(ol); s.setOnSeekBarChangeListener(ol); v.setOnSeekBarChangeListener(ol); a.setOnSeekBarChangeListener(ol);
        l.addView(prev, Ui.lp(-1, Ui.dp(this, 44)));
        l.addView(Ui.text(this, "Hue", 12, Ui.MUTED)); l.addView(h);
        l.addView(Ui.text(this, "Saturation", 12, Ui.MUTED)); l.addView(s);
        l.addView(Ui.text(this, "Brightness", 12, Ui.MUTED)); l.addView(v);
        l.addView(Ui.text(this, "Opacity", 12, Ui.MUTED)); l.addView(a);
        l.addView(hex);
        prev.setBackground(Ui.round(curColor, 8, this));
        new AlertDialog.Builder(this).setTitle("Color").setView(pad(l))
                .setPositiveButton("Use", (d, w) -> {
                    int c;
                    try { c = (int) Long.parseLong(hex.getText().toString().replace("#", "").trim(), 16); if (hex.getText().toString().replace("#", "").trim().length() <= 6) c |= 0xFF000000; }
                    catch (Exception e) { c = Color.HSVToColor(alpha[0], new float[]{h.getProgress(), s.getProgress() / 100f, v.getProgress() / 100f}); }
                    setColor(c, true);
                })
                .setNeutralButton("Transparent", (d, w) -> setColor(0x00000000, false))
                .setNegativeButton("Cancel", null).show();
    }

    private SeekBar seek(int max, int val) { SeekBar s = new SeekBar(this); s.setMax(max); s.setProgress(val); return s; }

    private void editorMenu() {
        stopPlay();
        String[] items = {"Export texture PNG (strip)", "Export texture ZIP (PNG + mcmeta)", "Import PNG into this texture", "Rename / folder", "Clear frame", "Flip horizontal", "Flip vertical", "Fit view"};
        new AlertDialog.Builder(this).setTitle(tex.name).setItems(items, (d, w) -> {
            switch (w) {
                case 0 -> createDoc(REQ_TEX_PNG, "image/png", tex.name + ".png");
                case 1 -> createDoc(REQ_TEX_ZIP, "application/zip", tex.name + ".zip");
                case 2 -> pickPng(REQ_REPLACE);
                case 3 -> renameDialog();
                case 4 -> pv.op(0);
                case 5 -> pv.op(1);
                case 6 -> pv.op(2);
                default -> { pv.fit(); pv.invalidate(); }
            }
        }).show();
    }

    private void renameDialog() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        EditText n = edit(tex.name, "Texture name"), c = edit(tex.category, "Folder");
        l.addView(n); l.addView(c);
        l.addView(Ui.text(this, "Exports to assets/minecraft/textures/<folder>/<name>.png", 11, Ui.MUTED));
        new AlertDialog.Builder(this).setTitle("Rename").setView(pad(l))
                .setPositiveButton("Save", (d, w) -> {
                    tex.name = Ui.clean(n.getText().toString().trim());
                    tex.category = Ui.clean(c.getText().toString().trim().isEmpty() ? "block" : c.getText().toString().trim());
                    Model.save(this); buildFrames();
                }).setNegativeButton("Cancel", null).show();
    }
}
