package dev.kodari.autocartpvp;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Crossbow mode: charge crossbow if needed, then in ONE tick: rail, TNT minecart, flint-and-steel fire on the line
 * between you and the cart, aim at the cart and fire the crossbow through the fire so the arrow arrives burning.
 * Bow mode (config off): rail, cart, then draw and release a Flame bow.
 */
public class CartSequence {
    enum Stage { IDLE, RAIL, CART, DRAW, LOAD, BURST }

    private Stage stage = Stage.IDLE;
    private BlockHitResult target;
    private BlockPos railPos;
    private int wait, originalSlot;
    private boolean loading;

    public boolean active() { return stage != Stage.IDLE; }

    public void start(MinecraftClient mc) {
        if (active() || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        CartConfig cfg = CartConfig.get();
        if (!cfg.enabled) { message(mc, "Disabled"); return; }
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler || mc.player.isUsingItem()) {
            message(mc, "Close inventories and stop using items first"); return;
        }
        Equipment eq = Equipment.scan(mc.player.getInventory(), cfg.autoDetect);
        if (!eq.ready(cfg.crossbowMode)) {
            message(mc, cfg.crossbowMode ? "Missing rail, TNT minecart, flint and steel, or crossbow" : "Missing rail, TNT minecart, Flame bow, or arrows"); return;
        }
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK || bhr.getSide() != Direction.UP
                || mc.player.getEyePos().squaredDistanceTo(bhr.getPos()) > (double) (cfg.range * cfg.range)) {
            message(mc, "Aim at the top of a block within range"); return;
        }
        target = bhr;
        railPos = bhr.getBlockPos().offset(bhr.getSide());
        originalSlot = mc.player.getInventory().selectedSlot;
        wait = 0; loading = false;
        if (cfg.crossbowMode) {
            boolean charged = CrossbowItem.isCharged(mc.player.getInventory().getStack(eq.crossbow()));
            stage = charged ? Stage.BURST : Stage.LOAD;
        } else stage = Stage.RAIL;
    }

    public void tick(MinecraftClient mc) {
        if (!active()) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { stage = Stage.IDLE; return; }
        CartConfig cfg = CartConfig.get();
        if (!cfg.enabled || mc.currentScreen != null || mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            cancel(mc, "Interrupted"); return;
        }
        if (mc.player.getEyePos().squaredDistanceTo(target.getPos()) > (double) (cfg.range * cfg.range)) { cancel(mc, "Target out of range"); return; }
        if (wait-- > 0) return;

        Equipment eq = Equipment.scan(mc.player.getInventory(), cfg.autoDetect);
        switch (stage) {
            case LOAD -> load(mc, eq, cfg);
            case BURST -> burst(mc, eq);
            case RAIL -> {
                if (!select(mc, eq.rail()) || !mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, target).isAccepted()) {
                    cancel(mc, "Rail placement failed"); return;
                }
                stage = Stage.CART;
                wait = Math.max(1, cfg.delay);
            }
            case CART -> {
                if (!placeCart(mc, eq)) return;
                eq = Equipment.scan(mc.player.getInventory(), cfg.autoDetect);
                if (!eq.arrows() || !select(mc, eq.bow())
                        || !mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted()) {
                    cancel(mc, "Could not draw Flame bow"); return;
                }
                mc.options.useKey.setPressed(true);
                stage = Stage.DRAW;
                wait = 0;
            }
            case DRAW -> {
                if (!mc.player.isUsingItem() || mc.player.getActiveHand() != Hand.MAIN_HAND) { cancel(mc, "Bow draw interrupted"); return; }
                if (mc.player.getItemUseTime() < Math.max(3, cfg.drawTicks)) return;
                mc.options.useKey.setPressed(false);
                mc.interactionManager.stopUsingItem(mc.player);
                finish(mc);
                message(mc, "Sequence complete");
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------- crossbow
    private void load(MinecraftClient mc, Equipment eq, CartConfig cfg) {
        if (!loading) {
            if (!cfg.autoLoad) { cancel(mc, "Crossbow is not loaded"); return; }
            if (!eq.arrows() && !hasProjectile(mc)) { cancel(mc, "No arrows to load"); return; }
            if (!select(mc, eq.crossbow()) || !mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted()) {
                cancel(mc, "Could not start loading"); return;
            }
            mc.options.useKey.setPressed(true);
            loading = true;
            return;
        }
        ItemStack main = mc.player.getMainHandStack();
        if (!mc.player.isUsingItem()) {
            mc.options.useKey.setPressed(false);
            if (CrossbowItem.isCharged(main)) { stage = Stage.BURST; wait = 2; } else cancel(mc, "Loading failed");
            return;
        }
        if (mc.player.getItemUseTime() >= CrossbowItem.getPullTime(main, mc.player) + 1) {
            mc.options.useKey.setPressed(false);
            mc.interactionManager.stopUsingItem(mc.player);
            stage = Stage.BURST; wait = 3;
        }
    }

    private boolean hasProjectile(MinecraftClient mc) { return mc.player.getOffHandStack().getItem() instanceof net.minecraft.item.ArrowItem; }

    /** Everything in a single client tick: rail, cart, fire, aim, shoot. */
    private void burst(MinecraftClient mc, Equipment eq) {
        if (!eq.ready(true)) { cancel(mc, "Missing items"); return; }
        if (!CrossbowItem.isCharged(mc.player.getInventory().getStack(eq.crossbow()))) { cancel(mc, "Crossbow is not loaded"); return; }

        Vec3d eye = mc.player.getEyePos();
        Vec3d c = new Vec3d(railPos.getX() + 0.5, railPos.getY() + 0.25, railPos.getZ() + 0.5);
        double hx = eye.x - c.x, hz = eye.z - c.z, hd = Math.sqrt(hx * hx + hz * hz);
        if (hd < 1.8) { cancel(mc, "Too close: step back at least 2 blocks"); return; }
        // point on the line eye -> cart, 1.2 blocks before the cart: the fire goes in that cell so the arrow passes through it
        double qx = c.x + hx / hd * 1.2, qz = c.z + hz / hd * 1.2, qy = c.y + (eye.y - c.y) * (1.2 / hd);
        BlockPos fire = new BlockPos(net.minecraft.util.math.MathHelper.floor(qx), net.minecraft.util.math.MathHelper.floor(qy), net.minecraft.util.math.MathHelper.floor(qz));
        BlockPos sup = fire.down();
        BlockPos pb = mc.player.getBlockPos();
        if (fire.getY() != railPos.getY() || fire.equals(railPos) || fire.equals(pb) || fire.equals(pb.up())) { cancel(mc, "Bad angle or too close for the fire"); return; }
        if (!mc.world.getBlockState(fire).isAir() || !mc.world.getBlockState(sup).isSideSolidFullSquare(mc.world, sup, Direction.UP)) {
            cancel(mc, "No free solid ground for the fire in front of you"); return;
        }

        // 1. rail
        if (!select(mc, eq.rail()) || !mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, target).isAccepted()) { cancel(mc, "Rail placement failed"); return; }
        // 2. TNT minecart
        if (!placeCart(mc, Equipment.scan(mc.player.getInventory(), CartConfig.get().autoDetect))) return;
        // 3. fire with flint and steel
        eq = Equipment.scan(mc.player.getInventory(), CartConfig.get().autoDetect);
        BlockHitResult fireHit = new BlockHitResult(new Vec3d(sup.getX() + 0.5, sup.getY() + 1.0, sup.getZ() + 0.5), Direction.UP, sup, false);
        if (!select(mc, eq.flint()) || !mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, fireHit).isAccepted()) { cancel(mc, "Could not light the fire"); return; }
        // 4. aim at the cart and shoot the loaded crossbow through the fire
        float oldYaw = mc.player.getYaw(), oldPitch = mc.player.getPitch();
        double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
        mc.player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        mc.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        eq = Equipment.scan(mc.player.getInventory(), CartConfig.get().autoDetect);
        boolean shot = select(mc, eq.crossbow()) && mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted();
        mc.player.setYaw(oldYaw); mc.player.setPitch(oldPitch);
        if (!shot) { cancel(mc, "Crossbow shot failed"); return; }
        finish(mc);
        message(mc, "Boom");
    }

    private boolean placeCart(MinecraftClient mc, Equipment eq) {
        if (!mc.world.getBlockState(railPos).isOf(Blocks.RAIL) || !select(mc, eq.cart())) { cancel(mc, "Rail unavailable"); return false; }
        Vec3d p = new Vec3d(railPos.getX() + 0.5, railPos.getY() + 0.1, railPos.getZ() + 0.5);
        BlockHitResult onRail = new BlockHitResult(p, Direction.UP, railPos, false);
        if (!mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, onRail).isAccepted()) { cancel(mc, "Minecart placement failed"); return false; }
        return true;
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
        if (stage == Stage.DRAW || stage == Stage.LOAD) {
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
