package com.robinui.launcher;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/** Lightweight paged scroller with snap + soft parallax. No per-frame allocations. */
public class PagerView extends HorizontalScrollView {
    public interface Listener { void onPage(int page, int count); }
    final LinearLayout holder;
    int page, pageW;
    VelocityTracker vt;
    ObjectAnimator anim;
    Listener listener;
    final int minFling;

    public PagerView(Context c) {
        super(c);
        holder = new LinearLayout(c);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        addView(holder, new LayoutParams(-2, -1));
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipChildren(false);
        minFling = ViewConfiguration.get(c).getScaledMinimumFlingVelocity() * 4;
    }

    public void setListener(Listener l) { listener = l; }

    public void setPages(java.util.List<View> pages) {
        holder.removeAllViews();
        for (View v : pages) holder.addView(v, new LinearLayout.LayoutParams(Math.max(pageW, 1), -1));
        page = Math.min(page, Math.max(0, pages.size() - 1));
        post(new Runnable() { public void run() { scrollTo(page * pageW, 0); transform(); notifyPage(); } });
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        pageW = w;
        for (int i = 0; i < holder.getChildCount(); i++) holder.getChildAt(i).getLayoutParams().width = w;
        holder.requestLayout();
        scrollTo(page * w, 0);
    }

    int count() { return holder.getChildCount(); }

    void notifyPage() { if (listener != null) listener.onPage(page, count()); }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN && anim != null) anim.cancel();
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            vt.computeCurrentVelocity(1000);
            float vx = vt.getXVelocity();
            vt.recycle(); vt = null;
            int target = Math.round(getScrollX() / (float) Math.max(pageW, 1));
            if (vx < -minFling) target = (int) Math.ceil(getScrollX() / (float) pageW - 0.001f) ;
            else if (vx > minFling) target = (int) Math.floor(getScrollX() / (float) pageW + 0.001f);
            goTo(target, true);
            return true;
        }
        return super.onTouchEvent(e);
    }

    public void goTo(int p, boolean animate) {
        p = Math.max(0, Math.min(p, count() - 1));
        page = p;
        if (anim != null) anim.cancel();
        if (animate) {
            anim = ObjectAnimator.ofInt(this, "scrollX", getScrollX(), p * pageW);
            anim.setDuration(300);
            anim.setInterpolator(new DecelerateInterpolator(1.6f));
            anim.start();
        } else scrollTo(p * pageW, 0);
        notifyPage();
    }

    @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        transform();
    }

    void transform() {
        if (pageW <= 0) return;
        int sx = getScrollX();
        for (int i = 0; i < holder.getChildCount(); i++) {
            View v = holder.getChildAt(i);
            float off = (sx - i * pageW) / (float) pageW;
            float ab = Math.min(1f, Math.abs(off));
            v.setAlpha(1f - 0.55f * ab);
            float sc = 1f - 0.05f * ab;
            v.setScaleX(sc); v.setScaleY(sc);
        }
    }
}
