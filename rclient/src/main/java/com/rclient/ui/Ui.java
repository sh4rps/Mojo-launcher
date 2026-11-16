package com.rclient.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class Ui {
    public static final int BG = 0xFF0C0C12, PANEL = 0xFF14141D, CARD = 0xFF1B1B27, CARD_HOVER = 0xFF232334,
            ACCENT = 0xFF8B5CF6, ACCENT2 = 0xFF3B82F6, TEXT = 0xFFE5E7EB, MUTED = 0xFF8A8FA3, OFF = 0xFF3A3A4D,
            GOOD = 0xFF34D399, BAD = 0xFFF87171;
    public static final int[] SWATCHES = {0xFFFFFFFF, 0xFFF87171, 0xFFFB923C, 0xFFFACC15, 0xFF34D399, 0xFF22D3EE, 0xFF3B82F6, 0xFF8B5CF6, 0xFFEC4899, 0xFF9CA3AF};

    private Ui() { }

    public static void rect(DrawContext g, int x, int y, int w, int h, int c) { g.fill(x, y, x + w, y + h, c); }

    public static void round(DrawContext g, int x, int y, int w, int h, int c) {
        if (w < 3 || h < 3) { rect(g, x, y, w, h, c); return; }
        g.fill(x + 1, y, x + w - 1, y + h, c);
        g.fill(x, y + 1, x + 1, y + h - 1, c);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    public static void outline(DrawContext g, int x, int y, int w, int h, int c) {
        g.fill(x + 1, y, x + w - 1, y + 1, c);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, c);
        g.fill(x, y + 1, x + 1, y + h - 1, c);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    public static void grad(DrawContext g, int x, int y, int w, int h, int top, int bottom) {
        g.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    public static int width(String s) { return MinecraftClient.getInstance().textRenderer.getWidth(s); }

    public static void text(DrawContext g, String s, int x, int y, int c) {
        g.drawText(MinecraftClient.getInstance().textRenderer, s, x, y, c, false);
    }
    public static void textShadow(DrawContext g, String s, int x, int y, int c) {
        g.drawText(MinecraftClient.getInstance().textRenderer, s, x, y, c, true);
    }
    public static void center(DrawContext g, String s, int cx, int y, int c) { text(g, s, cx - width(s) / 2, y, c); }

    public static String fit(String s, int maxW) {
        var tr = MinecraftClient.getInstance().textRenderer;
        if (tr.getWidth(s) <= maxW) return s;
        return tr.trimToWidth(s, Math.max(0, maxW - tr.getWidth("..."))) + "...";
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int ar = a >> 24 & 255, rr = a >> 16 & 255, rg = a >> 8 & 255, rb = a & 255;
        int br = b >> 24 & 255, cr = b >> 16 & 255, cg = b >> 8 & 255, cb = b & 255;
        return ((int) (ar + (br - ar) * t) << 24) | ((int) (rr + (cr - rr) * t) << 16) | ((int) (rg + (cg - rg) * t) << 8) | (int) (rb + (cb - rb) * t);
    }

    public static int alpha(int c, float a) { return ((int) (255 * Math.max(0, Math.min(1, a))) << 24) | (c & 0xFFFFFF); }

    /** Small pill-shaped toggle. t = 0..1 animation progress. */
    public static void toggle(DrawContext g, int x, int y, float t) {
        round(g, x, y, 22, 11, lerp(OFF, ACCENT, t));
        int kx = x + 1 + Math.round(t * 11);
        round(g, kx, y + 1, 9, 9, 0xFFFFFFFF);
    }

    public static void button(DrawContext g, int x, int y, int w, int h, String label, boolean hover, boolean primary) {
        int bg = primary ? (hover ? 0xFF9D74F8 : ACCENT) : (hover ? CARD_HOVER : CARD);
        round(g, x, y, w, h, bg);
        if (!primary) outline(g, x, y, w, h, hover ? ACCENT : 0xFF2A2A3C);
        center(g, label, x + w / 2, y + (h - 8) / 2, TEXT);
    }

    public static float anim(float cur, float target, float speed) {
        float d = target - cur;
        if (Math.abs(d) < 0.01f) return target;
        return cur + d * Math.min(1f, speed);
    }

    public static float now() { return (System.nanoTime() / 1_000_000L % 100_000_000L) / 1000f; }

    public static void scaledText(DrawContext g, String s, int x, int y, float scale, int c) {
        var m = g.getMatrices();
        m.pushMatrix(); m.translate(x, y); m.scale(scale, scale);
        g.drawText(MinecraftClient.getInstance().textRenderer, s, 0, 0, c, true);
        m.popMatrix();
    }

    /** Animated dark backdrop with drifting purple/blue particles. */
    public static void backdrop(DrawContext g, int w, int h, float t) {
        grad(g, 0, 0, w, h, 0xFF08080D, 0xFF12101F);
        int gx = (int) (w * (0.25 + 0.1 * Math.sin(t * 0.3))), gy = (int) (h * 0.35);
        for (int i = 0; i < 6; i++) {
            int r = 30 + i * 22;
            g.fill(gx - r, gy - r / 2, gx + r, gy + r / 2, alpha(ACCENT, 0.025f));
        }
        int bx = (int) (w * (0.8 + 0.08 * Math.cos(t * 0.25))), by = (int) (h * 0.7);
        for (int i = 0; i < 6; i++) {
            int r = 26 + i * 20;
            g.fill(bx - r, by - r / 2, bx + r, by + r / 2, alpha(ACCENT2, 0.025f));
        }
        for (int i = 0; i < 46; i++) {
            float sp = 4 + (i % 5) * 3;
            float x = ((i * 97 % Math.max(1, w)) + t * sp) % w;
            float y = (h + (i * 57 % Math.max(1, h)) - t * (3 + (i % 4) * 2) % h) % h;
            float a = 0.15f + 0.35f * (0.5f + 0.5f * (float) Math.sin(t + i));
            int sz = i % 3 == 0 ? 2 : 1;
            g.fill((int) x, (int) y, (int) x + sz, (int) y + sz, alpha(i % 2 == 0 ? ACCENT : ACCENT2, a));
        }
    }
}
