package dev.kodari.autocartpvp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class AutoCartClient implements ClientModInitializer {
    private final CartSequence sequence = new CartSequence();

    @Override public void onInitializeClient() {
        CartConfig.HANDLER.load();
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autocartpvp.activate", GLFW.GLFW_KEY_V, "key.category.autocartpvp.controls"));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.wasPressed()) if (mc.currentScreen == null) sequence.start(mc);
            sequence.tick(mc);
        });
        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!CartConfig.get().hud || mc.player == null || mc.world == null) return;
            boolean ready = Equipment.scan(mc.player.getInventory(), CartConfig.get().autoDetect).ready(CartConfig.get().crossbowMode);
            String s = sequence.active() ? "ACTIVE" : ready ? "READY" : "MISSING ITEMS";
            int color = sequence.active() ? 0xFFFF55 : ready ? 0x55FF55 : 0xFF5555;
            ctx.drawTextWithShadow(mc.textRenderer, "Auto CartPvP: " + s, 6, 6, 0xFF000000 | color);
        });
    }
}
