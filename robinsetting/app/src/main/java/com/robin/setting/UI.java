package com.robin.setting;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Horizon-style purple dark look, built in code. */
final class UI {
    static final int BG = 0xff0d0e13, CARD = 0xff16181f, EDGE = 0xff3b3354, ACC = 0xff9a86ff, ACC_D = 0xff5b3fd6,
            TXT = 0xffecedf3, SUB = 0xffc5c7d1, OK = 0xff45c27a, BAD = 0xfff07a68, OFF = 0xff585071;

    static int dp(Context c, float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics())); }

    static GradientDrawable box(Context c, int fill, int stroke, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(c, r)); g.setStroke(dp(c, 2), stroke);
        return g;
    }

    static TextView text(Context c, String s, float sp, int col, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s); t.setTextSize(sp); t.setTextColor(col);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(box(c, CARD, EDGE, 10));
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(c, 12));
        l.setLayoutParams(lp);
        return l;
    }

    static TextView button(Context c, String s, boolean on) {
        TextView b = text(c, s, 16, on ? Color.WHITE : SUB, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(box(c, on ? ACC_D : EDGE, on ? ACC : OFF, 8));
        int p = dp(c, 10);
        b.setPadding(p, p, p, p);
        return b;
    }

    static View gap(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(c, h)));
        return v;
    }
}
