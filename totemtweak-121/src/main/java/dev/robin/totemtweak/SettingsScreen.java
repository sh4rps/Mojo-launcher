package dev.robin.totemtweak;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SettingsScreen extends Screen {
    private static int hand = 0;     // 0 = main hand, 1 = off hand (remembered while the game runs)

    public SettingsScreen() { super(Text.literal("Totem Tweaker")); }

    private static final class Slide extends SliderWidget {
        private final String name; private final float min, max; private final Consumer<Float> set; private final Supplier<Float> get; private final boolean pct;
        Slide(int x, int y, int w, String name, float min, float max, Supplier<Float> get, Consumer<Float> set, boolean pct) {
            super(x, y, w, 20, Text.empty(), (get.get() - min) / (max - min));
            this.name = name; this.min = min; this.max = max; this.get = get; this.set = set; this.pct = pct;
            updateMessage();
        }
        float val() { return min + (float) value * (max - min); }
        @Override protected void updateMessage() {
            setMessage(Text.literal(name + ": " + (pct ? Math.round(val() * 100) + "%" : String.format("%.2f", val()))));
        }
        @Override protected void applyValue() { set.accept(val()); Cfg.clamp(); }
    }

    @Override protected void init() {
        int w = 220, x = width / 2 - w / 2, y = Math.max(34, height / 2 - 80);
        final int h = hand;
        addDrawableChild(ButtonWidget.builder(Text.literal(h == 0 ? "Hand: MAIN HAND  (click for off hand)" : "Hand: OFF HAND  (click for main hand)"), b -> {
            hand = 1 - hand; clearAndInit();
        }).dimensions(x, y - 26, w, 20).build());
        addDrawableChild(new Slide(x, y, w, "Left <-> Right", -Cfg.X_MAX, Cfg.X_MAX, () -> Cfg.x[h], v -> Cfg.x[h] = v, false));
        addDrawableChild(new Slide(x, y + 24, w, "Down <-> Up", -Cfg.Y_MAX, Cfg.Y_MAX, () -> Cfg.y[h], v -> Cfg.y[h] = v, false));
        addDrawableChild(new Slide(x, y + 48, w, "Back <-> Forward", Cfg.Z_MIN, Cfg.Z_MAX, () -> Cfg.z[h], v -> Cfg.z[h] = v, false));
        addDrawableChild(new Slide(x, y + 72, w, "Size", Cfg.S_MIN, Cfg.S_MAX, () -> Cfg.size[h], v -> Cfg.size[h] = v, true));
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset this hand"), b -> { Cfg.reset(h); clearAndInit(); }).dimensions(x, y + 100, 106, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(x + 114, y + 100, 106, 20).build());
    }

    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, Math.max(34, height / 2 - 80) - 40, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Hold a Totem of Undying to see the change"), width / 2, Math.max(34, height / 2 - 80) + 128, 0xFFAAAAAA);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { Cfg.save(); }
    @Override public void close() { MinecraftClient.getInstance().setScreen(null); }
}
