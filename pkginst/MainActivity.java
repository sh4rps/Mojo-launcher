package com.pkginst.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    static MainActivity cur;
    static final Handler UI = new Handler(Looper.getMainLooper());
    static final int PICK = 11;

    static class Item {
        String name, label, pkg, ver, installedVer, status = "Ready", extra;
        long size; int code; Drawable icon;
        List<File> files = new ArrayList<>();
        int state; // 0 ready 1 installing 2 ok 3 failed 4 loading
        int session = -1;
    }

    final List<Item> items = new ArrayList<>();
    final Map<Integer, Item> bySession = new HashMap<>();
    PackageManager pm;
    ListView list;
    BaseAdapter adapter;
    TextView info;
    Button installAll;
    boolean queueRunning;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cur = this;
        pm = getPackageManager();
        getWindow().setStatusBarColor(0xFF070C1F);
        getWindow().setNavigationBarColor(0xFF070C1F);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF070C1F);
        root.setPadding(dp(14), dp(40), dp(14), dp(10));

        TextView t = new TextView(this);
        t.setText("Package Installer");
        t.setTextColor(Color.WHITE); t.setTextSize(22); t.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(t);
        info = new TextView(this);
        info.setTextColor(0xFF8F9BB3); info.setTextSize(12);
        info.setPadding(0, dp(2), 0, dp(10));
        root.addView(info);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button pick = btn("Pick files", 0xFF1E6BFF);
        pick.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickFiles(); } });
        Button scan = btn("Scan phone", 0xFF18265A);
        scan.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { scan(); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(0, 0, dp(8), 0);
        row.addView(pick, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        row.addView(scan, lp2);
        root.addView(row);

        list = new ListView(this);
        list.setDivider(null);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) { itemMenu(items.get(pos)); }
        });
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, 0, 1f);
        llp.setMargins(0, dp(10), 0, dp(8));
        root.addView(list, llp);

        installAll = btn("Install all", 0xFF00B8D9);
        installAll.setTextColor(0xFF06122B);
        installAll.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startQueue(); } });
        root.addView(installAll, new LinearLayout.LayoutParams(-1, dp(50)));
        setContentView(root);
        refreshInfo();
        handle(getIntent());
    }

    Button btn(String s, int col) {
        Button x = new Button(this);
        x.setText(s); x.setAllCaps(false); x.setTextColor(Color.WHITE); x.setTextSize(15);
        x.setBackgroundColor(col);
        return x;
    }

    @Override
    protected void onNewIntent(Intent i) { super.onNewIntent(i); handle(i); }

    @Override
    protected void onDestroy() { if (cur == this) cur = null; super.onDestroy(); }

    void handle(Intent i) {
        if (i == null) return;
        Uri u = i.getData();
        if (u == null && i.hasExtra(Intent.EXTRA_STREAM)) u = (Uri) i.getParcelableExtra(Intent.EXTRA_STREAM);
        if (u != null && !Intent.ACTION_MAIN.equals(i.getAction())) { addUri(u); i.setData(null); }
    }

    void refreshInfo() {
        boolean can = Build.VERSION.SDK_INT < 26 || pm.canRequestPackageInstalls();
        info.setText(items.size() + " file(s) in queue  •  " + (can ? "install permission OK" : "needs 'Install unknown apps' permission"));
        info.setTextColor(can ? 0xFF8F9BB3 : 0xFFFFB020);
        installAll.setEnabled(!items.isEmpty());
        adapter.notifyDataSetChanged();
    }

    void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, PICK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (req == PICK && res == RESULT_OK && d != null) {
            if (d.getClipData() != null) {
                for (int k = 0; k < d.getClipData().getItemCount(); k++) addUri(d.getClipData().getItemAt(k).getUri());
            } else if (d.getData() != null) addUri(d.getData());
        }
    }

    @Override
    protected void onResume() { super.onResume(); refreshInfo(); }

    // ---------- scan ----------
    void scan() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            Toast.makeText(this, "Allow 'All files access' to scan, then come back", Toast.LENGTH_LONG).show();
            try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); }
            catch (Throwable t) { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
            return;
        }
        Toast.makeText(this, "Scanning…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                File root = Environment.getExternalStorageDirectory();
                final List<File> found = new ArrayList<>();
                String[] dirs = {"Download", "Documents", "WhatsApp/Media/WhatsApp Documents", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents",
                        "Telegram", "Android/media/org.telegram.messenger", "Bluetooth", "Movies", "DCIM"};
                for (String d : dirs) walk(new File(root, d), 0, found);
                Collections.sort(found, new Comparator<File>() { public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); } });
                UI.post(new Runnable() { public void run() { chooseScanned(found); } });
            }
        }).start();
    }

    static void walk(File d, int depth, List<File> out) {
        File[] fs = d.listFiles();
        if (fs == null || depth > 3) return;
        for (File f : fs) {
            if (f.isDirectory()) walk(f, depth + 1, out);
            else {
                String n = f.getName().toLowerCase();
                if (n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".xapk") || n.endsWith(".apkm")) out.add(f);
            }
        }
    }

    void chooseScanned(final List<File> found) {
        if (found.isEmpty()) { Toast.makeText(this, "No APK files found", Toast.LENGTH_LONG).show(); return; }
        final String[] names = new String[found.size()];
        final boolean[] sel = new boolean[found.size()];
        for (int i = 0; i < names.length; i++) names[i] = found.get(i).getName() + "  (" + fmt(found.get(i).length()) + ")";
        new AlertDialog.Builder(this).setTitle(found.size() + " files found")
            .setMultiChoiceItems(names, sel, new DialogInterface.OnMultiChoiceClickListener() {
                public void onClick(DialogInterface d, int w, boolean c) { sel[w] = c; }
            })
            .setPositiveButton("Add selected", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    for (int i = 0; i < sel.length; i++) if (sel[i]) addUri(Uri.fromFile(found.get(i)));
                }
            }).setNegativeButton("Cancel", null).show();
    }

    static String fmt(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return (b / 1024) + " KB";
        return String.format(Locale.US, "%.1f MB", b / 1048576.0);
    }

    // ---------- load files ----------
    String nameOf(Uri u) {
        if ("file".equals(u.getScheme())) return new File(u.getPath()).getName();
        Cursor c = null;
        try {
            c = getContentResolver().query(u, null, null, null, null);
            if (c != null && c.moveToFirst()) { int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) return c.getString(i); }
        } catch (Throwable t) { } finally { if (c != null) c.close(); }
        String p = u.getLastPathSegment();
        return p == null ? "file.apk" : p;
    }

    void addUri(final Uri u) {
        final Item it = new Item();
        it.name = nameOf(u); it.label = it.name; it.state = 4; it.status = "Reading…";
        items.add(it);
        refreshInfo();
        new Thread(new Runnable() {
            public void run() {
                try { load(it, u); }
                catch (final Throwable t) {
                    UI.post(new Runnable() { public void run() { it.state = 3; it.status = "Can't read: " + t.getMessage(); refreshInfo(); } });
                }
            }
        }).start();
    }

    void load(final Item it, Uri u) throws Exception {
        File dir = new File(getCacheDir(), "q" + System.nanoTime());
        dir.mkdirs();
        String low = it.name.toLowerCase();
        boolean bundle = low.endsWith(".apks") || low.endsWith(".xapk") || low.endsWith(".apkm") || low.endsWith(".zip");
        InputStream in = "file".equals(u.getScheme()) ? new FileInputStream(u.getPath()) : getContentResolver().openInputStream(u);
        if (in == null) throw new IOException("cannot open");
        if (!bundle) {
            File f = new File(dir, "base.apk");
            copy(in, new FileOutputStream(f));
            if (!isZip(f)) throw new IOException("not an APK");
            it.files.add(f);
        } else {
            ZipInputStream z = new ZipInputStream(new BufferedInputStream(in));
            ZipEntry e; int n = 0;
            while ((e = z.getNextEntry()) != null) {
                String en = e.getName();
                if (e.isDirectory() || !en.toLowerCase().endsWith(".apk")) continue;
                File f = new File(dir, (n++) + "_" + new File(en).getName());
                OutputStream o = new FileOutputStream(f);
                byte[] buf = new byte[65536]; int r;
                while ((r = z.read(buf)) > 0) o.write(buf, 0, r);
                o.close();
                it.files.add(f);
            }
            z.close();
            if (it.files.isEmpty()) throw new IOException("no APK inside");
        }
        // choose the base apk for info
        File base = it.files.get(0);
        for (File f : it.files) { if (f.getName().endsWith("base.apk")) { base = f; break; } if (f.length() > base.length()) base = f; }
        long total = 0; for (File f : it.files) total += f.length();
        it.size = total;
        PackageInfo pi = pm.getPackageArchiveInfo(base.getAbsolutePath(), 0);
        if (pi == null) {
            for (File f : it.files) { pi = pm.getPackageArchiveInfo(f.getAbsolutePath(), 0); if (pi != null) { base = f; break; } }
        }
        if (pi == null) throw new IOException("not a valid APK");
        pi.applicationInfo.sourceDir = base.getAbsolutePath();
        pi.applicationInfo.publicSourceDir = base.getAbsolutePath();
        it.label = String.valueOf(pi.applicationInfo.loadLabel(pm));
        it.icon = pi.applicationInfo.loadIcon(pm);
        it.pkg = pi.packageName; it.ver = pi.versionName == null ? "?" : pi.versionName;
        it.code = pi.versionCode;
        try { PackageInfo old = pm.getPackageInfo(pi.packageName, 0); it.installedVer = old.versionName + " (" + old.versionCode + ")"; }
        catch (Throwable t) { it.installedVer = null; }
        UI.post(new Runnable() {
            public void run() {
                it.state = 0;
                if (it.installedVer == null) it.status = "New install";
                else it.status = "Update from " + it.installedVer;
                refreshInfo();
            }
        });
    }

    static boolean isZip(File f) {
        try { InputStream i = new FileInputStream(f); int a = i.read(), b = i.read(); i.close(); return a == 'P' && b == 'K'; } catch (Throwable t) { return false; }
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536]; int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        in.close(); out.close();
    }

    // ---------- install ----------
    boolean canInstall() {
        if (Build.VERSION.SDK_INT >= 26 && !pm.canRequestPackageInstalls()) {
            Toast.makeText(this, "Allow 'Install unknown apps' for Package Installer, then try again", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return false;
        }
        return true;
    }

    void startQueue() {
        if (!canInstall()) return;
        queueRunning = true;
        next();
    }

    void next() {
        for (Item it : items) {
            if (it.state == 0) { install(it); return; }
            if (it.state == 1) return; // wait for the running one
        }
        queueRunning = false;
        refreshInfo();
    }

    void install(final Item it) {
        if (!canInstall()) { queueRunning = false; return; }
        it.state = 1; it.status = "Installing…";
        refreshInfo();
        new Thread(new Runnable() {
            public void run() {
                try {
                    PackageInstaller pi = pm.getPackageInstaller();
                    PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    if (Build.VERSION.SDK_INT >= 31) sp.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                    final int id = pi.createSession(sp);
                    it.session = id;
                    bySession.put(id, it);
                    PackageInstaller.Session s = pi.openSession(id);
                    int k = 0;
                    for (File f : it.files) {
                        InputStream in = new FileInputStream(f);
                        OutputStream out = s.openWrite((k++) + ".apk", 0, f.length());
                        byte[] buf = new byte[65536]; int r;
                        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                        s.fsync(out); out.close(); in.close();
                    }
                    Intent i = new Intent(MainActivity.this, InstallReceiver.class);
                    int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    PendingIntent pend = PendingIntent.getBroadcast(MainActivity.this, id, i, fl);
                    s.commit(pend.getIntentSender());
                    s.close();
                } catch (final Throwable t) {
                    UI.post(new Runnable() { public void run() { done(it, false, "Failed: " + t.getMessage()); } });
                }
            }
        }).start();
    }

    static void result(final int sid, final boolean ok, final String msg) {
        UI.post(new Runnable() {
            public void run() {
                MainActivity a = cur;
                if (a == null) return;
                Item it = a.bySession.get(sid);
                if (it != null) a.done(it, ok, msg);
            }
        });
    }

    void done(Item it, boolean ok, String msg) {
        it.state = ok ? 2 : 3;
        it.status = msg;
        if (ok) for (File f : it.files) f.delete();
        refreshInfo();
        if (queueRunning) next();
    }

    void itemMenu(final Item it) {
        if (it.state == 4) return;
        String[] opts = it.state == 2 ? new String[]{"Open app", "Remove from list"} : new String[]{"Install", "Remove from list"};
        new AlertDialog.Builder(this).setTitle(it.label).setMessage(it.pkg == null ? it.name : it.pkg + "\nversion " + it.ver + " (" + it.code + ")\n" + fmt(it.size))
            .setItems(opts, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (w == 1) { for (File f : it.files) f.delete(); items.remove(it); refreshInfo(); return; }
                    if (it.state == 2) {
                        Intent l = pm.getLaunchIntentForPackage(it.pkg);
                        if (l != null) startActivity(l);
                    } else if (it.state != 1) { it.state = 0; queueRunning = false; install(it); }
                }
            }).setNegativeButton("Close", null).show();
    }

    class Adapter extends BaseAdapter {
        public int getCount() { return items.size(); }
        public Object getItem(int i) { return items.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int pos, View cv, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(0xFF101A3A);
            row.setPadding(dp(10), dp(10), dp(10), dp(10));
            AbsListView.LayoutParams alp = new AbsListView.LayoutParams(-1, -2);
            row.setLayoutParams(alp);
            Item it = items.get(pos);
            ImageView ic = new ImageView(MainActivity.this);
            if (it.icon != null) ic.setImageDrawable(it.icon);
            row.addView(ic, new LinearLayout.LayoutParams(dp(44), dp(44)));
            LinearLayout col = new LinearLayout(MainActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(12), 0, dp(6), 0);
            TextView a = new TextView(MainActivity.this); a.setTextColor(Color.WHITE); a.setTextSize(15); a.setSingleLine(true); a.setText(it.label);
            TextView b = new TextView(MainActivity.this); b.setTextColor(0xFF8F9BB3); b.setTextSize(11); b.setSingleLine(true);
            b.setText(it.pkg == null ? it.name : it.pkg + "  •  v" + it.ver + "  •  " + fmt(it.size));
            TextView c = new TextView(MainActivity.this); c.setTextSize(12); c.setTypeface(Typeface.DEFAULT_BOLD);
            c.setText(it.status);
            c.setTextColor(it.state == 2 ? 0xFF4ADE80 : it.state == 3 ? 0xFFFF6B6B : it.state == 1 ? 0xFF00E5FF : 0xFFFFB020);
            col.addView(a); col.addView(b); col.addView(c);
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            // spacing between rows
            LinearLayout wrap = new LinearLayout(MainActivity.this);
            wrap.setPadding(0, 0, 0, dp(8));
            wrap.addView(row, new LinearLayout.LayoutParams(-1, -2));
            return wrap;
        }
    }
}
