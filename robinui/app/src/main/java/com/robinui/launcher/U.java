package com.robinui.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

public final class U {
    public static final int RED = 0xFFE5282E;
    public static final int PANEL = 0xF01A1A1E;
    public static final int TILE = 0xFF2C2C32;
    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    public static GradientDrawable rr(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }
}
