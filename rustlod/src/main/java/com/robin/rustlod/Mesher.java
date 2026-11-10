package com.robin.rustlod;

import java.util.Map;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;

/** Turns the remembered columns into one static mesh (greedy runs along X, walls where heights differ). */
final class Mesher {
    static final class Mesh {
        BuiltBuffer buf;
        BufferAllocator alloc;
        int ox, oz;
    }

    private Mesher() { }

    /**
     * @param ccx,ccz  centre chunk of the build
     * @param loaded   (2R+1)^2 flags, true = a real chunk is loaded there (skip)
     */
    static Mesh build(int ccx, int ccz, int R, boolean[] loaded, Map<Long, Store.C> snap, int yoff) {
        int W = 2 * R + 1;
        int N = W * 4;
        short[] gh = new short[N * N];
        int[] gc = new int[N * N];
        boolean[] has = new boolean[N * N];
        int r2 = R * R;
        for (int j = 0; j < W; j++) {
            for (int i = 0; i < W; i++) {
                int dx = i - R, dz = j - R;
                if (dx * dx + dz * dz > r2) continue;
                if (loaded[j * W + i]) continue;
                Store.C c = snap.get(Store.pack(ccx + dx, ccz + dz));
                if (c == null) continue;
                for (int cz = 0; cz < 4; cz++) {
                    for (int cx = 0; cx < 4; cx++) {
                        int idx = (j * 4 + cz) * N + (i * 4 + cx);
                        gh[idx] = c.h[cz * 4 + cx];
                        gc[idx] = c.c[cz * 4 + cx];
                        has[idx] = true;
                    }
                }
            }
        }

        Mesh m = new Mesh();
        m.ox = (ccx - R) * 16;
        m.oz = (ccz - R) * 16;
        m.alloc = new BufferAllocator(1 << 20);
        BufferBuilder bb = new BufferBuilder(m.alloc, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int quads = 0;

        for (int gz = 0; gz < N; gz++) {
            int gx = 0;
            while (gx < N) {
                int idx = gz * N + gx;
                if (!has[idx]) { gx++; continue; }
                int start = gx;
                short h = gh[idx];
                int c = gc[idx];
                gx++;
                while (gx < N && has[gz * N + gx] && gh[gz * N + gx] == h && gc[gz * N + gx] == c) gx++;
                float y = h - yoff;
                top(bb, start * 4f, gx * 4f, gz * 4f, (gz + 1) * 4f, y, c);
                quads++;
            }
        }
        for (int gz = 0; gz < N; gz++) {
            for (int gx = 0; gx < N; gx++) {
                int idx = gz * N + gx;
                if (!has[idx]) continue;
                int h = gh[idx], c = gc[idx];
                if (gx + 1 < N && has[idx + 1] && gh[idx + 1] < h) {
                    wallX(bb, (gx + 1) * 4f, gz * 4f, (gz + 1) * 4f, gh[idx + 1] - yoff, h - yoff, c); quads++;
                }
                if (gx > 0 && has[idx - 1] && gh[idx - 1] < h) {
                    wallX(bb, gx * 4f, gz * 4f, (gz + 1) * 4f, gh[idx - 1] - yoff, h - yoff, c); quads++;
                }
                if (gz + 1 < N && has[idx + N] && gh[idx + N] < h) {
                    wallZ(bb, (gz + 1) * 4f, gx * 4f, (gx + 1) * 4f, gh[idx + N] - yoff, h - yoff, c); quads++;
                }
                if (gz > 0 && has[idx - N] && gh[idx - N] < h) {
                    wallZ(bb, gz * 4f, gx * 4f, (gx + 1) * 4f, gh[idx - N] - yoff, h - yoff, c); quads++;
                }
            }
        }
        if (quads == 0) { m.alloc.close(); return null; }
        m.buf = bb.endNullable();
        if (m.buf == null) { m.alloc.close(); return null; }
        return m;
    }

    private static int shade(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static void v(BufferBuilder bb, float x, float y, float z, int rgb) {
        bb.vertex(x, y, z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255);
    }

    private static void top(BufferBuilder bb, float x0, float x1, float z0, float z1, float y, int c) {
        v(bb, x0, y, z0, c); v(bb, x0, y, z1, c); v(bb, x1, y, z1, c); v(bb, x1, y, z0, c);
    }

    private static void wallX(BufferBuilder bb, float x, float z0, float z1, float yl, float yh, int c) {
        int s = shade(c, 0.8f);
        v(bb, x, yl, z0, s); v(bb, x, yh, z0, s); v(bb, x, yh, z1, s); v(bb, x, yl, z1, s);
    }

    private static void wallZ(BufferBuilder bb, float z, float x0, float x1, float yl, float yh, int c) {
        int s = shade(c, 0.6f);
        v(bb, x0, yl, z, s); v(bb, x0, yh, z, s); v(bb, x1, yh, z, s); v(bb, x1, yl, z, s);
    }
}
