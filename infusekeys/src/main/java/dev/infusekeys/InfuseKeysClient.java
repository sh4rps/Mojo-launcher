package dev.infusekeys;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Four rebindable keys. Pressing a main key sends the server command /ability1 or /ability2.
 * Each ability has an optional "extra" key: if it is bound, it must be held while pressing the main key.
 */
public class InfuseKeysClient implements ClientModInitializer {
    private static final String CATEGORY = "key.category.infusekeys.abilities";

    private static KeyBinding ability1;
    private static KeyBinding ability1Extra;
    private static KeyBinding ability2;
    private static KeyBinding ability2Extra;

    @Override
    public void onInitializeClient() {
        ability1 = reg("key.infusekeys.ability1", GLFW.GLFW_KEY_G);
        ability1Extra = reg("key.infusekeys.ability1_extra", InputUtil.UNKNOWN_KEY.getCode());
        ability2 = reg("key.infusekeys.ability2", GLFW.GLFW_KEY_H);
        ability2Extra = reg("key.infusekeys.ability2_extra", InputUtil.UNKNOWN_KEY.getCode());
        ClientTickEvents.END_CLIENT_TICK.register(InfuseKeysClient::onEndTick);
    }

    private static KeyBinding reg(String id, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(id, InputUtil.Type.KEYSYM, code, CATEGORY));
    }

    /** Extra key is optional: unbound counts as satisfied, otherwise it has to be held. */
    private static boolean extraOk(KeyBinding extra) {
        return extra.isUnbound() || extra.isPressed();
    }

    private static void send(MinecraftClient mc, String command) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendChatCommand(command);
    }

    private static void onEndTick(MinecraftClient mc) {
        while (ability1.wasPressed()) {
            if (extraOk(ability1Extra)) send(mc, "ability1");
        }
        while (ability2.wasPressed()) {
            if (extraOk(ability2Extra)) send(mc, "ability2");
        }
        // drain presses of the extra keys so they don't pile up
        while (ability1Extra.wasPressed()) { }
        while (ability2Extra.wasPressed()) { }
    }
}
