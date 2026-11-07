package fsr;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;

/** FSR Launcher: "Unlock highest refresh rate" option (video settings) + applier. */
public final class Hz {
    private static final String PREFS = "fsr_launcher";
    private static final String KEY = "unlock_hz";

    private Hz() {}

    public static boolean enabled(Context c) {
        try {
            return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true);
        } catch (Throwable t) { return true; }
    }

    public static void init(Application app) {
        try { app.getExternalFilesDir(null); } catch (Throwable t) { } // makes sure Android/data/<pkg>/files exists
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity a, Bundle b) { request(a); watch(a); }
            public void onActivityStarted(Activity a) { request(a); }
            public void onActivityResumed(Activity a) { request(a); }
            public void onActivityPaused(Activity a) {}
            public void onActivityStopped(Activity a) {}
            public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            public void onActivityDestroyed(Activity a) {}
        });
    }

    /** Ask the system for the highest refresh rate at the current resolution. */
    public static void request(Activity a) {
        try {
            if (!enabled(a) || Build.VERSION.SDK_INT < 23) return;
            Window w = a.getWindow();
            Display d = a.getWindowManager().getDefaultDisplay();
            Display.Mode cur = d.getMode();
            Display.Mode best = cur;
            for (Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() == cur.getPhysicalWidth()
                        && m.getPhysicalHeight() == cur.getPhysicalHeight()
                        && m.getRefreshRate() > best.getRefreshRate()) best = m;
            }
            WindowManager.LayoutParams lp = w.getAttributes();
            if (lp.preferredDisplayModeId != best.getModeId()) {
                lp.preferredDisplayModeId = best.getModeId();
                lp.preferredRefreshRate = best.getRefreshRate();
                w.setAttributes(lp);
            }
        } catch (Throwable t) { }
    }

    /** Undo: go back to the system default. */
    static void release(Activity a) {
        try {
            Window w = a.getWindow();
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.preferredDisplayModeId = 0;
            lp.preferredRefreshRate = 0f;
            w.setAttributes(lp);
        } catch (Throwable t) { }
    }

    /** Finds the settings switch (added to the Video settings layout) whenever it shows up and wires it. */
    private static void watch(final Activity a) {
        try {
            final View decor = a.getWindow().getDecorView();
            final int id = a.getResources().getIdentifier("fsr_hz_switch", "id", a.getPackageName());
            if (id == 0) return;
            decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                public void onGlobalLayout() {
                    View v = decor.findViewById(id);
                    if (!(v instanceof CompoundButton) || v.getTag() != null) return;
                    v.setTag("bound");
                    final CompoundButton sw = (CompoundButton) v;
                    sw.setChecked(enabled(a));
                    sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        public void onCheckedChanged(CompoundButton b, boolean on) {
                            SharedPreferences.Editor e = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
                            e.putBoolean(KEY, on).apply();
                            if (on) request(a); else release(a);
                        }
                    });
                }
            });
        } catch (Throwable t) { }
    }
}
