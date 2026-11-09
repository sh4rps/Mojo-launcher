package com.robinui.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Apps {
    public static class App {
        public String label, pkg, cls;
        public ComponentName cn() { return new ComponentName(pkg, cls); }
        public String key() { return pkg + "/" + cls; }
    }

    public interface Cb { void on(Bitmap b); }

    static final LruCache<String, Bitmap> cache = new LruCache<>(220);
    static final ExecutorService ex = Executors.newFixedThreadPool(2);
    static final Handler main = new Handler(Looper.getMainLooper());

    public static List<App> load(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> l = pm.queryIntentActivities(i, 0);
        Set<String> hidden = Prefs.set(c, "hidden");
        List<App> out = new ArrayList<>();
        for (ResolveInfo r : l) {
            App a = new App();
            a.pkg = r.activityInfo.packageName;
            a.cls = r.activityInfo.name;
            a.label = String.valueOf(r.loadLabel(pm));
            if (hidden.contains(a.key())) continue;
            out.add(a);
        }
        Collections.sort(out, new Comparator<App>() {
            public int compare(App x, App y) { return x.label.compareToIgnoreCase(y.label); }
        });
        return out;
    }

    public static void icon(final Context c, final App a, final int size, final Cb cb) {
        Bitmap b = cache.get(a.key());
        if (b != null) { cb.on(b); return; }
        ex.execute(new Runnable() {
            public void run() {
                try {
                    Drawable d = c.getPackageManager().getActivityIcon(a.cn());
                    final Bitmap bm = render(d, size);
                    cache.put(a.key(), bm);
                    main.post(new Runnable() { public void run() { cb.on(bm); } });
                } catch (Throwable t) { }
            }
        });
    }

    /** Draws every icon in the same rounded-square (ColorOS-like) mask. */
    static Bitmap render(Drawable d, int s) {
        Bitmap bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, s, s), s * 0.29f, s * 0.29f, Path.Direction.CW);
        cv.clipPath(clip);
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable ai = (AdaptiveIconDrawable) d;
            int off = Math.round(s * 0.25f);
            Drawable bg = ai.getBackground(), fg = ai.getForeground();
            if (bg != null) { bg.setBounds(-off, -off, s + off, s + off); bg.draw(cv); }
            if (fg != null) { fg.setBounds(-off, -off, s + off, s + off); fg.draw(cv); }
        } else {
            cv.drawColor(0xFFF2F2F5);
            int in = Math.round(s * 0.14f);
            d.setBounds(in, in, s - in, s - in);
            d.draw(cv);
        }
        return bm;
    }
}
