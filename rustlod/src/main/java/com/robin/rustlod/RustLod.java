package com.robin.rustlod;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class RustLod implements ClientModInitializer {
    private static KeyBinding toggle;
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rustlod-mesher");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final AtomicReference<Mesher.Mesh> READY = new AtomicReference<>();
    private static volatile boolean busy;

    private static VertexBuffer vb;
    private static int meshOx, meshOz;
    private static ClientWorld lastWorld;
    private static int buildCx = Integer.MIN_VALUE, buildCz;
    private static long lastBuild, lastSave;
    private static int tick;
    private static boolean failed;

    @Override
    public void onInitializeClient() {
        Cfg.load();
        toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.rustlod.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.rustlod"));

        ClientChunkEvents.CHUNK_LOAD.register(Store::capture);
        ClientChunkEvents.CHUNK_UNLOAD.register(Store::capture);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> Store.save());
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> Store.save());
        ClientTickEvents.END_CLIENT_TICK.register(RustLod::onTick);
        WorldRenderEvents.BEFORE_ENTITIES.register(RustLod::render);
    }

    // ------------------------------------------------------------ tick: schedule mesh builds
    private static void onTick(MinecraftClient mc) {
        while (toggle.wasPressed()) {
            Cfg.enabled = !Cfg.enabled;
            Cfg.save();
            if (mc.player != null) mc.player.sendMessage(Text.literal("Rust LOD: " + (Cfg.enabled ? "far terrain ON" : "OFF")), true);
            buildCx = Integer.MIN_VALUE;
        }
        ClientWorld w = mc.world;
        if (w == null || mc.player == null) {
            if (lastWorld != null) { Store.save(); lastWorld = null; }
            return;
        }
        if (w != lastWorld) {
            if (lastWorld != null) Store.save();
            Store.load(mc, w);
            lastWorld = w;
            buildCx = Integer.MIN_VALUE;
        }
        long now = System.currentTimeMillis();
        if (now - lastSave > 120000) { lastSave = now; Store.save(); }
        if (!Cfg.enabled || failed || busy) return;
        if (++tick < 10) return;
        tick = 0;

        int pcx = mc.player.getChunkPos().x, pcz = mc.player.getChunkPos().z;
        boolean moved = buildCx == Integer.MIN_VALUE || Math.abs(pcx - buildCx) >= 3 || Math.abs(pcz - buildCz) >= 3;
        boolean changed = Store.dirty && now - lastBuild > 4000;
        if (!moved && !changed) return;

        final int R = Cfg.radius;
        final int W = 2 * R + 1;
        final boolean[] loaded = new boolean[W * W];
        final Map<Long, Store.C> snap = new HashMap<>();
        for (int j = 0; j < W; j++) {
            for (int i = 0; i < W; i++) {
                int cx = pcx - R + i, cz = pcz - R + j;
                loaded[j * W + i] = w.getChunkManager().isChunkLoaded(cx, cz);
                Store.C c = Store.MAP.get(Store.pack(cx, cz));
                if (c != null) snap.put(Store.pack(cx, cz), c);
            }
        }
        Store.dirty = false;
        buildCx = pcx; buildCz = pcz; lastBuild = now;
        busy = true;
        final int yoff = Cfg.yOffset;
        final int fx = pcx, fz = pcz;
        POOL.execute(() -> {
            try {
                Mesher.Mesh m = Mesher.build(fx, fz, R, loaded, snap, yoff);
                Mesher.Mesh old = READY.getAndSet(m == null ? emptyMarker() : m);
                free(old);
            } catch (Throwable t) {
                failed = true;
            } finally {
                busy = false;
            }
        });
    }

    private static Mesher.Mesh emptyMarker() { return new Mesher.Mesh(); }

    private static void free(Mesher.Mesh m) {
        if (m == null) return;
        try { if (m.buf != null) m.buf.close(); } catch (Throwable ignored) { }
        try { if (m.alloc != null) m.alloc.close(); } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------ render
    private static void render(WorldRenderContext ctx) {
        if (!Cfg.enabled || failed) return;
        try {
            Mesher.Mesh m = READY.getAndSet(null);
            if (m != null) {
                if (m.buf == null) {
                    if (vb != null) { vb.close(); vb = null; }
                } else {
                    if (vb == null) vb = new VertexBuffer(com.mojang.blaze3d.buffers.BufferUsage.STATIC_WRITE);
                    vb.bind();
                    vb.upload(m.buf);
                    VertexBuffer.unbind();
                    meshOx = m.ox; meshOz = m.oz;
                }
                free(m);
            }
            if (vb == null) return;
            Vec3d cam = ctx.camera().getPos();
            Matrix4f mv = new Matrix4f(ctx.positionMatrix())
                    .translate((float) (meshOx - cam.x), (float) (-cam.y), (float) (meshOz - cam.z));
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            RenderSystem.enableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            vb.bind();
            vb.draw(mv, ctx.projectionMatrix(), RenderSystem.getShader());
            VertexBuffer.unbind();
            RenderSystem.enableCull();
        } catch (Throwable t) {
            failed = true;
        }
    }
}
