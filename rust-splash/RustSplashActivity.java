package rl;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;

public class RustSplashActivity extends Activity {
    static final long TOTAL = 5000L;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean done;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = 1;
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        setContentView(new GlassView(this));
        h.postDelayed(new Runnable() { public void run() { go(); } }, TOTAL);
    }

    private void go() {
        if (done) return;
        done = true;
        try {
            Intent i = new Intent();
            i.setClassName(getPackageName(), "git.artdeell.mojo.TestStorageActivity");
            i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(i);
        } catch (Throwable t) { }
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy(); }

    @Override
    public void onBackPressed() { /* ignore during splash */ }

    static class GlassView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final long t0 = System.currentTimeMillis();
        Drawable logo;
        final float[][] blobs = {
            // x, y, radius(rel), speed, phase, color
            {0.20f, 0.30f, 0.55f, 0.00045f, 0.0f},
            {0.80f, 0.25f, 0.50f, 0.00038f, 2.1f},
            {0.55f, 0.85f, 0.60f, 0.00050f, 4.2f},
            {0.15f, 0.80f, 0.40f, 0.00042f, 1.3f},
        };
        final int[] cols = {0xFFE8621B, 0xFFB8350D, 0xFFFF9A3C, 0xFF7A2410};
        final RectF r = new RectF();
        final Path clip = new Path();
        final AccelerateDecelerateInterpolator ip = new AccelerateDecelerateInterpolator();

        GlassView(Context c) {
            super(c);
            try {
                int id = c.getResources().getIdentifier("ic_launcher", "mipmap", c.getPackageName());
                logo = c.getResources().getDrawable(id);
            } catch (Throwable t) { logo = null; }
        }

        @Override
        protected void onDraw(Canvas cv) {
            long el = System.currentTimeMillis() - t0;
            float prog = Math.min(1f, el / (float) TOTAL);
            int w = getWidth(), hh = getHeight();
            float m = Math.min(w, hh);

            // background
            p.setShader(new LinearGradient(0, 0, w, hh, 0xFF0B0705, 0xFF1C0E08, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, hh, p);

            // liquid blobs
            for (int i = 0; i < blobs.length; i++) {
                float[] b = blobs[i];
                float ph = b[4] + el * b[3] * 6.2832f;
                float cx = w * (b[0] + 0.12f * (float) Math.sin(ph));
                float cy = hh * (b[1] + 0.14f * (float) Math.cos(ph * 0.8f));
                float rad = m * b[2] * (1f + 0.08f * (float) Math.sin(ph * 1.7f));
                p.setShader(new RadialGradient(cx, cy, rad, (cols[i] & 0x00FFFFFF) | 0x88000000, 0x00000000, Shader.TileMode.CLAMP));
                cv.drawCircle(cx, cy, rad, p);
            }
            p.setShader(null);

            // entrance
            float in = ip.getInterpolation(Math.min(1f, el / 900f));
            float out = el > TOTAL - 350 ? (TOTAL - el) / 350f : 1f;
            if (out < 0) out = 0;
            float alpha = in * out;

            // glass card
            float cw = Math.min(m * 0.92f, w * 0.7f), ch = cw * 0.62f;
            float cx = w / 2f, cy = hh / 2f - ch * 0.05f + (1f - in) * 30f;
            r.set(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2);
            float rad = ch * 0.16f;
            p.setStyle(Paint.Style.FILL);
            p.setShadowLayer(40, 0, 14, ((int) (90 * alpha) << 24));
            p.setColor(((int) (46 * alpha) << 24) | 0xFFFFFF);
            cv.drawRoundRect(r, rad, rad, p);
            p.clearShadowLayer();
            // gloss on top half
            cv.save();
            clip.reset(); clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            cv.clipPath(clip);
            p.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                    ((int) (70 * alpha) << 24) | 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawRect(r.left, r.top, r.right, r.centerY(), p);
            // moving shine sweep
            float sw = ((el % 2200) / 2200f);
            float sx = r.left - cw * 0.4f + sw * cw * 1.8f;
            p.setShader(new LinearGradient(sx, 0, sx + cw * 0.35f, 0,
                    new int[]{0x00FFFFFF, ((int) (48 * alpha) << 24) | 0xFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            cv.drawRect(r, p);
            cv.restore();
            p.setShader(null);
            // rim light
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f, m * 0.003f));
            p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                    ((int) (170 * alpha) << 24) | 0xFFFFFF, ((int) (30 * alpha) << 24) | 0xFFFFFF, Shader.TileMode.CLAMP));
            cv.drawRoundRect(r, rad, rad, p);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);

            // logo
            float ls = ch * 0.46f;
            float lcy = r.top + ch * 0.36f;
            float bob = (float) Math.sin(el * 0.003f) * ls * 0.03f;
            if (logo != null) {
                logo.setBounds((int) (cx - ls / 2), (int) (lcy - ls / 2 + bob), (int) (cx + ls / 2), (int) (lcy + ls / 2 + bob));
                logo.setAlpha((int) (255 * alpha));
                logo.draw(cv);
            }

            // title
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            p.setTextSize(ch * 0.115f);
            p.setLetterSpacing(0.12f);
            p.setColor(((int) (255 * alpha) << 24) | 0xFFFFFF);
            cv.drawText("RUST LAUNCHER", cx, r.top + ch * 0.74f, p);
            p.setLetterSpacing(0f);

            // status text
            String[] st = {"Starting engine", "Preparing renderer", "Loading versions", "Almost ready"};
            int si = Math.min(st.length - 1, (int) (prog * st.length));
            p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            p.setTextSize(ch * 0.07f);
            p.setColor(((int) (170 * alpha) << 24) | 0xFFFFFF);
            int dots = (int) (el / 350) % 4;
            cv.drawText(st[si] + "...".substring(0, dots), cx, r.top + ch * 0.84f, p);

            // progress bar (glass pill)
            float bw = cw * 0.64f, bh = Math.max(6f, ch * 0.028f);
            float bx = cx - bw / 2, by = r.top + ch * 0.90f;
            r.set(bx, by, bx + bw, by + bh);
            p.setColor(((int) (50 * alpha) << 24) | 0xFFFFFF);
            cv.drawRoundRect(r, bh, bh, p);
            float pe = ip.getInterpolation(prog);
            r.set(bx, by, bx + bw * pe, by + bh);
            p.setShader(new LinearGradient(bx, 0, bx + bw, 0, 0xFFFF9A3C, 0xFFE8621B, Shader.TileMode.CLAMP));
            p.setAlpha((int) (255 * alpha));
            cv.drawRoundRect(r, bh, bh, p);
            p.setShader(null);

            if (el < TOTAL + 100) postInvalidateOnAnimation();
        }
    }
}
