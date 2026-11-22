package dev.robin.frameupscale;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Render scale: the game draws at (scale x window size) and the result is upscaled to the window. U cycles the scale. */
public class FrameUpscale implements ClientModInitializer {
    private static final int[] STEPS = {100, 90, 80, 75, 67, 50};
    private static volatile int percent = load();

    public static float scale() { return percent / 100f; }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("frameupscale.properties"); }

    private static int load() {
        try (InputStream in = Files.newInputStream(file())) {
            Properties p = new Properties(); p.load(in);
            int v = Integer.parseInt(p.getProperty("scalePercent", "75").trim());
            return Math.max(25, Math.min(100, v));
        } catch (Exception e) { return 75; }
    }

    private static void save() {
        try (OutputStream out = Files.newOutputStream(file())) {
            Properties p = new Properties(); p.setProperty("scalePercent", Integer.toString(percent));
            p.store(out, "Frame Upscale: render scale in percent (25-100). 100 = off.");
        } catch (IOException ignored) { }
    }

    @Override public void onInitializeClient() {
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.frameupscale.cycle", GLFW.GLFW_KEY_U, KeyBinding.Category.create(Identifier.of("frameupscale", "controls"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.wasPressed()) {
                int next = STEPS[0];
                for (int i = 0; i < STEPS.length; i++) if (STEPS[i] == percent) { next = STEPS[(i + 1) % STEPS.length]; break; }
                if (percent < STEPS[STEPS.length - 1]) next = STEPS[0];
                percent = next;
                save();
                mc.onResolutionChanged();   // re-creates the framebuffers at the new size
                if (mc.player != null) mc.player.sendMessage(Text.literal("Render scale " + percent + "%" + (percent == 100 ? " (off)" : "")), true);
            }
        });
    }
}
