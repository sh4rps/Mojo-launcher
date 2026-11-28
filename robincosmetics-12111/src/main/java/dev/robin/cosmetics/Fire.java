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
    public static final String[] STYLES = {"Flames", "Swirl", "Sparks", "Ring", "Embers", "Column"};
    private static boolean reported;
    public static final String[] NAMES = {"Classic fire", "Soul blue", "Green", "Purple", "Pink", "White", "Gold", "Cyan", "Red", "Lava", "Ice", "Toxic", "Rainbow", "Custom RGB"};
    private static final int[][] BASE = {{255, 110, 0}, {40, 170, 255}, {60, 255, 60}, {170, 60, 255}, {255, 80, 190}, {255, 255, 255},
            {255, 200, 40}, {0, 255, 230}, {255, 30, 30}, {255, 70, 0}, {170, 230, 255}, {160, 255, 0}};
    private static final Random RND = new Random();
    private static int t;

    private Fire() { }

    public static void tick(MinecraftClient mc) {
        try { run(mc); } catch (Throwable e) {
            e.printStackTrace();
            if (!reported && mc.player != null) { reported = true; mc.player.sendMessage(net.minecraft.text.Text.literal("[Robin Cosmetics] fire error: " + e), false); }
        }
    }

    private static void run(MinecraftClient mc) {
        t++;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || (!Cfg.fireLeft && !Cfg.fireRight) || !Cfg.sparks) return;
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
        int n = Cfg.amount;
        for (int i = 0; i < n; i++) {
            double ox = 0, oy = 0, oz = 0, vy = 0.02;
            switch (Cfg.style) {
                case 1: { double a = t * 0.45 + i * (Math.PI * 2 / n); ox = Math.cos(a) * 0.11; oz = Math.sin(a) * 0.11; oy = (t % 10) * 0.012; break; }
                case 2: { ox = (RND.nextDouble() - 0.5) * 0.5; oy = (RND.nextDouble() - 0.5) * 0.4; oz = (RND.nextDouble() - 0.5) * 0.5; vy = 0; break; }
                case 3: { double a = t * 0.35 + i * (Math.PI * 2 / n); ox = Math.cos(a) * 0.2; oz = Math.sin(a) * 0.2; oy = 0.02; vy = 0.005; break; }
                case 4: { ox = (RND.nextDouble() - 0.5) * 0.3; oz = (RND.nextDouble() - 0.5) * 0.3; oy = 0.25 + RND.nextDouble() * 0.15; vy = -0.03; break; }
                case 5: { ox = (RND.nextDouble() - 0.5) * 0.12; oz = (RND.nextDouble() - 0.5) * 0.12; oy = RND.nextDouble() * 0.7; vy = 0.04; break; }
                default: { ox = (RND.nextDouble() - 0.5) * 0.14; oz = (RND.nextDouble() - 0.5) * 0.14; oy = RND.nextDouble() * 0.1; vy = 0.015 + RND.nextDouble() * 0.03; }
            }
            if (Cfg.color == 0 && RND.nextInt(3) == 0) {
                mc.world.addImportantParticleClient(ParticleTypes.FLAME, at.x + ox, at.y + oy, at.z + oz, 0, vy, 0);
                continue;
            }
            int rgb = colour();
            double k = 0.65 + RND.nextDouble() * 0.5;      // flicker
            int rr = (int) Math.min(255, ((rgb >> 16) & 255) * k), gg = (int) Math.min(255, ((rgb >> 8) & 255) * k), bb = (int) Math.min(255, (rgb & 255) * k);
            if (Cfg.color == 0) gg = (int) Math.min(255, gg * (0.5 + RND.nextDouble()));       // red .. orange .. yellow
            float size = Cfg.style == 2 ? 0.5f + RND.nextFloat() * 0.4f : 0.9f + RND.nextFloat() * 0.5f;
            mc.world.addImportantParticleClient(new DustParticleEffect((rr << 16) | (gg << 8) | bb, size), at.x + ox, at.y + oy, at.z + oz, 0, vy, 0);
        }
    }

    /** The flame colour (0xRRGGBB) for the selected preset; rainbow cycles over time. */
    public static int baseColour(int time) { int old = t; t = time; try { return colour(); } finally { t = old; } }

    private static int colour() {
        if (Cfg.color == 12) return java.awt.Color.HSBtoRGB(((t * 6) % 360) / 360f, 0.9f, 1f) & 0xFFFFFF;
        if (Cfg.color == 13) return (Cfg.r << 16) | (Cfg.g << 8) | Cfg.b;
        int[] c = BASE[Cfg.color]; return (c[0] << 16) | (c[1] << 8) | c[2];
    }
}
