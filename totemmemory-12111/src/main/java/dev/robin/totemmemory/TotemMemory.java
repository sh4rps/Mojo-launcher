package dev.robin.totemmemory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Remembers which chests/barrels you opened hold Totems of Undying and outlines them (and totem item frames) in yellow. */
public class TotemMemory implements ClientModInitializer {
    private static final double RANGE = 256.0;             // blocks
    private static final int YELLOW = 0xFFFFFF00;
    private static final Map<String, Boolean> memory = new HashMap<>();   // "dim|x|y|z" -> holds a totem
    private static boolean enabled = true;
    private static BlockPos lastUsed;
    private static RenderLayer throughWalls;

    /** Same as vanilla lines, but with the depth test off so the outline shows through walls. */
    private static RenderLayer layer() {
        if (throughWalls == null) {
            RenderPipeline p = RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("totemmemory", "pipeline/lines_xray"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build();
            throughWalls = RenderLayer.of("totemmemory_lines_xray", RenderSetup.builder(p).build());
        }
        return throughWalls;
    }
    private static String loadedFor = "";

    private static String worldId(MinecraftClient mc) {
        var si = mc.getCurrentServerEntry();
        String id = si != null ? si.address : (mc.getServer() != null ? "sp-" + mc.getServer().getSaveProperties().getLevelName() : "unknown");
        return id.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Path file(String id) { return FabricLoader.getInstance().getConfigDir().resolve("totemmemory-" + id + ".txt"); }

    private static void ensureLoaded(MinecraftClient mc) {
        String id = worldId(mc);
        if (id.equals(loadedFor)) return;
        loadedFor = id; memory.clear();
        try { for (String line : Files.readAllLines(file(id))) { String[] p = line.split("=", 2); if (p.length == 2) memory.put(p[0], Boolean.parseBoolean(p[1])); } }
        catch (Exception ignored) { }
    }

    private static void save(MinecraftClient mc) {
        try {
            List<String> out = new ArrayList<>();
            for (var e : memory.entrySet()) if (e.getValue()) out.add(e.getKey() + "=true");
            Files.write(file(worldId(mc)), out);
        } catch (Exception ignored) { }
    }

    private static String key(MinecraftClient mc, BlockPos p) {
        return mc.world.getRegistryKey().getValue() + "|" + p.getX() + "|" + p.getY() + "|" + p.getZ();
    }

    private static boolean isStorage(BlockState s) {
        return s.isOf(Blocks.CHEST) || s.isOf(Blocks.TRAPPED_CHEST) || s.isOf(Blocks.BARREL);
    }

    private static void record(MinecraftClient mc, BlockPos pos, boolean has) {
        BlockState st = mc.world.getBlockState(pos);
        if (!isStorage(st)) return;
        Boolean old = memory.put(key(mc, pos), has);
        if (st.getBlock() instanceof ChestBlock && st.contains(ChestBlock.CHEST_TYPE) && st.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE)
            memory.put(key(mc, pos.offset(ChestBlock.getFacing(st))), has);
        if (old == null || old != has) save(mc);
    }

    @Override public void onInitializeClient() {
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.totemmemory.toggle", GLFW.GLFW_KEY_H, KeyBinding.Category.create(Identifier.of("totemmemory", "controls"))));

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient()) lastUsed = hit.getBlockPos();
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.wasPressed()) {
                enabled = !enabled;
                if (mc.player != null) mc.player.sendMessage(Text.literal("Totem Memory: " + (enabled ? "ON" : "OFF")), true);
            }
            if (mc.world == null) return;
            ensureLoaded(mc);
            if (mc.currentScreen instanceof GenericContainerScreen gs && lastUsed != null && gs.getScreenHandler() instanceof GenericContainerScreenHandler h) {
                Inventory inv = h.getInventory();
                boolean has = false;
                for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) { has = true; break; }
                record(mc, lastUsed, has);
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!enabled || mc.world == null || mc.player == null) return;
            Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
            VertexConsumer vc = ctx.consumers().getBuffer(layer());
            var ms = ctx.matrices();
            String dim = mc.world.getRegistryKey().getValue().toString();
            double r2 = RANGE * RANGE;
            Iterator<Map.Entry<String, Boolean>> it = memory.entrySet().iterator();
            VoxelShape unit = VoxelShapes.cuboid(0, 0, 0, 1, 1, 1);
            while (it.hasNext()) {
                var e = it.next();
                if (!e.getValue()) continue;
                String[] p = e.getKey().split("\\|");
                if (p.length != 4 || !p[0].equals(dim)) continue;
                BlockPos pos = new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                double dx = pos.getX() + 0.5 - cam.x, dy = pos.getY() + 0.5 - cam.y, dz = pos.getZ() + 0.5 - cam.z;
                if (dx * dx + dy * dy + dz * dz > r2) continue;
                if (mc.world.isChunkLoaded(pos) && !isStorage(mc.world.getBlockState(pos))) { it.remove(); continue; }  // chest was broken
                VertexRendering.drawOutline(ms, vc, unit, pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, YELLOW, 3.0f);
            }
            for (Entity en : mc.world.getEntities()) {
                if (!(en instanceof ItemFrameEntity f) || !f.getHeldItemStack().isOf(Items.TOTEM_OF_UNDYING)) continue;
                Box b = f.getBoundingBox();
                double dx = b.getCenter().x - cam.x, dy = b.getCenter().y - cam.y, dz = b.getCenter().z - cam.z;
                if (dx * dx + dy * dy + dz * dz > r2) continue;
                VoxelShape s = VoxelShapes.cuboid(0, 0, 0, b.getLengthX(), b.getLengthY(), b.getLengthZ());
                VertexRendering.drawOutline(ms, vc, s, b.minX - cam.x, b.minY - cam.y, b.minZ - cam.z, YELLOW, 3.0f);
            }
        });
    }
}
