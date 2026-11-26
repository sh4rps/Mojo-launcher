package dev.robin.autocart2;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 1) place a full block in front of you (your cover)  2) centre yourself behind it and aim over it at the cart spot
 * 3) rail, TNT minecart, flint-and-steel fire on the arrow path, crossbow shot  4) put a second block on top of the cover
 * and stay low until the cart has exploded.
 */
public class Sequence {
    enum Stage { IDLE, COVER, ADJUST, LOAD, BURST, SECOND, SNEAK }

    private Stage stage = Stage.IDLE;
    private BlockPos pb, cover, railPos;
    private Direction dir;
    private Vec3d cartC = Vec3d.ZERO, aim = Vec3d.ZERO, stand = Vec3d.ZERO;
    private int wait, originalSlot, ticks, adjustTicks, sneakTicks;
    private boolean loading;

    private static final double EYE = 1.62;

    boolean active() { return stage != Stage.IDLE; }

    // ------------------------------------------------------------ items
    private record Items3(int rail, int cart, int flint, int crossbow, int block, int blockCount) { }

    private static boolean isCoverBlock(MinecraftClient mc, ItemStack s) {
        if (!(s.getItem() instanceof BlockItem bi)) return false;
        Block b = bi.getBlock();
        if (b instanceof FallingBlock || b == Blocks.TNT) return false;
        return b.getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN) && !b.getDefaultState().hasBlockEntity();
    }

    private Items3 scan(MinecraftClient mc) {
        PlayerInventory inv = mc.player.getInventory();
        int rail = -1, cart = -1, flint = -1, cb = -1, blk = -1, count = 0;
        float best = -1;
        boolean cbCharged = false;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (rail < 0 && s.isOf(Items.RAIL)) rail = i;
            if (cart < 0 && s.isOf(Items.TNT_MINECART)) cart = i;
            if (flint < 0 && s.isOf(Items.FLINT_AND_STEEL)) flint = i;
            if (s.isOf(Items.CROSSBOW)) {
                boolean ch = CrossbowItem.isCharged(s);
                if (cb < 0 || (ch && !cbCharged)) { cb = i; cbCharged = ch; }
            }
            if (isCoverBlock(mc, s)) {
                float res = ((BlockItem) s.getItem()).getBlock().getBlastResistance();
                if (res > best || (res == best && s.getCount() > count)) { best = res; blk = i; count = s.getCount(); }
            }
        }
        return new Items3(rail, cart, flint, cb, blk, count);
    }

    // ------------------------------------------------------------ start / planning
    void start(MinecraftClient mc) {
        if (active() || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler || mc.player.isUsingItem()) { msg(mc, "Close inventories and stop using items first"); return; }
        if (!mc.player.isOnGround()) { msg(mc, "Stand on the ground first"); return; }
        Items3 it = scan(mc);
        if (it.rail() < 0 || it.cart() < 0 || it.flint() < 0 || it.crossbow() < 0) { msg(mc, "Need rail, TNT minecart, flint and steel, crossbow"); return; }
        if (it.block() < 0) { msg(mc, "Need a full block (cobblestone, obsidian...) as cover"); return; }

        pb = mc.player.getBlockPos();
        Direction f = mc.player.getHorizontalFacing();
        Direction[] order = {f, f.rotateYClockwise(), f.rotateYCounterclockwise(), f.getOpposite()};
        for (Direction d : order) {
            if (plan(mc, d)) {
                originalSlot = mc.player.getInventory().getSelectedSlot();
                stage = Stage.COVER; wait = 0; ticks = 0; adjustTicks = 0; loading = false;
                return;
            }
        }
        msg(mc, "No good spot: need flat free ground 3-5 blocks ahead with room for a block next to you");
    }

    /** Finds cover cell, cart cell and an aim point whose arrow flies over the cover and hits the cart. */
    private boolean plan(MinecraftClient mc, Direction d) {
        BlockPos c = pb.offset(d);
        var cs = mc.world.getBlockState(c);
        if (!(cs.isAir() || cs.isReplaceable())) return false;
        if (!mc.world.getBlockState(c.down()).isSideSolidFullSquare(mc.world, c.down(), Direction.UP)) return false;
        var up1 = mc.world.getBlockState(c.up());
        if (!(up1.isAir() || up1.isReplaceable())) return false;
        Vec3d s = new Vec3d(pb.getX() + 0.5, mc.player.getY(), pb.getZ() + 0.5);
        Vec3d from = new Vec3d(s.x, s.y + EYE - 0.15, s.z);
        int[] dists = {AutoCart2.distance, 5, 4, 3};
        for (int dist : dists) {
            if (dist > AutoCart2.distance) continue;
            BlockPos col = pb.offset(d, dist);
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos base = col.up(dy);
                if (!mc.world.getBlockState(base).isSideSolidFullSquare(mc.world, base, Direction.UP)) continue;
                BlockPos rail = base.up();
                var rs = mc.world.getBlockState(rail);
                if (!(rs.isAir() || rs.isReplaceable())) continue;
                if (!mc.world.getBlockState(rail.up()).isAir()) continue;
                if (Math.abs(rail.getY() - pb.getY()) > 2) continue;
                Vec3d cc = new Vec3d(rail.getX() + 0.5, rail.getY() + 0.25, rail.getZ() + 0.5);
                // try aim heights from the top of the cart box down; first that clears the cover and every other block
                for (double up : new double[]{0.45, 0.35, 0.25, 0.15, 0.0}) {
                    Vec3d t = new Vec3d(cc.x, cc.y + up, cc.z);
                    if (!clearsCover(from, t, c)) continue;
                    var rh = mc.world.raycast(new RaycastContext(from, t, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
                    if (rh.getType() != HitResult.Type.MISS) continue;
                    dir = d; cover = c; railPos = rail; cartC = cc; aim = t; stand = s;
                    return true;
                }
            }
        }
        return false;
    }

    /** The straight arrow path must pass above the cover block with room for the arrow's thickness. */
    private static boolean clearsCover(Vec3d from, Vec3d to, BlockPos cover) {
        Vec3d v = to.subtract(from);
        double len = v.length();
        for (double t = 0; t <= len; t += 0.05) {
            Vec3d p = from.add(v.multiply(t / len));
            if (p.x > cover.getX() - 0.15 && p.x < cover.getX() + 1.15 && p.z > cover.getZ() - 0.15 && p.z < cover.getZ() + 1.15
                    && p.y < cover.getY() + 1.0 + 0.16) return false;
        }
        return true;
    }

    // ------------------------------------------------------------ tick
    void tick(MinecraftClient mc) {
        if (!active()) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { stage = Stage.IDLE; return; }
        if (mc.currentScreen != null || mc.player.currentScreenHandler != mc.player.playerScreenHandler) { cancel(mc, "Interrupted"); return; }
        if (stage != Stage.SNEAK && ++ticks > 200) { cancel(mc, "Timed out"); return; }
        if (wait-- > 0) return;
        Items3 it = scan(mc);
        switch (stage) {
            case COVER -> {
                BlockPos sup = cover.down();
                BlockHitResult h = new BlockHitResult(new Vec3d(sup.getX() + 0.5, sup.getY() + 1.0, sup.getZ() + 0.5), Direction.UP, sup, false);
                if (!select(mc, it.block()) || !mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, h).isAccepted()) { cancel(mc, "Could not place the cover block"); return; }
                stage = Stage.ADJUST; wait = 2;
            }
            case ADJUST -> {
                if (!mc.world.getBlockState(cover).isSolidBlock(mc.world, cover) && adjustTicks > 6) { cancel(mc, "Cover block did not appear"); return; }
                adjustTicks++;
                aimNow(mc);
                Vec3d p = mc.player.getEntityPos();
                double dx = stand.x - p.x, dz = stand.z - p.z;
                if (Math.hypot(dx, dz) > 0.07 && adjustTicks < 14) {
                    Vec3d v = mc.player.getVelocity();
                    mc.player.setVelocity(MathHelper.clamp(dx * 0.6, -0.15, 0.15), v.y, MathHelper.clamp(dz * 0.6, -0.15, 0.15));
                    return;
                }
                mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
                if (!CrossbowItem.isCharged(mc.player.getInventory().getStack(it.crossbow()))) { stage = Stage.LOAD; return; }
                stage = Stage.BURST; wait = 1;
            }
            case LOAD -> { aimNow(mc); load(mc, it); }
            case BURST -> burst(mc, it);
            case SECOND -> {
                if (AutoCart2.secondBlock) {
                    BlockHitResult h = new BlockHitResult(new Vec3d(cover.getX() + 0.5, cover.getY() + 1.0, cover.getZ() + 0.5), Direction.UP, cover, false);
                    if (select(mc, it.block())) mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, h);
                }
                mc.player.getInventory().setSelectedSlot(originalSlot);
                stage = Stage.SNEAK; sneakTicks = 0;
            }
            case SNEAK -> {
                mc.options.sneakKey.setPressed(true);
                sneakTicks++;
                boolean cartLeft = !mc.world.getEntitiesByClass(Entity.class, new Box(cartC, cartC).expand(5.0), e -> e.getType() == EntityType.TNT_MINECART).isEmpty();
                if ((!cartLeft && sneakTicks > 3) || sneakTicks > 140) { KeyBinding.updatePressedStates(); stage = Stage.IDLE; msg(mc, "Boom"); }
            }
            default -> { }
        }
    }

    private void aimNow(MinecraftClient mc) {
        Vec3d eye = new Vec3d(stand.x, mc.player.getY() + EYE, stand.z);
        double dx = aim.x - eye.x, dy = aim.y - (eye.y - 0.15), dz = aim.z - eye.z;
        mc.player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        mc.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    private void load(MinecraftClient mc, Items3 it) {
        if (!loading) {
            if (!AutoCart2.autoLoad) { cancel(mc, "Crossbow is not loaded"); return; }
            if (!(mc.player.getOffHandStack().getItem() instanceof net.minecraft.item.ArrowItem) && !hasArrow(mc)) { cancel(mc, "No arrows to load"); return; }
            if (!select(mc, it.crossbow()) || !mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted()) { cancel(mc, "Could not start loading"); return; }
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

    private boolean hasArrow(MinecraftClient mc) {
        for (int i = 0; i < 36; i++) { ItemStack s = mc.player.getInventory().getStack(i); if (s.isOf(Items.ARROW) || s.isOf(Items.SPECTRAL_ARROW) || s.isOf(Items.TIPPED_ARROW)) return true; }
        return false;
    }

    /** rail, cart, fire on the arrow path, shoot - all in one tick. */
    private void burst(MinecraftClient mc, Items3 it) {
        if (!mc.world.getBlockState(cover).isSolidBlock(mc.world, cover)) { cancel(mc, "Cover block is gone"); return; }
        if (it.crossbow() < 0 || !CrossbowItem.isCharged(mc.player.getInventory().getStack(it.crossbow()))) { cancel(mc, "Crossbow is not loaded"); return; }
        aimNow(mc);
        Vec3d eye = mc.player.getEyePos();
        Vec3d from = new Vec3d(eye.x, eye.y - 0.15, eye.z);

        // fire cells exactly on the arrow path, walking from the cart towards you (the cover cell is excluded)
        List<BlockPos> fires = new ArrayList<>();
        Vec3d pv = cartC.subtract(from);
        double plen = pv.length();
        for (double t = plen; t >= 0 && fires.size() < 2; t -= 0.08) {
            Vec3d p = from.add(pv.multiply(t / plen));
            if (p.y < railPos.getY() + 0.02 || p.y >= railPos.getY() + 0.98) continue;
            BlockPos f = new BlockPos(MathHelper.floor(p.x), railPos.getY(), MathHelper.floor(p.z));
            if (f.equals(railPos) || f.equals(pb) || f.equals(pb.up()) || f.equals(cover) || fires.contains(f)) continue;
            var st = mc.world.getBlockState(f);
            if (!(st.isAir() || st.isReplaceable())) continue;
            if (!mc.world.getBlockState(f.down()).isSideSolidFullSquare(mc.world, f.down(), Direction.UP)) continue;
            fires.add(f);
        }
        if (fires.isEmpty()) { cancel(mc, "No free ground for the fire on the arrow path"); return; }

        // rail
        BlockPos base = railPos.down();
        BlockHitResult rh = new BlockHitResult(new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5), Direction.UP, base, false);
        if (!select(mc, it.rail()) || !mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, rh).isAccepted()) { cancel(mc, "Rail placement failed"); return; }
        // TNT minecart
        if (!mc.world.getBlockState(railPos).isOf(Blocks.RAIL) || !select(mc, scan(mc).cart())) { cancel(mc, "Rail unavailable"); return; }
        BlockHitResult onRail = new BlockHitResult(new Vec3d(railPos.getX() + 0.5, railPos.getY() + 0.1, railPos.getZ() + 0.5), Direction.UP, railPos, false);
        if (!mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, onRail).isAccepted()) { cancel(mc, "Minecart placement failed"); return; }
        // fire
        boolean lit = false;
        for (BlockPos f : fires) {
            BlockPos sp = f.down();
            BlockHitResult fh = new BlockHitResult(new Vec3d(sp.getX() + 0.5, sp.getY() + 1.0, sp.getZ() + 0.5), Direction.UP, sp, false);
            if (select(mc, scan(mc).flint()) && mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, fh).isAccepted()) lit = true;
        }
        if (!lit) { cancel(mc, "Could not light the fire"); return; }
        // shoot (view is already aimed over the cover)
        boolean shot = select(mc, scan(mc).crossbow()) && mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted();
        if (!shot) { cancel(mc, "Crossbow shot failed"); return; }
        stage = Stage.SECOND;
        wait = 4;     // let the arrow fly past the cover first, then close the wall
    }

    private boolean select(MinecraftClient mc, int slot) {
        if (slot < 0) return false;
        PlayerInventory inv = mc.player.getInventory();
        if (slot < 9) { if (inv.getSelectedSlot() != slot) inv.setSelectedSlot(slot); }
        else mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, inv.getSelectedSlot(), SlotActionType.SWAP, mc.player);
        return true;
    }

    private void cancel(MinecraftClient mc, String why) {
        mc.options.useKey.setPressed(false);
        if (mc.player.isUsingItem()) mc.interactionManager.stopUsingItem(mc.player);
        KeyBinding.updatePressedStates();
        mc.player.getInventory().setSelectedSlot(originalSlot);
        stage = Stage.IDLE;
        msg(mc, why);
    }

    private void msg(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[AutoCart2] " + s), true);
    }
}
