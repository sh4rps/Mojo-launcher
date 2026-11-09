package com.robin.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small custom views: icon cell, icon grid page, pager, page dots. */
public final class Views {

    /** App icon + label with spring press feedback. */
    public static class Cell extends LinearLayout {
        public final ImageView iv;
        public final TextView tv;
        public Apps.Info app;

        public Cell(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(0, U.dp(6), 0, U.dp(2));
            iv = new ImageView(c);
            addView(iv, new LayoutParams(U.dp(58), U.dp(58)));
            tv = new TextView(c);
            tv.setTextSize(11);
            tv.setTextColor(0xFFFFFFFF);
            tv.setShadowLayer(U.dp(2), 0, U.dp(1), 0x99000000);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine();
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setPadding(U.dp(2), U.dp(5), U.dp(2), 0);
            addView(tv, new LayoutParams(-1, -2));
            setClickable(true);
            setLongClickable(true);
        }

        public void bind(Apps.Info a, boolean labelOn, int labelColor) {
            app = a;
            iv.setImageBitmap(Apps.icon(getContext(), a));
            tv.setText(a.label);
            tv.setVisibility(labelOn ? VISIBLE : GONE);
            tv.setTextColor(labelColor);
            if (labelColor != 0xFFFFFFFF) tv.setShadowLayer(0, 0, 0, 0);
            else tv.setShadowLayer(U.dp(2), 0, U.dp(1), 0x99000000);
            setScaleX(1f); setScaleY(1f);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) animate().scaleX(0.88f).scaleY(0.88f).setDuration(90).start();
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) animate().scaleX(1f).scaleY(1f).setInterpolator(U.SPRING).setDuration(340).start();
            return super.onTouchEvent(e);
        }
    }

    /** One home page: equal cells, cols x rows. */
    public static class Grid extends ViewGroup {
        int cols, rows;
        public Grid(Context c, int cols, int rows) { super(c); this.cols = cols; this.rows = rows; }

        @Override protected void onMeasure(int wms, int hms) {
            int w = MeasureSpec.getSize(wms), h = MeasureSpec.getSize(hms);
            int cw = w / cols, ch = h / rows;
            for (int i = 0; i < getChildCount(); i++)
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
            setMeasuredDimension(w, h);
        }

        @Override protected void onLayout(boolean ch, int l, int t, int r, int b) {
            int cw = (r - l) / cols, chh = (b - t) / rows;
            for (int i = 0; i < getChildCount(); i++) {
                int cx = (i % cols) * cw, cy = (i / cols) * chh;
                getChildAt(i).layout(cx, cy, cx + cw, cy + chh);
            }
        }
    }

    /** Horizontal pager driven externally (one gesture owner), with parallax-ish scale/alpha. */
    public static class Pager extends FrameLayout {
        public float pos;
        public int count;
        ValueAnimator anim;
        public Runnable onPos;

        public Pager(Context c) { super(c); setClipChildren(false); }

        public void setPos(float p) {
            pos = Math.max(-0.25f, Math.min(Math.max(0, getChildCount() - 1) + 0.25f, p));
            int w = getWidth();
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                float d = i - pos;
                float ad = Math.min(1f, Math.abs(d));
                v.setTranslationX(d * w);
                v.setAlpha(1f - 0.55f * ad);
                float s = 1f - 0.06f * ad;
                v.setScaleX(s); v.setScaleY(s);
                v.setVisibility(ad >= 1f ? INVISIBLE : VISIBLE);
            }
            if (onPos != null) onPos.run();
        }

        public void settle(float velocityX) {
            int n = Math.max(1, getChildCount());
            int target = Math.round(pos - velocityX / Math.max(1, getWidth()) * 0.45f);
            target = Math.max(0, Math.min(n - 1, target));
            snap(target);
        }

        public void snap(int target) {
            if (anim != null) anim.cancel();
            final float from = pos, to = target;
            anim = ValueAnimator.ofFloat(0, 1);
            anim.setDuration(380);
            anim.setInterpolator(U.SPRING);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { setPos(from + (to - from) * (Float) a.getAnimatedValue()); }
            });
            anim.start();
        }

        @Override protected void onLayout(boolean c, int l, int t, int r, int b) {
            super.onLayout(c, l, t, r, b);
            setPos(pos);
        }
    }

    public static class Dots extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public int count;
        public float pos;
        public Dots(Context c) { super(c); }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), U.dp(14)); }

        @Override protected void onDraw(Canvas c) {
            if (count <= 1) return;
            float gap = U.dp(14), r = U.dp(3);
            float x0 = getWidth() / 2f - (count - 1) * gap / 2f, y = getHeight() / 2f;
            for (int i = 0; i < count; i++) {
                float a = Math.max(0f, 1f - Math.abs(i - pos));
                p.setColor(0xFFFFFFFF);
                p.setAlpha((int) (90 + 165 * a));
                c.drawCircle(x0 + i * gap, y, r * (1f + 0.45f * a), p);
            }
        }
    }
}
