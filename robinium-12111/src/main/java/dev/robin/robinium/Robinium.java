package dev.robin.robinium;

import dev.robin.robinium.mixin.SimpleOptionAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Robinium: render/simulation distance 2-64 and animation toggle. Small hooks only, so it stays compatible with other mods. */
public class Robinium implements ClientModInitializer {
    public static final int[] STEPS = {2, 3, 4, 6, 8, 10, 12, 16, 20, 24, 32, 40, 48, 56, 64};
    public static int render = 10, sim = 8; // indices into STEPS
    public static boolean animations = true;
    private static boolean loaded;

    static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("robinium.properties"); }

    static void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = Files.newInputStream(file())) {
            Properties p = new Properties(); p.load(in);
            render = idx(p, "render", 10); sim = idx(p, "sim", 8);
            animations = Boolean.parseBoolean(p.getProperty("animations", "true"));
        } catch (Exception ignored) { }
    }

    private static int idx(Properties p, String k, int d) {
        try { return Math.max(0, Math.min(STEPS.length - 1, Integer.parseInt(p.getProperty(k, "" + d).trim()))); } catch (Exception e) { return d; }
    }

    public static void save() {
        try (OutputStream out = Files.newOutputStream(file())) {
            Properties p = new Properties();
            p.setProperty("render", "" + render); p.setProperty("sim", "" + sim); p.setProperty("animations", "" + animations);
            p.store(out, "Robinium: render/sim are indices into 2,3,4,6,8,10,12,16,20,24,32,40,48,56,64 chunks");
        } catch (Exception ignored) { }
    }

    /** Pushes our values into the game options and the live chunk map. */
    @SuppressWarnings("unchecked")
    public static void apply() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;
        try {
            ((SimpleOptionAccessor) (Object) mc.options.getViewDistance()).robinium$setRaw(STEPS[render]);
            ((SimpleOptionAccessor) (Object) mc.options.getSimulationDistance()).robinium$setRaw(STEPS[sim]);
            if (mc.world != null) mc.world.getChunkManager().updateLoadDistance(STEPS[render]);
            if (mc.worldRenderer != null) mc.worldRenderer.reload();
        } catch (Throwable t) { /* never crash the game over a setting */ }
    }

    @Override public void onInitializeClient() {
        load();
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.robinium.settings", GLFW.GLFW_KEY_RIGHT_CONTROL, KeyBinding.Category.create(Identifier.of("robinium", "controls"))));
        ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> mc.execute(() -> {
            try {
                ((SimpleOptionAccessor) (Object) mc.options.getViewDistance()).robinium$setRaw(STEPS[render]);
                ((SimpleOptionAccessor) (Object) mc.options.getSimulationDistance()).robinium$setRaw(STEPS[sim]);
                if (mc.world != null) mc.world.getChunkManager().updateLoadDistance(STEPS[render]);
            } catch (Throwable ignored) { }
        }));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.wasPressed()) if (mc.currentScreen == null) mc.setScreen(new SettingsScreen(null));
        });
    }
}
