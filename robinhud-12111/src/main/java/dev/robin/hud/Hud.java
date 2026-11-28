package dev.robin.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Draws the three HUD panels. All panels are positioned by percentage and scaled, so they fit any screen / GUI scale. */
public final class Hud {
    private Hud() { }

    public static boolean preview;          // true while the settings screen is open: shows sample data

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || (mc.options.hudHidden && !preview)) return;
        if (Cfg.on[0]) potions(ctx, mc);
        if (Cfg.on[1]) armor(ctx, mc);
        if (Cfg.on[2]) inventory(ctx, mc);
    }

    // ------------------------------------------------------------------ helpers
    private static void begin(DrawContext ctx, int idx, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float s = Cfg.scale[idx] / 100f;
        float px = (mc.getWindow().getScaledWidth() - w * s) * Cfg.x[idx] / 100f;
        float py = (mc.getWindow().getScaledHeight() - h * s) * Cfg.y[idx] / 100f;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(px, py);
        ctx.getMatrices().scale(s, s);
    }

    private static void end(DrawContext ctx) { ctx.getMatrices().popMatrix(); }

    private static String time(StatusEffectInstance e) {
        if (e.isInfinite()) return "**:**";
        int s = e.getDuration() / 20;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : "" + n;
    }

    // ------------------------------------------------------------------ potion HUD
    private static void potions(DrawContext ctx, MinecraftClient mc) {
        List<StatusEffectInstance> list = new ArrayList<>(mc.player.getStatusEffects());
        if (list.isEmpty() && preview) {
            list.add(new StatusEffectInstance(StatusEffects.SPEED, 1800, 1));
            list.add(new StatusEffectInstance(StatusEffects.STRENGTH, 600, 0));
            list.add(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 150, 0));
        }
        if (list.isEmpty()) return;
        list.sort(Comparator.comparingInt(StatusEffectInstance::getDuration).reversed());
        TextRenderer tr = mc.textRenderer;
        int w = 0;
        for (StatusEffectInstance e : list) w = Math.max(w, 22 + tr.getWidth(effectName(e)) + 6);
        w = Math.max(w, 80);
        int h = list.size() * 20;
        begin(ctx, 0, w, h);
        int y = 0;
        for (StatusEffectInstance e : list) {
            ctx.fill(0, y, w, y + 18, 0x70000000);
            ctx.fill(0, y, 2, y + 18, e.getEffectType().value().isBeneficial() ? 0xFF55FF55 : 0xFFFF5555);
            ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, InGameHud.getEffectTexture(e.getEffectType()), 4, y + 1, 16, 16);
            ctx.drawTextWithShadow(tr, effectName(e), 24, y + 1, 0xFFFFFFFF);
            boolean low = !e.isInfinite() && e.getDuration() < 200;
            int col = low && (mc.player.age / 5) % 2 == 0 ? 0xFFFF5555 : low ? 0xFFFFAA00 : 0xFFAAAAAA;
            ctx.drawTextWithShadow(tr, time(e), 24, y + 9, col);
            y += 20;
        }
        end(ctx);
    }

    private static Text effectName(StatusEffectInstance e) {
        Text n = e.getEffectType().value().getName();
        return e.getAmplifier() > 0 ? Text.empty().append(n).append(" " + roman(e.getAmplifier() + 1)) : n;
    }

    // ------------------------------------------------------------------ armor HUD
    private static void armor(DrawContext ctx, MinecraftClient mc) {
        PlayerEntity p = mc.player;
        List<ItemStack> items = new ArrayList<>();
        items.add(p.getEquippedStack(EquipmentSlot.HEAD));
        items.add(p.getEquippedStack(EquipmentSlot.CHEST));
        items.add(p.getEquippedStack(EquipmentSlot.LEGS));
        items.add(p.getEquippedStack(EquipmentSlot.FEET));
        if (Cfg.armorHeld) { items.add(p.getMainHandStack()); items.add(p.getOffHandStack()); }
        boolean any = false;
        for (ItemStack s : items) if (!s.isEmpty()) any = true;
        if (!any) return;
        TextRenderer tr = mc.textRenderer;
        int w = Cfg.armorNumbers ? 64 : 22;
        begin(ctx, 1, w, items.size() * 18);
        int y = 0;
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                ctx.fill(0, y, w, y + 17, 0x60000000);
                ctx.drawItem(s, 0, y);
                if (s.isDamageable()) {
                    int max = s.getMaxDamage(), left = max - s.getDamage();
                    float f = left / (float) max;
                    int col = f > 0.6f ? 0xFF55FF55 : f > 0.3f ? 0xFFFFFF55 : f > 0.15f ? 0xFFFFAA00 : 0xFFFF5555;
                    if (Cfg.armorNumbers) ctx.drawTextWithShadow(tr, String.valueOf(left), 19, y + 4, col);
                    ctx.fill(0, y + 15, Math.max(1, Math.round(w * f)), y + 17, 0xFF000000 | (col & 0xFFFFFF));
                } else if (Cfg.armorNumbers && s.getCount() > 1) {
                    ctx.drawTextWithShadow(tr, "x" + s.getCount(), 19, y + 4, 0xFFFFFFFF);
                }
            }
            y += 18;
        }
        end(ctx);
    }

    // ------------------------------------------------------------------ inventory HUD
    private static void inventory(DrawContext ctx, MinecraftClient mc) {
        TextRenderer tr = mc.textRenderer;
        int w = 9 * 18 + 4, h = 3 * 18 + 4;
        begin(ctx, 2, w, h);
        ctx.fill(0, 0, w, h, 0x70000000);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) {
            ItemStack s = mc.player.getInventory().getStack(9 + r * 9 + c);
            int sx = 2 + c * 18, sy = 2 + r * 18;
            ctx.fill(sx, sy, sx + 17, sy + 17, 0x40FFFFFF & 0x30FFFFFF);
            if (!s.isEmpty()) { ctx.drawItem(s, sx, sy); ctx.drawStackOverlay(tr, s, sx, sy); }
        }
        end(ctx);
    }
}
