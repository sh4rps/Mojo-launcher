package com.robin.setting;

import android.content.ContentResolver;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.Display;

import java.util.ArrayList;
import java.util.List;

/** Refresh-rate helpers. */
final class HzCtl {
    static final int[] RATES = {60, 90, 120, 144};

    static Display display(Context c) {
        return c.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
    }

    static int current(Context c) {
        try { return Math.round(display(c).getRefreshRate()); } catch (Throwable t) { return 0; }
    }

    /** Supported {modeId, hz} at the current resolution. */
    static List<int[]> modes(Context c) {
        List<int[]> out = new ArrayList<>();
        try {
            Display d = display(c);
            Display.Mode cur = d.getMode();
            for (Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() != cur.getPhysicalWidth() || m.getPhysicalHeight() != cur.getPhysicalHeight()) continue;
                out.add(new int[]{m.getModeId(), Math.round(m.getRefreshRate())});
            }
        } catch (Throwable t) { }
        return out;
    }

    static boolean supported(Context c, int hz) {
        for (int[] m : modes(c)) if (m[1] == hz) return true;
        return false;
    }

    /** Mode id whose rate is closest to hz (0 if none). */
    static int modeFor(Context c, int hz) {
        int best = 0, bd = Integer.MAX_VALUE;
        for (int[] m : modes(c)) {
            int dd = Math.abs(m[1] - hz);
            if (dd < bd) { bd = dd; best = m[0]; }
        }
        return best;
    }

    /** Writes the system refresh-rate settings. Returns a short human report. */
    static String writeSettings(Context c, int hz) {
        ContentResolver cr = c.getContentResolver();
        StringBuilder r = new StringBuilder();
        if (Settings.System.canWrite(c)) {
            try {
                Settings.System.putFloat(cr, "peak_refresh_rate", hz);
                Settings.System.putFloat(cr, "min_refresh_rate", hz);
                r.append("System settings: written. ");
            } catch (Throwable t) { r.append("System settings: failed. "); }
            try { Settings.System.putInt(cr, "user_refresh_rate", hz); } catch (Throwable t) { }
        } else {
            r.append("Allow 'Modify system settings' first. ");
        }
        if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == 0) {
            try {
                Settings.Secure.putInt(cr, "user_refresh_rate", hz);
                Settings.Secure.putFloat(cr, "peak_refresh_rate", hz);
                Settings.Secure.putFloat(cr, "min_refresh_rate", hz);
                r.append("Secure settings: written. ");
            } catch (Throwable t) { r.append("Secure settings: failed. "); }
        }
        return r.toString().trim();
    }

    /** Back to the phone's own adaptive mode: min 0, peak = highest supported. */
    static void writeAuto(Context c) {
        int max = 60;
        for (int[] m : modes(c)) max = Math.max(max, m[1]);
        ContentResolver cr = c.getContentResolver();
        try {
            if (Settings.System.canWrite(c)) {
                Settings.System.putFloat(cr, "peak_refresh_rate", max);
                Settings.System.putFloat(cr, "min_refresh_rate", 0f);
            }
        } catch (Throwable t) { }
    }
}
