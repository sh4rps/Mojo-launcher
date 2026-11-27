package dev.robin.totemtweak;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SettingsScreen extends Screen {
    private int age;
    private final java.util.List<Slide> slides = new java.util.ArrayList<>();

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
            setMessage(Text.literal(name + ": " + (pct ? Math.round(val() * 100) + "%" : String.format("%.1f", val()))));
        }
        @Override protected void applyValue() { set.accept(val()); Cfg.clamp(); }
        void refresh() { value = (get.get() - min) / (max - min); updateMessage(); }
    }

    @Override protected void init() {
        slides.clear();
        int w = 220, x = width / 2 - w / 2, y = Math.max(20, height / 2 - 70);
        slides.add(new Slide(x, y, w, "Left <-> Right", -Cfg.X_MAX, Cfg.X_MAX, () -> Cfg.x, v -> Cfg.x = v, false));
        slides.add(new Slide(x, y + 24, w, "Down <-> Up", -Cfg.Y_MAX, Cfg.Y_MAX, () -> Cfg.y, v -> Cfg.y = v, false));
        slides.add(new Slide(x, y + 48, w, "Back <-> Forward", Cfg.Z_MIN, Cfg.Z_MAX, () -> Cfg.z, v -> Cfg.z = v, false));
        slides.add(new Slide(x, y + 72, w, "Size", Cfg.S_MIN, Cfg.S_MAX, () -> Cfg.size, v -> Cfg.size = v, true));
        for (Slide s : slides) addDrawableChild(s);
        addDrawableChild(ButtonWidget.builder(Text.literal("Preview"), b -> preview()).dimensions(x, y + 100, 106, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), b -> { Cfg.reset(); for (Slide s : slides) s.refresh(); }).dimensions(x + 114, y + 100, 106, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(x, y + 124, w, 20).build());
        preview();
    }

    private void preview() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.gameRenderer != null) mc.gameRenderer.showFloatingItem(new ItemStack(Items.TOTEM_OF_UNDYING));
    }

    @Override public void tick() { if (++age % 42 == 0) preview(); }   // keeps the totem popping while you adjust

    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, Math.max(20, height / 2 - 70) - 16, 0xFFFFFFFF);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { Cfg.save(); }
    @Override public void close() { MinecraftClient.getInstance().setScreen(null); }
}
