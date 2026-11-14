package rl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/** Static pixel-art night forest, drawn once into a tiny bitmap and scaled up (no per-frame work). */
public class ForestView extends View {
    private Bitmap bmp;
    private final Paint paint = new Paint();

    public ForestView(Context c) { super(c); init(); }
    public ForestView(Context c, AttributeSet a) { super(c, a); init(); }
    private void init() { paint.setFilterBitmap(false); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        try {
            int px = Math.max(3, Math.round(getResources().getDisplayMetrics().density * 2.5f));
            int bw = Math.max(16, w / px), bh = Math.max(16, h / px);
            Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            int[] sky = {0xff0e0b1f, 0xff231450, 0xff3a1d7a};
            for (int y = 0; y < bh; y++) {
                float t = y / (float) bh;
                int a = t < 0.55f ? sky[0] : sky[1], z = t < 0.55f ? sky[1] : sky[2];
                float k = t < 0.55f ? t / 0.55f : (t - 0.55f) / 0.45f;
                p.setColor(mix(a, z, k)); c.drawRect(0, y, bw, y + 1, p);
            }
            Random r = new Random(7);
            p.setColor(0xffc4b5fd);
            for (int i = 0; i < bw * bh / 400; i++) { int x = r.nextInt(bw), y = r.nextInt(bh / 2); c.drawRect(x, y, x + 1, y + 1, p); }
            p.setColor(0xfff5f3ff); c.drawRect(bw * 0.78f, bh * 0.12f, bw * 0.78f + 5, bh * 0.12f + 5, p);   // moon
            p.setColor(0xff3a1d7a); c.drawRect(bw * 0.78f + 3, bh * 0.12f - 1, bw * 0.78f + 6, bh * 0.12f + 3, p);
            layer(c, p, r, bw, bh, 0.62f, 0xff2a1763, 7, 16);
            layer(c, p, r, bw, bh, 0.74f, 0xff1b0f45, 9, 22);
            layer(c, p, r, bw, bh, 0.88f, 0xff120a2d, 11, 28);
            p.setColor(0xff0b0720); c.drawRect(0, bh * 0.95f, bw, bh, p);
            bmp = b;
        } catch (Throwable t) { bmp = null; }
    }

    private static void layer(Canvas c, Paint p, Random r, int bw, int bh, float base, int col, int minH, int maxH) {
        p.setColor(col);
        int x = -3;
        while (x < bw) {
            int h = minH + r.nextInt(maxH - minH), half = Math.max(2, h / 3);
            int top = (int) (bh * base) - h;
            c.drawRect(x + half - 1, (int) (bh * base) - 3, x + half + 1, bh, p);   // trunk
            for (int i = 0; i < h; i++) {                                          // stepped pine
                int w = 1 + (i * half) / h * 1;
                int ww = Math.max(1, (i * half * 2) / h);
                c.drawRect(x + half - ww / 2f, top + i, x + half + ww / 2f + 1, top + i + 1, p);
            }
            c.drawRect(x, (int) (bh * base), x + half * 2 + 2, bh, p);
            x += half * 2 + 1 + r.nextInt(3);
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xff000000 | r << 16 | g << 8 | bl;
    }

    @Override protected void onDraw(Canvas c) {
        if (bmp == null) { c.drawColor(0xff0e0b1f); return; }
        c.drawBitmap(bmp, null, new android.graphics.Rect(0, 0, getWidth(), getHeight()), paint);
    }
}
