package dev.robin.hud;

import dev.robin.hud.mixin.SimpleOptionAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class RobinHud implements ClientModInitializer {
    private static double savedGamma = -1;
    private static boolean applied;

    @Override public void onInitializeClient() {
        Cfg.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("robinhud", "controls"));
        KeyBinding open = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.robinhud.gui", GLFW.GLFW_KEY_H, cat));
        KeyBinding fb = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.robinhud.fullbright", GLFW.GLFW_KEY_B, cat));

        HudElementRegistry.addLast(Identifier.of("robinhud", "panels"), (ctx, tick) -> { try { Hud.render(ctx); } catch (Throwable t) { t.printStackTrace(); } });
        HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, orig -> (ctx, tick) -> {
            if (Cfg.on[0] && Cfg.hideVanillaEffects) return;
            orig.render(ctx, tick);
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                while (open.wasPressed()) if (mc.currentScreen == null && mc.player != null) mc.setScreen(new RobinHudScreen(null));
                while (fb.wasPressed()) if (mc.currentScreen == null && mc.player != null) {
                    Cfg.fullbright = !Cfg.fullbright; Cfg.save();
                    mc.player.sendMessage(Text.literal("Fullbright " + (Cfg.fullbright ? "ON" : "OFF")), true);
                }
                fullbright(mc);
            } catch (Throwable t) { t.printStackTrace(); }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> restore(mc));

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof GameMenuScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Robin HUD"),
                        b -> client.setScreen(new RobinHudScreen(screen))).dimensions(6, 32, 110, 20).build());
            }
        });
    }

    private static void fullbright(MinecraftClient mc) {
        var gamma = mc.options.getGamma();
        if (Cfg.fullbright) {
            if (!applied) { savedGamma = gamma.getValue(); applied = true; }
            if (gamma.getValue() < 15.0) ((SimpleOptionAccessor) (Object) gamma).robinhud$setRaw(16.0);
        } else if (applied) restore(mc);
    }

    private static void restore(MinecraftClient mc) {
        if (applied && savedGamma >= 0) ((SimpleOptionAccessor) (Object) mc.options.getGamma()).robinhud$setRaw(savedGamma);
        applied = false;
    }
}
