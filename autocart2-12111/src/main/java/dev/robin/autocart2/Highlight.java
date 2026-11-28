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
        if (mc.player.getPitch() > 55f && mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bh
                && bh.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && bh.getSide() != Direction.DOWN) {
            // looking down: this block is where the cart will go (you are walked to the right distance automatically)
            Vec3d c0 = mc.gameRenderer.getCamera().getCameraPos();
            VertexRendering.drawOutline(ms, consumers.getBuffer(RenderLayers.lines()), VoxelShapes.cuboid(-0.003, -0.003, -0.003, 1.003, 1.003, 1.003),
                    bh.getBlockPos().getX() - c0.x, bh.getBlockPos().getY() - c0.y, bh.getBlockPos().getZ() - c0.z, 0xFFFFFF00, 5.0f);
        }
        // a circle around you: the ring of blocks 5 away (bright) and the ring 4 away (softer), all sides
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        VertexConsumer vc = consumers.getBuffer(RenderLayers.lines());
        VoxelShape box = VoxelShapes.cuboid(-0.003, -0.003, -0.003, 1.003, 1.003, 1.003);
        BlockPos me = mc.player.getBlockPos();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int n = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                if (n != 4 && n != 5) continue;
                BlockPos col = me.add(dx, 0, dz);
                BlockPos g = null;                       // follow the ground up or down
                for (int dy = 2; dy >= -4; dy--) {
                    BlockPos b = col.up(dy);
                    var st = mc.world.getBlockState(b);
                    var up = mc.world.getBlockState(b.up());
                    if (st.isSideSolidFullSquare(mc.world, b, Direction.UP) && (up.isAir() || up.isReplaceable())) { g = b; break; }
                }
                if (g == null) continue;
                int color = n == 5 ? 0xFFFFFF00 : 0xFFFFC800;   // 5 away = bright yellow (aim and press V here), 4 away = softer yellow
                VertexRendering.drawOutline(ms, vc, box, g.getX() - cam.x, g.getY() - cam.y, g.getZ() - cam.z, color, n == 5 ? 4.0f : 2.5f);
            }
        }
    }
}
