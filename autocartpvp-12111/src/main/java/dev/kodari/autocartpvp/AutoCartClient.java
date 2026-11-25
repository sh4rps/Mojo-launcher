package dev.kodari.autocartpvp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class AutoCartClient implements ClientModInitializer {
    private final CartSequence sequence = new CartSequence();

    @Override public void onInitializeClient() {
        CartConfig.HANDLER.load();
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autocartpvp.activate", GLFW.GLFW_KEY_V, KeyBinding.Category.create(Identifier.of("autocartpvp", "controls"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.wasPressed()) if (mc.currentScreen == null) sequence.start(mc);
            sequence.tick(mc);
            SafeSpots.tick(mc);
        });
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.AFTER_ENTITIES.register(ctx ->
                SafeSpots.render(MinecraftClient.getInstance(), ctx.matrices(), ctx.consumers()));
    }
}
