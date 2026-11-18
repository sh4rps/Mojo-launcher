package com.robin.packsed;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = 0xFF0C0C12, PANEL = 0xFF14141D, CARD = 0xFF1B1B27, ACCENT = 0xFF8B5CF6, ACCENT2 = 0xFF3B82F6,
            TEXT = 0xFFE5E7EB, MUTED = 0xFF8A8FA3, OFF = 0xFF2A2A3C;
    private Ui() { }

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        return t;
    }

    static TextView btn(Context c, String label, boolean primary, Runnable r) {
        TextView t = text(c, label, 13, Ui.TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        t.setMinHeight(dp(c, 36));
        t.setBackground(round(primary ? ACCENT : CARD, 10, c));
        t.setClickable(true);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    static void setSelected(TextView t, boolean on) {
        t.setBackground(round(on ? ACCENT : CARD, 10, t.getContext()));
    }

    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static LinearLayout.LayoutParams margin(LinearLayout.LayoutParams p, Context c, int l, int t, int r, int b) {
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static String clean(String s) {
        String r = s.toLowerCase().replaceAll("[^a-z0-9_./-]", "_").replaceAll("^[./]+", "");
        return r.isEmpty() ? "texture" : r;
    }
}
