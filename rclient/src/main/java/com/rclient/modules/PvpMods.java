package com.rclient.modules;

import com.rclient.core.*;
import com.rclient.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

public final class PvpMods {
    private PvpMods() { }

    // shared combat state fed by the attack event
    public static LivingEntity lastTarget;
    public static long lastTargetTime;
    public static double lastReach = -1;
    public static long lastReachTime;

    public static void onAttack(Entity target, EntityHitResult hit) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (target instanceof LivingEntity le) { lastTarget = le; lastTargetTime = System.currentTimeMillis(); }
        double d = hit != null ? hit.getPos().distanceTo(mc.player.getEyePos())
                : target.getBoundingBox().getCenter().distanceTo(mc.player.getEyePos());
        lastReach = d; lastReachTime = System.currentTimeMillis();
        Combo c = ModuleManager.of(Combo.class);
        if (c != null && target instanceof LivingEntity le) c.pending = le;
    }

    public static void register() {
        ModuleManager.register(new Cps());
        ModuleManager.register(new Combo());
        ModuleManager.register(new Reach());
        ModuleManager.register(new CrosshairMod());
        ModuleManager.register(new Armor());
        ModuleManager.register(new Potions());
        ModuleManager.register(new PvpInfo());
        ModuleManager.register(new Cooldown());
        ModuleManager.register(new Keystrokes());
        ModuleManager.register(new Uhc());
        ModuleManager.register(new Tnt());
    }

    // ---------------------------------------------------------------- CPS
    public static final class Cps extends TextHud {
        private final Deque<Long> left = new ArrayDeque<>(), right = new ArrayDeque<>();
        private boolean pl, pr;
        final Setting showRight = add(Setting.bool("right", "Show right click", true));
        final Setting prefix = add(Setting.string("prefix", "Label", "CPS"));
        public Cps() { super("cps", "CPS Counter", Category.PVP, "Clicks per second for left and right mouse buttons.", 0.01, 0.12); }
        @Override public void onFrame(MinecraftClient mc) {
            if (mc.currentScreen != null || mc.getWindow() == null) { pl = pr = false; return; }
            long h = mc.getWindow().getHandle();
            boolean l = GLFW.glfwGetMouseButton(h, 0) == GLFW.GLFW_PRESS, r = GLFW.glfwGetMouseButton(h, 1) == GLFW.GLFW_PRESS;
            long now = System.currentTimeMillis();
            if (l && !pl) left.add(now);
            if (r && !pr) right.add(now);
            pl = l; pr = r;
        }
        private int cnt(Deque<Long> q) {
            long now = System.currentTimeMillis();
            while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
            return q.size();
        }
        @Override protected String text(MinecraftClient mc) {
            return prefix.str() + ": " + cnt(left) + (showRight.bool() ? " | " + cnt(right) : "");
        }
        @Override protected String sample() { return prefix.str() + ": 8 | 3"; }
    }

    // -------------------------------------------------------------- Combo
    public static final class Combo extends TextHud {
        LivingEntity pending;
        int combo, lastHurt;
        long lastHit;
        final Setting timeout = add(Setting.num("timeout", "Reset after (s)", 3, 1, 10, 0.5));
        public Combo() { super("combo", "Combo Counter", Category.PVP, "Counts consecutive hits you land without being hit back.", 0.01, 0.17); }
        @Override public void onTick(MinecraftClient mc) {
            if (pending != null) {
                if (pending.hurtTime > 0) { combo++; lastHit = System.currentTimeMillis(); }
                pending = null;
            }
            int ht = mc.player.hurtTime;
            if (ht > 0 && lastHurt == 0) combo = 0;
            lastHurt = ht;
            if (combo > 0 && System.currentTimeMillis() - lastHit > timeout.num() * 1000) combo = 0;
        }
        @Override protected String text(MinecraftClient mc) { return combo > 0 ? "Combo: " + combo : null; }
        @Override protected String sample() { return "Combo: 4"; }
    }

    // -------------------------------------------------------------- Reach
    public static final class Reach extends TextHud {
        final Setting hold = add(Setting.num("hold", "Show for (s)", 4, 1, 15, 1));
        final Setting decimals = add(Setting.num("decimals", "Decimals", 2, 0, 3, 1));
        public Reach() { super("reach", "Reach Display", Category.PVP, "Distance of your last attack to the target.", 0.01, 0.22); }
        @Override protected String text(MinecraftClient mc) {
            if (lastReach < 0 || System.currentTimeMillis() - lastReachTime > hold.num() * 1000) return null;
            return String.format("Reach: %." + decimals.integer() + "f blocks", lastReach);
        }
        @Override protected String sample() { return "Reach: 2.84 blocks"; }
    }

    // ---------------------------------------------------- Custom crosshair
    public static final class CrosshairMod extends Module {
        public final Setting style = add(Setting.choice("style", "Style", "Cross", "Cross", "Dot", "Circle", "Square"));
        public final Setting color = add(Setting.color("color", "Color", 0xFFFFFFFF));
        public final Setting hitColor = add(Setting.color("hitcolor", "Color on enemy", 0xFFF87171));
        public final Setting size = add(Setting.num("size", "Size", 5, 1, 15, 1));
        public final Setting gap = add(Setting.num("gap", "Gap", 2, 0, 8, 1));
        public final Setting thick = add(Setting.num("thickness", "Thickness", 1, 1, 4, 1));
        public final Setting outline = add(Setting.bool("outline", "Outline", true));
        public CrosshairMod() { super("crosshair", "Custom Crosshair", Category.PVP, "Replaces the vanilla crosshair with a customizable one."); }

        public void draw(DrawContext g, MinecraftClient mc) {
            int cx = mc.getWindow().getScaledWidth() / 2, cy = mc.getWindow().getScaledHeight() / 2;
            int c = color.color();
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) c = hitColor.color();
            int s = size.integer(), gp = gap.integer(), t = thick.integer(), a = t / 2;
            switch (style.str()) {
                case "Dot" -> {
                    if (outline.bool()) g.fill(cx - a - 1, cy - a - 1, cx - a + t + 1, cy - a + t + 1, 0xFF000000);
                    g.fill(cx - a, cy - a, cx - a + t, cy - a + t, c);
                }
                case "Circle", "Square" -> {
                    int r = s + gp;
                    if (style.str().equals("Square")) {
                        g.fill(cx - r, cy - r, cx + r + 1, cy - r + t, c); g.fill(cx - r, cy + r + 1 - t, cx + r + 1, cy + r + 1, c);
                        g.fill(cx - r, cy - r, cx - r + t, cy + r + 1, c); g.fill(cx + r + 1 - t, cy - r, cx + r + 1, cy + r + 1, c);
                    } else {
                        for (int i = 0; i < 48; i++) {
                            double ang = i * Math.PI * 2 / 48;
                            int x = cx + (int) Math.round(Math.cos(ang) * r), y = cy + (int) Math.round(Math.sin(ang) * r);
                            g.fill(x, y, x + t, y + t, c);
                        }
                    }
                }
                default -> {
                    int[][] arms = {{-gp - s, -a, -gp, -a + t}, {gp, -a, gp + s, -a + t}, {-a, -gp - s, -a + t, -gp}, {-a, gp, -a + t, gp + s}};
                    if (outline.bool()) for (int[] r : arms) g.fill(cx + r[0] - 1, cy + r[1] - 1, cx + r[2] + 1, cy + r[3] + 1, 0xFF000000);
                    for (int[] r : arms) g.fill(cx + r[0], cy + r[1], cx + r[2], cy + r[3], c);
                }
            }
        }
    }

    // ---------------------------------------------------------- Armor
    public static final class Armor extends HudModule {
        final Setting hand = add(Setting.bool("hand", "Show held item", true));
        final Setting mode = add(Setting.choice("mode", "Durability", "Value", "Value", "Percent", "Hidden"));
        public Armor() { super("armor", "Armor Status", Category.PVP, "Shows worn armor and held item with durability.", 0.01, 0.4); }
        @Override protected void draw(DrawContext g, MinecraftClient mc, boolean editing) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            int y = 0, mw = 16;
            int n = hand.bool() ? 5 : 4;
            for (int i = 0; i < n; i++) {
                ItemStack st = mc.player == null ? ItemStack.EMPTY : (i < 4 ? mc.player.getEquippedStack(slots[i]) : mc.player.getMainHandStack());
                if (!st.isEmpty()) {
                    g.drawItem(st, 0, y);
                    if (st.isDamageable() && !mode.str().equals("Hidden")) {
                        int left = st.getMaxDamage() - st.getDamage();
                        String t = mode.str().equals("Percent") ? (left * 100 / Math.max(1, st.getMaxDamage())) + "%" : String.valueOf(left);
                        float f = left / (float) Math.max(1, st.getMaxDamage());
                        int c = Ui.lerp(0xFFF87171, 0xFF34D399, f);
                        g.drawText(mc.textRenderer, t, 19, y + 4, c, true);
                        mw = Math.max(mw, 19 + mc.textRenderer.getWidth(t));
                    }
                } else if (editing) {
                    Ui.outline(g, 0, y, 16, 16, 0x55FFFFFF);
                }
                y += 18;
            }
            w = mw; h = y - 2;
        }
    }

    // --------------------------------------------------------- Potions
    public static final class Potions extends TextHud {
        public Potions() { super("potions", "Potion Effects HUD", Category.PVP, "Lists active status effects with remaining time.", 0.88, 0.3); }
        @Override protected String text(MinecraftClient mc) {
            if (mc.player == null || mc.player.getStatusEffects().isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            for (StatusEffectInstance e : mc.player.getStatusEffects()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(e.getEffectType().value().getName().getString());
                if (e.getAmplifier() > 0) sb.append(' ').append(e.getAmplifier() + 1);
                sb.append(' ').append(e.isInfinite() ? "**:**" : U.mmss(e.getDuration() / 20));
            }
            return sb.toString();
        }
        @Override protected String sample() { return "Speed 2 0:42\nStrength 1:10"; }
    }

    // ---------------------------------------------------------- PvP info
    public static final class PvpInfo extends TextHud {
        public PvpInfo() { super("pvpinfo", "PvP Information", Category.PVP, "Your health plus health and distance of your current target.", 0.01, 0.3); }
        @Override protected String text(MinecraftClient mc) {
            if (mc.player == null) return null;
            StringBuilder sb = new StringBuilder(String.format("HP %.1f/%.0f", mc.player.getHealth(), mc.player.getMaxHealth()));
            LivingEntity t = lastTarget;
            if (t != null && t.isAlive() && System.currentTimeMillis() - lastTargetTime < 8000) {
                sb.append(String.format("\n%s %.1f HP | %.1fm", t.getName().getString(), t.getHealth(), mc.player.distanceTo(t)));
            }
            return sb.toString();
        }
        @Override protected String sample() { return "HP 20.0/20\nPlayer 14.0 HP | 3.1m"; }
    }

    // -------------------------------------------------------- Cooldown
    public static final class Cooldown extends HudModule {
        final Setting always = add(Setting.bool("always", "Always visible", false));
        final Setting width = add(Setting.num("width", "Bar width", 60, 20, 160, 2));
        public Cooldown() { super("cooldown", "Attack Cooldown", Category.PVP, "Bar showing your weapon's attack cooldown charge.", 0.45, 0.62); color.value = 0xFF8B5CF6; }
        @Override protected void draw(DrawContext g, MinecraftClient mc, boolean editing) {
            float p = mc.player == null ? 0.6f : mc.player.getAttackCooldownProgress(0f);
            int bw = width.integer();
            w = bw; h = 5;
            if (p >= 1f && !always.bool() && !editing) { w = 0; h = 0; return; }
            g.fill(0, 0, bw, 5, 0x99000000);
            g.fill(1, 1, 1 + (int) ((bw - 2) * Math.min(1f, p)), 4, p >= 1f ? 0xFF34D399 : col());
        }
    }

    // ------------------------------------------------------- Keystrokes
    public static final class Keystrokes extends HudModule {
        final Setting mouse = add(Setting.bool("mouse", "Mouse buttons", true));
        final Setting space = add(Setting.bool("space", "Space bar", true));
        final Setting pressedColor = add(Setting.color("pressed", "Pressed color", 0xFF8B5CF6));
        public Keystrokes() { super("keystrokes", "Keystrokes", Category.PVP, "Shows which movement keys and mouse buttons are held.", 0.01, 0.55); }
        private void key(DrawContext g, MinecraftClient mc, int x, int y, int bw, int bh, String label, boolean down) {
            Ui.round(g, x, y, bw, bh, down ? pressedColor.color() : 0x88000000);
            g.drawText(mc.textRenderer, label, x + (bw - mc.textRenderer.getWidth(label)) / 2, y + (bh - 8) / 2, down ? 0xFFFFFFFF : col(), false);
        }
        @Override protected void draw(DrawContext g, MinecraftClient mc, boolean editing) {
            var o = mc.options;
            key(g, mc, 20, 0, 18, 18, "W", o.forwardKey.isPressed());
            key(g, mc, 0, 19, 18, 18, "A", o.leftKey.isPressed());
            key(g, mc, 20, 19, 18, 18, "S", o.backKey.isPressed());
            key(g, mc, 40, 19, 18, 18, "D", o.rightKey.isPressed());
            int y = 38;
            if (mouse.bool()) {
                key(g, mc, 0, y, 28, 16, "LMB", o.attackKey.isPressed());
                key(g, mc, 30, y, 28, 16, "RMB", o.useKey.isPressed());
                y += 17;
            }
            if (space.bool()) { key(g, mc, 0, y, 58, 10, "", o.jumpKey.isPressed()); y += 11; }
            w = 58; h = y - 1;
        }
    }

    // --------------------------------------------------------------- UHC
    public static final class Uhc extends TextHud {
        public Uhc() { super("uhc", "UHC Overlay", Category.PVP, "Golden apples, heads, health, absorption and XP level at a glance.", 0.88, 0.5); }
        @Override protected String text(MinecraftClient mc) {
            if (mc.player == null) return null;
            int gap = U.countInv(mc, s -> U.id(s).equals("minecraft:golden_apple") || U.id(s).equals("minecraft:enchanted_golden_apple"));
            int heads = U.countInv(mc, s -> U.id(s).equals("minecraft:player_head"));
            return String.format("Gapples: %d\nHeads: %d\nHP: %.1f (+%.1f)\nLevel: %d", gap, heads, mc.player.getHealth(), mc.player.getAbsorptionAmount(), mc.player.experienceLevel);
        }
        @Override protected String sample() { return "Gapples: 6\nHeads: 2\nHP: 20.0 (+4.0)\nLevel: 12"; }
    }

    // -------------------------------------------------------------- TNT
    public static final class Tnt extends TextHud {
        final Setting range = add(Setting.num("range", "Range (blocks)", 32, 8, 128, 4));
        public Tnt() { super("tnt", "TNT Countdown", Category.PVP, "Fuse time and distance of nearby primed TNT.", 0.45, 0.2); }
        @Override protected String text(MinecraftClient mc) {
            if (mc.world == null || mc.player == null) return null;
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof TntEntity t) {
                    float d = mc.player.distanceTo(t);
                    if (d > range.num()) continue;
                    if (n++ >= 4) break;
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(String.format("TNT %.1fs (%.0fm)", t.getFuse() / 20.0, d));
                }
            }
            return n == 0 ? null : sb.toString();
        }
        @Override protected String sample() { return "TNT 2.4s (6m)"; }
    }
}
