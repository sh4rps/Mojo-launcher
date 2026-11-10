package com.robin.rustlod.client;

import com.robin.rustlod.Cfg;
import com.robin.rustlod.LodPayload;
import com.robin.rustlod.LodSampler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

public class RustLodClient implements ClientModInitializer {
    private static KeyBinding toggle;

    @Override
    public void onInitializeClient() {
        toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.rustlod.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.rustlod"));

        ClientPlayNetworking.registerGlobalReceiver(LodPayload.ID, (payload, ctx) -> {
            for (LodPayload.Entry e : payload.entries()) LodStore.put(e.cx(), e.cz(), e.h(), e.c(), false);
        });

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (world.getRegistryKey() != World.OVERWORLD) return;
            int cx = chunk.getPos().x, cz = chunk.getPos().z;
            LodSampler.Out o = LodSampler.fromChunk(chunk, world, cx, cz);
            LodStore.put(cx, cz, o.h, o.c, true);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> LodStore.loadFor(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LodStore.save());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> LodStore.save());

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggle.wasPressed()) {
                Cfg.enabled = !Cfg.enabled;
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal("Rust LOD: " + (Cfg.enabled ? "ON (" + LodStore.size() + " chunks known)" : "OFF")), true);
                }
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(LodRenderer::render);
    }
}
