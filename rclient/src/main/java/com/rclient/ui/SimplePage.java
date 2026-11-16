package com.rclient.ui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;

/** A titled page of text lines and action buttons, drawn in the RClient style. */
public abstract class SimplePage extends RScreen {
    protected record Btn(String label, Runnable run, boolean primary) { }
    protected final List<Btn> buttons = new ArrayList<>();
    protected final List<String> lines = new ArrayList<>();
    protected String heading;

    private int px, py, pw, ph;
    private final List<int[]> rects = new ArrayList<>(); // x,y,w for each button (+ back last)

    protected SimplePage(String title, Screen parent) { super(title, parent); heading = title; }

    protected abstract void build();

    @Override protected void init() { buttons.clear(); lines.clear(); build(); }

    private void layout() {
        rects.clear();
        int n = buttons.size() + 1;
        int textH = 34 + lines.size() * 11 + 8;
        int bh = 16, step = 19;
        int cols = 1;
        if (textH + n * step + 8 > height - 8) cols = 2;
        pw = cols == 1 ? Math.min(260, width - 24) : Math.min(400, width - 24);
        int rows = (n + cols - 1) / cols;
        ph = textH + rows * step + 8;
        px = (width - pw) / 2; py = Math.max(4, (height - ph) / 2);
        int colW = (pw - 28 - (cols - 1) * 8) / cols;
        for (int i = 0; i < n; i++) {
            int c = i % cols, r = i / cols;
            rects.add(new int[]{px + 14 + c * (colW + 8), py + textH + r * step, colW});
        }
    }

    @Override protected void paint(DrawContext g, int mx, int my, float t) {
        layout();
        Ui.backdrop(g, width, height, t);
        Ui.round(g, px, py, pw, ph, 0xEE14141D);
        Ui.outline(g, px, py, pw, ph, 0xFF2A2A3C);
        Ui.text(g, heading, px + 14, py + 10, Ui.TEXT);
        Ui.rect(g, px + 14, py + 22, 24, 2, Ui.ACCENT);
        int ly = py + 30;
        for (String l : lines) { Ui.text(g, Ui.fit(l, pw - 28), px + 14, ly, Ui.MUTED); ly += 11; }
        for (int i = 0; i < rects.size(); i++) {
            int[] r = rects.get(i);
            boolean back = i == rects.size() - 1;
            Ui.button(g, r[0], r[1], r[2], 16, back ? "Back" : buttons.get(i).label, Ui.in(mx, my, r[0], r[1], r[2], 16), !back && buttons.get(i).primary);
        }
    }

    @Override public boolean mouseClicked(Click c, boolean d) {
        layout();
        for (int i = 0; i < rects.size(); i++) {
            int[] r = rects.get(i);
            if (hit(c, r[0], r[1], r[2], 16)) {
                if (i == rects.size() - 1) close(); else buttons.get(i).run.run();
                return true;
            }
        }
        return false;
    }
}
