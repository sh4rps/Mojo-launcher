package dev.robin.totemtweak;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class TotemTweak implements ClientModInitializer {
    @Override public void onInitializeClient() {
        Cfg.load();
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.totemtweak.gui", GLFW.GLFW_KEY_O, KeyBinding.Category.create(Identifier.of("totemtweak", "controls"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.wasPressed()) {
                if (mc.currentScreen == null && mc.player != null) mc.setScreen(new SettingsScreen());
            }
        });
    }
}
