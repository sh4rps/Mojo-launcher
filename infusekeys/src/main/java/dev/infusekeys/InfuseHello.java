package dev.infusekeys;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** On join, tells the server which Infuse Keys version the client runs (channel infusekeys:hello). */
public class InfuseHello implements ClientModInitializer {
    private static final String VERSION = "1.5.0";

    public record Hello(String version) implements CustomPayload {
        public static final CustomPayload.Id<Hello> ID = new CustomPayload.Id<>(Identifier.of("infusekeys", "hello"));
        public static final PacketCodec<RegistryByteBuf, Hello> CODEC =
                PacketCodecs.STRING.xmap(Hello::new, Hello::version).cast();

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playC2S().register(Hello.ID, Hello.CODEC);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                ClientPlayNetworking.send(new Hello(VERSION)));
    }
}
