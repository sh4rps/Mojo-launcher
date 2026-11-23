package dev.robin.aggrchunk;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Keeps short-lived loading tickets around and ahead of every player, so the chunks are generated before you get there. */
public class AggrChunk implements ModInitializer {
    private static final String[] NAMES = {"Off", "Normal", "High", "Extreme"};
    private static final int[] RADIUS = {0, 2, 3, 4};
    private static final int[] AHEAD = {0, 1, 2, 3};      // number of look-ahead points
    private static final int[] EVERY = {0, 20, 10, 5};    // ticks between refreshes
    private static volatile int level = 2;

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("aggrchunk.properties"); }

    private static void load() {
        try (InputStream in = Files.newInputStream(file())) {
            Properties p = new Properties(); p.load(in);
            level = Math.max(0, Math.min(3, Integer.parseInt(p.getProperty("level", "2").trim())));
        } catch (Exception ignored) { }
    }

    private static void save() {
        try (OutputStream out = Files.newOutputStream(file())) {
            Properties p = new Properties(); p.setProperty("level", "" + level);
            p.store(out, "Aggressive Chunk Gen level: 0 Off, 1 Normal, 2 High, 3 Extreme");
        } catch (Exception ignored) { }
    }

    @Override public void onInitialize() {
        load();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
                dispatcher.register(CommandManager.literal("aggrchunk")
                        .executes(c -> {
                            c.getSource().sendFeedback(() -> Text.literal("Aggressive Chunk Gen: " + NAMES[level] + " (use /aggrchunk 0-3)"), false);
                            return 1;
                        })
                        .then(CommandManager.argument("level", IntegerArgumentType.integer(0, 3)).executes(c -> {
                            level = IntegerArgumentType.getInteger(c, "level");
                            save();
                            c.getSource().sendFeedback(() -> Text.literal("Aggressive Chunk Gen: " + NAMES[level]), false);
                            return 1;
                        }))));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int lv = level;
            if (lv == 0 || server.getTicks() % EVERY[lv] != 0) return;
            for (ServerWorld world : server.getWorlds()) {
                for (ServerPlayerEntity pl : world.getPlayers()) {
                    try { preload(world, pl, lv); } catch (Throwable ignored) { }
                }
            }
        });
    }

    private static void preload(ServerWorld world, ServerPlayerEntity pl, int lv) {
        var cm = world.getChunkManager();
        int r = RADIUS[lv];
        ChunkPos here = pl.getChunkPos();
        cm.addTicket(ChunkTicketType.PORTAL, here, r + 2);
        // look ahead along the facing/move direction
        Vec3d dir = pl.getVelocity().horizontalLength() > 0.05 ? pl.getVelocity() : pl.getRotationVec(1.0f);
        double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (len < 1.0e-4) return;
        double dx = dir.x / len, dz = dir.z / len;
        for (int i = 1; i <= AHEAD[lv]; i++) {
            int step = (r + 3) * i; // chunks
            ChunkPos p = new ChunkPos(here.x + (int) Math.round(dx * step), here.z + (int) Math.round(dz * step));
            cm.addTicket(ChunkTicketType.PORTAL, p, r);
        }
    }
}
