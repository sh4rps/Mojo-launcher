package com.robin.packsed;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Zoomable pixel canvas: pen, eraser, fill, picker, line, X-mirror, grid, onion skin, undo/redo, animation preview. */
final class PixelView extends View {
    static final int PEN = 0, ERASER = 1, FILL = 2, PICK = 3, LINE = 4;

    interface Listener { void onEdited(); void onPicked(int color); }

    Model.Tex tex;
    int frame = 0;
    int tool = PEN;
    int color = 0xFF000000;
    boolean mirror, grid = true, onion = true;
    Listener listener;

    // preview (playback)
    boolean playing;
    int playA, playB; float playFrac;

    private Bitmap bmp, bmpB, onionBmp;
    private final Paint pix = new Paint(), gridP = new Paint(), checkA = new Paint(), ovl = new Paint();
    private final Paint boundsP = new Paint();
    private Bitmap checkBmp;
    private float scale = 1, offX, offY;
    private boolean fitted;
    private final ScaleGestureDetector sgd;
    private float lastCx, lastCy;
    private int lastPointers;
    private boolean drawing, changed, multi;
    private int[] snapshot;
    private int lx, ly, sx, sy;   // last/start pixel
    private int curX = -1, curY = -1; // line end for preview

    private static final class Undo { int frame; int[] data; Undo(int f, int[] d) { frame = f; data = d; } }
    private final ArrayDeque<Undo> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    PixelView(Context c) {
        super(c);
        pix.setFilterBitmap(false); pix.setAntiAlias(false);
        gridP.setColor(0x33000000); gridP.setStrokeWidth(1f);
        ovl.setFilterBitmap(false);
        boundsP.setStyle(Paint.Style.STROKE); boundsP.setColor(0xFF8B5CF6); boundsP.setStrokeWidth(3f);
        checkBmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) checkBmp.setPixel(x, y, ((x / 8 + y / 8) & 1) == 0 ? 0xFF3A3A48 : 0xFF2C2C38);
        checkA.setShader(new BitmapShader(checkBmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        sgd = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float f = d.getScaleFactor();
                float ns = Math.max(minScale(), Math.min(80f * getResources().getDisplayMetrics().density, scale * f));
                f = ns / scale;
                offX = d.getFocusX() - (d.getFocusX() - offX) * f;
                offY = d.getFocusY() - (d.getFocusY() - offY) * f;
                scale = ns;
                invalidate();
                return true;
            }
        });
    }

    private float minScale() { return tex == null ? 1 : Math.max(2f, Math.min(getWidth(), getHeight()) / (float) tex.size * 0.4f); }

    void setTex(Model.Tex t, int f) {
        tex = t; frame = Math.max(0, Math.min(f, t.frames.size() - 1)); fitted = false;
        undo.clear(); redo.clear(); refresh(); invalidate();
    }

    void setFrame(int f) { frame = Math.max(0, Math.min(f, tex.frames.size() - 1)); refresh(); invalidate(); }

    void fit() {
        if (tex == null || getWidth() == 0) return;
        scale = Math.min(getWidth(), getHeight()) / (float) tex.size * 0.94f;
        offX = (getWidth() - scale * tex.size) / 2f;
        offY = (getHeight() - scale * tex.size) / 2f;
        fitted = true;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { fitted = false; }

    private Bitmap toBmp(int[] data, Bitmap reuse) {
        int s = tex.size;
        Bitmap b = (reuse != null && reuse.getWidth() == s && reuse.getHeight() == s) ? reuse : Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        b.setPixels(data, 0, s, 0, 0, s, s);
        return b;
    }

    void refresh() {
        if (tex == null) return;
        bmp = toBmp(tex.frames.get(frame), bmp);
        onionBmp = (frame > 0) ? toBmp(tex.frames.get(frame - 1), onionBmp) : null;
    }

    /** Preview animation state. */
    void setPlayback(boolean on, int a, int b, float frac) {
        playing = on; playA = a; playB = b; playFrac = frac;
        if (on && tex != null) { bmp = toBmp(tex.frames.get(a), bmp); bmpB = toBmp(tex.frames.get(b), bmpB); }
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        c.drawColor(0xFF15151E);
        if (tex == null || bmp == null) return;
        if (!fitted) fit();
        float s = tex.size * scale;
        RectF dst = new RectF(offX, offY, offX + s, offY + s);
        c.drawRect(dst, checkA);
        c.drawBitmap(bmp, null, dst, pix);
        if (playing) {
            if (tex.interpolate && playB != playA && bmpB != null) { ovl.setAlpha((int) (255 * playFrac)); c.drawBitmap(bmpB, null, dst, ovl); }
        } else {
            if (onion && onionBmp != null) { ovl.setAlpha(70); c.drawBitmap(onionBmp, null, dst, ovl); }
            if (drawing && tool == LINE && curX >= 0) {
                Paint lp = new Paint(); lp.setColor(color);
                for (int[] p : line(sx, sy, curX, curY)) c.drawRect(offX + p[0] * scale, offY + p[1] * scale, offX + (p[0] + 1) * scale, offY + (p[1] + 1) * scale, lp);
            }
            if (grid && scale >= 7) {
                int n = tex.size;
                float[] pts = new float[(n + 1) * 8];
                int k = 0;
                for (int i = 0; i <= n; i++) {
                    pts[k++] = offX + i * scale; pts[k++] = offY; pts[k++] = offX + i * scale; pts[k++] = offY + s;
                    pts[k++] = offX; pts[k++] = offY + i * scale; pts[k++] = offX + s; pts[k++] = offY + i * scale;
                }
                c.drawLines(pts, 0, k, gridP);
            }
            if (mirror) { Paint m = new Paint(); m.setColor(0x88F87171); m.setStrokeWidth(2f); c.drawLine(offX + s / 2, offY, offX + s / 2, offY + s, m); }
        }
        c.drawRect(dst, boundsP);
    }

    private int px(float x) { return (int) Math.floor((x - offX) / scale); }
    private int py(float y) { return (int) Math.floor((y - offY) / scale); }
    private boolean in(int x, int y) { return x >= 0 && y >= 0 && x < tex.size && y < tex.size; }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (tex == null || playing) return true;
        sgd.onTouchEvent(e);
        int n = e.getPointerCount();
        int act = e.getActionMasked();
        if (n >= 2) {
            if (drawing) cancelStroke();
            multi = true;
            float cx = 0, cy = 0;
            for (int i = 0; i < n; i++) { cx += e.getX(i); cy += e.getY(i); }
            cx /= n; cy /= n;
            if (lastPointers == n && !sgd.isInProgress() || lastPointers == n) { offX += cx - lastCx; offY += cy - lastCy; }
            lastCx = cx; lastCy = cy; lastPointers = n;
            invalidate();
            if (act == MotionEvent.ACTION_POINTER_UP) lastPointers = 0;
            return true;
        }
        lastPointers = 0;
        if (multi) { if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) multi = false; return true; }
        int x = px(e.getX()), y = py(e.getY());
        switch (act) {
            case MotionEvent.ACTION_DOWN:
                if (!in(x, y)) return true;
                if (tool == PICK) { pick(x, y); return true; }
                beginStroke();
                sx = lx = x; sy = ly = y; curX = x; curY = y;
                if (tool == PEN || tool == ERASER) plot(x, y);
                else if (tool == FILL) { fill(x, y); endStroke(); }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!drawing) { if (tool == PICK && in(x, y)) pick(x, y); return true; }
                if (tool == PEN || tool == ERASER) {
                    for (int[] p : line(lx, ly, x, y)) plot(p[0], p[1]);
                    lx = x; ly = y;
                } else if (tool == LINE) { curX = x; curY = y; }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (drawing) {
                    if (tool == LINE) for (int[] p : line(sx, sy, x, y)) plot(p[0], p[1]);
                    endStroke();
                }
                curX = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (drawing) cancelStroke();
                return true;
            default: return true;
        }
    }

    private void pick(int x, int y) {
        int v = tex.frames.get(frame)[y * tex.size + x];
        if ((v >>> 24) != 0) { color = v; if (listener != null) listener.onPicked(v); }
    }

    private void beginStroke() { snapshot = tex.frames.get(frame).clone(); drawing = true; changed = false; }

    private void endStroke() {
        drawing = false;
        if (changed && snapshot != null) {
            undo.push(new Undo(frame, snapshot)); redo.clear();
            while (undo.size() > 60) undo.removeLast();
            if (listener != null) listener.onEdited();
        }
        snapshot = null;
        refresh();
    }

    private void cancelStroke() {
        if (snapshot != null) System.arraycopy(snapshot, 0, tex.frames.get(frame), 0, snapshot.length);
        drawing = false; snapshot = null; curX = -1; refresh(); invalidate();
    }

    private void plot(int x, int y) {
        if (!in(x, y)) return;
        int[] d = tex.frames.get(frame);
        int v = tool == ERASER ? 0 : color;
        set(d, x, y, v);
        if (mirror) set(d, tex.size - 1 - x, y, v);
        refresh();
    }

    private void set(int[] d, int x, int y, int v) {
        int i = y * tex.size + x;
        if (d[i] != v) { d[i] = v; changed = true; }
    }

    private void fill(int x, int y) {
        int[] d = tex.frames.get(frame);
        int n = tex.size, target = d[y * n + x], v = color;
        if (target == v) return;
        ArrayDeque<Integer> st = new ArrayDeque<>();
        st.push(y * n + x);
        while (!st.isEmpty()) {
            int i = st.pop();
            if (d[i] != target) continue;
            d[i] = v; changed = true;
            int cx = i % n, cy = i / n;
            if (cx > 0) st.push(i - 1);
            if (cx < n - 1) st.push(i + 1);
            if (cy > 0) st.push(i - n);
            if (cy < n - 1) st.push(i + n);
        }
        if (mirror) { /* fill is area based; mirror applies to pen/line/eraser only */ }
        refresh();
    }

    private static List<int[]> line(int x0, int y0, int x1, int y1) {
        List<int[]> out = new ArrayList<>();
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            out.add(new int[]{x0, y0});
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
        return out;
    }

    boolean undo() {
        if (undo.isEmpty()) return false;
        Undo u = undo.pop();
        if (u.frame >= tex.frames.size()) return false;
        redo.push(new Undo(u.frame, tex.frames.get(u.frame).clone()));
        System.arraycopy(u.data, 0, tex.frames.get(u.frame), 0, u.data.length);
        frame = u.frame; refresh(); invalidate();
        if (listener != null) listener.onEdited();
        return true;
    }

    boolean redo() {
        if (redo.isEmpty()) return false;
        Undo u = redo.pop();
        if (u.frame >= tex.frames.size()) return false;
        undo.push(new Undo(u.frame, tex.frames.get(u.frame).clone()));
        System.arraycopy(u.data, 0, tex.frames.get(u.frame), 0, u.data.length);
        frame = u.frame; refresh(); invalidate();
        if (listener != null) listener.onEdited();
        return true;
    }

    /** 0 = clear frame, 1 = flip horizontal, 2 = flip vertical. Undoable. */
    void op(int kind) {
        if (tex == null) return;
        int[] d = tex.frames.get(frame), before = d.clone();
        int n = tex.size;
        if (kind == 0) java.util.Arrays.fill(d, 0);
        else for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            int sx2 = kind == 1 ? n - 1 - x : x, sy2 = kind == 2 ? n - 1 - y : y;
            d[y * n + x] = before[sy2 * n + sx2];
        }
        if (!java.util.Arrays.equals(before, d)) {
            undo.push(new Undo(frame, before)); redo.clear();
            if (listener != null) listener.onEdited();
        }
        refresh(); invalidate();
    }

    void clearHistory() { undo.clear(); redo.clear(); }
}
