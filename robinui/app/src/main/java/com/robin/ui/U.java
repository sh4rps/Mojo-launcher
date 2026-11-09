package com.robin.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.TextView;

public final class U {
    public static final int ACCENT = 0xFF1ACD6B;
    public static float dens = 3f;

    public static int dp(float v) { return Math.round(v * dens); }

    public static boolean night(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int text(Context c) { return night(c) ? 0xFFF2F2F5 : 0xFF17171A; }
    public static int sub(Context c) { return night(c) ? 0xFFA5A5AD : 0xFF6B6B73; }
    public static int glass(Context c) { return night(c) ? 0xE61A1B1F : 0xE6F6F6F9; }
    public static int tile(Context c) { return night(c) ? 0xFF2C2D33 : 0xFFFFFFFF; }

    public static GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    public static TextView tv(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Damped-spring style interpolator (smooth, slight overshoot). */
    public static final Interpolator SPRING = new Interpolator() {
        public float getInterpolation(float t) {
            return (float) (1 - Math.exp(-7 * t) * Math.cos(9.5 * t));
        }
    };

    /** Press feedback: icon shrinks, springs back. */
    public static void pressable(final View v) {
        v.setOnTouchListener(null);
    }
}
