package com.robin.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_ADD = 1, REQ_SAVE = 2;
    private static final int BG = 0xFF14141C, CARD = 0xFF22222E, ACC = 0xFF8B5CF6, TXT = 0xFFF2F2F7, MUT = 0xFF9A9AAE;

    private final List<Clip> clips = new ArrayList<>();
    private int sel = -1;
    private final Handler h = new Handler(Looper.getMainLooper());

    private VideoView vv;
    private LinearLayout strip;
    private SeekBar startBar, endBar;
    private TextView startLbl, endLbl, info, playBtn;
    private CheckBox muteBox;
    private Uri loaded;
    private boolean playing, playAll;
    private int playIdx;
    private long pendingSeek = -1;
    private boolean pendingPlay;
    private boolean updating;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (playing && loaded != null && playIdx >= 0 && playIdx < clips.size()) {
                Clip c = clips.get(playIdx);
                int pos = vv.getCurrentPosition();
                if (pos >= c.end - 30) {
                    if (playAll && playIdx + 1 < clips.size()) { playIdx++; selectClip(playIdx, true); }
                    else stopPlay();
                }
            }
            h.postDelayed(this, 60);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        h.post(tick);
        refresh();
    }

    @Override protected void onPause() { super.onPause(); stopPlay(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        return t;
    }

    private TextView btn(String s, boolean primary, Runnable r) {
        TextView t = text(s, 13, TXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary ? ACC : CARD); g.setCornerRadius(dp(10));
        t.setBackground(g);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int hh, int l, int t, int r, int bt) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, hh);
        p.setMargins(dp(l), dp(t), dp(r), dp(bt));
        return p;
    }

    private HorizontalScrollView hs(View v) {
        HorizontalScrollView s = new HorizontalScrollView(this);
        s.setHorizontalScrollBarEnabled(false);
        s.addView(v);
        return s;
    }

    private void buildUi() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(BG);
        col.setPadding(dp(10), dp(22), dp(10), dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Robin Pro", 20, TXT);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(btn("Export MP4", true, this::export));
        col.addView(top);

        vv = new VideoView(this);
        FrameLayout vf = new FrameLayout(this);
        vf.setBackgroundColor(Color.BLACK);
        vf.addView(vv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        col.addView(vf, lp(-1, 0, 0, 8, 0, 6));
        ((LinearLayout.LayoutParams) vf.getLayoutParams()).weight = 1f;
        vv.setOnPreparedListener((MediaPlayer mp) -> {
            if (pendingSeek >= 0) { vv.seekTo((int) pendingSeek); pendingSeek = -1; }
            if (pendingPlay) { vv.start(); pendingPlay = false; }
        });
        vv.setOnCompletionListener(mp -> stopPlay());
        vv.setOnErrorListener((mp, w, e) -> { toast("Cannot play this video"); stopPlay(); return true; });

        info = text("", 12, MUT);
        col.addView(info);

        LinearLayout row = new LinearLayout(this);
        row.addView(btn("+ Add clips", true, this::addClips), lp(-2, -2, 0, 0, 6, 0));
        playBtn = btn("Play clip", false, () -> togglePlay(false));
        row.addView(playBtn, lp(-2, -2, 0, 0, 6, 0));
        row.addView(btn("Play all", false, () -> togglePlay(true)), lp(-2, -2, 0, 0, 6, 0));
        muteBox = new CheckBox(this);
        muteBox.setText("Mute"); muteBox.setTextColor(TXT);
        row.addView(muteBox);
        col.addView(hs(row), lp(-1, -2, 0, 4, 0, 0));

        strip = new LinearLayout(this);
        col.addView(hs(strip), lp(-1, -2, 0, 8, 0, 0));

        startLbl = text("", 12, TXT); endLbl = text("", 12, TXT);
        startBar = new SeekBar(this); endBar = new SeekBar(this);
        startBar.setOnSeekBarChangeListener(new Seek(true));
        endBar.setOnSeekBarChangeListener(new Seek(false));
        col.addView(startLbl); col.addView(startBar);
        col.addView(endLbl); col.addView(endBar);

        LinearLayout ed = new LinearLayout(this);
        ed.addView(btn("Split here", false, this::split), lp(-2, -2, 0, 0, 6, 0));
        ed.addView(btn("Duplicate", false, this::duplicate), lp(-2, -2, 0, 0, 6, 0));
        ed.addView(btn("< Move", false, () -> move(-1)), lp(-2, -2, 0, 0, 6, 0));
        ed.addView(btn("Move >", false, () -> move(1)), lp(-2, -2, 0, 0, 6, 0));
        ed.addView(btn("Delete", false, this::delete), lp(-2, -2, 0, 0, 6, 0));
        col.addView(hs(ed), lp(-1, -2, 0, 8, 0, 0));
        setContentView(col);
    }

    private class Seek implements SeekBar.OnSeekBarChangeListener {
        final boolean isStart;
        Seek(boolean s) { isStart = s; }
        @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
            if (!user || updating || sel < 0) return;
            Clip c = clips.get(sel);
            if (isStart) c.start = Math.min(p, c.end - 300); else c.end = Math.max(p, c.start + 300);
            if (c.start < 0) c.start = 0;
            if (c.end > c.dur) c.end = c.dur;
            stopPlay();
            labels();
            seekPreview(isStart ? c.start : c.end - 100);
        }
        @Override public void onStartTrackingTouch(SeekBar sb) { }
        @Override public void onStopTrackingTouch(SeekBar sb) { refresh(); }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private String fmt(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d.%d", s / 60, s % 60, (ms % 1000) / 100);
    }

    // ---------------------------------------------------------- clips
    private void addClips() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, REQ_ADD);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_ADD) {
            List<Uri> uris = new ArrayList<>();
            ClipData cd = data.getClipData();
            if (cd != null) for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            loadClips(uris);
        } else if (req == REQ_SAVE && data.getData() != null) {
            doExport(data.getData());
        }
    }

    private String nameOf(Uri u) {
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) { }
        return "clip";
    }

    private void loadClips(List<Uri> uris) {
        ProgressDialog pd = ProgressDialog.show(this, null, "Loading clips...", true);
        new Thread(() -> {
            List<Clip> added = new ArrayList<>();
            int bad = 0;
            for (Uri u : uris) {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    r.setDataSource(this, u);
                    String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    Clip c = new Clip();
                    c.uri = u; c.name = nameOf(u);
                    c.dur = d == null ? 0 : Long.parseLong(d);
                    if (c.dur <= 0) throw new Exception("no duration");
                    c.start = 0; c.end = c.dur;
                    Bitmap f = r.getFrameAtTime(0);
                    if (f != null) {
                        int w = dp(80), hh = Math.max(1, f.getHeight() * w / Math.max(1, f.getWidth()));
                        c.thumb = Bitmap.createScaledBitmap(f, w, hh, true);
                    }
                    added.add(c);
                } catch (Exception e) { bad++; }
                finally { try { r.release(); } catch (Exception ignored) { } }
            }
            final int badF = bad;
            runOnUiThread(() -> {
                pd.dismiss();
                clips.addAll(added);
                if (!added.isEmpty()) { sel = clips.size() - added.size(); selectClip(sel, false); }
                refresh();
                if (badF > 0) toast(badF + " file(s) could not be read");
            });
        }).start();
    }

    private void selectClip(int i, boolean play) {
        sel = i;
        Clip c = clips.get(i);
        playIdx = i;
        if (c.uri.equals(loaded)) {
            vv.seekTo((int) c.start);
            if (play) vv.start();
        } else {
            loaded = c.uri;
            pendingSeek = c.start; pendingPlay = play;
            vv.setVideoURI(c.uri);
        }
        refresh();
    }

    private void seekPreview(long ms) {
        if (sel < 0) return;
        Clip c = clips.get(sel);
        if (c.uri.equals(loaded)) vv.seekTo((int) ms);
        else { loaded = c.uri; pendingSeek = ms; pendingPlay = false; vv.setVideoURI(c.uri); }
    }

    private void togglePlay(boolean all) {
        if (clips.isEmpty()) { toast("Add a clip first"); return; }
        if (playing) { stopPlay(); return; }
        if (all) sel = 0;
        if (sel < 0) sel = 0;
        playAll = all; playing = true;
        playBtn.setText("Stop");
        selectClip(sel, true);
    }

    private void stopPlay() {
        playing = false; playAll = false; pendingPlay = false;
        try { if (vv != null && vv.isPlaying()) vv.pause(); } catch (Exception ignored) { }
        if (playBtn != null) playBtn.setText("Play clip");
    }

    private void split() {
        if (sel < 0) return;
        Clip c = clips.get(sel);
        int pos = c.uri.equals(loaded) ? vv.getCurrentPosition() : -1;
        if (pos <= c.start + 300 || pos >= c.end - 300) { toast("Move the preview inside the clip first (use the sliders), then Split"); return; }
        stopPlay();
        Clip b = c.copy();
        b.start = pos; c.end = pos;
        clips.add(sel + 1, b);
        refresh();
    }

    private void duplicate() { if (sel < 0) return; clips.add(sel + 1, clips.get(sel).copy()); sel++; refresh(); }

    private void move(int d) {
        if (sel < 0) return;
        int j = sel + d;
        if (j < 0 || j >= clips.size()) return;
        Clip c = clips.remove(sel);
        clips.add(j, c); sel = j; refresh();
    }

    private void delete() {
        if (sel < 0) return;
        stopPlay();
        clips.remove(sel);
        sel = clips.isEmpty() ? -1 : Math.min(sel, clips.size() - 1);
        if (sel >= 0) selectClip(sel, false); else { loaded = null; vv.stopPlayback(); }
        refresh();
    }

    private void labels() {
        if (sel < 0) { startLbl.setText("Trim start"); endLbl.setText("Trim end"); return; }
        Clip c = clips.get(sel);
        startLbl.setText("Start  " + fmt(c.start)); endLbl.setText("End  " + fmt(c.end) + "   (length " + fmt(c.end - c.start) + ")");
    }

    private void refresh() {
        strip.removeAllViews();
        long total = 0;
        for (int i = 0; i < clips.size(); i++) {
            Clip c = clips.get(i);
            total += c.end - c.start;
            final int k = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(4), dp(4), dp(4), dp(4));
            GradientDrawable g = new GradientDrawable();
            g.setColor(i == sel ? ACC : CARD); g.setCornerRadius(dp(8));
            card.setBackground(g);
            ImageView iv = new ImageView(this);
            if (c.thumb != null) iv.setImageBitmap(c.thumb);
            card.addView(iv, new LinearLayout.LayoutParams(dp(80), dp(50)));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            TextView t = text((i + 1) + ". " + fmt(c.end - c.start), 11, TXT);
            card.addView(t);
            card.setOnClickListener(v -> { stopPlay(); selectClip(k, false); });
            strip.addView(card, lp(-2, -2, 0, 0, 6, 0));
        }
        info.setText(clips.isEmpty() ? "No clips. Tap + Add clips." : clips.size() + " clips  -  total " + fmt(total));
        updating = true;
        if (sel >= 0) {
            Clip c = clips.get(sel);
            startBar.setMax((int) c.dur); endBar.setMax((int) c.dur);
            startBar.setProgress((int) c.start); endBar.setProgress((int) c.end);
        } else { startBar.setProgress(0); endBar.setProgress(0); }
        updating = false;
        labels();
    }

    // --------------------------------------------------------- export
    private void export() {
        if (clips.isEmpty()) { toast("Add a clip first"); return; }
        stopPlay();
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/mp4");
        i.putExtra(Intent.EXTRA_TITLE, "RobinPro_" + System.currentTimeMillis() / 1000 + ".mp4");
        startActivityForResult(i, REQ_SAVE);
    }

    private void doExport(Uri out) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Exporting...");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100); pd.setCancelable(false); pd.show();
        final boolean mute = muteBox.isChecked();
        final List<Clip> list = new ArrayList<>(clips);
        new Thread(() -> {
            File tmp = new File(getCacheDir(), "export.mp4");
            String msg;
            try {
                int skipped = Remux.run(this, list, tmp.getAbsolutePath(), mute, p -> runOnUiThread(() -> pd.setProgress(p)));
                try (InputStream in = new FileInputStream(tmp); OutputStream os = getContentResolver().openOutputStream(out)) {
                    byte[] b = new byte[1 << 16]; int n;
                    while ((n = in.read(b)) > 0) os.write(b, 0, n);
                }
                msg = "Saved." + (skipped > 0 ? " " + skipped + " clip(s) skipped: different resolution/codec from the first clip." : "");
            } catch (Exception e) {
                msg = "Export failed: " + e.getMessage();
            } finally { tmp.delete(); }
            final String m = msg;
            runOnUiThread(() -> { pd.dismiss(); new AlertDialog.Builder(this).setMessage(m).setPositiveButton("OK", null).show(); });
        }).start();
    }
}
