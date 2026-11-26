package dev.robin.autocart2;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class AutoCart2 implements ClientModInitializer {
    /** Cart distance in blocks from the block you stand on (max 5: block reach). */
    public static int distance = 5;
    public static boolean autoLoad = true;
    public static boolean secondBlock = true;
    private final Sequence seq = new Sequence();

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("autocart2.properties"); }

    private static void load() {
        try (InputStream in = Files.newInputStream(file())) {
            Properties p = new Properties(); p.load(in);
            distance = Math.max(3, Math.min(5, Integer.parseInt(p.getProperty("distance", "5").trim())));
            autoLoad = Boolean.parseBoolean(p.getProperty("autoLoad", "true"));
            secondBlock = Boolean.parseBoolean(p.getProperty("secondBlock", "true"));
        } catch (Exception e) {
            try (OutputStream out = Files.newOutputStream(file())) {
                Properties p = new Properties();
                p.setProperty("distance", "5"); p.setProperty("autoLoad", "true"); p.setProperty("secondBlock", "true");
                p.store(out, "AutoCart2: distance 3-5 blocks to the cart; autoLoad = load the crossbow if empty; secondBlock = put a 2nd block on top of the cover after the shot");
            } catch (Exception ignored) { }
        }
    }

    @Override public void onInitializeClient() {
        load();
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autocart2.activate", GLFW.GLFW_KEY_V, KeyBinding.Category.create(Identifier.of("autocart2", "controls"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.wasPressed()) if (mc.currentScreen == null) seq.start(mc);
            seq.tick(mc);
        });
    }
}
