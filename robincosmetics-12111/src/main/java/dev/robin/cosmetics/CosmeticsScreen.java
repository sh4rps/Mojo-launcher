package dev.robin.cosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class CosmeticsScreen extends Screen {
    private final Screen parent;

    public CosmeticsScreen(Screen parent) { super(Text.literal("Robin Cosmetics")); this.parent = parent; }

    private static final class Slide extends SliderWidget {
        private final String name; private final int min, max; private final Consumer<Integer> set;
        Slide(int x, int y, int w, String name, int min, int max, int cur, Consumer<Integer> set) {
            super(x, y, w, 20, Text.empty(), (cur - min) / (double) (max - min));
            this.name = name; this.min = min; this.max = max; this.set = set; updateMessage();
        }
        int val() { return min + (int) Math.round(value * (max - min)); }
        @Override protected void updateMessage() { setMessage(Text.literal(name + ": " + val())); }
        @Override protected void applyValue() { set.accept(val()); }
    }

    @Override protected void init() {
        int w = 260, x = width / 2 - w / 2, y = Math.max(28, height / 2 - 110);
        // ---- capes: 6 buttons in a row
        int bw = (w - 5 * 3) / 6;
        for (int i = 0; i <= Capes.COUNT; i++) {
            final int idx = i;
            String label = Capes.NAMES[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(Cfg.cape == i ? "[" + shortName(label) + "]" : shortName(label)), b -> {
                Cfg.cape = idx; Cfg.save(); clearAndInit();
            }).dimensions(x + i * (bw + 3), y + 14, bw, 20).build());
        }
        // ---- fire hands
        int fy = y + 56;
        addDrawableChild(ButtonWidget.builder(Text.literal("Left hand fire: " + (Cfg.fireLeft ? "ON" : "OFF")), b -> {
            Cfg.fireLeft = !Cfg.fireLeft; Cfg.save(); clearAndInit();
        }).dimensions(x, fy, w / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Right hand fire: " + (Cfg.fireRight ? "ON" : "OFF")), b -> {
            Cfg.fireRight = !Cfg.fireRight; Cfg.save(); clearAndInit();
        }).dimensions(x + w / 2 + 2, fy, w / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Fire colour: " + Fire.NAMES[Cfg.color]), b -> {
            Cfg.color = (Cfg.color + 1) % Fire.NAMES.length; Cfg.save(); clearAndInit();
        }).dimensions(x, fy + 24, w, 20).build());
        int sy = fy + 48;
        if (Cfg.color == Fire.NAMES.length - 1) {
            addDrawableChild(new Slide(x, sy, w, "Red", 0, 255, Cfg.r, v -> { Cfg.r = v; Cfg.save(); }));
            addDrawableChild(new Slide(x, sy + 22, w, "Green", 0, 255, Cfg.g, v -> { Cfg.g = v; Cfg.save(); }));
            addDrawableChild(new Slide(x, sy + 44, w, "Blue", 0, 255, Cfg.b, v -> { Cfg.b = v; Cfg.save(); }));
            sy += 66;
        }
        addDrawableChild(new Slide(x, sy, w, "Fire amount", 1, 6, Cfg.amount, v -> { Cfg.amount = v; Cfg.save(); }));
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(x, sy + 28, w, 20).build());
    }

    private static String shortName(String s) { return s.length() > 7 ? s.substring(0, 7) : s; }

    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        int y = Math.max(28, height / 2 - 110);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, y - 14, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Cape (only you see it)"), width / 2 - 130, y + 3, 0xFFFFAA00);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Fire on hands (only you see it)"), width / 2 - 130, y + 45, 0xFFFFAA00);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { Cfg.save(); }
    @Override public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
