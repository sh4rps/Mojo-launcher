package com.robin.cord;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.Date;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int RC_PROJ = 1, RC_TREE = 2, RC_PERM = 3;
    static final int RED = 0xFFE53935, BG = 0xFF0D0D10, CARD = 0xFF17171C, DIM = 0xFF9A9AA3;

    private SharedPreferences sp;
    private TextView timer, status, sdState, listTitle;
    private Button rec, pause;
    private LinearLayout list;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver rcv = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); h.postDelayed(this, 500); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = Cfg.sp(this);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(28));
        sv.addView(root);

        TextView t = tv("Robincord", 28, Color.WHITE, true);
        root.addView(t);
        root.addView(tv("Screen recorder with game sound + mic", 13, DIM, false));

        // ---- record panel
        LinearLayout p = card();
        p.setGravity(Gravity.CENTER_HORIZONTAL);
        timer = tv("00:00", 44, Color.WHITE, true);
        timer.setGravity(Gravity.CENTER);
        p.addView(timer);
        status = tv("Ready", 13, DIM, false);
        status.setGravity(Gravity.CENTER);
        p.addView(status);
        rec = btn("●  START RECORDING", RED);
        rec.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { onRec(); } });
        p.addView(rec, lp(-1, 56, 0, 14, 0, 0));
        pause = btn("❚❚  PAUSE", 0xFF2A2A31);
        pause.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { onPauseBtn(); } });
        p.addView(pause, lp(-1, 48, 0, 8, 0, 0));
        root.addView(p, lp(-1, -2, 0, 14, 0, 0));

        // ---- video
        LinearLayout v = section("Video");
        spinner(v, "Resolution", new String[]{"1080p", "720p", "480p", "360p"}, "res", 0);
        spinner(v, "Frame rate", new String[]{"30 fps", "60 fps", "90 fps"}, "fps", 1);
        spinner(v, "Bitrate", new String[]{"48 Mbps", "36 Mbps", "24 Mbps", "16 Mbps", "12 Mbps", "8 Mbps", "4 Mbps", "2 Mbps"}, "br", 3);
        spinner(v, "Codec", Cfg.CODEC, "codec", 0);
        spinner(v, "Orientation", Cfg.ORIENT, "orient", 0);
        spinner(v, "Start delay", new String[]{"None", "3 seconds", "5 seconds", "10 seconds"}, "cd", 1);
        root.addView(v);

        // ---- audio
        LinearLayout a = section("Sound");
        spinner(a, "Internal sound", Cfg.INTERNAL, "internal", 1);
        sw(a, "Microphone", "mic", true);
        spinner(a, "Mic source", Cfg.MICSRC, "micsrc", 0);
        seek(a, "Mic volume", "gmic");
        seek(a, "Internal volume", "gint");
        sw(a, "Also try voice-call audio (experimental, usually silent)", "exp", false);
        TextView note = tv("Friends' voices in Discord / WhatsApp / Meet calls are VoIP audio. Android blocks apps from "
                + "capturing VoIP audio internally, so no screen recorder can grab it directly.\n\n"
                + "To get your friends in the recording: play the call on the phone LOUDSPEAKER (not earphones), keep "
                + "Microphone ON and set Mic source to Unprocessed. The mic then records you and the speaker. "
                + "Game sound / media is captured internally, clean.", 12, DIM, false);
        note.setPadding(0, dp(8), 0, 0);
        a.addView(note);
        root.addView(a);

        // ---- storage
        LinearLayout s = section("Save to");
        spinner(s, "Location", Cfg.STORE, "store", 0);
        sdState = tv("", 12, DIM, false);
        s.addView(sdState);
        Button pick = btn("Choose SD card folder", 0xFF2A2A31);
        pick.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickTree(); } });
        s.addView(pick, lp(-1, 44, 0, 8, 0, 0));
        root.addView(s);

        // ---- recordings
        listTitle = tv("Recent recordings", 16, Color.WHITE, true);
        listTitle.setPadding(dp(4), dp(18), 0, dp(6));
        root.addView(listTitle);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        setContentView(sv);
    }

    @Override protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(RecService.A_STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(rcv, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(rcv, f);
        h.post(tick);
        buildList();
        updateSd();
    }

    @Override protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
        try { unregisterReceiver(rcv); } catch (Throwable ignore) { }
    }

    // ------------------------------------------------------------ record flow
    private void onRec() {
        if (RecService.state != RecService.IDLE) { svc(RecService.A_STOP); return; }
        Cfg c = Cfg.load(this);
        if (c.store == 1 && c.sdTree == null) {
            Toast.makeText(this, "Choose the SD card folder first", Toast.LENGTH_LONG).show();
            pickTree();
            return;
        }
        java.util.ArrayList<String> need = new java.util.ArrayList<>();
        if (c.audio() && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), RC_PERM);
        else askProjection();
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        if (rc == RC_PERM) askProjection();
    }

    private void askProjection() {
        MediaProjectionManager m = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(), RC_PROJ);
    }

    private void onPauseBtn() {
        if (RecService.state == RecService.REC) svc(RecService.A_PAUSE);
        else if (RecService.state == RecService.PAUSED) svc(RecService.A_RESUME);
    }

    private void svc(String action) {
        startForegroundService(new Intent(this, RecService.class).setAction(action));
    }

    @Override protected void onActivityResult(int rc, int res, Intent d) {
        if (rc == RC_PROJ) {
            if (res == RESULT_OK && d != null) {
                Intent i = new Intent(this, RecService.class).setAction(RecService.A_START)
                        .putExtra("code", res).putExtra("data", d);
                startForegroundService(i);
                moveTaskToBack(true);
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show();
            }
        } else if (rc == RC_TREE && res == RESULT_OK && d != null && d.getData() != null) {
            Uri u = d.getData();
            try {
                getContentResolver().takePersistableUriPermission(u,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Throwable t) {
                Toast.makeText(this, "Could not keep access to that folder", Toast.LENGTH_LONG).show();
            }
            sp.edit().putString("sdtree", u.toString()).putInt("store", 1).apply();
            updateSd();
            Toast.makeText(this, "Saving to the chosen folder", Toast.LENGTH_SHORT).show();
            recreateSpinnerState();
        }
    }

    private Spinner storeSpinner;
    private void recreateSpinnerState() { if (storeSpinner != null) storeSpinner.setSelection(1); }

    private void pickTree() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try { startActivityForResult(i, RC_TREE); } catch (Throwable t) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_LONG).show();
        }
    }

    private void updateSd() {
        boolean has = false;
        try {
            StorageManager sm = (StorageManager) getSystemService(STORAGE_SERVICE);
            for (StorageVolume v : sm.getStorageVolumes())
                if (v.isRemovable() && Environment_MOUNTED.equals(v.getState())) has = true;
        } catch (Throwable ignore) { }
        String tree = sp.getString("sdtree", null);
        String folder = "none chosen";
        if (tree != null) {
            try { folder = DocumentsContract.getTreeDocumentId(Uri.parse(tree)); } catch (Throwable ignore) { folder = tree; }
        }
        sdState.setText((has ? "SD card detected" : "No SD card detected") + "  -  folder: " + folder
                + "\nIn the picker open your SD card, pick (or create) a folder, then tap 'Use this folder'.");
    }
    private static final String Environment_MOUNTED = android.os.Environment.MEDIA_MOUNTED;

    // ------------------------------------------------------------ live state
    private void refresh() {
        int st = RecService.state;
        long ms = RecService.elapsedMs();
        long s = ms / 1000;
        timer.setText(String.format("%02d:%02d", s / 60, s % 60) + (s >= 3600 ? "" : ""));
        if (s >= 3600) timer.setText(String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
        rec.setText(st == RecService.IDLE ? "●  START RECORDING" : "■  STOP");
        setBg(rec, st == RecService.IDLE ? RED : 0xFF8E1C1A);
        pause.setVisibility(st == RecService.REC || st == RecService.PAUSED ? View.VISIBLE : View.GONE);
        pause.setText(st == RecService.PAUSED ? "▶  RESUME" : "❚❚  PAUSE");
        status.setText(st == RecService.IDLE ? "Ready" : st == RecService.COUNTDOWN ? "Starting..."
                : st == RecService.REC ? "Recording" : "Paused");
        if (st == RecService.IDLE && list != null && lastState != RecService.IDLE) buildList();
        lastState = st;
    }
    private int lastState = RecService.IDLE;

    // ------------------------------------------------------------ recordings list
    private void buildList() {
        list.removeAllViews();
        JSONArray a = Cfg.recent(this);
        if (a.length() == 0) {
            list.addView(tv("Nothing recorded yet.", 13, DIM, false));
            return;
        }
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        for (int i = 0; i < a.length(); i++) {
            try {
                final JSONObject o = a.getJSONObject(i);
                LinearLayout row = card();
                row.addView(tv(o.getString("name"), 14, Color.WHITE, true));
                row.addView(tv(df.format(new Date(o.getLong("ts"))) + "   " + (o.getLong("size") / 1048576) + " MB", 12, DIM, false));
                row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { play(o); } });
                row.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) { menu(o); return true; }
                });
                list.addView(row, lp(-1, -2, 0, 0, 0, 8));
            } catch (Throwable ignore) { }
        }
        list.addView(tv("Tap = play   Long-press = share / delete", 11, DIM, false));
    }

    private void play(JSONObject o) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(o.getString("uri")), "video/mp4")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Cannot open (file moved or deleted?)", Toast.LENGTH_SHORT).show();
        }
    }

    private void menu(final JSONObject o) {
        new AlertDialog.Builder(this).setItems(new String[]{"Share", "Delete file", "Remove from list"},
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            Uri u = Uri.parse(o.getString("uri"));
                            if (w == 0) {
                                startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("video/mp4")
                                        .putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share"));
                            } else {
                                if (w == 1) {
                                    ContentResolver r = getContentResolver();
                                    try {
                                        if ("media".equals(u.getAuthority())) r.delete(u, null, null);
                                        else DocumentsContract.deleteDocument(r, u);
                                    } catch (Throwable t) {
                                        Toast.makeText(MainActivity.this, "Could not delete the file", Toast.LENGTH_SHORT).show();
                                    }
                                }
                                Cfg.removeRecent(MainActivity.this, u.toString());
                                buildList();
                            }
                        } catch (Throwable ignore) { }
                    }
                }).show();
    }

    // ------------------------------------------------------------ ui helpers
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private LinearLayout.LayoutParams lp(int w, int hh, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w < 0 ? w : dp(w), hh < 0 ? hh : dp(hh));
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD); g.setCornerRadius(dp(18));
        l.setBackground(g);
        return l;
    }

    private LinearLayout section(String title) {
        LinearLayout l = card();
        TextView t = tv(title.toUpperCase(), 12, RED, true);
        t.setPadding(0, 0, 0, dp(6));
        l.addView(t);
        LinearLayout.LayoutParams p = lp(-1, -2, 0, 14, 0, 0);
        l.setLayoutParams(p);
        return l;
    }

    private void setBg(Button b, int c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c); g.setCornerRadius(dp(16));
        b.setBackground(g);
    }

    private Button btn(String s, int c) {
        Button b = new Button(this);
        b.setText(s); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextSize(15);
        setBg(b, c);
        return b;
    }

    private void spinner(LinearLayout parent, String label, String[] items, final String key, int def) {
        parent.addView(tv(label, 12, DIM, false), lp(-1, -2, 0, 8, 0, 0));
        final Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items));
        int saved = sp.getInt(key.equals("cd") ? "cdidx" : key, def);
        s.setSelection(Math.min(saved, items.length - 1));
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                if (key.equals("cd")) sp.edit().putInt("cdidx", pos).putInt("countdown", new int[]{0, 3, 5, 10}[pos]).apply();
                else sp.edit().putInt(key, pos).apply();
            }
            public void onNothingSelected(AdapterView<?> a) { }
        });
        if (key.equals("store")) storeSpinner = s;
        parent.addView(s);
    }

    private void sw(LinearLayout parent, String label, final String key, boolean def) {
        Switch w = new Switch(this);
        w.setText(label); w.setTextColor(Color.WHITE); w.setTextSize(14);
        w.setChecked(sp.getBoolean(key, def));
        w.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { sp.edit().putBoolean(key, on).apply(); }
        });
        parent.addView(w, lp(-1, -2, 0, 10, 0, 0));
    }

    private void seek(LinearLayout parent, String label, final String key) {
        final TextView t = tv("", 12, DIM, false);
        int v = sp.getInt(key, 100);
        t.setText(label + ": " + v + "%");
        parent.addView(t, lp(-1, -2, 0, 10, 0, 0));
        SeekBar s = new SeekBar(this);
        s.setMax(200);
        s.setProgress(v);
        final String lab = label;
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean u) {
                t.setText(lab + ": " + p + "%");
                sp.edit().putInt(key, p).apply();
            }
            public void onStartTrackingTouch(SeekBar b) { }
            public void onStopTrackingTouch(SeekBar b) { }
        });
        parent.addView(s);
    }
}
