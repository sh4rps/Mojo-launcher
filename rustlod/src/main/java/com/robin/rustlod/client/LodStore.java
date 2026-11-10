package com.robin.rustlod.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;

/** Every chunk's far-terrain cells, remembered across unloads and (per world/server) across sessions. */
public final class LodStore {
    public static final class Cell {
        public final short[] h;
        public final int[] c;
        public boolean real;
        Cell(short[] h, int[] c, boolean real) { this.h = h; this.c = c; this.real = real; }
    }

    private static final Long2ObjectOpenHashMap<Cell> MAP = new Long2ObjectOpenHashMap<>();
    private static String key = null;

    private LodStore() { }

    public static void put(int cx, int cz, short[] h, int[] c, boolean real) {
        long k = ChunkPos.toLong(cx, cz);
        Cell old = MAP.get(k);
        if (old != null && old.real && !real) return; // real data always wins over an estimate
        MAP.put(k, new Cell(h, c, real));
    }

    public static Cell get(int cx, int cz) { return MAP.get(ChunkPos.toLong(cx, cz)); }
    public static int size() { return MAP.size(); }

    private static String keyFor(MinecraftClient c) {
        String s;
        if (c.getServer() != null) s = "sp_" + c.getServer().getSaveProperties().getLevelName();
        else if (c.getCurrentServerEntry() != null) s = "mp_" + c.getCurrentServerEntry().address;
        else s = "unknown";
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Path file(String k) {
        Path d = FabricLoader.getInstance().getGameDir().resolve("rustlod");
        try { Files.createDirectories(d); } catch (IOException ignored) { }
        return d.resolve(k + ".bin");
    }

    public static void loadFor(MinecraftClient c) {
        save();
        MAP.clear();
        key = keyFor(c);
        Path f = file(key);
        if (!Files.exists(f)) return;
        try (InputStream in = Files.newInputStream(f); DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in))) {
            int n = d.readInt();
            for (int i = 0; i < n; i++) {
                int cx = d.readInt(), cz = d.readInt();
                short[] h = new short[4];
                int[] col = new int[4];
                for (int j = 0; j < 4; j++) h[j] = d.readShort();
                for (int j = 0; j < 4; j++) col[j] = d.readInt();
                boolean real = d.readBoolean();
                MAP.put(ChunkPos.toLong(cx, cz), new Cell(h, col, real));
            }
        } catch (Throwable ignored) { }
    }

    public static void save() {
        if (key == null || MAP.isEmpty()) return;
        Path f = file(key);
        try (OutputStream out = Files.newOutputStream(f); DataOutputStream d = new DataOutputStream(new java.io.BufferedOutputStream(out))) {
            d.writeInt(MAP.size());
            for (Long2ObjectOpenHashMap.Entry<Cell> e : MAP.long2ObjectEntrySet()) {
                long k = e.getLongKey();
                Cell c = e.getValue();
                d.writeInt(ChunkPos.getPackedX(k));
                d.writeInt(ChunkPos.getPackedZ(k));
                for (int j = 0; j < 4; j++) d.writeShort(c.h[j]);
                for (int j = 0; j < 4; j++) d.writeInt(c.c[j]);
                d.writeBoolean(c.real);
            }
        } catch (Throwable ignored) { }
    }
}
