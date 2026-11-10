package com.robin.rustlod;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/** Streams far-terrain cells to players that have the mod (overworld only). */
public final class LodServer {
    private static final class St {
        boolean built;
        int cx0, cz0;
        long[] q = new long[0];
        int qi;
        final LongOpenHashSet sent = new LongOpenHashSet();
    }

    private static final Map<UUID, St> STATES = new HashMap<>();

    private LodServer() { }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(LodPayload.ID, LodPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(LodServer::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.getPlayer().getUuid()));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 2 != 0) return;
        int vd = server.getPlayerManager().getViewDistance();
        int radius = Cfg.serverRadius;
        if (radius <= vd + 1) return;
        for (ServerPlayerEntity p : new ArrayList<>(server.getPlayerManager().getPlayerList())) {
            ServerWorld w = p.getServerWorld();
            if (w.getRegistryKey() != World.OVERWORLD) continue;
            if (!ServerPlayNetworking.canSend(p, LodPayload.ID)) continue;
            St st = STATES.computeIfAbsent(p.getUuid(), k -> new St());
            ChunkPos cp = p.getChunkPos();
            if (!st.built || Math.abs(cp.x - st.cx0) > 6 || Math.abs(cp.z - st.cz0) > 6) rebuild(st, cp, vd, radius);
            if (st.qi >= st.q.length) continue;
            List<LodPayload.Entry> batch = new ArrayList<>();
            int n = 0;
            while (st.qi < st.q.length && n < Cfg.budget) {
                long k = st.q[st.qi++];
                if (!st.sent.add(k)) continue;
                int cx = ChunkPos.getPackedX(k), cz = ChunkPos.getPackedZ(k);
                WorldChunk real = w.getChunkManager().getWorldChunk(cx, cz);
                LodSampler.Out o = real != null ? LodSampler.fromChunk(real, w, cx, cz) : LodSampler.estimate(w, cx, cz);
                batch.add(new LodPayload.Entry(cx, cz, o.h, o.c));
                n++;
            }
            if (!batch.isEmpty()) ServerPlayNetworking.send(p, new LodPayload(batch));
        }
    }

    private static void rebuild(St st, ChunkPos c, int vd, int radius) {
        st.built = true;
        st.cx0 = c.x;
        st.cz0 = c.z;
        int inner = (vd + 1) * (vd + 1), outer = radius * radius;
        List<long[]> l = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d = dx * dx + dz * dz;
                if (d <= inner || d > outer) continue;
                long k = ChunkPos.toLong(c.x + dx, c.z + dz);
                if (st.sent.contains(k)) continue;
                l.add(new long[]{d, k});
            }
        }
        l.sort(Comparator.comparingLong(a -> a[0]));
        st.q = new long[l.size()];
        for (int i = 0; i < st.q.length; i++) st.q[i] = l.get(i)[1];
        st.qi = 0;
    }
}
