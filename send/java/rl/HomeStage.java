package rl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Home screen centre: glowing podium + the account's 3D skin. Redraws only while it is being moved
 * (drag / Rotate / arrows), so it costs nothing when idle.
 */
public class HomeStage extends SkinView3D {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final List<Bitmap> skins = new ArrayList<Bitmap>();
    private int idx;
    private final RectF left = new RectF(), right = new RectF(), rot = new RectF();
    private boolean loaded, accountSkin, moving;
    private float tgt;
    private Bitmap cape;

    public HomeStage(Context c) { super(c); init(); }
    public HomeStage(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        try { init0(); } catch (Throwable t) { }
    }

    private void init0() {
        scaleMul = 0.80f; cyFrac = 0.47f; autoSpin = false; walk = false;
        yaw = -22; pitch = 7; tgt = yaw;
        Bitmap d = null, cp = null;
        try {
            InputStream in = getContext().getAssets().open("send/skin.png"); d = BitmapFactory.decodeStream(in); in.close();
            in = getContext().getAssets().open("send/cape.png"); cp = BitmapFactory.decodeStream(in); in.close();
        } catch (Throwable t) { }
        if (d == null) d = defaultSkin();
        cape = cp;
        setSkin(d, false);
        if (cape != null) setCape(cape);
        skins.add(d);
        Home.avatar(d);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override protected boolean keepAnimating() { return moving; }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (loaded) return;
        loaded = true;
        Thread t = new Thread(new Runnable() { public void run() { try { loadAccountSkin(); loadLibrary(); } catch (Throwable e) { } } });
        t.setDaemon(true); t.start();
    }

    private void loadAccountSkin() {
        for (int tries = 0; tries < 6; tries++) {
            try {
                Class<?> c = Class.forName("git.artdeell.mojo.authenticator.accounts.Accounts");
                Object a = c.getMethod("getCurrent").invoke(null);
                if (a == null) { Thread.sleep(2000); continue; }
                boolean ms = a.getClass().getField("isMicrosoft").getBoolean(a);
                final String name = (String) a.getClass().getField("username").get(a);
                if (name == null) return;
                post(new Runnable() { public void run() { Home.who(name, null); } });
                if (!ms) return;
                File cache = new File(getContext().getCacheDir(), "home_skin_" + name + ".png");
                Bitmap b = cache.exists() ? BitmapFactory.decodeFile(cache.getAbsolutePath()) : null;
                boolean slim = false;
                if (b == null) {
                    String[] rr = Mojang.playerSkin(name);
                    b = Mojang.bitmap(rr[0]); slim = "slim".equals(rr[1]);
                    java.io.FileOutputStream o = new java.io.FileOutputStream(cache);
                    b.compress(Bitmap.CompressFormat.PNG, 100, o); o.close();
                }
                final Bitmap fb = b; final boolean fs = slim;
                post(new Runnable() { public void run() {
                    skins.set(0, fb); idx = 0; accountSkin = true; setSkin(fb, fs); setCape(null);
                    Home.who(null, label()); Home.avatar(fb); } });
                return;
            } catch (Throwable t) { return; }
        }
    }

    private String label() { return idx == 0 ? (accountSkin ? "Current Skin" : "Default Skin") : "Saved Skin " + idx; }

    private void loadLibrary() {
        try {
            Class<?> c = Class.forName("git.artdeell.mojo.Tools");
            String base = (String) c.getField("DIR_GAME_HOME").get(null);
            File[] fs = new File(base, "skins").listFiles();
            if (fs == null) return;
            final List<Bitmap> found = new ArrayList<Bitmap>();
            for (File f : fs) {
                Bitmap b = f.getName().endsWith(".png") ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
                if (b != null && b.getWidth() == 64 && found.size() < 11) found.add(b);
            }
            post(new Runnable() { public void run() { for (Bitmap b : found) if (skins.size() < 12) skins.add(b); } });
        } catch (Throwable t) { }
    }

    /** Plain fallback skin, drawn in code so there is always something to show. */
    static Bitmap defaultSkin() {
        Bitmap b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b); Paint q = new Paint();
        int skin = 0xffe0ac86, hair = 0xff2a2140, dark = 0xff1d1633, purple = 0xff8b5cf6, shoe = 0xffe9e4ff;
        q.setColor(hair); c.drawRect(0, 0, 32, 16, q);
        q.setColor(skin); c.drawRect(8, 10, 16, 16, q);
        q.setColor(hair); c.drawRect(8, 8, 16, 10, q);
        q.setColor(purple); c.drawRect(9, 12, 11, 13, q); c.drawRect(13, 12, 15, 13, q);
        q.setColor(dark); c.drawRect(16, 16, 40, 32, q);
        q.setColor(purple); c.drawRect(23, 20, 25, 32, q);
        q.setColor(dark); c.drawRect(40, 16, 56, 32, q); c.drawRect(32, 48, 48, 64, q);
        q.setColor(skin); c.drawRect(40, 30, 56, 32, q); c.drawRect(32, 62, 48, 64, q);
        q.setColor(dark); c.drawRect(0, 16, 16, 32, q); c.drawRect(16, 48, 32, 64, q);
        q.setColor(shoe); c.drawRect(0, 30, 16, 32, q); c.drawRect(16, 62, 32, 64, q);
        return b;
    }

    private RadialGradient glow;
    private final Paint gp = new Paint();

    @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
        super.onSizeChanged(w, hh, ow, oh);
        glow = new RadialGradient(w / 2f, hh * 0.55f, Math.max(1f, Math.min(w, hh) * 0.70f), 0x778b5cf6, 0x008b5cf6, Shader.TileMode.CLAMP);
        gp.setShader(glow);
    }

    @Override protected void beforeModel(Canvas c) {
        moving = false;
        float d = tgt - yaw;
        if (Math.abs(d) > 0.6f) { yaw += d * 0.28f; moving = true; } else yaw = tgt;
        int w = getWidth(), h = getHeight();
        if (glow != null) c.drawRect(0, 0, w, h, gp);
        float s = unit();
        float cx = w / 2f, cy = h * cyFrac + 16 * s + dp(4), rw = Math.min(w * 0.46f, s * 15f), rh = rw * 0.26f;
        p.setShader(null);
        // halo under the podium
        for (int i = 4; i >= 1; i--) {
            p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(22, 168, 85, 247));
            r.set(cx - rw - dp(6) * i, cy - rh - dp(2) * i, cx + rw + dp(6) * i, cy + rh + dp(3) * i + dp(10)); c.drawOval(r, p);
        }
        p.setStyle(Paint.Style.FILL); p.setColor(0xff120a2a);
        r.set(cx - rw, cy - rh + dp(14), cx + rw, cy + rh + dp(14)); c.drawOval(r, p);            // base side
        p.setColor(0xff1d1040); r.set(cx - rw, cy - rh + dp(7), cx + rw, cy + rh + dp(7)); c.drawOval(r, p);
        p.setColor(0xff2d1a5c); r.set(cx - rw, cy - rh, cx + rw, cy + rh); c.drawOval(r, p);       // top
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(6)); p.setColor(0x338b5cf6); r.set(cx - rw * 0.93f, cy - rh * 0.93f, cx + rw * 0.93f, cy + rh * 0.93f); c.drawOval(r, p);
        p.setStrokeWidth(dp(2.2f)); p.setColor(0xffc4a8ff); c.drawOval(r, p);                       // neon ring
        p.setStrokeWidth(dp(1)); p.setColor(0x88c4b5fd);
        r.set(cx - rw * 0.66f, cy - rh * 0.66f, cx + rw * 0.66f, cy + rh * 0.66f); c.drawOval(r, p);
        p.setStyle(Paint.Style.FILL);
    }

    @Override protected void afterModel(Canvas c) {
        int w = getWidth(), h = getHeight();
        float bs = dp(38), my = h * 0.46f;
        left.set(dp(10), my - bs / 2, dp(10) + bs, my + bs / 2);
        right.set(w - dp(10) - bs, my - bs / 2, w - dp(10), my + bs / 2);
        float s = unit(), py = h * cyFrac + 16 * s + dp(4);
        float rh2 = Math.min(w * 0.46f, s * 15f) * 0.26f;
        rot.set(w / 2f - dp(50), py + rh2 * 0.45f, w / 2f + dp(50), py + rh2 * 0.45f + dp(30));
        box(c, left); chevron(c, left, true); box(c, right); chevron(c, right, false);
        box(c, rot);
        // rotate glyph + label
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.8f)); p.setColor(Color.WHITE);
        float gx = rot.left + dp(22), gy = rot.centerY(), gr = dp(6.5f);
        r.set(gx - gr, gy - gr, gx + gr, gy + gr); c.drawArc(r, -40, 290, false, p);
        p.setStyle(Paint.Style.FILL);
        Path tri = new Path(); tri.moveTo(gx + gr + dp(3), gy - dp(4)); tri.lineTo(gx + gr - dp(3), gy - dp(4)); tri.lineTo(gx + gr, gy + dp(1)); tri.close(); c.drawPath(tri, p);
        p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(dp(13)); p.setTextAlign(Paint.Align.LEFT);
        c.drawText("Rotate", rot.left + dp(36), gy + dp(4.5f), p);
    }

    private void box(Canvas c, RectF b) {
        p.setStyle(Paint.Style.FILL); p.setColor(0xb81a1030); c.drawRoundRect(b, dp(11), dp(11), p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.5f)); p.setColor(0xff6d4bd6); c.drawRoundRect(b, dp(11), dp(11), p);
        p.setStyle(Paint.Style.FILL);
    }

    private void chevron(Canvas c, RectF b, boolean l) {
        float cx = b.centerX(), cy = b.centerY(), a = dp(6), k = l ? -1 : 1;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2.6f)); p.setColor(Color.WHITE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        Path q = new Path(); q.moveTo(cx - k * a * 0.5f, cy - a); q.lineTo(cx + k * a * 0.5f, cy); q.lineTo(cx - k * a * 0.5f, cy + a); c.drawPath(q, p);
        p.setStyle(Paint.Style.FILL); p.setStrokeCap(Paint.Cap.BUTT);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        try { return touch(e); } catch (Throwable t) { return true; }
    }

    private boolean touch(MotionEvent e) {
        int act = e.getActionMasked();
        boolean up = act == MotionEvent.ACTION_UP;
        boolean res = super.onTouchEvent(e);
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_MOVE) { tgt = yaw; moving = true; }
        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) { tgt = yaw; moving = false; invalidate(); }
        if (up) {
            float x = e.getX(), y = e.getY();
            if (rot.contains(x, y)) turn(90);
            else if ((left.contains(x, y) || right.contains(x, y)) && skins.size() > 1) {
                idx = (idx + (left.contains(x, y) ? skins.size() - 1 : 1)) % skins.size();
                setSkin(skins.get(idx), slim()); setCape(idx == 0 && !accountSkin ? cape : null);
                Home.who(null, label()); Home.avatar(skins.get(idx));
            }
        }
        return res;
    }

    @Override public void turn(float deg) { tgt += deg; moving = true; invalidate(); }
}
