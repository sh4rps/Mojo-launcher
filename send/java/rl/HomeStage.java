package rl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Home screen centre: title, glowing podium and the account's 3D skin (arrows cycle saved skins, Rotate spins). */
public class HomeStage extends SkinView3D {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final List<Bitmap> skins = new ArrayList<>();
    private int idx;
    private RectF left = new RectF(), right = new RectF(), rot = new RectF();
    private boolean loaded;

    public HomeStage(Context c) { super(c); init(); }
    public HomeStage(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        scaleMul = 0.78f; cyFrac = 0.46f;
        setSkin(defaultSkin(), false);
        skins.add(defaultSkin());
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (loaded) return;
        loaded = true;
        new Thread(new Runnable() { public void run() { loadAccountSkin(); loadLibrary(); } }).start();
    }

    private void loadAccountSkin() {
        try {
            Class<?> c = Class.forName("git.artdeell.mojo.authenticator.accounts.Accounts");
            Object a = c.getMethod("getCurrent").invoke(null);
            if (a == null) return;
            boolean ms = a.getClass().getField("isMicrosoft").getBoolean(a);
            String name = (String) a.getClass().getField("username").get(a);
            if (!ms || name == null) return;
            File cache = new File(getContext().getCacheDir(), "home_skin_" + name + ".png");
            Bitmap b = cache.exists() ? BitmapFactory.decodeFile(cache.getAbsolutePath()) : null;
            boolean slim = false;
            if (b == null) {
                String[] r = Mojang.playerSkin(name);
                b = Mojang.bitmap(r[0]); slim = "slim".equals(r[1]);
                java.io.FileOutputStream o = new java.io.FileOutputStream(cache);
                b.compress(Bitmap.CompressFormat.PNG, 100, o); o.close();
            }
            final Bitmap fb = b; final boolean fs = slim;
            post(new Runnable() { public void run() { skins.set(0, fb); idx = 0; setSkin(fb, fs); } });
        } catch (Throwable t) { }
    }

    private void loadLibrary() {
        try {
            Class<?> c = Class.forName("git.artdeell.mojo.Tools");
            String base = (String) c.getField("DIR_GAME_HOME").get(null);
            File[] fs = new File(base, "skins").listFiles();
            if (fs == null) return;
            for (File f : fs) {
                Bitmap b = f.getName().endsWith(".png") ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
                if (b != null && b.getWidth() == 64) skins.add(b);
            }
        } catch (Throwable t) { }
    }

    /** Plain purple-outfit skin, drawn in code so there is always something to show. */
    static Bitmap defaultSkin() {
        Bitmap b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b); Paint q = new Paint();
        int skin = 0xffe0ac86, hair = 0xff2a2140, dark = 0xff1d1633, purple = 0xff8b5cf6, shoe = 0xffe9e4ff;
        q.setColor(hair); c.drawRect(0, 0, 32, 16, q);                 // head (all faces hair)
        q.setColor(skin); c.drawRect(8, 10, 16, 16, q);                // face
        q.setColor(hair); c.drawRect(8, 8, 16, 10, q);                 // fringe
        q.setColor(purple); c.drawRect(9, 12, 11, 13, q); c.drawRect(13, 12, 15, 13, q);   // eyes
        q.setColor(dark); c.drawRect(16, 16, 40, 32, q);               // body
        q.setColor(purple); c.drawRect(23, 20, 25, 32, q);             // stripe
        q.setColor(dark); c.drawRect(40, 16, 56, 32, q); c.drawRect(32, 48, 48, 64, q);   // arms
        q.setColor(skin); c.drawRect(44, 20, 48, 20, q); c.drawRect(40, 30, 56, 32, q); c.drawRect(32, 62, 48, 64, q);
        q.setColor(dark); c.drawRect(0, 16, 16, 32, q); c.drawRect(16, 48, 32, 64, q);   // legs
        q.setColor(shoe); c.drawRect(0, 30, 16, 32, q); c.drawRect(16, 62, 32, 64, q);
        q.setColor(purple); c.drawRect(0, 29, 16, 30, q); c.drawRect(16, 61, 32, 62, q);
        return b;
    }

    @Override protected void beforeModel(Canvas c) {
        int w = getWidth(), h = getHeight();
        // soft glow behind the character
        p.setShader(new RadialGradient(w / 2f, h * 0.45f, Math.min(w, h) * 0.6f, 0x558b5cf6, 0x008b5cf6, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        // podium
        float cx = w / 2f, cy = h * 0.82f, rw = Math.min(w * 0.42f, h * 0.55f), rh = rw * 0.22f;
        p.setStyle(Paint.Style.FILL); p.setColor(0xff1d1236);
        r.set(cx - rw, cy - rh + dp(14), cx + rw, cy + rh + dp(14)); c.drawOval(r, p);
        p.setColor(0xff2c1a58);
        r.set(cx - rw, cy - rh, cx + rw, cy + rh); c.drawOval(r, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(0xffa78bfa);
        r.set(cx - rw * 0.9f, cy - rh * 0.9f, cx + rw * 0.9f, cy + rh * 0.9f); c.drawOval(r, p);
        p.setStyle(Paint.Style.FILL);
    }

    @Override protected void afterModel(Canvas c) {
        int w = getWidth(), h = getHeight();
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(Color.WHITE); p.setTextSize(dp(26));
        c.drawText("Send Launcher", dp(14), dp(34), p);
        p.setTypeface(Typeface.DEFAULT); p.setColor(0xffc4b5fd); p.setTextSize(dp(13));
        c.drawText("Minecraft Java Edition", dp(14), dp(54), p);
        // arrows + rotate pill
        float bs = dp(40);
        left.set(dp(8), h * 0.45f - bs / 2, dp(8) + bs, h * 0.45f + bs / 2);
        right.set(w - dp(8) - bs, h * 0.45f - bs / 2, w - dp(8), h * 0.45f + bs / 2);
        rot.set(w / 2f - dp(52), h - dp(40), w / 2f + dp(52), h - dp(10));
        pill(c, left, "‹", 22); pill(c, right, "›", 22); pill(c, rot, "↻  Rotate", 13);
    }

    private void pill(Canvas c, RectF b, String t, float sp) {
        p.setStyle(Paint.Style.FILL); p.setColor(0xaa1d1236); c.drawRoundRect(b, dp(10), dp(10), p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.5f)); p.setColor(0xff6d4bd6); c.drawRoundRect(b, dp(10), dp(10), p);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); p.setTextSize(dp(sp)); p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(t, b.centerX(), b.centerY() + dp(sp) * 0.35f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
        boolean res = super.onTouchEvent(e);
        if (up) {
            float x = e.getX(), y = e.getY();
            if (rot.contains(x, y)) turn(90);
            else if ((left.contains(x, y) || right.contains(x, y)) && skins.size() > 1) {
                idx = (idx + (left.contains(x, y) ? skins.size() - 1 : 1)) % skins.size();
                setSkin(skins.get(idx), slim());
            }
        }
        return res;
    }
}
