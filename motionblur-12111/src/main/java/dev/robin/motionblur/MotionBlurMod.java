package dev.robin.motionblur;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Motion blur by frame blending. B toggles, N cycles the strength. Config: config/motionblur.properties */
public class MotionBlurMod implements ClientModInitializer {
    private static final int[] STEPS = {30, 45, 60, 75, 88};
    private static volatile boolean enabled = true;
    private static volatile int strength = 60;       // % of the old image kept per 1/60 s
    private static volatile boolean broken = false;
    private static long last = 0;
    private static boolean loaded = false;

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("motionblur.properties"); }

    private static void load() {
        loaded = true;
        try (InputStream in = Files.newInputStream(file())) {
            Properties p = new Properties(); p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            strength = Math.max(5, Math.min(95, Integer.parseInt(p.getProperty("strengthPercent", "60").trim())));
        } catch (Exception ignored) { }
    }

    private static void save() {
        try (OutputStream out = Files.newOutputStream(file())) {
            Properties p = new Properties();
            p.setProperty("enabled", Boolean.toString(enabled));
            p.setProperty("strengthPercent", Integer.toString(strength));
            p.store(out, "Motion Blur: strengthPercent 5-95 (higher = longer trails)");
        } catch (IOException ignored) { }
    }

    /** Called by the mixin right before the finished frame is shown. */
    public static void onPresent(GpuTextureView view) {
        if (broken) return;
        if (!loaded) load();
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.nanoTime();
        double dt = last == 0 ? 0.016 : (now - last) / 1e9;
        last = now;
        if (!enabled || mc.world == null || mc.currentScreen != null || mc.isPaused()) { BlurRenderer.invalidate(); return; }
        try {
            if (!(view.texture() instanceof GlTexture gl)) return;
            BlurRenderer.process(gl.getGlId(), view.getWidth(0), view.getHeight(0), strength / 100f, dt);
        } catch (Throwable t) {
            broken = true;   // never crash the game over a cosmetic effect
            System.err.println("[MotionBlur] disabled after error: " + t);
        }
    }

    @Override public void onInitializeClient() {
        if (!loaded) load();
        KeyBinding toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.motionblur.toggle", GLFW.GLFW_KEY_B, KeyBinding.Category.create(Identifier.of("motionblur", "controls"))));
        KeyBinding level = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.motionblur.strength", GLFW.GLFW_KEY_N, KeyBinding.Category.create(Identifier.of("motionblur", "controls"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggle.wasPressed()) {
                enabled = !enabled; save();
                if (mc.player != null) mc.player.sendMessage(Text.literal("Motion blur " + (enabled ? "ON (" + strength + "%)" : "OFF")), true);
            }
            while (level.wasPressed()) {
                int next = STEPS[0];
                for (int i = 0; i < STEPS.length; i++) if (STEPS[i] == strength) { next = STEPS[(i + 1) % STEPS.length]; break; }
                strength = next; enabled = true; save();
                if (mc.player != null) mc.player.sendMessage(Text.literal("Motion blur strength " + strength + "%"), true);
            }
        });
    }
}
