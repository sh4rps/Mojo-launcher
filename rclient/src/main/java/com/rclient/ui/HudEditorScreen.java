package com.rclient.ui;

import com.rclient.core.Config;
import com.rclient.core.HudModule;
import com.rclient.core.ModuleManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.List;

/** Drag HUD elements anywhere, scroll over one to resize it. Edits are saved to the active profile. */
public class HudEditorScreen extends RScreen {
    private HudModule sel;
    private double offX, offY;
    private boolean dragging;
    private String hint = "Drag to move  •  scroll to resize  •  right-click to hide/show  •  Esc to finish";

    public HudEditorScreen(Screen parent) { super("HUD Editor", parent); }

    private List<HudModule> huds() { return ModuleManager.huds(); }

    @Override protected void paint(DrawContext g, int mx, int my, float t) {
        // dim the world / backdrop behind, then draw a faint grid
        g.fill(0, 0, width, height, mc.world != null ? 0x88000000 : 0xFF0C0C12);
        for (int x = 0; x < width; x += 20) g.fill(x, 0, x + 1, height, 0x10FFFFFF);
        for (int y = 0; y < height; y += 20) g.fill(0, y, width, y + 1, 0x10FFFFFF);
        g.fill(width / 2, 0, width / 2 + 1, height, 0x22FFFFFF);
        g.fill(0, height / 2, width, height / 2 + 1, 0x22FFFFFF);

        for (HudModule h : huds()) {
            if (!h.isEnabled()) continue;
            h.render(g, mc, true);
            int x = h.bx(mc), y = h.by(mc), w = h.sw(), hh = h.sh();
            boolean over = Ui.in(mx, my, x, y, w, hh);
            int c = h == sel ? Ui.ACCENT : over ? Ui.ACCENT2 : 0x66FFFFFF;
            Ui.outline(g, x, y, w, hh, c);
        }
        // bar
        Ui.round(g, width / 2 - 150, height - 24, 300, 18, 0xEE14141D);
        String info = sel != null ? sel.name + "  •  scale " + String.format("%.1f", sel.scale.num()) : hint;
        Ui.center(g, Ui.fit(info, 290), width / 2, height - 19, sel != null ? Ui.TEXT : Ui.MUTED);
        // disabled list (click to enable)
        int y = 6;
        Ui.text(g, "Disabled (click to enable):", 6, y, Ui.MUTED); y += 11;
        for (HudModule h : huds()) {
            if (h.isEnabled()) continue;
            if (y > height - 40) break;
            boolean over = Ui.in(mx, my, 6, y - 1, 110, 10);
            Ui.text(g, Ui.fit(h.name, 108), 6, y, over ? Ui.TEXT : 0xFF6B7080);
            y += 10;
        }
    }

    @Override public boolean mouseClicked(Click c, boolean d) {
        double mx = c.x(), my = c.y();
        // enable from list
        int y = 17;
        for (HudModule h : huds()) {
            if (h.isEnabled()) continue;
            if (y > height - 40) break;
            if (Ui.in(mx, my, 6, y - 1, 110, 10)) { h.setEnabled(true); return true; }
            y += 10;
        }
        List<HudModule> l = huds();
        for (int i = l.size() - 1; i >= 0; i--) {
            HudModule h = l.get(i);
            if (!h.isEnabled()) continue;
            if (Ui.in(mx, my, h.bx(mc), h.by(mc), h.sw(), h.sh())) {
                if (c.button() == 1) { h.setEnabled(false); if (sel == h) sel = null; return true; }
                sel = h; dragging = true;
                offX = mx - h.px(mc); offY = my - h.py(mc);
                return true;
            }
        }
        sel = null;
        return false;
    }

    @Override public boolean mouseDragged(Click c, double dx, double dy) {
        if (!dragging || sel == null) return false;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        double nx = c.x() - offX, ny = c.y() - offY;
        int w = (int) (sel.w * sel.scale.num()), h = (int) (sel.h * sel.scale.num());
        // snap to centre lines and screen edges
        if (Math.abs(nx + w / 2.0 - sw / 2.0) < 4) nx = sw / 2.0 - w / 2.0;
        if (Math.abs(ny + h / 2.0 - sh / 2.0) < 4) ny = sh / 2.0 - h / 2.0;
        if (Math.abs(nx) < 4) nx = 0;
        if (Math.abs(ny) < 4) ny = 0;
        nx = Math.max(0, Math.min(sw - Math.max(8, w), nx));
        ny = Math.max(0, Math.min(sh - Math.max(8, h), ny));
        sel.fx = nx / sw; sel.fy = ny / sh;
        Config.markDirty();
        return true;
    }

    @Override public boolean mouseReleased(Click c) { dragging = false; return false; }

    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        HudModule target = sel;
        if (target == null) for (HudModule m : huds()) if (m.isEnabled() && Ui.in(mx, my, m.bx(mc), m.by(mc), m.sw(), m.sh())) target = m;
        if (target == null) return false;
        target.scale.setNum(target.scale.num() + (v > 0 ? 0.1 : -0.1));
        return true;
    }

    @Override public void removed() { Config.markDirty(); }
}
