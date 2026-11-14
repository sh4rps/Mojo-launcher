package rl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Software 3D skin + cape preview (orthographic, textured cuboids, drag to rotate). */
public class SkinView3D extends View {
    private Bitmap skin, cape;
    private boolean slim;
    private float yaw = 25, pitch = 12;
    private long lastTouch;
    private float lx, ly;
    private boolean drag;
    private final Paint paint = new Paint();
    private final Matrix mx = new Matrix();
    private final List<Face> faces = new ArrayList<>();
    private final float[] src = new float[6], dst = new float[6];

    private static class Face {
        Bitmap bmp; float[] p = new float[3], u = new float[3], v = new float[3]; float depth;
    }

    protected float scaleMul = 1f, cyFrac = 0.5f;
    protected boolean autoSpin = true;

    public SkinView3D(Context c) {
        super(c);
        paint.setFilterBitmap(false); paint.setAntiAlias(false);
    }

    public SkinView3D(Context c, android.util.AttributeSet a) { this(c); }

    protected void beforeModel(Canvas c) { }
    protected void afterModel(Canvas c) { }
    public void turn(float deg) { yaw += deg; lastTouch = System.currentTimeMillis(); invalidate(); }

    public void setSkin(Bitmap b, boolean slim) { this.skin = b == null ? null : normalize(b); this.slim = slim; cache.clear(); invalidate(); }
    public void setCape(Bitmap b) { this.cape = b; cache.clear(); invalidate(); }
    public Bitmap skin() { return skin; }
    public boolean slim() { return slim; }

    /** 64x32 legacy skins -> 64x64. */
    public static Bitmap normalize(Bitmap b) {
        if (b.getWidth() == 64 && b.getHeight() == 64) return b.copy(Bitmap.Config.ARGB_8888, false);
        Bitmap o = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(o);
        c.drawBitmap(b, 0, 0, null);
        int[][] boxes = {{0, 16, 16, 48}, {40, 16, 32, 48}};   // right leg -> left leg, right arm -> left arm
        for (int[] bx : boxes) {
            int sx = bx[0], sy = bx[1], dx = bx[2], dy = bx[3];
            flipRegion(o, sx + 4, sy, dx + 4, dy, 4, 4);          // top
            flipRegion(o, sx + 8, sy, dx + 8, dy, 4, 4);          // bottom
            flipRegion(o, sx + 8, sy + 4, dx, dy + 4, 4, 12);     // left face -> new right
            flipRegion(o, sx, sy + 4, dx + 8, dy + 4, 4, 12);     // right face -> new left
            flipRegion(o, sx + 4, sy + 4, dx + 4, dy + 4, 4, 12); // front
            flipRegion(o, sx + 12, sy + 4, dx + 12, dy + 4, 4, 12); // back
        }
        return o;
    }

    private static void flipRegion(Bitmap b, int sx, int sy, int dx, int dy, int w, int h) {
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) b.setPixel(dx + x, dy + y, b.getPixel(sx + w - 1 - x, sy + y));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: drag = true; lx = e.getX(); ly = e.getY(); getParent().requestDisallowInterceptTouchEvent(true); return true;
            case MotionEvent.ACTION_MOVE:
                yaw += (e.getX() - lx) * 0.6f;
                pitch = Math.max(-40, Math.min(40, pitch + (e.getY() - ly) * 0.4f));
                lx = e.getX(); ly = e.getY(); lastTouch = System.currentTimeMillis(); invalidate(); return true;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: drag = false; lastTouch = System.currentTimeMillis(); return true;
        }
        return super.onTouchEvent(e);
    }

    private final java.util.HashMap<String, Bitmap> cache = new java.util.HashMap<>();

    private Bitmap region(Bitmap tex, String key, int x, int y, int w, int h) {
        String k = key + x + "," + y + "," + w + "," + h;
        Bitmap b = cache.get(k);
        if (b == null) { b = Bitmap.createBitmap(tex, x, y, w, h); cache.put(k, b); }
        return b;
    }

    /** Textured cuboid. (w,h,d) are the texture dimensions, inf is extra geometry size (overlay layers). */
    private void addBox(Bitmap tex, String key, int tu, int tv, float w, float h, float d, float inf,
                        float cx, float cy, float cz, float pivY, float pivZ, float ang, boolean flip180) {
        float gw = w + inf, gh = h + inf, gd = d + inf;
        float hw = gw / 2, hh = gh / 2, hd = gd / 2;
        int iw = Math.round(w), ih = Math.round(h), id = Math.round(d);
        float[][] P = {{-hw, hh, -hd}, {-hw, -hh, hd}, {-hw, hh, -hd}, {-hw, hh, hd}, {hw, hh, hd}, {hw, hh, -hd}};
        float[][] U = {{gw, 0, 0}, {gw, 0, 0}, {0, 0, gd}, {gw, 0, 0}, {0, 0, -gd}, {-gw, 0, 0}};
        float[][] V = {{0, 0, gd}, {0, 0, -gd}, {0, -gh, 0}, {0, -gh, 0}, {0, -gh, 0}, {0, -gh, 0}};
        int[][] R = {{tu + id, tv, iw, id}, {tu + id + iw, tv, iw, id}, {tu, tv + id, id, ih},
                {tu + id, tv + id, iw, ih}, {tu + id + iw, tv + id, id, ih}, {tu + id + iw + id, tv + id, iw, ih}};
        for (int i = 0; i < 6; i++) {
            int[] r = R[i];
            if (r[0] < 0 || r[1] < 0 || r[0] + r[2] > tex.getWidth() || r[1] + r[3] > tex.getHeight() || r[2] <= 0 || r[3] <= 0) continue;
            Face f = new Face();
            f.bmp = region(tex, key, r[0], r[1], r[2], r[3]);
            for (int k = 0; k < 3; k++) { f.p[k] = P[i][k]; f.u[k] = U[i][k]; f.v[k] = V[i][k]; }
            if (flip180) { f.p[0] = -f.p[0]; f.p[2] = -f.p[2]; f.u[0] = -f.u[0]; f.u[2] = -f.u[2]; f.v[0] = -f.v[0]; f.v[2] = -f.v[2]; }
            place(f, cx, cy, cz, pivY, pivZ, ang);
            faces.add(f);
        }
    }

    // local -> posed (swing about pivot) -> world yaw/pitch
    private void place(Face f, float cx, float cy, float cz, float pivY, float pivZ, float ang) {
        float[] a = {f.p[0] + cx, f.p[1] + cy, f.p[2] + cz};
        float[] b = {a[0] + f.u[0], a[1] + f.u[1], a[2] + f.u[2]};
        float[] c = {a[0] + f.v[0], a[1] + f.v[1], a[2] + f.v[2]};
        float[][] pts = {a, b, c};
        float ca = (float) Math.cos(Math.toRadians(ang)), sa = (float) Math.sin(Math.toRadians(ang));
        float cy2 = (float) Math.cos(Math.toRadians(yaw)), sy = (float) Math.sin(Math.toRadians(yaw));
        float cp = (float) Math.cos(Math.toRadians(pitch)), sp = (float) Math.sin(Math.toRadians(pitch));
        for (float[] q : pts) {
            float y = q[1] - pivY, z = q[2] - pivZ;
            float y1 = y * ca - z * sa, z1 = y * sa + z * ca;      // swing about X
            q[1] = y1 + pivY; q[2] = z1 + pivZ;
            float x2 = q[0] * cy2 + q[2] * sy, z2 = -q[0] * sy + q[2] * cy2;   // yaw
            float y3 = (q[1] - 16) * cp - z2 * sp, z3 = (q[1] - 16) * sp + z2 * cp; // pitch around centre
            q[0] = x2; q[1] = y3; q[2] = z3;
        }
        f.p = a; f.u = new float[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]}; f.v = new float[]{c[0] - a[0], c[1] - a[1], c[2] - a[2]};
        f.depth = a[2] + (f.u[2] + f.v[2]) / 2;
    }

    private void build() {
        faces.clear();
        if (skin == null) return;
        if (autoSpin && !drag && System.currentTimeMillis() - lastTouch > 2500) yaw += 1.8f;
        float t = (System.currentTimeMillis() % 100000) / 1000f;
        float sw = (float) Math.sin(t * 2.2) * 14;
        float aw = slim ? 3 : 4;
        float ax = 4 + aw / 2;
        Bitmap k = skin;
        addBox(k, "s", 0, 16, 4, 12, 4, 0, -2, 6, 0, 12, 0, -sw, false);
        addBox(k, "s", 16, 48, 4, 12, 4, 0, 2, 6, 0, 12, 0, sw, false);
        addBox(k, "s", 16, 16, 8, 12, 4, 0, 0, 18, 0, 0, 0, 0, false);
        addBox(k, "s", 40, 16, aw, 12, 4, 0, -ax, 18, 0, 24, 0, sw, false);
        addBox(k, "s", 32, 48, aw, 12, 4, 0, ax, 18, 0, 24, 0, -sw, false);
        addBox(k, "s", 0, 0, 8, 8, 8, 0, 0, 28, 0, 0, 0, 0, false);
        addBox(k, "s", 0, 32, 4, 12, 4, 1, -2, 6, 0, 12, 0, -sw, false);
        addBox(k, "s", 0, 48, 4, 12, 4, 1, 2, 6, 0, 12, 0, sw, false);
        addBox(k, "s", 16, 32, 8, 12, 4, 1, 0, 18, 0, 0, 0, 0, false);
        addBox(k, "s", 40, 32, aw, 12, 4, 1, -ax, 18, 0, 24, 0, sw, false);
        addBox(k, "s", 48, 48, aw, 12, 4, 1, ax, 18, 0, 24, 0, -sw, false);
        addBox(k, "s", 32, 0, 8, 8, 8, 1, 0, 28, 0, 0, 0, 0, false);
        if (cape != null && cape.getWidth() >= 22 && cape.getHeight() >= 17) {
            addBox(cape, "c", 0, 0, 10, 16, 1, 0, 0, 16, -2.6f, 24, -2, 6 + Math.abs(sw) * 0.5f, true);
        }
        Collections.sort(faces, new Comparator<Face>() {
            public int compare(Face a, Face b) { return Float.compare(a.depth, b.depth); }
        });
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); invalidate(); }
    @Override protected void onWindowVisibilityChanged(int v) { super.onWindowVisibilityChanged(v); if (v == View.VISIBLE) invalidate(); }
    @Override protected void onVisibilityChanged(View w, int v) { super.onVisibilityChanged(w, v); if (v == View.VISIBLE) invalidate(); }

    @Override protected void onDraw(Canvas c) {
        try { draw3d(c); } catch (Throwable t) { }
        // ~30 fps, and nothing at all while the view is hidden / the game is running on top
        if (isShown() && getWindowVisibility() == View.VISIBLE) postInvalidateDelayed(33);
    }

    private void draw3d(Canvas c) {
        beforeModel(c);
        build();
        float s = Math.min(getHeight() / 38f, getWidth() / 24f) * scaleMul;
        float ox = getWidth() / 2f, oy = getHeight() * cyFrac;
        for (Face f : faces) {
            // outward normal = V x U ; draw only faces looking at the viewer (+z)
            float nz = f.v[0] * f.u[1] - f.v[1] * f.u[0];
            if (nz <= 0) continue;
            int bw = f.bmp.getWidth(), bh = f.bmp.getHeight();
            src[0] = 0; src[1] = 0; src[2] = bw; src[3] = 0; src[4] = 0; src[5] = bh;
            dst[0] = ox + f.p[0] * s;               dst[1] = oy - f.p[1] * s;
            dst[2] = ox + (f.p[0] + f.u[0]) * s;    dst[3] = oy - (f.p[1] + f.u[1]) * s;
            dst[4] = ox + (f.p[0] + f.v[0]) * s;    dst[5] = oy - (f.p[1] + f.v[1]) * s;
            mx.setPolyToPoly(src, 0, dst, 0, 3);
            c.drawBitmap(f.bmp, mx, paint);
        }
        afterModel(c);
    }
}
