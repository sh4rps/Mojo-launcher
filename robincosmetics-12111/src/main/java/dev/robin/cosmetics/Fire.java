package dev.robin.cosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/** Animated fire on both hands: flame + coloured dust particles spawned at each hand every tick (only you see them). */
public final class Fire {
    public static final String[] NAMES = {"Classic fire", "Soul blue", "Green", "Purple", "Pink", "White", "Rainbow", "Custom RGB"};
    private static final int[][] BASE = {{255, 110, 0}, {40, 170, 255}, {60, 255, 60}, {170, 60, 255}, {255, 80, 190}, {255, 255, 255}};
    private static final Random RND = new Random();
    private static int t;

    private Fire() { }

    public static void tick(MinecraftClient mc) {
        t++;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.isPaused() || (!Cfg.fireLeft && !Cfg.fireRight)) return;
        boolean first = mc.options.getPerspective().isFirstPerson();
        boolean rightMain = p.getMainArm() == Arm.RIGHT;
        if (Cfg.fireRight) emit(mc, hand(mc, p, first, 1.0));
        if (Cfg.fireLeft) emit(mc, hand(mc, p, first, -1.0));
    }

    /** Approximate hand position; side +1 = right hand of the player, -1 = left. */
    private static Vec3d hand(MinecraftClient mc, ClientPlayerEntity p, boolean first, double side) {
        if (first) {
            Vec3d eye = p.getEyePos();
            float yaw = (float) Math.toRadians(p.getYaw()), pitch = (float) Math.toRadians(p.getPitch());
            Vec3d f = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
            Vec3d up = right.crossProduct(f).normalize();
            return eye.add(f.multiply(0.6)).add(right.multiply(0.34 * side)).add(up.multiply(-0.33));
        }
        double yaw = Math.toRadians(p.bodyYaw);
        Vec3d f = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        double h = p.isSneaking() ? 0.78 : 0.95;
        return p.getEntityPos().add(0, h, 0).add(right.multiply(0.38 * side)).add(f.multiply(0.1));
    }

    private static void emit(MinecraftClient mc, Vec3d at) {
        for (int i = 0; i < Cfg.amount; i++) {
            double ox = (RND.nextDouble() - 0.5) * 0.14, oz = (RND.nextDouble() - 0.5) * 0.14, oy = RND.nextDouble() * 0.1;
            double vy = 0.015 + RND.nextDouble() * 0.03;
            if (Cfg.color == 0 && RND.nextInt(3) == 0) {
                mc.world.addParticleClient(ParticleTypes.FLAME, at.x + ox, at.y + oy, at.z + oz, 0, vy, 0);
                continue;
            }
            int rgb = colour();
            // flicker: each particle a little brighter or darker than the base colour
            double k = 0.65 + RND.nextDouble() * 0.5;
            int rr = (int) Math.min(255, ((rgb >> 16) & 255) * k), gg = (int) Math.min(255, ((rgb >> 8) & 255) * k), bb = (int) Math.min(255, (rgb & 255) * k);
            if (Cfg.color == 0) { gg = (int) Math.min(255, gg * (0.5 + RND.nextDouble())); }   // red .. orange .. yellow
            mc.world.addParticleClient(new DustParticleEffect((rr << 16) | (gg << 8) | bb, 0.9f + RND.nextFloat() * 0.5f),
                    at.x + ox, at.y + oy, at.z + oz, 0, vy, 0);
        }
    }

    private static int colour() {
        if (Cfg.color == 6) return java.awt.Color.HSBtoRGB(((t * 6) % 360) / 360f, 0.9f, 1f) & 0xFFFFFF;
        if (Cfg.color == 7) return (Cfg.r << 16) | (Cfg.g << 8) | Cfg.b;
        int[] c = BASE[Cfg.color]; return (c[0] << 16) | (c[1] << 8) | c[2];
    }
}
