package com.robin.rustlod.client;

import com.robin.rustlod.Cfg;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import org.joml.Matrix4f;

/** Draws remembered / streamed far terrain as flat-shaded coloured columns outside the loaded chunks. */
public final class LodRenderer {
    private static final int MISSING = Integer.MIN_VALUE;

    private LodRenderer() { }

    public static void render(WorldRenderContext ctx) {
        if (!Cfg.enabled) return;
        ClientWorld w = ctx.world();
        if (w == null || w.getRegistryKey() != World.OVERWORLD) return;
        VertexConsumerProvider vcp = ctx.consumers();
        MatrixStack ms = ctx.matrixStack();
        if (vcp == null || ms == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d cam = ctx.camera().getPos();
        float yawRad = ctx.camera().getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double lookX = -Math.sin(yawRad), lookZ = Math.cos(yawRad);
        float td = ctx.tickCounter().getTickDelta(false);

        // time-of-day brightness and sky colour for the haze
        float ang = w.getSkyAngle(td);
        float f = MathHelper.clamp(MathHelper.cos(ang * (float) (Math.PI * 2)) * 2.0F + 0.5F, 0.0F, 1.0F);
        float bright = 0.18f + 0.82f * f;
        Vec3d sky = w.getSkyColor(cam, td);
        float sr = (float) sky.x, sg = (float) sky.y, sb = (float) sky.z;

        int rd = mc.options.getClampedViewDistance();
        double near = rd * 16.0;
        int radius = Math.max(Cfg.radius, rd + 2);
        double far = radius * 16.0;
        float hazeBase = Cfg.haze / 100f;

        Matrix4f m = ms.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(Layers.LOD);

        int ccx = MathHelper.floor(cam.x) >> 4, ccz = MathHelper.floor(cam.z) >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = ccx + dx, cz = ccz + dz;
                if (dx * dx + dz * dz > radius * radius) continue;
                LodStore.Cell cell = LodStore.get(cx, cz);
                if (cell == null) continue;
                // skip chunks the game is drawing itself
                if (w.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false) != null) continue;
                // behind the camera
                double mx = cx * 16 + 8 - cam.x, mz = cz * 16 + 8 - cam.z;
                if (mx * lookX + mz * lookZ < -24.0) continue;
                double dist = Math.sqrt(mx * mx + mz * mz);
                float t = (float) MathHelper.clamp((dist - near) / Math.max(1.0, far - near), 0.0, 1.0);
                float mix = MathHelper.clamp(hazeBase + (1.0f - hazeBase) * t, 0f, 1f);
                for (int i = 0; i < 4; i++) {
                    int gx = cx * 2 + (i & 1), gz = cz * 2 + (i >> 1);
                    int h = cell.h[i];
                    int rgb = cell.c[i];
                    float r = ((rgb >> 16) & 255) / 255f * bright;
                    float g = ((rgb >> 8) & 255) / 255f * bright;
                    float b = (rgb & 255) / 255f * bright;
                    r += (sr - r) * mix; g += (sg - g) * mix; b += (sb - b) * mix;
                    double x0 = gx * 8 - cam.x, z0 = gz * 8 - cam.z;
                    float yt = (float) (h - 1 - cam.y);
                    quadTop(vc, m, (float) x0, yt, (float) z0, r, g, b);
                    // walls toward lower neighbours
                    wall(vc, m, gx + 1, gz, h, (float) (x0 + 8), (float) z0, (float) (x0 + 8), (float) (z0 + 8), yt, cam, r * 0.8f, g * 0.8f, b * 0.8f);
                    wall(vc, m, gx - 1, gz, h, (float) x0, (float) z0, (float) x0, (float) (z0 + 8), yt, cam, r * 0.8f, g * 0.8f, b * 0.8f);
                    wall(vc, m, gx, gz + 1, h, (float) x0, (float) (z0 + 8), (float) (x0 + 8), (float) (z0 + 8), yt, cam, r * 0.9f, g * 0.9f, b * 0.9f);
                    wall(vc, m, gx, gz - 1, h, (float) x0, (float) z0, (float) (x0 + 8), (float) z0, yt, cam, r * 0.9f, g * 0.9f, b * 0.9f);
                }
            }
        }
    }

    private static int cellHeight(int gx, int gz) {
        LodStore.Cell c = LodStore.get(gx >> 1, gz >> 1);
        if (c == null) return MISSING;
        return c.h[(gx & 1) | ((gz & 1) << 1)];
    }

    private static void quadTop(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b) {
        int R = clamp(r), G = clamp(g), B = clamp(b);
        vc.vertex(m, x, y, z).color(R, G, B, 255);
        vc.vertex(m, x, y, z + 8).color(R, G, B, 255);
        vc.vertex(m, x + 8, y, z + 8).color(R, G, B, 255);
        vc.vertex(m, x + 8, y, z).color(R, G, B, 255);
    }

    private static void wall(VertexConsumer vc, Matrix4f m, int ngx, int ngz, int h, float xa, float za, float xb, float zb,
                             float yTop, Vec3d cam, float r, float g, float b) {
        int nh = cellHeight(ngx, ngz);
        if (nh == MISSING || nh >= h) return;
        float yBot = (float) (nh - 1 - cam.y);
        int R = clamp(r), G = clamp(g), B = clamp(b);
        vc.vertex(m, xa, yTop, za).color(R, G, B, 255);
        vc.vertex(m, xb, yTop, zb).color(R, G, B, 255);
        vc.vertex(m, xb, yBot, zb).color(R, G, B, 255);
        vc.vertex(m, xa, yBot, za).color(R, G, B, 255);
    }

    private static int clamp(float v) { return MathHelper.clamp((int) (v * 255f + 0.5f), 0, 255); }
}
