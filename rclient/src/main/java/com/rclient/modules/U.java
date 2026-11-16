package com.rclient.modules;

import com.rclient.core.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.IdentityHashMap;
import java.util.Map;

/** Small shared helpers for modules. */
public final class U {
    private static final Map<Setting, Boolean> DOWN = new IdentityHashMap<>();
    private U() { }

    public static String id(ItemStack s) { return Registries.ITEM.getId(s.getItem()).toString(); }

    public static boolean held(MinecraftClient mc, Setting key) {
        int k = key.key();
        return k > 0 && mc.currentScreen == null && mc.getWindow() != null && InputUtil.isKeyPressed(mc.getWindow(), k);
    }

    /** True once per key press (rising edge). Call every tick from one place only. */
    public static boolean edge(MinecraftClient mc, Setting key) {
        boolean now = held(mc, key);
        boolean was = DOWN.getOrDefault(key, false);
        DOWN.put(key, now);
        return now && !was;
    }

    public static int countInv(MinecraftClient mc, java.util.function.Predicate<ItemStack> p) {
        if (mc.player == null) return 0;
        int n = 0;
        for (ItemStack s : mc.player.getInventory().getMainStacks()) if (!s.isEmpty() && p.test(s)) n += s.getCount();
        ItemStack off = mc.player.getOffHandStack();
        if (!off.isEmpty() && p.test(off)) n += off.getCount();
        return n;
    }

    public static String mmss(long totalSeconds) {
        long h = totalSeconds / 3600, m = totalSeconds / 60 % 60, s = totalSeconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}
