package com.rclient.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

import java.util.*;

public final class ModuleManager {
    private static final List<Mod> MODULES = new ArrayList<>();
    private static final Map<String, Mod> BY_ID = new HashMap<>();
    private static final Map<Integer, Boolean> KEY_DOWN = new HashMap<>();

    private ModuleManager() { }

    public static void register(Mod m) {
        if (BY_ID.containsKey(m.id)) throw new IllegalStateException("duplicate module id " + m.id);
        MODULES.add(m); BY_ID.put(m.id, m);
    }
    public static List<Mod> all() { return MODULES; }
    public static Mod get(String id) { return BY_ID.get(id); }
    @SuppressWarnings("unchecked")
    public static <T extends Mod> T of(Class<T> c) {
        for (Mod m : MODULES) if (c.isInstance(m)) return (T) m;
        return null;
    }
    public static List<HudModule> huds() {
        List<HudModule> l = new ArrayList<>();
        for (Mod m : MODULES) if (m instanceof HudModule h) l.add(h);
        return l;
    }
    public static boolean on(Class<? extends Mod> c) {
        Mod m = of(c);
        return m != null && m.isEnabled();
    }

    public static void tick(MinecraftClient mc) {
        boolean inGame = mc.player != null && mc.world != null;
        if (mc.currentScreen == null && mc.getWindow() != null) {
            long win = mc.getWindow().getHandle();
            for (Mod m : MODULES) {
                int k = m.toggleKey;
                if (k <= 0) continue;
                boolean down = InputUtil.isKeyPressed(mc.getWindow(), k);
                boolean was = KEY_DOWN.getOrDefault(k, false);
                if (down && !was && inGame) { m.toggle(); Config.touchRecent(m.id); }
                KEY_DOWN.put(k, down);
            }
        }
        for (Mod m : MODULES) {
            try {
                m.alwaysTick(mc);
                if (inGame && m.isEnabled()) m.onTick(mc);
            } catch (Throwable t) { com.rclient.RClient.LOG.error("tick {}", m.id, t); }
        }
    }

    public static void frame(MinecraftClient mc) {
        for (Mod m : MODULES) {
            if (!m.isEnabled()) continue;
            try { m.onFrame(mc); } catch (Throwable t) { com.rclient.RClient.LOG.error("frame {}", m.id, t); }
        }
    }

    public static void join(MinecraftClient mc) {
        for (Mod m : MODULES) { try { if (m.isEnabled()) m.onJoin(mc); } catch (Throwable t) { com.rclient.RClient.LOG.error("join {}", m.id, t); } }
    }
}
