package com.rclient.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Base for RClient screens: no vanilla background/widgets, returns to parent on close. */
public abstract class RScreen extends Screen {
    protected final Screen parent;
    protected final MinecraftClient mc = MinecraftClient.getInstance();
    protected float t0 = Ui.now();
    protected float open = 0f;

    protected RScreen(String title, Screen parent) { super(Text.literal(title)); this.parent = parent; }

    @Override public void close() { mc.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext g, int mx, int my, float d) { }

    protected abstract void paint(DrawContext g, int mx, int my, float t);

    @Override public void render(DrawContext g, int mx, int my, float delta) {
        open = Ui.anim(open, 1f, 0.12f);
        paint(g, mx, my, Ui.now());
    }

    protected boolean hit(Click c, int x, int y, int w, int h) { return Ui.in(c.x(), c.y(), x, y, w, h); }
}
