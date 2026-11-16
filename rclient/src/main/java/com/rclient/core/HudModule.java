package com.rclient.core;

import com.rclient.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public abstract class HudModule extends Mod {
    public double fx, fy;
    public final double dfx, dfy;              // top-left as fraction of scaled screen
    public final Setting scale, color, background;
    public int w = 40, h = 10;          // last drawn content size (unscaled)

    protected HudModule(String id, String name, Category cat, String desc, double fx, double fy) {
        super(id, name, cat, desc);
        this.fx = fx; this.fy = fy; this.dfx = fx; this.dfy = fy;
        scale = add(Setting.num("scale", "Scale", 1.0, 0.5, 3.0, 0.1));
        color = add(Setting.color("color", "Color", 0xFFFFFFFF));
        background = add(Setting.bool("background", "Background", true));
    }

    @Override public boolean isHud() { return true; }

    /** Draw content with origin at (0,0). Must set w/h. */
    protected abstract void draw(DrawContext g, MinecraftClient mc, boolean editing);

    public int px(MinecraftClient mc) { return (int) Math.round(fx * mc.getWindow().getScaledWidth()); }
    public int py(MinecraftClient mc) { return (int) Math.round(fy * mc.getWindow().getScaledHeight()); }
    public int sw() { return Math.max(1, (int) Math.round((w + 6) * scale.num())); }
    public int sh() { return Math.max(1, (int) Math.round((h + 6) * scale.num())); }
    public int bx(MinecraftClient mc) { return px(mc) - (int) Math.round(3 * scale.num()); }
    public int by(MinecraftClient mc) { return py(mc) - (int) Math.round(3 * scale.num()); }

    public void render(DrawContext g, MinecraftClient mc, boolean editing) {
        float s = scale.flt();
        var m = g.getMatrices();
        m.pushMatrix();
        m.translate(px(mc), py(mc));
        m.scale(s, s);
        if (background.bool() && w > 0) Ui.round(g, -3, -3, w + 6, h + 6, 0x66000000);
        draw(g, mc, editing);
        m.popMatrix();
    }

    protected int col() { return color.color(); }
}
