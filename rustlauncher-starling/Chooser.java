package com.robin.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Start screen: Horizon (Rust engine) or Java Edition (the Mojo launcher GUI). */
public class Chooser extends Activity {
    static final String HORIZON = "dev.horizon.android.HorizonActivity";
    static final String JAVA = "git.artdeell.mojo.TestStorageActivity";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        try { w.setStatusBarColor(0xFF0D0D10); w.setNavigationBarColor(0xFF0D0D10); } catch (Throwable t) { }

        final boolean land = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0D0D10);
        root.setPadding(dp(20), dp(24), dp(20), dp(20));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = text("Rust Launcher", 26, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = text("Choose an edition", 14, 0xFF9A9AA3, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(2), 0, dp(18));
        root.addView(sub);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(land ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, land ? 0 : -2, 1f);
        if (!land) rp.weight = 0;
        row.setLayoutParams(rp);

        row.addView(card("Horizon", "Rust Edition",
                "Native Rust engine · Minecraft 26.3\nMods (.hmod), shaders, worlds, own touch controls",
                0xFFE53935, 0xFF7F0F14, HORIZON));
        row.addView(card("Java Edition", "Full launcher",
                "All versions · Fabric / Forge / NeoForge / Quilt\n"
                + "Renderers: LTW, Holy GL4ES, MobileGlues, Zink\n"
                + "Java 8 / 17 / 21, RAM, resolution, controls",
                0xFF2A2A31, 0xFF16161A, JAVA));
        root.addView(row);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(0xFF0D0D10);
        sv.addView(root);
        setContentView(sv);
    }

    private View card(String name, String tag, String desc, int c1, int c2, final String target) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(22), dp(20), dp(22));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setCornerRadius(dp(22));
        if (c1 != 0xFFE53935) g.setStroke(dp(2), 0xFFE53935);
        c.setBackground(g);
        c.setClickable(true);
        c.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2, 1f);
        lp.setMargins(dp(8), dp(8), dp(8), dp(8));
        if (getResources().getConfiguration().orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            lp.weight = 0;
        } else {
            lp.width = 0;
        }
        c.setLayoutParams(lp);

        c.addView(text(tag.toUpperCase(), 11, 0xCCFFFFFF, true));
        TextView n = text(name, 28, Color.WHITE, true);
        n.setPadding(0, dp(4), 0, dp(8));
        c.addView(n);
        c.addView(text(desc, 14, 0xE6FFFFFF, false));
        TextView go = text("Open  ›", 15, Color.WHITE, true);
        go.setPadding(0, dp(16), 0, 0);
        c.addView(go);

        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launch(target); }
        });
        return c;
    }

    private void launch(String cls) {
        try {
            Intent i = new Intent();
            i.setClassName(getPackageName(), cls);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Could not open: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
