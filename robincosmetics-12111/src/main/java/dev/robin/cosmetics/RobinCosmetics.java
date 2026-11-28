package dev.robin.cosmetics;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class RobinCosmetics implements ClientModInitializer {
    @Override public void onInitializeClient() {
        Cfg.load();
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.robincosmetics.gui", GLFW.GLFW_KEY_J, KeyBinding.Category.create(Identifier.of("robincosmetics", "controls"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                while (key.wasPressed()) if (mc.currentScreen == null && mc.player != null) mc.setScreen(new CosmeticsScreen(null));
                Capes.tick();
                Fire.tick(mc);
                FlameFx.tick();
            } catch (Throwable t) { t.printStackTrace(); }
        });
        // "Robin Cosmetics" button in the pause menu and on the title screen
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof GameMenuScreen || screen instanceof TitleScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Robin Cosmetics"),
                        b -> client.setScreen(new CosmeticsScreen(screen))).dimensions(6, 6, 110, 20).build());
            }
        });
    }
}
