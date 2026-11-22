package dev.robin.lagguard;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Distance limits for rendering players, mobs, decorations, block entities and particles. Nothing in the world itself is changed. */
public class LagGuard implements ClientModInitializer {
    // { players, mobs, decor (armor stands / frames / items), block entities, particles }
    private static final String[] NAMES = {"Gentle", "Balanced", "Aggressive", "Off"};
    private static final int[][] LEVELS = {
            {96, 80, 48, 80, 48},
            {64, 56, 32, 48, 32},
            {40, 40, 24, 32, 20},
            {0, 0, 0, 0, 0}
    };
    private static volatile int level = 1;
    // squared distances in blocks^2; 0 = unlimited
    public static volatile double playerSq, mobSq, decorSq, beSq, particleSq;
    private static boolean loaded = false;

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("robinlagguard.properties"); }

    private static void apply() {
        int[] d = LEVELS[level];
        playerSq = d[0] * (double) d[0]; mobSq = d[1] * (double) d[1]; decorSq = d[2] * (double) d[2];
        beSq = d[3] * (double) d[3]; particleSq = d[4] * (double) d[4];
    }

    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = Files.newInputStream(file())) {
            Properties p = new Properties(); p.load(in);
            level = Math.max(0, Math.min(LEVELS.length - 1, Integer.parseInt(p.getProperty("level", "1").trim())));
        } catch (Exception ignored) { }
        apply();
    }

    private static void save() {
        try (OutputStream out = Files.newOutputStream(file())) {
            Properties p = new Properties(); p.setProperty("level", Integer.toString(level));
            p.store(out, "Robin Lag Guard level: 0 Gentle, 1 Balanced, 2 Aggressive, 3 Off");
        } catch (IOException ignored) { }
    }

    /** True when this entity is too far away to be worth drawing. */
    public static boolean tooFar(Entity e, double cx, double cy, double cz) {
        ensureLoaded();
        if (level == 3) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (e == mc.player || e == mc.cameraEntity || e == mc.player.getVehicle() || e.hasPassengers()) return false;
        double dx = e.getX() - cx, dy = e.getY() - cy, dz = e.getZ() - cz;
        double d = dx * dx + dy * dy + dz * dz;
        double lim = e instanceof PlayerEntity ? playerSq
                : (e instanceof ArmorStandEntity || e instanceof AbstractDecorationEntity || e instanceof ItemEntity) ? decorSq : mobSq;
        return lim > 0 && d > lim;
    }

    public static boolean blockEntityTooFar(BlockEntity be) {
        ensureLoaded();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (level == 3 || beSq <= 0 || mc.player == null) return false;
        var p = be.getPos();
        double dx = p.getX() + 0.5 - mc.player.getX(), dy = p.getY() + 0.5 - mc.player.getY(), dz = p.getZ() + 0.5 - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz > beSq;
    }

    public static boolean particleTooFar(double x, double y, double z) {
        ensureLoaded();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (level == 3 || particleSq <= 0 || mc.player == null) return false;
        double dx = x - mc.player.getX(), dy = y - mc.player.getY(), dz = z - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz > particleSq;
    }

    @Override public void onInitializeClient() {
        ensureLoaded();
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lagguard.cycle", GLFW.GLFW_KEY_SEMICOLON, KeyBinding.Category.create(Identifier.of("lagguard", "controls"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.wasPressed()) {
                level = (level + 1) % LEVELS.length;
                apply(); save();
                int[] d = LEVELS[level];
                if (mc.player != null) mc.player.sendMessage(Text.literal("Lag Guard: " + NAMES[level]
                        + (level == 3 ? "" : " (players " + d[0] + ", mobs " + d[1] + ", decor " + d[2] + ", block entities " + d[3] + ", particles " + d[4] + ")")), true);
            }
        });
    }
}
