package com.robin.rustmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class RustMod implements ClientModInitializer {
    private static KeyBinding toggle;

    @Override
    public void onInitializeClient() {
        Cfg.load();
        if (!Cfg.has("enabled")) { Cfg.set("enabled", true); Cfg.save(); }
        Perf.refreshCfg();

        toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.rustmod.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F9, "key.categories.rustmod"));

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            if (Perf.on) Perf.enable(mc);
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Perf.tick(mc);
            while (toggle.wasPressed()) {
                if (Perf.on) Perf.disable(mc); else Perf.enable(mc);
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal("Rust Mod: " + (Perf.on ? "MAX PERFORMANCE ON" : "OFF (your settings restored)")), true);
                }
            }
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!Perf.on || !Cfg.bool("overlay", true) || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;
            String s = "RUST " + mc.getCurrentFps() + " FPS  RD " + mc.options.getViewDistance().getValue();
            ctx.drawTextWithShadow(mc.textRenderer, s, 4, 4, 0xFFE53935);
        });
    }
}
