package com.robin.setting;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Watches which app is in front and forces the Hz you picked for it.
 * Two layers: (1) writes the system refresh-rate settings, (2) holds a 1px invisible accessibility overlay
 * that requests the matching display mode (preferredDisplayModeId).
 */
public class HzService extends AccessibilityService {
    static volatile HzService inst;
    private WindowManager wm;
    private View overlay;
    private WindowManager.LayoutParams lp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int lastHz = -1;
    private String lastPkg = "";

    @Override protected void onServiceConnected() {
        inst = this;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        int g = Prefs.global(this);
        if (g > 0) force(g);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (pkg.equals(lastPkg)) return;
        if (pkg.equals("com.android.systemui") || pkg.contains("inputmethod") || pkg.equals(getPackageName())) return;
        lastPkg = pkg;
        int hz = Prefs.app(this, pkg);
        if (hz == 0) hz = Prefs.global(this);
        if (hz > 0) force(hz); else release();
    }

    /** No rule for this app: drop the overlay and let the phone's own auto refresh rate work again. */
    void release() {
        h.post(new Runnable() { public void run() {
            if (lastHz == 0) return;
            lastHz = 0;
            try { if (overlay != null) { wm.removeView(overlay); overlay = null; } } catch (Throwable t) { }
            HzCtl.writeAuto(HzService.this);
        }});
    }

    /** Apply hz now (settings + overlay). */
    void force(final int hz) {
        h.post(new Runnable() { public void run() {
            if (hz == lastHz) return;
            lastHz = hz;
            HzCtl.writeSettings(HzService.this, hz);
            overlay(hz);
        }});
    }

    private void overlay(int hz) {
        try {
            int mode = HzCtl.modeFor(this, hz);
            if (overlay == null) {
                overlay = new View(this);
                lp = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
                lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
                lp.preferredDisplayModeId = mode;
                lp.preferredRefreshRate = hz;
                wm.addView(overlay, lp);
            } else {
                lp.preferredDisplayModeId = mode;
                lp.preferredRefreshRate = hz;
                wm.updateViewLayout(overlay, lp);
            }
        } catch (Throwable t) { }
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        try { if (overlay != null) wm.removeView(overlay); } catch (Throwable t) { }
        overlay = null;
        if (inst == this) inst = null;
        super.onDestroy();
    }

    /** Called from the UI after the user changes a choice. */
    static void refresh(Context c) {
        HzService s = inst;
        if (s != null) { s.lastHz = -1; s.lastPkg = ""; int g = Prefs.global(c); if (g > 0) s.force(g); }
    }
}
