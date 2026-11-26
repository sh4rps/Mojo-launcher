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

import java.util.ArrayList;
import java.util.List;

/**
 * Aim at any block and press V:
 *  1) one full block is placed right in front of the cart spot, on your side (your cover)
 *  2) rail, TNT minecart
 *  3) the cart is lit with flint and steel (reliable, no arrow needed)
 *  4) you crouch behind the cover until it blows, and your hotbar is put back exactly as it was.
 */
public class Sequence {
    enum Stage { IDLE, COVER, GO, IGNITE, SNEAK }

    private Stage stage = Stage.IDLE;
    private BlockPos pb, cover, railPos;
    private Vec3d cartC = Vec3d.ZERO;
    private int wait, originalSlot, ticks, sneakTicks;
    private final List<int[]> swaps = new ArrayList<>();   // {inventorySlot, hotbarSlot} pairs to undo

    boolean active() { return stage != Stage.IDLE; }

    // ------------------------------------------------------------ items
    private static boolean isCoverBlock(MinecraftClient mc, ItemStack s) {
        if (!(s.getItem() instanceof BlockItem bi)) return false;
        Block b = bi.getBlock();
        if (b instanceof FallingBlock || b == Blocks.TNT) return false;
        return b.getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN) && !b.getDefaultState().hasBlockEntity();
    }

    private int find(MinecraftClient mc, String what) {
        PlayerInventory inv = mc.player.getInventory();
        int best = -1; float bestRes = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            switch (what) {
                case "rail" -> { if (s.isOf(Items.RAIL)) return i; }
                case "cart" -> { if (s.isOf(Items.TNT_MINECART)) return i; }
                case "flint" -> { if (s.isOf(Items.FLINT_AND_STEEL)) return i; }
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

    /** Puts the item into the main hand. Items from the inventory are swapped into the selected hotbar slot; every swap is undone at the end. */
    private boolean hold(MinecraftClient mc, int slot) {
        if (slot < 0) return false;
        PlayerInventory inv = mc.player.getInventory();
        if (slot < 9) { if (inv.getSelectedSlot() != slot) inv.setSelectedSlot(slot); return true; }
        int hb = inv.getSelectedSlot();
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, hb, SlotActionType.SWAP, mc.player);
        swaps.add(new int[]{slot, hb});
        return true;
    }

    private void restoreHotbar(MinecraftClient mc) {
        for (int i = swaps.size() - 1; i >= 0; i--) {
            int[] p = swaps.get(i);
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, p[0], p[1], SlotActionType.SWAP, mc.player);
        }
        swaps.clear();
        mc.player.getInventory().setSelectedSlot(originalSlot);
    }

    // ------------------------------------------------------------ start
    void start(MinecraftClient mc) {
        if (active() || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler || mc.player.isUsingItem()) { msg(mc, "Close inventories and stop using items first"); return; }
        if (find(mc, "rail") < 0 || find(mc, "cart") < 0 || find(mc, "flint") < 0) { msg(mc, "Need rail, TNT minecart and flint and steel"); return; }
        if (find(mc, "block") < 0) { msg(mc, "Need a full block (cobblestone, obsidian...) as cover"); return; }
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK || bhr.getSide() == Direction.DOWN) { msg(mc, "Aim at a block where the cart should go"); return; }
        BlockPos base = bhr.getBlockPos();
        railPos = base.up();
        var rs = mc.world.getBlockState(railPos);
        if (!(rs.isAir() || rs.isReplaceable()) || !mc.world.getBlockState(railPos.up()).isAir()) { msg(mc, "No free space above that block"); return; }
        cartC = new Vec3d(railPos.getX() + 0.5, railPos.getY() + 0.25, railPos.getZ() + 0.5);
        Vec3d eye = mc.player.getEyePos();
        // lighting the cart with flint needs the cart within about 4 blocks of you
        Box cb = new Box(railPos.getX() + 0.01, railPos.getY(), railPos.getZ() + 0.01, railPos.getX() + 0.99, railPos.getY() + 0.7, railPos.getZ() + 0.99);
        double dx = Math.max(Math.max(cb.minX - eye.x, 0), eye.x - cb.maxX), dy = Math.max(Math.max(cb.minY - eye.y, 0), eye.y - cb.maxY), dz = Math.max(Math.max(cb.minZ - eye.z, 0), eye.z - cb.maxZ);
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) > 3.6) { msg(mc, "Too far: the cart must be within about 3.5 blocks of you"); return; }
        double hx = cartC.x - eye.x, hz = cartC.z - eye.z, hd = Math.sqrt(hx * hx + hz * hz);
        if (hd < 2.2) { msg(mc, "Too close: step back a little"); return; }
        pb = mc.player.getBlockPos();
        double ux = hx / hd, uz = hz / hd;

        // cover cell: on the line between you and the cart, right next to the cart (best shield with one block)
        cover = null;
        outer:
        for (double back : new double[]{1.0, 1.2, 0.9, 1.4, 1.6}) {
            for (int ddy : new int[]{0, -1, 1}) {
                BlockPos t = new BlockPos(MathHelper.floor(cartC.x - ux * back), railPos.getY() + ddy, MathHelper.floor(cartC.z - uz * back));
                if (t.equals(railPos) || t.equals(pb) || t.equals(pb.up()) || t.equals(railPos.up())) continue;
                var st = mc.world.getBlockState(t);
                if (!(st.isAir() || st.isReplaceable())) continue;
                if (!mc.world.getBlockState(t.down()).isSideSolidFullSquare(mc.world, t.down(), Direction.UP)) continue;
                cover = t; break outer;
            }
        }
        if (cover == null) { msg(mc, "No room for the cover block between you and the cart"); return; }
        originalSlot = mc.player.getInventory().getSelectedSlot();
        swaps.clear();
        stage = Stage.COVER; wait = 0; ticks = 0;
    }

    // ------------------------------------------------------------ tick
    void tick(MinecraftClient mc) {
        if (!active()) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { stage = Stage.IDLE; swaps.clear(); return; }
        if (stage != Stage.SNEAK && (mc.currentScreen != null || mc.player.currentScreenHandler != mc.player.playerScreenHandler)) { cancel(mc, "Interrupted"); return; }
        if (stage != Stage.SNEAK && ++ticks > 80) { cancel(mc, "Timed out"); return; }
        if (wait-- > 0) return;
        switch (stage) {
            case COVER -> {
                BlockPos sup = cover.down();
                BlockHitResult h = new BlockHitResult(new Vec3d(sup.getX() + 0.5, sup.getY() + 1.0, sup.getZ() + 0.5), Direction.UP, sup, false);
                if (!hold(mc, find(mc, "block")) || !mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, h).isAccepted()) { cancel(mc, "Could not place the cover block"); return; }
                stage = Stage.GO; wait = 2;
            }
            case GO -> {
                BlockPos base = railPos.down();
                BlockHitResult rh = new BlockHitResult(new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5), Direction.UP, base, false);
                if (!hold(mc, find(mc, "rail")) || !mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, rh).isAccepted()) { cancel(mc, "Rail placement failed"); return; }
                if (!mc.world.getBlockState(railPos).isOf(Blocks.RAIL)) { stage = Stage.GO; wait = 1; if (ticks > 20) cancel(mc, "Rail did not appear"); return; }
                BlockHitResult onRail = new BlockHitResult(new Vec3d(railPos.getX() + 0.5, railPos.getY() + 0.1, railPos.getZ() + 0.5), Direction.UP, railPos, false);
                if (!hold(mc, find(mc, "cart")) || !mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, onRail).isAccepted()) { cancel(mc, "Minecart placement failed"); return; }
                stage = Stage.IGNITE; wait = 1;
            }
            case IGNITE -> {
                List<Entity> carts = mc.world.getEntitiesByClass(Entity.class, new Box(cartC, cartC).expand(1.2), e -> e.getType() == EntityType.TNT_MINECART);
                if (carts.isEmpty()) return;   // the cart entity has not arrived yet; keep waiting (timeout above)
                if (!hold(mc, find(mc, "flint"))) { cancel(mc, "Flint and steel missing"); return; }
                var res = mc.interactionManager.interactEntity(mc.player, carts.get(0), Hand.MAIN_HAND);
                if (!res.isAccepted()) { cancel(mc, "Could not light the cart"); return; }
                restoreHotbar(mc);
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

    private void cancel(MinecraftClient mc, String why) {
        mc.options.useKey.setPressed(false);
        KeyBinding.updatePressedStates();
        restoreHotbar(mc);
        stage = Stage.IDLE;
        msg(mc, why);
    }

    private void msg(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[AutoCart2] " + s), true);
    }
}
