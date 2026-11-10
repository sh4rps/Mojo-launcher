package com.robin.rustlod;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

/** Turns a chunk (real, or estimated from the world generator) into 2x2 LOD cells: top height + colour. */
public final class LodSampler {
    public static final class Out {
        public final short[] h = new short[4];
        public final int[] c = new int[4];
    }

    private LodSampler() { }

    /** From real chunk data (server chunk or client chunk). */
    public static Out fromChunk(Chunk chunk, World world, int cx, int cz) {
        Out o = new Out();
        Heightmap hm = chunk.getHeightmap(Heightmap.Type.MOTION_BLOCKING);
        int bottom = world.getBottomY();
        for (int i = 0; i < 4; i++) {
            int lx = (i & 1) * 8 + 4, lz = (i >> 1) * 8 + 4;
            int h = hm.get(lx, lz);
            if (h <= bottom) h = world.getSeaLevel();
            BlockPos pos = new BlockPos(cx * 16 + lx, h - 1, cz * 16 + lz);
            BlockState st = chunk.getBlockState(pos);
            Biome b = world.getBiome(pos).value();
            o.h[i] = (short) h;
            o.c[i] = realColor(st, world, pos, b);
        }
        return o;
    }

    /** From the world generator only (chunk need not exist). Server thread. */
    public static Out estimate(ServerWorld w, int cx, int cz) {
        Out o = new Out();
        ChunkGenerator g = w.getChunkManager().getChunkGenerator();
        NoiseConfig nc = w.getChunkManager().getNoiseConfig();
        int bottom = w.getBottomY(), top = bottom + w.getHeight(), sea = w.getSeaLevel();
        for (int i = 0; i < 4; i++) {
            int x = cx * 16 + (i & 1) * 8 + 4, z = cz * 16 + (i >> 1) * 8 + 4;
            VerticalBlockSample col = g.getColumnSample(x, z, w, nc);
            int y = top - 1;
            BlockState s = Blocks.AIR.getDefaultState();
            while (y > bottom) {
                s = col.getState(y);
                if (!s.isAir()) break;
                y--;
            }
            boolean water = s.getFluidState().isIn(FluidTags.WATER);
            int h = y + 1;
            if (y <= bottom) { h = sea; water = true; }
            Biome b = w.getBiome(new BlockPos(x, Math.max(y, bottom), z)).value();
            o.h[i] = (short) h;
            o.c[i] = estimateColor(water, b, x, z, h, sea);
        }
        return o;
    }

    private static int realColor(BlockState st, World w, BlockPos pos, Biome b) {
        if (st.getFluidState().isIn(FluidTags.WATER)) return b.getWaterColor();
        if (st.isOf(Blocks.GRASS_BLOCK)) return b.getGrassColorAt(pos.getX(), pos.getZ());
        if (st.isIn(BlockTags.LEAVES)) return b.getFoliageColor();
        if (st.isOf(Blocks.SNOW) || st.isOf(Blocks.SNOW_BLOCK) || st.isOf(Blocks.POWDER_SNOW)) return 0xF2F6F8;
        MapColor mc = st.getMapColor(w, pos);
        if (mc == MapColor.CLEAR) return 0x7F7F7F;
        return mc.color;
    }

    private static int estimateColor(boolean water, Biome b, int x, int z, int h, int sea) {
        if (water) return b.getWaterColor();
        float t = b.getTemperature();
        if (t < 0.15f || h > 200) return 0xF2F6F8;
        if (h > 150) return 0x8A8A8A;
        if (t >= 1.5f && !b.hasPrecipitation()) return 0xDCD0A0;
        if (h <= sea + 1) return 0xDCD0A0;
        return b.getGrassColorAt(x, z);
    }
}
