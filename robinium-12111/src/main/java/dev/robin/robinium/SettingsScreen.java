package dev.robin.robinium;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class SettingsScreen extends Screen {
    private final Screen parent;

    public SettingsScreen(Screen parent) { super(Text.literal("Robinium")); this.parent = parent; }

    private static Text renderText() { return Text.literal("Render distance: " + Robinium.STEPS[Robinium.render] + " chunks"); }
    private static Text simText() { return Text.literal("Simulation distance: " + Robinium.STEPS[Robinium.sim] + " chunks"); }
    private static Text animText() { return Text.literal("Animations: " + (Robinium.animations ? "ON" : "OFF")); }

    @Override protected void init() {
        int x = width / 2 - 110, y = height / 4;
        int n = Robinium.STEPS.length;
        addDrawableChild(ButtonWidget.builder(renderText(), b -> {
            Robinium.render = (Robinium.render + 1) % n; Robinium.save(); Robinium.apply(); b.setMessage(renderText());
        }).dimensions(x, y, 220, 20).build());
        addDrawableChild(ButtonWidget.builder(simText(), b -> {
            Robinium.sim = (Robinium.sim + 1) % n; Robinium.save(); Robinium.apply(); b.setMessage(simText());
        }).dimensions(x, y + 26, 220, 20).build());
        addDrawableChild(ButtonWidget.builder(animText(), b -> {
            Robinium.animations = !Robinium.animations; Robinium.save(); b.setMessage(animText());
        }).dimensions(x, y + 52, 220, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(x, y + 84, 220, 20).build());
    }

    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 4 - 18, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Servers may cap what they send"), width / 2, height / 4 + 112, 0xFFAAAAAA);
    }

    @Override public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
