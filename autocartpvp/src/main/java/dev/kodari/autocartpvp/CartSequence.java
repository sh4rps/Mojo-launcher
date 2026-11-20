package dev.kodari.autocartpvp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Place rail -> place TNT minecart -> draw Flame bow -> shoot, all in a few ticks. */
public class CartSequence {
    enum Stage { IDLE, RAIL, CART, DRAW }

    private Stage stage = Stage.IDLE;
    private BlockHitResult target;
    private BlockPos railPos;
    private int wait, originalSlot, drawn;

    public boolean active() { return stage != Stage.IDLE; }

    public void start(MinecraftClient mc) {
        if (active() || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        CartConfig cfg = CartConfig.get();
        if (!cfg.enabled) { message(mc, "Disabled"); return; }
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler || mc.player.isUsingItem()) {
            message(mc, "Close inventories and stop using items first"); return;
        }
        if (!Equipment.scan(mc.player.getInventory(), cfg.autoDetect).ready()) {
            message(mc, "Missing rail, TNT minecart, Flame bow, or arrows"); return;
        }
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK || bhr.getSide() != Direction.UP
                || mc.player.getEyePos().squaredDistanceTo(bhr.getPos()) > (double) (cfg.range * cfg.range)) {
            message(mc, "Aim at the top of a block within range"); return;
        }
        target = bhr;
        railPos = bhr.getBlockPos().offset(bhr.getSide());
        originalSlot = mc.player.getInventory().selectedSlot;
        wait = 0; drawn = 0;
        stage = Stage.RAIL;
    }

    public void tick(MinecraftClient mc) {
        if (!active()) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { stage = Stage.IDLE; return; }
        if (!CartConfig.get().enabled || mc.currentScreen != null || mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            cancel(mc, "Interrupted"); return;
        }
        float range = CartConfig.get().range;
        if (mc.player.getEyePos().squaredDistanceTo(target.getPos()) > (double) (range * range)) { cancel(mc, "Target out of range"); return; }
        if (wait-- > 0) return;

        Equipment eq = Equipment.scan(mc.player.getInventory(), CartConfig.get().autoDetect);
        switch (stage) {
            case RAIL -> {
                if (!select(mc, eq.rail()) || !mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, target).isAccepted()) {
                    cancel(mc, "Rail placement failed"); return;
                }
                stage = Stage.CART;
                wait = Math.max(1, CartConfig.get().delay);
            }
            case CART -> {
                if (!mc.world.getBlockState(railPos).isOf(Blocks.RAIL) || !select(mc, eq.cart())) {
                    cancel(mc, "Rail unavailable or placement rejected"); return;
                }
                Vec3d p = new Vec3d(railPos.getX() + 0.5, railPos.getY() + 0.1, railPos.getZ() + 0.5);
                BlockHitResult onRail = new BlockHitResult(p, Direction.UP, railPos, false);
                if (!mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, onRail).isAccepted()) {
                    cancel(mc, "Minecart placement failed"); return;
                }
                // cart is down: switch to the bow and start drawing in the same tick
                eq = Equipment.scan(mc.player.getInventory(), CartConfig.get().autoDetect);
                if (!eq.arrows() || !select(mc, eq.bow())
                        || !mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted()) {
                    cancel(mc, "Could not draw Flame bow"); return;
                }
                mc.options.useKey.setPressed(true);
                stage = Stage.DRAW;
                wait = 0; drawn = 0;
            }
            case DRAW -> {
                if (!mc.player.isUsingItem() || mc.player.getActiveHand() != Hand.MAIN_HAND) { cancel(mc, "Bow draw interrupted"); return; }
                drawn++;
                // Release only once the bow is charged enough to actually fire (>= 3 ticks), with margin for server tick lag.
                if (mc.player.getItemUseTime() < Math.max(3, CartConfig.get().drawTicks)) return;
                mc.options.useKey.setPressed(false);
                mc.interactionManager.stopUsingItem(mc.player);
                finish(mc);
                message(mc, "Sequence complete");
            }
            default -> { }
        }
    }

    private boolean select(MinecraftClient mc, int slot) {
        if (slot < 0) return false;
        PlayerInventory inv = mc.player.getInventory();
        if (slot < 9) {
            if (inv.selectedSlot != slot) inv.selectedSlot = slot;
        } else {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, inv.selectedSlot, SlotActionType.SWAP, mc.player);
        }
        return true;
    }

    private void cancel(MinecraftClient mc, String why) {
        if (stage == Stage.DRAW) {
            mc.options.useKey.setPressed(false);
            if (mc.player.isUsingItem()) mc.interactionManager.stopUsingItem(mc.player);
        }
        finish(mc);
        message(mc, why);
    }

    private void finish(MinecraftClient mc) {
        mc.player.getInventory().selectedSlot = originalSlot;
        stage = Stage.IDLE;
    }

    private void message(MinecraftClient mc, String s) {
        if (CartConfig.get().debug && mc.player != null) mc.player.sendMessage(Text.literal("[AutoCartPvP] " + s), true);
    }
}
