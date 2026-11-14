package rl;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Arrays;

/** Wires the Send Launcher home screen: sidebar, profile card, installed versions and quick actions. */
public class Home {
    private static WeakReference<ImageView> avatarV;
    private static WeakReference<TextView> nameV, subV;
    private static Bitmap lastAvatar;
    private static String lastName, lastSub;

    private static View find(View root, String tag) { return root.findViewWithTag(tag); }

    private static void tap(final View root, String tag, final Runnable r) {
        View v = find(root, tag);
        if (v != null) v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) { try { r.run(); } catch (Throwable t) { } }
        });
    }

    private static Runnable click(final View root, final String tag, final boolean fromTop) {
        return new Runnable() { public void run() {
            View v = (fromTop ? root.getRootView() : root).findViewWithTag(tag);
            if (v == null) v = root.findViewWithTag(tag);
            if (v != null) v.performClick();
        } };
    }

    public static void wire(final View root) {
        try {
            avatarV = new WeakReference<ImageView>((ImageView) find(root, "h_avatar"));
            nameV = new WeakReference<TextView>((TextView) find(root, "h_name"));
            subV = new WeakReference<TextView>((TextView) find(root, "h_sub"));
            who(lastName, lastSub);
            if (lastAvatar != null) avatar(lastAvatar);

            Runnable mods = click(root, "robin_content", false);
            Runnable ver = click(root, "h_spinner", false);
            tap(root, "h_versions", ver);
            tap(root, "h_viewall", ver);
            tap(root, "h_mods", mods); tap(root, "h_packs", mods); tap(root, "h_shaders", mods);
            tap(root, "h_q_mods", mods); tap(root, "h_q_packs", mods); tap(root, "h_q_shaders", mods);
            tap(root, "h_settings", click(root, "send_settings", true));
            tap(root, "h_pencil", click(root, "robin_skins", false));
            tap(root, "h_home", new Runnable() { public void run() { } });
            tap(root, "h_about", new Runnable() { public void run() { about(root); } });
            online(root);
            versions(root);
            root.postDelayed(new Runnable() { public void run() { versions(root); online(root); } }, 2500);
        } catch (Throwable t) { }
    }

    public static void who(String name, String sub) {
        if (name != null) lastName = name;
        if (sub != null) lastSub = sub;
        TextView n = nameV == null ? null : nameV.get(), s = subV == null ? null : subV.get();
        if (n != null && lastName != null) n.setText(lastName);
        if (s != null) s.setText(lastSub == null ? "Default Skin" : lastSub);
    }

    public static void avatar(Bitmap skin) {
        try {
            lastAvatar = skin;
            ImageView v = avatarV == null ? null : avatarV.get();
            if (v == null || skin == null) return;
            Bitmap face = Bitmap.createBitmap(skin, 8, 8, 8, 8);
            Bitmap out = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(out);
            c.drawBitmap(face, 0, 0, null);
            if (skin.getWidth() >= 48 && skin.getHeight() >= 16) c.drawBitmap(Bitmap.createBitmap(skin, 40, 8, 8, 8), 0, 0, null);
            v.setImageBitmap(Bitmap.createScaledBitmap(out, 64, 64, false));
        } catch (Throwable t) { }
    }

    private static void online(View root) {
        try {
            TextView t = (TextView) find(root, "h_online");
            if (t == null) return;
            boolean on = true;
            try {
                NetworkInfo ni = ((ConnectivityManager) root.getContext().getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
                on = ni != null && ni.isConnected();
            } catch (Throwable e) { }
            t.setText(on ? "●  Online" : "●  Offline");
            t.setTextColor(on ? 0xff4ade80 : 0xfff07a68);
        } catch (Throwable t) { }
    }

    private static int dp(View v, float d) { return Math.round(d * v.getResources().getDisplayMetrics().density); }

    private static GradientDrawable card(View v, int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(v, 12)); g.setStroke(dp(v, 1), stroke);
        return g;
    }

    private static void versions(final View root) {
        try {
            final LinearLayout row = (LinearLayout) find(root, "h_vers");
            if (row == null) return;
            row.removeAllViews();
            String[] names = null;
            try {
                String dir = (String) Class.forName("git.artdeell.mojo.Tools").getField("DIR_HOME_VERSION").get(null);
                File[] fs = dir == null ? null : new File(dir).listFiles();
                if (fs != null) {
                    java.util.ArrayList<String> l = new java.util.ArrayList<String>();
                    for (File f : fs) if (f.isDirectory() && new File(f, f.getName() + ".json").exists()) l.add(f.getName());
                    Object[] o = l.toArray(); Arrays.sort(o);
                    names = new String[o.length]; for (int i = 0; i < o.length; i++) names[i] = (String) o[i];
                }
            } catch (Throwable e) { }
            Context cx = root.getContext();
            int n = names == null ? 0 : Math.min(3, names.length);
            View.OnClickListener open = new View.OnClickListener() { public void onClick(View x) {
                View s = root.findViewWithTag("h_spinner"); if (s != null) s.performClick(); } };
            if (n == 0) {
                row.addView(vcard(cx, row, "No version yet", "Tap to add", false, open));
                return;
            }
            for (int i = 0; i < n; i++) row.addView(vcard(cx, row, names[i], "Installed", true, open));
        } catch (Throwable t) { }
    }

    private static View vcard(Context cx, View ref, String title, String badge, boolean ok, View.OnClickListener l) {
        LinearLayout c = new LinearLayout(cx);
        c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(ref, 10), dp(ref, 6), dp(ref, 10), dp(ref, 6));
        c.setBackground(card(ref, 0xee1b1436, 0xff3b2a73));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.rightMargin = dp(ref, 6); c.setLayoutParams(lp);
        TextView t = new TextView(cx); t.setText(title); t.setTextColor(0xfff5f3ff); t.setTextSize(13); t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setSingleLine(true); t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView b = new TextView(cx); b.setText((ok ? "●  " : "") + badge); b.setTextSize(11); b.setTextColor(ok ? 0xff4ade80 : 0xffc4b5fd);
        c.addView(t); c.addView(b);
        c.setClickable(true); c.setOnClickListener(l);
        return c;
    }

    private static void about(final View root) {
        Context cx = root.getContext();
        LinearLayout box = new LinearLayout(cx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(root, 18), dp(root, 14), dp(root, 18), dp(root, 10));
        box.setBackground(card(root, 0xff1b1436, 0xff6d4bd6));
        TextView h = new TextView(cx); h.setText("Send Launcher V1.0"); h.setTextColor(Color.WHITE); h.setTextSize(20); h.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        TextView s = new TextView(cx); s.setText("Minecraft Java Edition launcher for Android"); s.setTextColor(0xffc4b5fd); s.setTextSize(12);
        s.setPadding(0, 0, 0, dp(root, 10));
        box.addView(h); box.addView(s);
        final AlertDialog[] dlg = new AlertDialog[1];
        String[][] items = {{"Control editor", "h_o_controls"}, {"Install .jar", "h_o_jar"}, {"Open games folder", "h_o_files"}, {"Share logs", "h_o_logs"}};
        for (final String[] it : items) {
            TextView b = new TextView(cx); b.setText(it[0]); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setGravity(Gravity.CENTER);
            b.setBackground(card(root, 0xff2a1f4d, 0xff3b2a73));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(root, 42));
            lp.topMargin = dp(root, 6); b.setLayoutParams(lp);
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View x) {
                if (dlg[0] != null) dlg[0].dismiss();
                View v = root.findViewWithTag(it[1]); if (v != null) v.performClick();
            } });
            box.addView(b);
        }
        dlg[0] = new AlertDialog.Builder(cx).setView(box).create();
        dlg[0].show();
        if (dlg[0].getWindow() != null) dlg[0].getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
    }
}
