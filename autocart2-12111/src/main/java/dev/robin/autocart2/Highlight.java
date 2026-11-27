package dev.robin.autocart2;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/** Outlines the block you are aiming at: YELLOW = press V now, RED = V would refuse (too close / no free space above). */
public final class Highlight {
    private Highlight() { }

    private static boolean has(MinecraftClient mc, net.minecraft.item.Item it) {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(it)) return true;
        return false;
    }

    /** Only when the hotbar really holds a working kit. */
    private static boolean ready(MinecraftClient mc) {
        if (!has(mc, net.minecraft.item.Items.TNT_MINECART)) return false;
        boolean block = false;
        for (int i = 0; i < 9; i++) {
            var st = mc.player.getInventory().getStack(i);
            if (st.getItem() instanceof net.minecraft.item.BlockItem bi) {
                var b = bi.getBlock();
                if (!(b instanceof net.minecraft.block.FallingBlock) && b != net.minecraft.block.Blocks.TNT
                        && b.getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN) && !b.getDefaultState().hasBlockEntity()) { block = true; break; }
            }
        }
        if (!block) return false;
        return (has(mc, net.minecraft.item.Items.ACTIVATOR_RAIL) && has(mc, net.minecraft.item.Items.REDSTONE_BLOCK))
                || (has(mc, net.minecraft.item.Items.RAIL) && has(mc, net.minecraft.item.Items.FLINT_AND_STEEL) && has(mc, net.minecraft.item.Items.CROSSBOW));
    }

    public static void render(MinecraftClient mc, MatrixStack ms, VertexConsumerProvider consumers) {
        try { draw(mc, ms, consumers); } catch (Throwable ignored) { }
    }

    private static void draw(MinecraftClient mc, MatrixStack ms, VertexConsumerProvider consumers) {
        if (mc.player == null || mc.world == null || consumers == null || ms == null || mc.currentScreen != null) return;
        if (!ready(mc)) return;
        // counting starts at the block you stand on: the next block in a direction is no. 1, then 2 ... 5
        Direction front = mc.player.getHorizontalFacing();
        Direction[] dirs = {front, front.rotateYClockwise(), front.rotateYCounterclockwise()};   // ahead, right, left
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        VertexConsumer vc = consumers.getBuffer(RenderLayers.lines());
        VoxelShape box = VoxelShapes.cuboid(-0.003, -0.003, -0.003, 1.003, 1.003, 1.003);
        BlockPos me = mc.player.getBlockPos();
        for (Direction d : dirs) {
            for (int n : new int[]{4, 5}) {
                BlockPos col = me.offset(d, n);
                BlockPos g = null;                       // follow the ground up or down
                for (int dy = 2; dy >= -4; dy--) {
                    BlockPos b = col.up(dy);
                    var st = mc.world.getBlockState(b);
                    var up = mc.world.getBlockState(b.up());
                    if (st.isSideSolidFullSquare(mc.world, b, Direction.UP) && (up.isAir() || up.isReplaceable())) { g = b; break; }
                }
                if (g == null) continue;
                int color = n == 5 ? 0xFFFFFF00 : 0xFFFFC800;   // 5th = bright yellow (aim/press V here), 4th = softer yellow
                VertexRendering.drawOutline(ms, vc, box, g.getX() - cam.x, g.getY() - cam.y, g.getZ() - cam.z, color, n == 5 ? 5.0f : 3.0f);
            }
        }
    }
}
