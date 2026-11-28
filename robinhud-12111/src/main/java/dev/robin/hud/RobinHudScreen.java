package dev.robin.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class RobinHudScreen extends Screen {
    private final Screen parent;
    private static final String[] NAMES = {"Potion HUD", "Armor HUD", "Inventory HUD"};

    public RobinHudScreen(Screen parent) { super(Text.literal("Robin HUD")); this.parent = parent; }

    private static final class Slide extends SliderWidget {
        private final String name; private final int min, max; private final Consumer<Integer> set; private final String suffix;
        Slide(int x, int y, int w, String name, int min, int max, int cur, String suffix, Consumer<Integer> set) {
            super(x, y, w, 20, Text.empty(), (cur - min) / (double) (max - min));
            this.name = name; this.min = min; this.max = max; this.set = set; this.suffix = suffix; updateMessage();
        }
        int val() { return min + (int) Math.round(value * (max - min)); }
        @Override protected void updateMessage() { setMessage(Text.literal(name + ": " + val() + suffix)); }
        @Override protected void applyValue() { set.accept(val()); }
    }

    @Override protected void init() {
        Hud.preview = true;
        int w = 300, x = width / 2 - w / 2, y = Math.max(22, height / 2 - 105);
        int third = (w - 8) / 3;
        for (int i = 0; i < 3; i++) {
            final int m = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(NAMES[i] + ": " + (Cfg.on[i] ? "ON" : "OFF")), b -> {
                Cfg.on[m] = !Cfg.on[m]; Cfg.save(); clearAndInit();
            }).dimensions(x, y, third, 20).build());
            addDrawableChild(new Slide(x + third + 4, y, third, "X", 0, 100, Cfg.x[i], "%", v -> { Cfg.x[m] = v; Cfg.save(); }));
            addDrawableChild(new Slide(x + 2 * (third + 4), y, third, "Y", 0, 100, Cfg.y[i], "%", v -> { Cfg.y[m] = v; Cfg.save(); }));
            addDrawableChild(new Slide(x, y + 22, w, "Size", 50, 200, Cfg.scale[i], "%", v -> { Cfg.scale[m] = v; Cfg.save(); }));
            y += 48;
        }
        int half = (w - 4) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Fullbright: " + (Cfg.fullbright ? "ON" : "OFF")), b -> {
            Cfg.fullbright = !Cfg.fullbright; Cfg.save(); clearAndInit();
        }).dimensions(x, y, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Hide vanilla effects: " + (Cfg.hideVanillaEffects ? "YES" : "NO")), b -> {
            Cfg.hideVanillaEffects = !Cfg.hideVanillaEffects; Cfg.save(); clearAndInit();
        }).dimensions(x + half + 4, y, half, 20).build());
        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Armor numbers: " + (Cfg.armorNumbers ? "ON" : "OFF")), b -> {
            Cfg.armorNumbers = !Cfg.armorNumbers; Cfg.save(); clearAndInit();
        }).dimensions(x, y, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Armor shows held items: " + (Cfg.armorHeld ? "ON" : "OFF")), b -> {
            Cfg.armorHeld = !Cfg.armorHeld; Cfg.save(); clearAndInit();
        }).dimensions(x + half + 4, y, half, 20).build());
        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(x, y, w, 20).build());
    }

    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, Math.max(22, height / 2 - 105) - 14, 0xFFFFFFFF);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { Hud.preview = false; Cfg.save(); }
    @Override public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
