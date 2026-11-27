package dev.robin.autocart2;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class AutoCart2 implements ClientModInitializer {
    private final Sequence seq = new Sequence();

    @Override public void onInitializeClient() {
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autocart2.activate", GLFW.GLFW_KEY_V, KeyBinding.Category.create(Identifier.of("autocart2", "controls"))));
        boolean clash = FabricLoader.getInstance().isModLoaded("autocartpvp");
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.wasPressed()) {
                if (mc.currentScreen != null) continue;
                if (clash) { if (mc.player != null) mc.player.sendMessage(Text.literal("[AutoCart2] Remove the AutoCartPvP mod: both use V and fight each other"), true); }
                else seq.start(mc);
            }
            if (!clash) seq.tick(mc);
        });
    }
}
