package dev.kodari.autocartpvp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Looking down, outlines in green every block where a TNT minecart blast would hurt you little or not at all.
 * Damage is estimated like vanilla explosions do (distance + how much of your body the blocks shield), without armor/enchants.
 */
public final class SafeSpots {
    private static final double POWER = 4.5;            // TNT minecart blast power (4.0 - 5.5)
    private static volatile List<BlockPos> safe = Collections.emptyList();
    private static int timer;

    private SafeSpots() { }

    public static void tick(MinecraftClient mc) {
        CartConfig cfg = CartConfig.get();
        if (!cfg.showSafeSpots || mc.player == null || mc.world == null || mc.player.getPitch() < 12f) { safe = Collections.emptyList(); return; }
        // only when every AutoCart item is in the inventory (rail, TNT minecart, flint/crossbow...)
        if (!Equipment.scan(mc.player.getInventory(), cfg.autoDetect).ready(cfg.crossbowMode)) { safe = Collections.emptyList(); return; }
        if (timer-- > 0) return;
        timer = 8;
        double reach = cfg.range + 1.0;
        int r = (int) Math.ceil(reach);
        BlockPos pb = mc.player.getBlockPos();
        Vec3d eye = mc.player.getEyePos();
        Box bb = mc.player.getBoundingBox();
        double w = bb.getLengthX(), h = bb.getLengthY();
        // sample points inside the player's body (like vanilla's exposure grid, a bit coarser)
        List<Vec3d> pts = new ArrayList<>();
        for (double fx : new double[]{0.2, 0.8}) for (double fy : new double[]{0.15, 0.5, 0.85}) for (double fz : new double[]{0.2, 0.8})
            pts.add(new Vec3d(bb.minX + w * fx, bb.minY + h * fy, bb.minZ + bb.getLengthZ() * fz));
        Vec3d feet = mc.player.getEntityPos();
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -3; dy <= 1; dy++) {
            BlockPos base = pb.add(dx, dy, dz);
            BlockPos above = base.up();
            var bs = mc.world.getBlockState(base);
            if (!bs.isSideSolidFullSquare(mc.world, base, Direction.UP)) continue;
            var as = mc.world.getBlockState(above);
            if (!(as.isAir() || as.isReplaceable())) continue;
            Vec3d top = new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
            if (eye.squaredDistanceTo(top) > reach * reach) continue;
            Vec3d c = new Vec3d(base.getX() + 0.5, base.getY() + 1.25, base.getZ() + 0.5);
            if (damage(mc, c, feet, pts) <= cfg.safeDamage) out.add(base);
        }
        safe = out;
    }

    /** Estimated damage (half-hearts) the blast at c would do to the player. */
    private static double damage(MinecraftClient mc, Vec3d c, Vec3d feet, List<Vec3d> pts) {
        double q = POWER * 2.0;
        double dist = Math.sqrt(feet.squaredDistanceTo(c)) / q;
        if (dist > 1.0) return 0;
        int seen = 0;
        for (Vec3d p : pts) {
            var hit = mc.world.raycast(new RaycastContext(c, p, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) seen++;
        }
        double exposure = seen / (double) pts.size();
        double impact = (1.0 - dist) * exposure;
        return Math.floor((impact * impact + impact) / 2.0 * 7.0 * q + 1.0) * (exposure <= 0 ? 0 : 1);
    }

    public static void render(MinecraftClient mc, MatrixStack ms, net.minecraft.client.render.VertexConsumerProvider consumers) {
        List<BlockPos> list = safe;
        if (list.isEmpty() || mc.player == null) return;
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        VertexConsumer vc = consumers.getBuffer(RenderLayers.lines());
        VoxelShape top = VoxelShapes.cuboid(0.04, 0.0, 0.04, 0.96, 0.02, 0.96);
        for (BlockPos p : list)
            VertexRendering.drawOutline(ms, vc, top, p.getX() - cam.x, p.getY() + 1.0 - cam.y, p.getZ() - cam.z, 0xFF55FF55, 3.0f);
    }
}
