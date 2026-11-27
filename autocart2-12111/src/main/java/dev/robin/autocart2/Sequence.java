package dev.robin.autocart2;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
 * Aim at a block, press V. Everything is taken from the HOTBAR only (nothing is pulled out of your inventory):
 *  1) one full block as cover
 *  2) rail + TNT minecart
 *  3) lighting - best: ACTIVATOR RAIL + REDSTONE BLOCK in the hotbar (cart is primed by the powered activator rail, fuse ~4 s)
 *                else: normal RAIL + FLINT + loaded CROSSBOW (fire on the arrow path, arrow shot over the cover)
 *  4) crouch until it has blown. The selected slot is put back.
 * Every step shows a small message in the action bar so a failure tells exactly where it stopped.
 */
public class Sequence {
    enum Stage { IDLE, COVER, RAIL, CART, POWER, PRIMED, LOAD, SHOOT, SNEAK }

    private Stage stage = Stage.IDLE;
    private boolean redstoneMode;
    private BlockPos pb, cover, railPos;
    private Vec3d cartC = Vec3d.ZERO, aim = Vec3d.ZERO;
    private int wait, originalSlot, ticks, sneakTicks;
    private boolean loading;
    private final List<BlockPos> fires = new ArrayList<>();

    boolean active() { return stage != Stage.IDLE; }

    // ------------------------------------------------------------ hotbar lookup
    private static boolean isCoverBlock(MinecraftClient mc, ItemStack s) {
        if (!(s.getItem() instanceof BlockItem bi)) return false;
        Block b = bi.getBlock();
        if (b instanceof FallingBlock || b == Blocks.TNT) return false;
        return b.getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN) && !b.getDefaultState().hasBlockEntity();
    }

    private int hb(MinecraftClient mc, String what) {
        int best = -1; float bestRes = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            switch (what) {
                case "rail" -> { if (s.isOf(Items.RAIL)) return i; }
                case "arail" -> { if (s.isOf(Items.ACTIVATOR_RAIL)) return i; }
                case "rblock" -> { if (s.isOf(Items.REDSTONE_BLOCK)) return i; }
                case "cart" -> { if (s.isOf(Items.TNT_MINECART)) return i; }
                case "flint" -> { if (s.isOf(Items.FLINT_AND_STEEL)) return i; }
                case "crossbow" -> { if (s.isOf(Items.CROSSBOW)) return i; }
                default -> {
                    if (isCoverBlock(mc, s)) {
                        float r = ((BlockItem) s.getItem()).getBlock().getBlastResistance();
                        if (r > bestRes) { bestRes = r; best = i; }
                    }
                }
            }
        }
        return best;
    }

    private boolean hold(MinecraftClient mc, int slot) {
        if (slot < 0 || slot > 8) return false;
        if (mc.player.getInventory().getSelectedSlot() != slot) mc.player.getInventory().setSelectedSlot(slot);
        return true;
    }

    // ------------------------------------------------------------ start
    void start(MinecraftClient mc) {
        if (active() || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler || mc.player.isUsingItem()) { msg(mc, "Close inventories and stop using items first"); return; }
        if (hb(mc, "cart") < 0) { msg(mc, "Put the TNT minecart in your hotbar"); return; }
        if (hb(mc, "block") < 0) { msg(mc, "Put a full block (cobblestone, obsidian...) in your hotbar"); return; }
        redstoneMode = hb(mc, "arail") >= 0 && hb(mc, "rblock") >= 0;
        if (!redstoneMode && (hb(mc, "rail") < 0 || hb(mc, "flint") < 0 || hb(mc, "crossbow") < 0)) {
            msg(mc, "Hotbar needs: rail, flint and steel, crossbow  (or activator rail + redstone block)"); return;
        }
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK || bhr.getSide() == Direction.DOWN) { msg(mc, "Aim at a block where the cart should go"); return; }
        BlockPos base = bhr.getBlockPos();
        railPos = base.up();
        var rs = mc.world.getBlockState(railPos);
        if (!(rs.isAir() || rs.isReplaceable()) || !mc.world.getBlockState(railPos.up()).isAir()) { msg(mc, "No free space above that block"); return; }
        cartC = new Vec3d(railPos.getX() + 0.5, railPos.getY() + 0.25, railPos.getZ() + 0.5);
        Vec3d eye = mc.player.getEyePos();
        double hx = cartC.x - eye.x, hz = cartC.z - eye.z, hd = Math.sqrt(hx * hx + hz * hz);
        if (hd < 2.2) { msg(mc, "Too close: step back a little"); return; }
        pb = mc.player.getBlockPos();
        double ux = hx / hd, uz = hz / hd;
        cover = null; fires.clear();

        if (redstoneMode) {
            // cover right next to the cart on your side: the best shield for a single block
            outer:
            for (double back : new double[]{1.0, 1.2, 0.9, 1.4, 1.6}) {
                for (int ddy : new int[]{0, -1, 1}) {
                    BlockPos t = new BlockPos(MathHelper.floor(cartC.x - ux * back), railPos.getY() + ddy, MathHelper.floor(cartC.z - uz * back));
                    if (freeSpot(mc, t) && !t.equals(pb) && !t.equals(pb.up())) { cover = t; break outer; }
                }
            }
            if (cover == null) { msg(mc, "No room for the cover block between you and the cart"); return; }
        } else {
            // arrow must fly OVER the cover, so the cover sits next to you; the cart has to be far enough for a flat shot
            Vec3d from = new Vec3d(eye.x, eye.y - 0.15, eye.z);
            outer:
            for (double fwd : new double[]{1.0, 1.2, 0.9}) {
                for (int ddy : new int[]{0, -1, 1}) {
                    BlockPos t = new BlockPos(MathHelper.floor(eye.x + ux * fwd), pb.getY() + ddy, MathHelper.floor(eye.z + uz * fwd));
                    if (!freeSpot(mc, t) || t.equals(pb) || t.equals(pb.up()) || t.equals(railPos)) continue;
                    for (double up : new double[]{0.5, 0.4, 0.3, 0.2, 0.1}) {
                        Vec3d a = new Vec3d(cartC.x, cartC.y + up, cartC.z);
                        if (!clearsCover(from, a, t)) continue;
                        var rh = mc.world.raycast(new RaycastContext(from, a, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
                        if (rh.getType() != HitResult.Type.MISS) continue;
                        cover = t; aim = a; break outer;
                    }
                }
            }
            if (cover == null) { msg(mc, "Arrow mode: cart too close to shoot over the cover. Aim 5 blocks away, or use activator rail + redstone block"); return; }
            // fire cells on the arrow path where the arrow flies in its first tick (2.4-3.4 blocks from you) and next to the cart
            Vec3d pv = aim.subtract(from);
            double plen = pv.length();
            for (double t = 0; t <= plen && fires.size() < 3; t += 0.08) {
                Vec3d p = from.add(pv.multiply(t / plen));
                double dh = Math.hypot(p.x - from.x, p.z - from.z);
                if (dh < 2.4 || dh > 3.4) continue;
                if (p.y < railPos.getY() + 0.02 || p.y >= railPos.getY() + 0.98) continue;
                BlockPos f = new BlockPos(MathHelper.floor(p.x), railPos.getY(), MathHelper.floor(p.z));
                if (f.equals(railPos) || f.equals(pb) || f.equals(pb.up()) || f.equals(cover) || fires.contains(f)) continue;
                if (!freeSpot(mc, f)) continue;
                fires.add(f);
            }
            if (fires.isEmpty()) { msg(mc, "Arrow mode: no free ground for the fire on the arrow path"); return; }
        }
        originalSlot = mc.player.getInventory().getSelectedSlot();
        stage = Stage.COVER; wait = 0; ticks = 0; loading = false;
    }

    private boolean freeSpot(MinecraftClient mc, BlockPos t) {
        var st = mc.world.getBlockState(t);
        if (!(st.isAir() || st.isReplaceable())) return false;
        return mc.world.getBlockState(t.down()).isSideSolidFullSquare(mc.world, t.down(), Direction.UP);
    }

    private static boolean clearsCover(Vec3d from, Vec3d to, BlockPos cover) {
        Vec3d v = to.subtract(from);
        double len = v.length();
        for (double t = 0; t <= len; t += 0.05) {
            Vec3d p = from.add(v.multiply(t / len));
            if (p.x > cover.getX() - 0.15 && p.x < cover.getX() + 1.15 && p.z > cover.getZ() - 0.15 && p.z < cover.getZ() + 1.15
                    && p.y < cover.getY() + 1.0 + 0.14) return false;
        }
        return true;
    }

    // ------------------------------------------------------------ tick
    void tick(MinecraftClient mc) {
        if (!active()) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { stage = Stage.IDLE; return; }
        if (stage != Stage.SNEAK && (mc.currentScreen != null || mc.player.currentScreenHandler != mc.player.playerScreenHandler)) { cancel(mc, "Interrupted"); return; }
        if (stage != Stage.SNEAK && ++ticks > 100) { cancel(mc, "Timed out at step " + stage); return; }
        if (wait-- > 0) return;
        switch (stage) {
            case COVER -> {
                BlockPos sup = cover.down();
                BlockHitResult h = new BlockHitResult(new Vec3d(sup.getX() + 0.5, sup.getY() + 1.0, sup.getZ() + 0.5), Direction.UP, sup, false);
                if (!hold(mc, hb(mc, "block")) || !mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, h).isAccepted()) { cancel(mc, "Could not place the cover block"); return; }
                msg(mc, "1: cover block");
                stage = Stage.RAIL; wait = 0;
            }
            case RAIL -> {
                BlockPos base = railPos.down();
                BlockHitResult rh = new BlockHitResult(new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5), Direction.UP, base, false);
                int slot = hb(mc, redstoneMode ? "arail" : "rail");
                if (!hold(mc, slot) || !mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, rh).isAccepted()) { cancel(mc, "Rail placement failed"); return; }
                msg(mc, "2: rail");
                stage = Stage.CART; wait = 1;
            }
            case CART -> {
                if (!(mc.world.getBlockState(railPos).isOf(Blocks.RAIL) || mc.world.getBlockState(railPos).isOf(Blocks.ACTIVATOR_RAIL))) { cancel(mc, "Rail did not appear"); return; }
                BlockHitResult onRail = new BlockHitResult(new Vec3d(railPos.getX() + 0.5, railPos.getY() + 0.1, railPos.getZ() + 0.5), Direction.UP, railPos, false);
                if (!hold(mc, hb(mc, "cart")) || !mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, onRail).isAccepted()) { cancel(mc, "Minecart placement failed"); return; }
                msg(mc, "3: TNT minecart");
                stage = redstoneMode ? Stage.POWER : Stage.LOAD; wait = 0;
            }
            // ---------- redstone mode
            case POWER -> {
                BlockPos at = null;
                Direction[] ds = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
                double best = -1;
                for (Direction d : ds) {   // neighbour of the rail, farthest from you, never the cover
                    BlockPos n = railPos.offset(d);
                    if (n.equals(cover) || !freeSpot(mc, n)) continue;
                    double dist = n.getSquaredDistance(mc.player.getBlockPos());
                    if (dist > best) { best = dist; at = n; }
                }
                if (at == null) { cancel(mc, "No free spot next to the rail for the redstone block"); return; }
                BlockPos sup = at.down();
                BlockHitResult h = new BlockHitResult(new Vec3d(sup.getX() + 0.5, sup.getY() + 1.0, sup.getZ() + 0.5), Direction.UP, sup, false);
                if (!hold(mc, hb(mc, "rblock")) || !mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, h).isAccepted()) { cancel(mc, "Could not place the redstone block"); return; }
                msg(mc, "4: redstone block - cart is powering up");
                stage = Stage.PRIMED; wait = 0; ticks = 60;
            }
            case PRIMED -> {
                boolean primed = false;
                for (TntMinecartEntity c : mc.world.getEntitiesByClass(TntMinecartEntity.class, new Box(cartC, cartC).expand(1.5), e -> true)) if (c.isPrimed()) primed = true;
                if (primed) { mc.player.getInventory().setSelectedSlot(originalSlot); stage = Stage.SNEAK; sneakTicks = 0; msg(mc, "Cart lit - stay behind the block"); }
                else if (ticks > 90) cancel(mc, "Cart did not light. Is the rail an ACTIVATOR rail?");
            }
            // ---------- arrow mode
            case LOAD -> {
                int cb = hb(mc, "crossbow");
                if (cb < 0) { cancel(mc, "Crossbow not in hotbar"); return; }
                if (CrossbowItem.isCharged(mc.player.getInventory().getStack(cb))) { stage = Stage.SHOOT; wait = 0; return; }
                if (!loading) {
                    if (!hold(mc, cb) || !mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted()) { cancel(mc, "Could not start loading the crossbow (arrows in inventory?)"); return; }
                    mc.options.useKey.setPressed(true);
                    loading = true;
                    return;
                }
                ItemStack main = mc.player.getMainHandStack();
                if (!mc.player.isUsingItem()) { mc.options.useKey.setPressed(false); if (CrossbowItem.isCharged(main)) { stage = Stage.SHOOT; wait = 1; } else cancel(mc, "Crossbow loading failed"); return; }
                if (mc.player.getItemUseTime() >= CrossbowItem.getPullTime(main, mc.player) + 1) {
                    mc.options.useKey.setPressed(false);
                    mc.interactionManager.stopUsingItem(mc.player);
                    stage = Stage.SHOOT; wait = 1;
                }
            }
            case SHOOT -> shoot(mc);
            case SNEAK -> {
                mc.options.sneakKey.setPressed(true);
                sneakTicks++;
                boolean cartLeft = !mc.world.getEntitiesByClass(Entity.class, new Box(cartC, cartC).expand(5.0), e -> e.getType() == EntityType.TNT_MINECART).isEmpty();
                if ((!cartLeft && sneakTicks > 3) || sneakTicks > 160) { KeyBinding.updatePressedStates(); stage = Stage.IDLE; msg(mc, "Boom"); }
            }
            default -> { }
        }
    }

    private void shoot(MinecraftClient mc) {
        int cb = hb(mc, "crossbow");
        if (cb < 0 || !CrossbowItem.isCharged(mc.player.getInventory().getStack(cb))) { cancel(mc, "Crossbow is not loaded"); return; }
        if (!mc.world.getBlockState(cover).isSolidBlock(mc.world, cover)) { cancel(mc, "Cover block is gone"); return; }
        boolean lit = false;
        for (BlockPos f : fires) {
            BlockPos sp = f.down();
            BlockHitResult fh = new BlockHitResult(new Vec3d(sp.getX() + 0.5, sp.getY() + 1.0, sp.getZ() + 0.5), Direction.UP, sp, false);
            if (hold(mc, hb(mc, "flint")) && mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, fh).isAccepted()) lit = true;
        }
        if (!lit) { cancel(mc, "Could not light the fire"); return; }
        msg(mc, "4: fire");
        Vec3d eye = mc.player.getEyePos();
        float oldYaw = mc.player.getYaw(), oldPitch = mc.player.getPitch();
        double dx = aim.x - eye.x, dy = aim.y - (eye.y - 0.15), dz = aim.z - eye.z;
        mc.player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        mc.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        boolean shot = hold(mc, cb) && mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted();
        mc.player.setYaw(oldYaw); mc.player.setPitch(oldPitch);
        if (!shot) { cancel(mc, "Crossbow shot failed"); return; }
        mc.player.getInventory().setSelectedSlot(originalSlot);
        msg(mc, "5: shot - stay behind the block");
        stage = Stage.SNEAK; sneakTicks = 0;
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
