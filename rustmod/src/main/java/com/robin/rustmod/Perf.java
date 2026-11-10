package com.robin.rustmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.client.option.SimpleOption;

/** The performance preset, its undo, and the adaptive render-distance loop. */
public final class Perf {
    /** Particles spawned during the current client tick (reset every tick). */
    public static volatile int particlesThisTick;
    public static volatile boolean on;
    public static volatile int entityCullBlocks;
    public static volatile int particleCap;

    private static int[] fpsHist = new int[3];
    private static int fpsIdx, fpsN;
    private static int cooldown;
    private static int tickCount;

    private Perf() { }

    public static void refreshCfg() {
        on = Cfg.bool("enabled", true);
        entityCullBlocks = Cfg.num("entityCull", 48);
        particleCap = Cfg.num("particleCap", 120);
    }

    private static <T> void set(SimpleOption<T> o, T v) {
        try { if (!o.getValue().equals(v)) o.setValue(v); } catch (Throwable ignored) { }
    }

    private static void bak(String key, Object cur) {
        if (!Cfg.has("bak." + key)) Cfg.set("bak." + key, cur);
    }

    /** Back up the player's current settings once, then apply the fast preset. */
    public static void enable(MinecraftClient mc) {
        GameOptions o = mc.options;
        bak("graphics", o.getGraphicsMode().getValue().name());
        bak("particles", o.getParticles().getValue().name());
        bak("clouds", o.getCloudRenderMode().getValue().name());
        bak("shadows", o.getEntityShadows().getValue());
        bak("blend", o.getBiomeBlendRadius().getValue());
        bak("ao", o.getAo().getValue());
        bak("vsync", o.getEnableVsync().getValue());
        bak("fps", o.getMaxFps().getValue());
        bak("view", o.getViewDistance().getValue());
        bak("sim", o.getSimulationDistance().getValue());
        bak("edist", o.getEntityDistanceScaling().getValue());

        set(o.getGraphicsMode(), GraphicsMode.FAST);
        set(o.getParticles(), ParticlesMode.MINIMAL);
        set(o.getCloudRenderMode(), CloudRenderMode.OFF);
        set(o.getEntityShadows(), false);
        set(o.getBiomeBlendRadius(), 0);
        set(o.getAo(), false);
        set(o.getEnableVsync(), false);
        set(o.getMaxFps(), 260);
        set(o.getSimulationDistance(), clamp(Cfg.num("simDist", 5), 5, 32));
        set(o.getEntityDistanceScaling(), 0.75);
        set(o.getViewDistance(), clamp(Cfg.num("maxRd", 12), 2, 32));
        Cfg.set("enabled", true);
        Cfg.save();
        o.write();
        refreshCfg();
        fpsN = 0; cooldown = 0;
    }

    /** Put the player's own settings back. */
    public static void disable(MinecraftClient mc) {
        GameOptions o = mc.options;
        try { set(o.getGraphicsMode(), GraphicsMode.valueOf(Cfg.str("bak.graphics"))); } catch (Throwable ignored) { }
        try { set(o.getParticles(), ParticlesMode.valueOf(Cfg.str("bak.particles"))); } catch (Throwable ignored) { }
        try { set(o.getCloudRenderMode(), CloudRenderMode.valueOf(Cfg.str("bak.clouds"))); } catch (Throwable ignored) { }
        if (Cfg.has("bak.shadows")) set(o.getEntityShadows(), Cfg.bool("bak.shadows", true));
        if (Cfg.has("bak.blend")) set(o.getBiomeBlendRadius(), Cfg.num("bak.blend", 2));
        if (Cfg.has("bak.ao")) set(o.getAo(), Cfg.bool("bak.ao", true));
        if (Cfg.has("bak.vsync")) set(o.getEnableVsync(), Cfg.bool("bak.vsync", true));
        if (Cfg.has("bak.fps")) set(o.getMaxFps(), Cfg.num("bak.fps", 120));
        if (Cfg.has("bak.sim")) set(o.getSimulationDistance(), Cfg.num("bak.sim", 12));
        if (Cfg.has("bak.view")) set(o.getViewDistance(), Cfg.num("bak.view", 12));
        if (Cfg.has("bak.edist")) {
            try { set(o.getEntityDistanceScaling(), Double.parseDouble(Cfg.str("bak.edist"))); } catch (Throwable ignored) { }
        }
        for (String k : new String[]{"graphics", "particles", "clouds", "shadows", "blend", "ao", "vsync", "fps", "view", "sim", "edist"})
            Cfg.remove("bak." + k);
        Cfg.set("enabled", false);
        Cfg.save();
        o.write();
        refreshCfg();
    }

    /** Called every client tick. */
    public static void tick(MinecraftClient mc) {
        particlesThisTick = 0;
        if (!on || mc.world == null || !Cfg.bool("adaptive", true)) return;
        if (++tickCount < 20) return;
        tickCount = 0;
        int fps = mc.getCurrentFps();
        fpsHist[fpsIdx] = fps;
        fpsIdx = (fpsIdx + 1) % fpsHist.length;
        if (fpsN < fpsHist.length) { fpsN++; return; }
        int sum = 0;
        for (int v : fpsHist) sum += v;
        int avg = sum / fpsHist.length;
        if (cooldown > 0) { cooldown--; return; }
        int target = Cfg.num("targetFps", 60);
        int min = clamp(Cfg.num("minRd", 4), 2, 32), max = clamp(Cfg.num("maxRd", 12), min, 32);
        int rd = mc.options.getViewDistance().getValue();
        if (avg < target - 5 && rd > min) {
            mc.options.getViewDistance().setValue(rd - 1);
            cooldown = 3;
        } else if (avg > target + 45 && rd < max) {
            mc.options.getViewDistance().setValue(rd + 1);
            cooldown = 6;
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
