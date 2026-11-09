package com.robin.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.text.Collator;
import java.util.*;

/** App list + icon cache (loaded once, refreshed on package changes only). */
public final class Apps {
    public static final class Info {
        public String pkg, cls, label;
        public String key() { return pkg + "/" + cls; }
        public ComponentName cn() { return new ComponentName(pkg, cls); }
    }

    public interface Listener { void onApps(); }

    public static volatile List<Info> all = new ArrayList<>();
    static final LruCache<String, Bitmap> icons = new LruCache<>(400);
    static int px = 144;
    static final List<Listener> ls = new ArrayList<>();
    static final Handler main = new Handler(Looper.getMainLooper());
    static boolean loading;

    public static void listen(Listener l) { if (!ls.contains(l)) ls.add(l); }
    public static void unlisten(Listener l) { ls.remove(l); }

    public static void reload(final Context c) {
        if (loading) return;
        loading = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    PackageManager pm = c.getPackageManager();
                    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> rs = pm.queryIntentActivities(i, 0);
                    final List<Info> out = new ArrayList<>();
                    for (ResolveInfo r : rs) {
                        Info a = new Info();
                        a.pkg = r.activityInfo.packageName;
                        a.cls = r.activityInfo.name;
                        a.label = String.valueOf(r.loadLabel(pm));
                        if (a.pkg.equals(c.getPackageName())) continue;
                        out.add(a);
                    }
                    final Collator col = Collator.getInstance();
                    Collections.sort(out, new Comparator<Info>() {
                        public int compare(Info a, Info b) { return col.compare(a.label, b.label); }
                    });
                    all = out;
                    for (Info a : out) icon(c, a);
                    main.post(new Runnable() { public void run() { for (Listener l : new ArrayList<>(ls)) l.onApps(); } });
                } catch (Throwable t) { }
                loading = false;
            }
        }).start();
    }

    public static Info find(String key) {
        for (Info a : all) if (a.key().equals(key)) return a;
        return null;
    }

    public static Info resolve(Context c, Intent i) {
        try {
            ResolveInfo r = c.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (r == null || r.activityInfo == null) return null;
            for (Info a : all) if (a.pkg.equals(r.activityInfo.packageName)) return a;
        } catch (Throwable t) { }
        return null;
    }

    public static Bitmap icon(Context c, Info a) {
        Bitmap b = icons.get(a.key());
        if (b != null) return b;
        try {
            Drawable d = c.getPackageManager().getActivityIcon(a.cn());
            b = make(d, px);
        } catch (Throwable t) {
            b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        }
        icons.put(a.key(), b);
        return b;
    }

    /** Uniform ColorOS-like rounded-square icon. */
    static Bitmap make(Drawable d, int s) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, s, s), s * 0.27f, s * 0.27f, Path.Direction.CW);
        c.clipPath(clip);
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable ad = (AdaptiveIconDrawable) d;
            int o = (int) (s * 0.25f);
            Drawable bg = ad.getBackground(), fg = ad.getForeground();
            if (bg != null) { bg.setBounds(-o, -o, s + o, s + o); bg.draw(c); }
            if (fg != null) { fg.setBounds(-o, -o, s + o, s + o); fg.draw(c); }
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFF4F4F6);
            c.drawRect(0, 0, s, s, p);
            int in = (int) (s * 0.14f);
            d.setBounds(in, in, s - in, s - in);
            d.draw(c);
        }
        return b;
    }
}
