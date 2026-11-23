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
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.command.ServerCommandSource;

/** Keeps short-lived loading tickets around and ahead of every player, so the chunks are generated before you get there. */
public class AggrChunk implements ModInitializer {
    private static final String[] NAMES = {"Off", "Normal", "High", "Extreme"};
    private static final int[] RADIUS = {0, 2, 3, 4};
    private static final int[] AHEAD = {0, 1, 2, 3};      // number of look-ahead points
    private static final int[] EVERY = {0, 20, 10, 5};    // ticks between refreshes
    private static volatile int level = 2;

    // ---- pregeneration (square spiral around the command position) ----
    private static ServerWorld pgWorld;
    private static int pgCx, pgCz, pgRadius, pgTotal, pgNext, pgDone;
    private static int sx, sz, sdx, sdy, sLeg, sLen, sTurns;
    private static final AtomicInteger inFlight = new AtomicInteger();
    private static final int MAX_FLIGHT = 12;
    private static boolean pgRunning;

    private static void pgStart(ServerCommandSource src, int radius) {
        pgWorld = src.getWorld();
        ChunkPos c = new ChunkPos(net.minecraft.util.math.BlockPos.ofFloored(src.getPosition()));
        pgCx = c.x; pgCz = c.z; pgRadius = radius;
        pgTotal = (2 * radius + 1) * (2 * radius + 1);
        pgNext = 0; pgDone = 0; sx = 0; sz = 0; sdx = 1; sdy = 0; sLeg = 0; sLen = 1; sTurns = 0;
        inFlight.set(0); pgRunning = true;
    }

    /** Next chunk offset along the spiral. */
    private static int[] spiralNext() {
        int[] r = {sx, sz};
        sx += sdx; sz += sdy; sLeg++;
        if (sLeg == sLen) { sLeg = 0; int t = sdx; sdx = -sdy; sdy = t; sTurns++; if (sTurns % 2 == 0) sLen++; }
        return r;
    }

    private static void pgTick(net.minecraft.server.MinecraftServer server) {
        if (!pgRunning || pgWorld == null) return;
        var cm = pgWorld.getChunkManager();
        while (inFlight.get() < MAX_FLIGHT && pgNext < pgTotal) {
            int[] o = spiralNext(); pgNext++;
            ChunkPos pos = new ChunkPos(pgCx + o[0], pgCz + o[1]);
            inFlight.incrementAndGet();
            try {
                cm.addChunkLoadingTicket(ChunkTicketType.PORTAL, pos, 1).whenComplete((r, t) -> { inFlight.decrementAndGet(); pgDone++; });
            } catch (Throwable t) { inFlight.decrementAndGet(); pgDone++; }
        }
        if (server.getTicks() % 40 == 0 || (pgNext >= pgTotal && inFlight.get() == 0)) {
            String msg = "Pregen " + Math.min(pgDone, pgTotal) + "/" + pgTotal + " chunks (" + (100 * Math.min(pgDone, pgTotal) / pgTotal) + "%)";
            for (ServerPlayerEntity pl : pgWorld.getPlayers()) pl.sendMessage(Text.literal(msg), true);
        }
        if (pgNext >= pgTotal && inFlight.get() == 0) pgRunning = false;
    }

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
                        .then(CommandManager.literal("pregen")
                                .then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 3000)).executes(c -> {
                                    int r = IntegerArgumentType.getInteger(c, "radius");
                                    pgStart(c.getSource(), r);
                                    c.getSource().sendFeedback(() -> Text.literal("Pregenerating radius " + r + " chunks (" + ((2 * r + 1) * (2 * r + 1)) + " chunks) around you. /aggrchunk stop to cancel. Stay in the world."), false);
                                    return 1;
                                })))
                        .then(CommandManager.literal("stop").executes(c -> {
                            pgRunning = false;
                            c.getSource().sendFeedback(() -> Text.literal("Pregen stopped."), false);
                            return 1;
                        }))
                        .then(CommandManager.argument("level", IntegerArgumentType.integer(0, 3)).executes(c -> {
                            level = IntegerArgumentType.getInteger(c, "level");
                            save();
                            c.getSource().sendFeedback(() -> Text.literal("Aggressive Chunk Gen: " + NAMES[level]), false);
                            return 1;
                        }))));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            pgTick(server);
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
