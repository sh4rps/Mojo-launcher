package dev.kodari.autocartpvp;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.math.Box;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
    enum Stage { IDLE, RAIL, CART, DRAW, LOAD, SHIELD, BURST, HOLD, RETREAT, SNEAK }

    private Stage stage = Stage.IDLE;
    private BlockHitResult target;
    private BlockPos railPos;
    private int wait, originalSlot;
    private boolean loading, shieldOn, shieldUp;
    private int retreatLeft, sneakTicks;
    private Vec3d cartCenter = Vec3d.ZERO;

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
        if (!(hit instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK || bhr.getSide() == Direction.DOWN) {
            message(mc, "Aim at a block (top or side) within range"); return;
        }
        // rail always goes on top of the aimed block, whatever face was hit
        BlockPos base = bhr.getSide() == Direction.UP ? bhr.getBlockPos() : bhr.getBlockPos();
        BlockPos above = base.up();
        if (!mc.world.getBlockState(above).isAir() && !mc.world.getBlockState(above).isReplaceable()) { message(mc, "No free space above that block"); return; }
        target = new BlockHitResult(new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5), Direction.UP, base, false);
        if (mc.player.getEyePos().squaredDistanceTo(target.getPos()) > (double) (cfg.range + 1) * (cfg.range + 1)) { message(mc, "Too far: move closer"); return; }
        railPos = above;
        originalSlot = mc.player.getInventory().getSelectedSlot();
        wait = 0; loading = false; shieldUp = false;
        shieldOn = cfg.crossbowMode && cfg.blastShield && (eq.shield() >= 0 || mc.player.getOffHandStack().isOf(Items.SHIELD));
        if (cfg.crossbowMode && cfg.blastShield && !shieldOn) message(mc, "No shield found: blast will hurt");
        if (cfg.crossbowMode) {
            boolean charged = CrossbowItem.isCharged(mc.player.getInventory().getStack(eq.crossbow()));
            stage = charged ? (shieldOn ? Stage.SHIELD : Stage.BURST) : Stage.LOAD;
        } else stage = Stage.RAIL;
    }

    public void tick(MinecraftClient mc) {
        if (!active()) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { stage = Stage.IDLE; return; }
        CartConfig cfg = CartConfig.get();
        if (!cfg.enabled || mc.currentScreen != null || mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            cancel(mc, "Interrupted"); return;
        }
        if (stage != Stage.HOLD && stage != Stage.RETREAT && stage != Stage.SNEAK && mc.player.getEyePos().squaredDistanceTo(target.getPos()) > (double) ((cfg.range + 1) * (cfg.range + 1))) { cancel(mc, "Target out of range"); return; }
        if (wait-- > 0) return;

        Equipment eq = Equipment.scan(mc.player.getInventory(), cfg.autoDetect);
        switch (stage) {
            case LOAD -> load(mc, eq, cfg);
            case SHIELD -> raiseShield(mc, cfg);
            case BURST -> burst(mc, eq);
            case RETREAT -> {
                if (retreatLeft-- <= 0) {
                    releaseMove();
                    if (cfg.sneakUntilBlast) { stage = Stage.SNEAK; sneakTicks = 0; mc.options.sneakKey.setPressed(true); }
                    else { stage = Stage.IDLE; message(mc, "Boom"); }
                } else steerAway(mc);
            }
            case SNEAK -> {
                // stay low (shift) until the TNT minecart has exploded, then stand up
                mc.options.sneakKey.setPressed(true);
                sneakTicks++;
                boolean cartLeft = !mc.world.getEntitiesByClass(Entity.class, new Box(cartCenter, cartCenter).expand(5.0),
                        e -> e.getType() == EntityType.TNT_MINECART).isEmpty();
                if ((!cartLeft && sneakTicks > 3) || sneakTicks > 120) {
                    releaseMove(); stage = Stage.IDLE; message(mc, "Boom");
                }
            }
            case HOLD -> {
                mc.options.useKey.setPressed(false);
                if (mc.player.isUsingItem()) mc.interactionManager.stopUsingItem(mc.player);
                stage = Stage.IDLE;
                message(mc, "Boom (shield down)");
            }
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
            if (CrossbowItem.isCharged(main)) { stage = shieldOn ? Stage.SHIELD : Stage.BURST; wait = 2; } else cancel(mc, "Loading failed");
            return;
        }
        if (mc.player.getItemUseTime() >= CrossbowItem.getPullTime(main, mc.player) + 1) {
            mc.options.useKey.setPressed(false);
            mc.interactionManager.stopUsingItem(mc.player);
            stage = shieldOn ? Stage.SHIELD : Stage.BURST; wait = 3;
        }
    }

    /** Put a shield in the off-hand and raise it; vanilla blocks explosion damage from the front once it has been up ~5 ticks. */
    private void raiseShield(MinecraftClient mc, CartConfig cfg) {
        if (!mc.player.getOffHandStack().isOf(Items.SHIELD)) {
            int s = Equipment.scan(mc.player.getInventory(), true).shield();
            if (s < 0) { cancel(mc, "Shield missing"); return; }
            int screenSlot = s < 9 ? 36 + s : s;
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, screenSlot, 40, SlotActionType.SWAP, mc.player);
            if (!mc.player.getOffHandStack().isOf(Items.SHIELD)) { cancel(mc, "Could not move shield to off-hand"); return; }
        }
        if (!mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND).isAccepted()) { cancel(mc, "Could not raise shield"); return; }
        mc.options.useKey.setPressed(true);
        shieldUp = true;
        stage = Stage.BURST;
        wait = Math.max(5, cfg.shieldDelay);
    }

    private boolean hasProjectile(MinecraftClient mc) { return mc.player.getOffHandStack().getItem() instanceof net.minecraft.item.ArrowItem; }

    /** Everything in a single client tick: rail, cart, fire, aim, shoot. */
    private void burst(MinecraftClient mc, Equipment eq) {
        if (!eq.ready(true)) { cancel(mc, "Missing items"); return; }
        if (!CrossbowItem.isCharged(mc.player.getInventory().getStack(eq.crossbow()))) { cancel(mc, "Crossbow is not loaded"); return; }

        Vec3d eye = mc.player.getEyePos();
        Vec3d c = new Vec3d(railPos.getX() + 0.5, railPos.getY() + 0.25, railPos.getZ() + 0.5);
        double hx = eye.x - c.x, hz = eye.z - c.z, hd = Math.sqrt(hx * hx + hz * hz);
        if (hd < 1.6) { cancel(mc, "Too close: step back 2 blocks"); return; }
        BlockPos pb = mc.player.getBlockPos();
        double ux = -hx / hd, uz = -hz / hd;       // unit vector player -> cart (horizontal)
        // slab cell: 1 block in front of you, on the line to the cart (tries a few spots / heights)
        BlockPos slabCell = null;
        if (CartConfig.get().slabMode) {
            if (eq.slab() < 0) message(mc, "No slab in hotbar: blast will hurt");
            else {
                outer:
                for (double dd : new double[]{1.0, 1.3, 0.8, 1.6}) {
                    for (int dy : new int[]{0, -1, 1}) {
                        BlockPos t = new BlockPos(net.minecraft.util.math.MathHelper.floor(eye.x + ux * dd),
                                pb.getY() + dy, net.minecraft.util.math.MathHelper.floor(eye.z + uz * dd));
                        if (t.equals(pb) || t.equals(railPos) || t.equals(railPos.up())) continue;
                        if (t.getSquaredDistance(railPos) < 2.0) continue;
                        var st = mc.world.getBlockState(t);
                        if (!(st.isAir() || st.isReplaceable())) continue;
                        if (!mc.world.getBlockState(t.down()).isSideSolidFullSquare(mc.world, t.down(), Direction.UP)) continue;
                        slabCell = t; break outer;
                    }
                }
                if (slabCell == null) message(mc, "No room for a slab: blast will hurt");
            }
        }
        // fire cells on the arrow line just before the cart (several, so the arrow is lit wherever it flies through)
        java.util.List<BlockPos> fires = new java.util.ArrayList<>();
        for (double dd : new double[]{1.0, 1.4, 1.8, 0.8, 2.2}) {
            if (dd > hd - 0.6) continue;
            BlockPos f = new BlockPos(net.minecraft.util.math.MathHelper.floor(c.x + hx / hd * dd), railPos.getY(), net.minecraft.util.math.MathHelper.floor(c.z + hz / hd * dd));
            if (f.equals(railPos) || f.equals(pb) || f.equals(pb.up()) || f.equals(slabCell) || fires.contains(f)) continue;
            var st = mc.world.getBlockState(f);
            if (!(st.isAir() || st.isReplaceable())) continue;
            if (!mc.world.getBlockState(f.down()).isSideSolidFullSquare(mc.world, f.down(), Direction.UP)) continue;
            fires.add(f);
            if (fires.size() >= 2) break;
        }
        if (fires.isEmpty()) { cancel(mc, "No free solid ground for fire between you and the cart"); return; }

        // 1. rail
        if (!select(mc, eq.rail()) || !mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, target).isAccepted()) { cancel(mc, "Rail placement failed"); return; }
        // 2. TNT minecart
        if (!placeCart(mc, Equipment.scan(mc.player.getInventory(), CartConfig.get().autoDetect))) return;
        // 3. fire with flint and steel
        eq = Equipment.scan(mc.player.getInventory(), CartConfig.get().autoDetect);
        boolean lit = false;
        for (BlockPos f : fires) {
            BlockPos sp = f.down();
            BlockHitResult fh = new BlockHitResult(new Vec3d(sp.getX() + 0.5, sp.getY() + 1.0, sp.getZ() + 0.5), Direction.UP, sp, false);
            if (select(mc, eq.flint()) && mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, fh).isAccepted()) lit = true;
        }
        if (!lit) { cancel(mc, "Could not light the fire"); return; }
        // 3b. one blast slab in front of you
        if (slabCell != null) {
            Equipment e2 = Equipment.scan(mc.player.getInventory(), CartConfig.get().autoDetect);
            BlockPos sd = slabCell.down();
            BlockHitResult h = new BlockHitResult(new Vec3d(sd.getX() + 0.5, sd.getY() + 1.0, sd.getZ() + 0.5), Direction.UP, sd, false);
            if (!select(mc, e2.slab()) || !mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, h).isAccepted()) message(mc, "Could not place slab");
        }
        // 4. aim at the cart and shoot the loaded crossbow through the fire
        float oldYaw = mc.player.getYaw(), oldPitch = mc.player.getPitch();
        double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
        mc.player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        mc.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        eq = Equipment.scan(mc.player.getInventory(), CartConfig.get().autoDetect);
        boolean shot = select(mc, eq.crossbow()) && mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted();
        mc.player.setYaw(oldYaw); mc.player.setPitch(oldPitch);
        if (!shot) { cancel(mc, "Crossbow shot failed"); return; }
        mc.player.getInventory().setSelectedSlot(originalSlot);
        cartCenter = c;
        CartConfig rc = CartConfig.get();
        if (shieldUp) { stage = Stage.HOLD; wait = Math.max(5, rc.shieldHold); }
        else if (rc.retreat && rc.retreatTicks > 0) {
            stage = Stage.RETREAT; retreatLeft = rc.retreatTicks; wait = 0;
            Vec3d away = new Vec3d(mc.player.getX() - c.x, 0, mc.player.getZ() - c.z);
            if (away.lengthSquared() > 1.0E-4 && rc.retreatPush > 0) {
                away = away.normalize().multiply(rc.retreatPush / 100.0);
                mc.player.setVelocity(mc.player.getVelocity().add(away.x, 0, away.z));
            }
            steerAway(mc);
        }
        else { stage = Stage.IDLE; message(mc, "Boom"); }
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
            if (inv.getSelectedSlot() != slot) inv.setSelectedSlot(slot);
        } else {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, inv.getSelectedSlot(), SlotActionType.SWAP, mc.player);
        }
        return true;
    }

    /** Presses the movement keys (W/A/S/D combos) that move the player directly away from the cart, whatever way they look. */
    private void steerAway(MinecraftClient mc) {
        double dx = mc.player.getX() - cartCenter.x, dz = mc.player.getZ() - cartCenter.z;
        double awayYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double rel = net.minecraft.util.math.MathHelper.wrapDegrees(awayYaw - mc.player.getYaw());
        int sector = (int) Math.round(rel / 45.0);   // 0 = straight ahead, 4 = straight behind
        boolean f = false, b = false, l = false, r = false;
        switch (sector) {
            case 0 -> f = true;
            case 1 -> { f = true; r = true; }
            case 2 -> r = true;
            case 3 -> { b = true; r = true; }
            case -1 -> { f = true; l = true; }
            case -2 -> l = true;
            case -3 -> { b = true; l = true; }
            default -> b = true;
        }
        mc.options.forwardKey.setPressed(f); mc.options.backKey.setPressed(b);
        mc.options.leftKey.setPressed(l); mc.options.rightKey.setPressed(r);
        if (CartConfig.get().sneakUntilBlast) mc.options.sneakKey.setPressed(true);
    }

    private void releaseMove() {
        KeyBinding.updatePressedStates();   // hand the keys back to the real keyboard state
    }

    private void cancel(MinecraftClient mc, String why) {
        if (stage == Stage.RETREAT || stage == Stage.SNEAK) releaseMove();
        mc.options.useKey.setPressed(false);
        if (mc.player.isUsingItem()) mc.interactionManager.stopUsingItem(mc.player);
        finish(mc);
        message(mc, why);
    }

    private void finish(MinecraftClient mc) {
        mc.player.getInventory().setSelectedSlot(originalSlot);
        stage = Stage.IDLE;
    }

    private void message(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[AutoCartPvP] " + s), true);
    }
}
