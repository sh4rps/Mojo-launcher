package com.robinui.launcher;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

/** Adaptive refresh-rate helper: high rate while touching/animating, system default (low) while idle. */
public final class Hz {
    public static boolean adaptive(Context c) { return Prefs.b(c, "adaptive", true); }

    public static float max(Context c) {
        float m = 60f;
        try {
            Display d = ((DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY);
            Display.Mode cur = d.getMode();
            for (Display.Mode x : d.getSupportedModes()) {
                if (x.getPhysicalWidth() == cur.getPhysicalWidth() && x.getPhysicalHeight() == cur.getPhysicalHeight() && x.getRefreshRate() > m) m = x.getRefreshRate();
            }
        } catch (Throwable t) { }
        return m;
    }

    /** boost=true asks for the panel's top rate for this window; false lets the system pick (adaptive/idle). */
    public static void window(Window w, boolean boost) {
        try {
            Context c = w.getContext();
            float want;
            if (!adaptive(c)) want = 60f; else want = boost ? max(c) : 0f;
            WindowManager.LayoutParams p = w.getAttributes();
            if (p.preferredRefreshRate != want) { p.preferredRefreshRate = want; w.setAttributes(p); }
        } catch (Throwable t) { }
    }

    /** System-wide: peak refresh = panel max when on, 60 when off; min stays 0 so the system can idle low. Needs "Modify system settings". */
    public static boolean applySystem(Context c, boolean on) {
        try {
            if (!Settings.System.canWrite(c)) return false;
            android.content.ContentResolver r = c.getContentResolver();
            Settings.System.putFloat(r, "peak_refresh_rate", on ? max(c) : 60f);
            Settings.System.putFloat(r, "min_refresh_rate", 0f);
            return true;
        } catch (Throwable t) { return false; }
    }
}
