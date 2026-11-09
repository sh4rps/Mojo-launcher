package com.robin.ui;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

/** Adaptive refresh rate for RobinUI's own windows: max Hz while touching/animating, 60 Hz when idle. */
public final class Hz {
    private static int hi = -1, lo = -1;

    static void probe(Context c) {
        if (hi >= 0) return;
        try {
            Display d = c.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            Display.Mode cur = d.getMode();
            Display.Mode best = cur, base = null;
            for (Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() != cur.getPhysicalWidth() || m.getPhysicalHeight() != cur.getPhysicalHeight()) continue;
                if (m.getRefreshRate() > best.getRefreshRate()) best = m;
                if (m.getRefreshRate() >= 59f && (base == null || m.getRefreshRate() < base.getRefreshRate())) base = m;
            }
            hi = best.getModeId();
            lo = base != null ? base.getModeId() : cur.getModeId();
        } catch (Throwable t) { hi = 0; lo = 0; }
    }

    /** Mode id to request. high=true -> max (if adaptive enabled), else 60 Hz. */
    public static int mode(Context c, boolean high) {
        probe(c);
        if (!Prefs.hz()) return lo;
        return high ? hi : lo;
    }

    public static void apply(WindowManager.LayoutParams p, Context c, boolean high) { p.preferredDisplayModeId = mode(c, high); }

    /** Per-window controller (activities). */
    public static final class Ctl {
        private final Window w;
        private final Context c;
        private final Handler h = new Handler(Looper.getMainLooper());
        private boolean high;
        private final Runnable relax = new Runnable() { public void run() { set(false); } };

        public Ctl(Window w, Context c) { this.w = w; this.c = c; set(false); }

        public void boost() {
            if (!high) set(true);
            h.removeCallbacks(relax);
            h.postDelayed(relax, 1400);
        }

        void set(boolean hi) {
            high = hi;
            try {
                WindowManager.LayoutParams p = w.getAttributes();
                int m = mode(c, hi);
                if (p.preferredDisplayModeId != m) { p.preferredDisplayModeId = m; w.setAttributes(p); }
            } catch (Throwable t) { }
        }

        public void refresh() { set(high); }
    }
}
