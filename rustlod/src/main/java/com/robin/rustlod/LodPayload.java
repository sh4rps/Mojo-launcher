package com.robin.rustlod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: LOD cells for a batch of chunks (2x2 cells of 8 blocks each: height + RGB). */
public record LodPayload(List<Entry> entries) implements CustomPayload {
    public record Entry(int cx, int cz, short[] h, int[] c) { }

    public static final CustomPayload.Id<LodPayload> ID = new CustomPayload.Id<>(Identifier.of("rustlod", "lod"));
    public static final PacketCodec<RegistryByteBuf, LodPayload> CODEC = PacketCodec.of(LodPayload::write, LodPayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeInt(e.cx());
            buf.writeInt(e.cz());
            for (int i = 0; i < 4; i++) buf.writeShort(e.h()[i]);
            for (int i = 0; i < 4; i++) buf.writeInt(e.c()[i]);
        }
    }

    private static LodPayload read(RegistryByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> l = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            int cx = buf.readInt(), cz = buf.readInt();
            short[] h = new short[4];
            int[] c = new int[4];
            for (int i = 0; i < 4; i++) h[i] = buf.readShort();
            for (int i = 0; i < 4; i++) c[i] = buf.readInt();
            l.add(new Entry(cx, cz, h, c));
        }
        return new LodPayload(l);
    }

    @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
