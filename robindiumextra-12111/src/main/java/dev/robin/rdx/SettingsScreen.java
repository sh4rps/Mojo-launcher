package dev.robin.rdx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.text.Text;

import java.util.function.IntSupplier;
import java.util.function.IntConsumer;

public class SettingsScreen extends Screen {
    private final Screen parent;

    public SettingsScreen(Screen parent) { super(Text.literal("Robindium Extra")); this.parent = parent; }

    private static String d(int i) { return RobindiumExtra.DIST[i] == 0 ? "Unlimited" : RobindiumExtra.DIST[i] + " blocks"; }

    private void cycle(int x, int y, String label, IntSupplier get, IntConsumer set) {
        ButtonWidget[] b = new ButtonWidget[1];
        b[0] = ButtonWidget.builder(Text.literal(label + ": " + d(get.getAsInt())), btn -> {
            set.accept((get.getAsInt() + 1) % RobindiumExtra.DIST.length);
            RobindiumExtra.save();
            btn.setMessage(Text.literal(label + ": " + d(get.getAsInt())));
        }).dimensions(x, y, 200, 20).build();
        addDrawableChild(b[0]);
    }

    @Override protected void init() {
        int x = width / 2 - 100, y = height / 6;
        MinecraftClient mc = MinecraftClient.getInstance();
        addDrawableChild(ButtonWidget.builder(Text.literal("Limits: " + (RobindiumExtra.enabled ? "ON" : "OFF")), btn -> {
            RobindiumExtra.enabled = !RobindiumExtra.enabled; RobindiumExtra.save();
            btn.setMessage(Text.literal("Limits: " + (RobindiumExtra.enabled ? "ON" : "OFF")));
        }).dimensions(x, y, 200, 20).build());
        cycle(x, y + 24, "Players", () -> RobindiumExtra.players, v -> RobindiumExtra.players = v);
        cycle(x, y + 48, "Mobs", () -> RobindiumExtra.mobs, v -> RobindiumExtra.mobs = v);
        cycle(x, y + 72, "Decor / items", () -> RobindiumExtra.decor, v -> RobindiumExtra.decor = v);
        cycle(x, y + 96, "Block entities", () -> RobindiumExtra.blockEntities, v -> RobindiumExtra.blockEntities = v);
        cycle(x, y + 120, "Particles", () -> RobindiumExtra.particles, v -> RobindiumExtra.particles = v);
        addDrawableChild(ButtonWidget.builder(Text.literal("Clouds: " + (mc.options.getCloudRenderMode().getValue() == CloudRenderMode.OFF ? "OFF" : "ON")), btn -> {
            boolean off = mc.options.getCloudRenderMode().getValue() == CloudRenderMode.OFF;
            mc.options.getCloudRenderMode().setValue(off ? CloudRenderMode.FAST : CloudRenderMode.OFF);
            mc.options.write();
            btn.setMessage(Text.literal("Clouds: " + (off ? "ON" : "OFF")));
        }).dimensions(x, y + 148, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Shadows: " + (mc.options.getEntityShadows().getValue() ? "ON" : "OFF")), btn -> {
            boolean on = mc.options.getEntityShadows().getValue();
            mc.options.getEntityShadows().setValue(!on);
            mc.options.write();
            btn.setMessage(Text.literal("Shadows: " + (!on ? "ON" : "OFF")));
        }).dimensions(x + 102, y + 148, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> close()).dimensions(x, y + 176, 200, 20).build());
    }

    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 6 - 16, 0xFFFFFFFF);
    }

    @Override public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
