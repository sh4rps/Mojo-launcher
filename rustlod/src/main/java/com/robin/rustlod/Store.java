package com.robin.rustlod;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

/** Per-chunk 4x4 summary (height + map colour), remembered across sessions per server / world / dimension. */
public final class Store {
    public static final class C {
        public final short[] h = new short[16];
        public final int[] c = new int[16];
    }

    public static final ConcurrentHashMap<Long, C> MAP = new ConcurrentHashMap<>();
    public static volatile boolean dirty;
    private static Path file;

    private Store() { }

    public static long pack(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }

    public static void capture(ClientWorld w, WorldChunk ch) {
        if (!Cfg.enabled) return;
        try {
            C c = new C();
            ChunkPos cp = ch.getPos();
            int sx = cp.getStartX(), sz = cp.getStartZ();
            BlockPos.Mutable p = new BlockPos.Mutable();
            int bottom = w.getBottomY();
            int nonEmpty = 0;
            for (int cz = 0; cz < 4; cz++) {
                for (int cx = 0; cx < 4; cx++) {
                    int lx = cx * 4 + 2, lz = cz * 4 + 2;
                    int y = ch.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, lx, lz);
                    int ty = Math.max(bottom, y - 1);
                    p.set(sx + lx, ty, sz + lz);
                    BlockState s = ch.getBlockState(p);
                    int col = s.getMapColor(w, p).color;
                    if (col == 0) col = 0x808080;
                    if (y > bottom + 1) nonEmpty++;
                    c.h[cz * 4 + cx] = (short) y;
                    c.c[cz * 4 + cx] = col;
                }
            }
            if (nonEmpty == 0) return;
            MAP.put(pack(cp.x, cp.z), c);
            dirty = true;
        } catch (Throwable ignored) { }
    }

    // ---------------------------------------------------------------- persistence
    private static Path pathFor(MinecraftClient mc, ClientWorld w) {
        String k;
        ServerInfo si = mc.getCurrentServerEntry();
        if (si != null) k = si.address;
        else if (mc.getServer() != null) k = "sp_" + mc.getServer().getSaveProperties().getLevelName();
        else k = "unknown";
        k = (k + "__" + w.getRegistryKey().getValue()).replaceAll("[^A-Za-z0-9._-]", "_");
        return FabricLoader.getInstance().getGameDir().resolve("rustlod").resolve(k + ".bin");
    }

    public static void load(MinecraftClient mc, ClientWorld w) {
        MAP.clear();
        file = pathFor(mc, w);
        dirty = true;
        if (!Files.exists(file)) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != 0x52_4C_4F_44) return;
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                long k = in.readLong();
                C c = new C();
                for (int j = 0; j < 16; j++) c.h[j] = in.readShort();
                for (int j = 0; j < 16; j++) c.c[j] = in.readInt() & 0xFFFFFF;
                MAP.put(k, c);
            }
        } catch (Throwable ignored) { }
    }

    public static void save() {
        if (file == null || MAP.isEmpty()) return;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeInt(0x52_4C_4F_44);
                out.writeInt(MAP.size());
                for (java.util.Map.Entry<Long, C> e : MAP.entrySet()) {
                    out.writeLong(e.getKey());
                    for (short s : e.getValue().h) out.writeShort(s);
                    for (int c : e.getValue().c) out.writeInt(c);
                }
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable ignored) { }
    }
}
