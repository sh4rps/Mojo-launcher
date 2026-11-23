package dev.robin.rdx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Robindium Extra: standalone performance settings. No Sodium needed. */
public class RobindiumExtra implements ClientModInitializer {
    public static final int[] DIST = {0, 24, 32, 48, 64, 96, 128}; // 0 = unlimited
    // indices into DIST
    public static int players = 4, mobs = 3, decor = 2, blockEntities = 3, particles = 2;
    public static boolean enabled = true;
    private static boolean loaded;

    static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("robindiumextra.properties"); }

    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = Files.newInputStream(file())) {
            Properties p = new Properties(); p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            players = idx(p, "players", players); mobs = idx(p, "mobs", mobs); decor = idx(p, "decor", decor);
            blockEntities = idx(p, "blockEntities", blockEntities); particles = idx(p, "particles", particles);
        } catch (Exception ignored) { }
    }

    private static int idx(Properties p, String k, int d) {
        try { return Math.max(0, Math.min(DIST.length - 1, Integer.parseInt(p.getProperty(k, "" + d).trim()))); } catch (Exception e) { return d; }
    }

    public static void save() {
        try (OutputStream out = Files.newOutputStream(file())) {
            Properties p = new Properties();
            p.setProperty("enabled", "" + enabled);
            p.setProperty("players", "" + players); p.setProperty("mobs", "" + mobs); p.setProperty("decor", "" + decor);
            p.setProperty("blockEntities", "" + blockEntities); p.setProperty("particles", "" + particles);
            p.store(out, "Robindium Extra (indices into 0,24,32,48,64,96,128 blocks; 0 = unlimited)");
        } catch (Exception ignored) { }
    }

    private static double sq(int i) { double d = DIST[i]; return d * d; }

    public static boolean tooFar(Entity e, double cx, double cy, double cz) {
        ensureLoaded();
        if (!enabled) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || e == mc.player || e == mc.getCameraEntity() || e == mc.player.getVehicle() || e.hasPassengers()) return false;
        double dx = e.getX() - cx, dy = e.getY() - cy, dz = e.getZ() - cz;
        double d = dx * dx + dy * dy + dz * dz;
        double lim = e instanceof PlayerEntity ? sq(players)
                : (e instanceof ArmorStandEntity || e instanceof AbstractDecorationEntity || e instanceof ItemEntity) ? sq(decor) : sq(mobs);
        return lim > 0 && d > lim;
    }

    public static boolean blockEntityTooFar(BlockEntity be) {
        ensureLoaded();
        MinecraftClient mc = MinecraftClient.getInstance();
        double lim = sq(blockEntities);
        if (!enabled || lim <= 0 || mc.player == null) return false;
        var p = be.getPos();
        double dx = p.getX() + 0.5 - mc.player.getX(), dy = p.getY() + 0.5 - mc.player.getY(), dz = p.getZ() + 0.5 - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz > lim;
    }

    public static boolean particleTooFar(double x, double y, double z) {
        ensureLoaded();
        MinecraftClient mc = MinecraftClient.getInstance();
        double lim = sq(particles);
        if (!enabled || lim <= 0 || mc.player == null) return false;
        double dx = x - mc.player.getX(), dy = y - mc.player.getY(), dz = z - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz > lim;
    }

    @Override public void onInitializeClient() {
        ensureLoaded();
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.robindiumextra.settings", GLFW.GLFW_KEY_RIGHT_SHIFT, KeyBinding.Category.create(Identifier.of("robindiumextra", "controls"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new SettingsScreen(null));
            }
        });
    }
}
