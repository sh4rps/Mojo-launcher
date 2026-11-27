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

    public static void render(MinecraftClient mc, MatrixStack ms, VertexConsumerProvider consumers) {
        if (mc.player == null || mc.world == null || consumers == null) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK || bhr.getSide() == Direction.DOWN) return;
        BlockPos base = bhr.getBlockPos();
        BlockPos rail = base.up();
        var rs = mc.world.getBlockState(rail);
        boolean free = (rs.isAir() || rs.isReplaceable()) && mc.world.getBlockState(rail.up()).isAir();
        Vec3d eye = mc.player.getEyePos();
        double hx = rail.getX() + 0.5 - eye.x, hz = rail.getZ() + 0.5 - eye.z;
        boolean farEnough = Math.sqrt(hx * hx + hz * hz) >= 2.2;
        int color = (free && farEnough) ? 0xFFFFFF00 : 0xFFFF3030;
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        VertexConsumer vc = consumers.getBuffer(RenderLayers.lines());
        VoxelShape box = VoxelShapes.cuboid(-0.003, -0.003, -0.003, 1.003, 1.003, 1.003);
        VertexRendering.drawOutline(ms, vc, box, base.getX() - cam.x, base.getY() - cam.y, base.getZ() - cam.z, color, 5.0f);
    }
}
