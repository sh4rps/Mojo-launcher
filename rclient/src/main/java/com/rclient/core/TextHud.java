package com.rclient.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** HUD module that renders one or more text lines. */
public abstract class TextHud extends HudModule {
    protected TextHud(String id, String name, Category cat, String desc, double fx, double fy) {
        super(id, name, cat, desc, fx, fy);
    }
    /** Lines separated by \n, or null to hide (outside editor). */
    protected abstract String text(MinecraftClient mc);
    protected String sample() { return name; }

    @Override
    protected void draw(DrawContext g, MinecraftClient mc, boolean editing) {
        String t = text(mc);
        if (t == null) { if (!editing) { w = 0; h = 0; return; } t = sample(); }
        String[] lines = t.split("\n");
        int mw = 0, y = 0;
        for (String l : lines) {
            g.drawText(mc.textRenderer, l, 0, y, col(), true);
            mw = Math.max(mw, mc.textRenderer.getWidth(l));
            y += 10;
        }
        w = mw; h = y - 1;
    }
}
