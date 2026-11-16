package com.rclient.modules;

import com.google.gson.*;
import com.rclient.RClient;
import com.rclient.core.*;
import com.rclient.ui.Ui;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class WorldUtilMods {
    private WorldUtilMods() { }

    public static void register() {
        ModuleManager.register(new Waypoints());
        ModuleManager.register(new Minimap());
        ModuleManager.register(new AutoText());
        ModuleManager.register(new Quickplay());
        ModuleManager.register(new Performance());
    }

    // ------------------------------------------------------------ Waypoints
    public record Wp(String name, int x, int y, int z, int color, String dim) { }

    public static final class Waypoints extends TextHud {
        private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("rclient").resolve("waypoints.json");
        private final Map<String, List<Wp>> byWorld = new HashMap<>();
        final Setting addKey = add(Setting.key("add", "Add waypoint here (key)", GLFW.GLFW_KEY_KP_ADD));
        final Setting delKey = add(Setting.key("del", "Delete nearest within 8 blocks (key)", -1));
        final Setting name = add(Setting.string("name", "Name for next waypoint", "Waypoint"));
        final Setting list = add(Setting.num("list", "Entries shown in HUD", 3, 0, 8, 1));
        private boolean loaded;

        public Waypoints() { super("waypoints", "Waypoints", Category.WORLD, "Save positions per world/server; shown on the minimap and in a distance list.", 0.88, 0.7); }

        public static String worldKey(MinecraftClient mc) {
            var e = mc.getCurrentServerEntry();
            return e != null ? "srv:" + e.address : "local";
        }
        private String dim(MinecraftClient mc) { return mc.world.getRegistryKey().getValue().toString(); }

        public List<Wp> here(MinecraftClient mc) {
            load();
            if (mc.world == null) return List.of();
            List<Wp> all = byWorld.getOrDefault(worldKey(mc), List.of()), out = new ArrayList<>();
            String d = dim(mc);
            for (Wp w : all) if (w.dim.equals(d)) out.add(w);
            return out;
        }

        private void load() {
            if (loaded) return; loaded = true;
            try {
                if (!Files.exists(FILE)) return;
                JsonObject o = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
                for (var en : o.entrySet()) {
                    List<Wp> l = new ArrayList<>();
                    for (JsonElement e : en.getValue().getAsJsonArray()) {
                        JsonObject w = e.getAsJsonObject();
                        l.add(new Wp(w.get("n").getAsString(), w.get("x").getAsInt(), w.get("y").getAsInt(), w.get("z").getAsInt(), w.get("c").getAsInt(), w.get("d").getAsString()));
                    }
                    byWorld.put(en.getKey(), l);
                }
            } catch (Exception e) { RClient.LOG.warn("waypoints unreadable", e); }
        }

        private void save() {
            JsonObject o = new JsonObject();
            for (var en : byWorld.entrySet()) {
                JsonArray a = new JsonArray();
                for (Wp w : en.getValue()) {
                    JsonObject j = new JsonObject();
                    j.addProperty("n", w.name); j.addProperty("x", w.x); j.addProperty("y", w.y); j.addProperty("z", w.z); j.addProperty("c", w.color); j.addProperty("d", w.dim);
                    a.add(j);
                }
                o.add(en.getKey(), a);
            }
            try { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(o)); }
            catch (Exception e) { RClient.LOG.error("waypoints save", e); }
        }

        @Override public void alwaysTick(MinecraftClient mc) {
            if (!isEnabled() || mc.player == null || mc.world == null) return;
            load();
            if (U.edge(mc, addKey)) {
                List<Wp> l = byWorld.computeIfAbsent(worldKey(mc), k -> new ArrayList<>());
                int c = Ui.SWATCHES[l.size() % Ui.SWATCHES.length];
                l.add(new Wp(name.str() + " " + (l.size() + 1), (int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ()), c, dim(mc)));
                save();
                mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("[RClient] Waypoint added"));
            }
            if (U.edge(mc, delKey)) {
                List<Wp> l = byWorld.get(worldKey(mc));
                if (l != null) {
                    Wp best = null; double bd = 64;
                    for (Wp w : here(mc)) { double d = mc.player.squaredDistanceTo(w.x + .5, w.y, w.z + .5); if (d < bd) { bd = d; best = w; } }
                    if (best != null) { l.remove(best); save(); mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("[RClient] Waypoint removed")); }
                }
            }
        }

        @Override protected String text(MinecraftClient mc) {
            if (mc.player == null || mc.world == null || list.integer() == 0) return null;
            List<Wp> l = new ArrayList<>(here(mc));
            if (l.isEmpty()) return null;
            l.sort(Comparator.comparingDouble(w -> mc.player.squaredDistanceTo(w.x + .5, w.y, w.z + .5)));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(l.size(), list.integer()); i++) {
                Wp w = l.get(i);
                if (i > 0) sb.append('\n');
                sb.append(w.name).append(' ').append((int) Math.sqrt(mc.player.squaredDistanceTo(w.x + .5, w.y, w.z + .5))).append('m');
            }
            return sb.toString();
        }
        @Override protected String sample() { return "Base 120m\nMine 340m"; }
    }

    // -------------------------------------------------------------- Minimap
    public static final class Minimap extends HudModule {
        final Setting shape = add(Setting.choice("shape", "Shape", "Circle", "Circle", "Square"));
        final Setting mode = add(Setting.choice("mode", "Orientation", "Rotating", "North up", "Rotating"));
        final Setting size = add(Setting.num("size", "Size (px)", 96, 60, 180, 6));
        final Setting range = add(Setting.num("range", "Range (blocks)", 48, 16, 160, 8));
        final Setting opacity = add(Setting.num("opacity", "Opacity", 0.9, 0.2, 1.0, 0.05));
        final Setting arrow = add(Setting.bool("arrow", "Player arrow", true));
        final Setting markers = add(Setting.bool("markers", "Waypoint markers", true));
        final Setting north = add(Setting.bool("northletter", "Show N marker", true));
        final Setting zoomIn = add(Setting.key("zin", "Zoom in key", GLFW.GLFW_KEY_EQUAL));
        final Setting zoomOut = add(Setting.key("zout", "Zoom out key", GLFW.GLFW_KEY_MINUS));

        private static final int CELL = 3;
        private int[] colors = new int[0];
        private int n, cx, cz, cy;
        private long lastSample;
        private double bpc = 1;
        private int sampledN = -1;

        public Minimap() { super("minimap", "Minimap", Category.WORLD, "Top-down map of loaded terrain with waypoint markers.", 0.86, 0.02); background.value = false; }

        @Override public void alwaysTick(MinecraftClient mc) {
            if (!isEnabled()) return;
            if (U.edge(mc, zoomIn)) range.setNum(range.num() - 8);
            if (U.edge(mc, zoomOut)) range.setNum(range.num() + 8);
        }

        private void sample(MinecraftClient mc) {
            var w = mc.world; var p = mc.player;
            n = Math.max(8, size.integer() / CELL);
            if (colors.length != n * n) colors = new int[n * n];
            bpc = range.num() * 2 / n;
            cx = (int) Math.floor(p.getX()); cz = (int) Math.floor(p.getZ()); cy = (int) Math.floor(p.getY());
            boolean ceiling = w.getDimension().hasCeiling();
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
                int wx = cx + (int) Math.floor((i - n / 2.0 + 0.5) * bpc), wz = cz + (int) Math.floor((j - n / 2.0 + 0.5) * bpc);
                int col = 0;
                Chunk ch = w.getChunk(wx >> 4, wz >> 4, ChunkStatus.FULL, false);
                if (ch != null) {
                    int y;
                    if (ceiling) {
                        y = cy + 3;
                        while (y > w.getBottomY() && w.getBlockState(pos.set(wx, y, wz)).isAir()) y--;
                    } else y = w.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz) - 1;
                    var st = w.getBlockState(pos.set(wx, y, wz));
                    int rgb = st.getMapColor(w, pos).color;
                    if (rgb != 0) {
                        float f = Math.max(0.6f, Math.min(1.15f, 0.85f + (y - cy) * 0.006f));
                        int r = Math.min(255, (int) ((rgb >> 16 & 255) * f)), g = Math.min(255, (int) ((rgb >> 8 & 255) * f)), b = Math.min(255, (int) ((rgb & 255) * f));
                        col = 0xFF000000 | r << 16 | g << 8 | b;
                    }
                }
                colors[j * n + i] = col;
            }
            sampledN = n;
        }

        @Override protected void draw(DrawContext g, MinecraftClient mc, boolean editing) {
            int s = size.integer();
            w = s; h = s;
            boolean circle = shape.str().equals("Circle");
            if (mc.world == null || mc.player == null) {
                Ui.round(g, 0, 0, s, s, 0x88202030);
                g.drawText(mc.textRenderer, "Minimap", 6, s / 2 - 4, col(), true);
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastSample > 250 || sampledN != Math.max(8, s / CELL)) { sample(mc); lastSample = now; }

            float yaw = mc.player.getYaw() * (float) Math.PI / 180f;
            boolean rot = mode.str().equals("Rotating");
            float alpha = opacity.flt();
            float half = s / 2f;
            // frame behind map
            Ui.round(g, -1, -1, s + 2, s + 2, Ui.alpha(0xFF000000, alpha * 0.7f));

            g.enableScissor(px(mc), py(mc), px(mc) + Math.round(s * scale.flt()), py(mc) + Math.round(s * scale.flt()));
            var m = g.getMatrices();
            m.pushMatrix();
            m.translate(half, half);
            if (rot) m.rotate((float) Math.PI - yaw);
            double offX = (mc.player.getX() - (cx + 0.5)) / bpc, offZ = (mc.player.getZ() - (cz + 0.5)) / bpc;
            float cell = s / (float) sampledN;
            for (int j = 0; j < sampledN; j++) for (int i = 0; i < sampledN; i++) {
                int col = colors[j * sampledN + i];
                if (col == 0) continue;
                float rx = (i - sampledN / 2f - (float) offX) * cell, rz = (j - sampledN / 2f - (float) offZ) * cell;
                if (circle) {
                    float ccx = rx + cell / 2, ccz = rz + cell / 2;
                    if (ccx * ccx + ccz * ccz > half * half) continue;
                }
                int x0 = Math.round(rx), y0 = Math.round(rz);
                g.fill(x0, y0, x0 + Math.max(1, Math.round(cell)), y0 + Math.max(1, Math.round(cell)), (col & 0xFFFFFF) | ((int) (255 * alpha) << 24));
            }
            m.popMatrix();
            g.disableScissor();

            // markers (mapped manually so they stay upright)
            if (markers.bool()) {
                Waypoints wps = ModuleManager.of(Waypoints.class);
                if (wps != null && wps.isEnabled()) for (Wp wp : wps.here(mc)) {
                    double dx = wp.x + 0.5 - mc.player.getX(), dz = wp.z + 0.5 - mc.player.getZ();
                    double[] p = map(dx, dz, yaw, rot);
                    double px = p[0] / bpc * cell, py = p[1] / bpc * cell;
                    double lim = half - 3, len = circle ? Math.hypot(px, py) : Math.max(Math.abs(px), Math.abs(py));
                    if (len > lim) { px = px / len * lim; py = py / len * lim; }
                    int x = (int) (half + px), y = (int) (half + py);
                    g.fill(x - 2, y - 2, x + 3, y + 3, 0xFF000000);
                    g.fill(x - 1, y - 1, x + 2, y + 2, wp.color);
                }
            }
            // N marker
            if (north.bool()) {
                double[] p = map(0, -1, yaw, rot);
                double ang = Math.atan2(p[1], p[0]);
                int nx = (int) (half + Math.cos(ang) * (half - 6)), ny = (int) (half + Math.sin(ang) * (half - 6));
                g.drawText(mc.textRenderer, "N", nx - 2, ny - 4, 0xFFF87171, true);
            }
            // player arrow
            if (arrow.bool()) {
                m.pushMatrix();
                m.translate(half, half);
                if (!rot) m.rotate(yaw + (float) Math.PI);
                for (int i = 0; i < 5; i++) g.fill(-i - 1, -5 + i - 1, i + 2, -4 + i, 0xFF000000);
                for (int i = 0; i < 5; i++) g.fill(-i, -5 + i, i + 1, -4 + i, 0xFFFFFFFF);
                m.popMatrix();
            }
            // border
            if (!circle) Ui.outline(g, -1, -1, s + 2, s + 2, Ui.ACCENT);
        }

        /** World offset -> screen offset (y down) for current orientation. */
        private static double[] map(double dx, double dz, float yaw, boolean rot) {
            if (!rot) return new double[]{dx, dz};
            double c = Math.cos(yaw), sn = Math.sin(yaw);
            return new double[]{-dx * c - dz * sn, dx * sn - dz * c};
        }
    }

    // ------------------------------------------------------------- AutoText
    public static final class AutoText extends Mod {
        final Setting k1 = add(Setting.key("k1", "Slot 1 key", -1)), t1 = add(Setting.string("t1", "Slot 1 text (/ = command)", "gg"));
        final Setting k2 = add(Setting.key("k2", "Slot 2 key", -1)), t2 = add(Setting.string("t2", "Slot 2 text", "/lobby"));
        final Setting k3 = add(Setting.key("k3", "Slot 3 key", -1)), t3 = add(Setting.string("t3", "Slot 3 text", "Hello!"));
        private long last;
        public AutoText() { super("autotext", "Auto Text Hotkey", Category.UTILITY, "Send a chat message or command with one key press."); t1.maxLen = t2.maxLen = t3.maxLen = 100; }
        private void send(MinecraftClient mc, Setting t) {
            String s = t.str().trim();
            if (s.isEmpty() || mc.getNetworkHandler() == null || System.currentTimeMillis() - last < 400) return;
            last = System.currentTimeMillis();
            if (s.startsWith("/")) mc.getNetworkHandler().sendChatCommand(s.substring(1)); else mc.getNetworkHandler().sendChatMessage(s);
        }
        @Override public void onTick(MinecraftClient mc) {
            if (U.edge(mc, k1)) send(mc, t1);
            if (U.edge(mc, k2)) send(mc, t2);
            if (U.edge(mc, k3)) send(mc, t3);
        }
    }

    // ------------------------------------------------------------ Quickplay
    public static final class Quickplay extends Mod {
        final Setting kBw = add(Setting.key("bw", "BedWars solo key", -1));
        final Setting kSw = add(Setting.key("sw", "SkyWars solo key", -1));
        final Setting kDu = add(Setting.key("du", "Duels (classic) key", -1));
        final Setting kLobby = add(Setting.key("lobby", "Return to lobby key", -1));
        public Quickplay() { super("quickplay", "Hypixel Quickplay", Category.UTILITY, "Optional: queue Hypixel games with a key. Only acts while connected to hypixel.net."); }
        private void run(MinecraftClient mc, String cmd) {
            var e = mc.getCurrentServerEntry();
            if (e == null || !e.address.toLowerCase().contains("hypixel.net") || mc.getNetworkHandler() == null) return;
            mc.getNetworkHandler().sendChatCommand(cmd);
        }
        @Override public void onTick(MinecraftClient mc) {
            if (U.edge(mc, kBw)) run(mc, "play bedwars_eight_one");
            if (U.edge(mc, kSw)) run(mc, "play solo_normal");
            if (U.edge(mc, kDu)) run(mc, "play duels_classic_duel");
            if (U.edge(mc, kLobby)) run(mc, "lobby");
        }
    }

    // ---------------------------------------------------------- Performance
    public static final class Performance extends Mod {
        final Setting profile = add(Setting.choice("profile", "Profile", "Balanced", "Low", "Balanced", "High"));
        final Setting dynFps = add(Setting.bool("dynfps", "Dynamic FPS (limit when unfocused)", true));
        final Setting bgFps = add(Setting.num("bgfps", "Unfocused FPS", 15, 5, 60, 5));
        final Setting gc = add(Setting.bool("gc", "Free memory when leaving a world", true));
        private String applied = "";
        private Integer savedFps;
        private boolean wasIn;

        public Performance() { super("performance", "Performance Engine", Category.PERFORMANCE, "Low/Balanced/High presets for vanilla video options, dynamic FPS and memory cleanup."); }

        private void apply(MinecraftClient mc) {
            var o = mc.options;
            int view, sim; double ent; ParticlesMode pm; int blend; boolean shadows;
            switch (profile.str()) {
                case "Low" -> { view = 6; sim = 5; ent = 0.5; pm = ParticlesMode.MINIMAL; blend = 0; shadows = false; }
                case "High" -> { view = 16; sim = 12; ent = 1.0; pm = ParticlesMode.ALL; blend = 5; shadows = true; }
                default -> { view = 10; sim = 8; ent = 0.75; pm = ParticlesMode.DECREASED; blend = 2; shadows = true; }
            }
            o.getViewDistance().setValue(view);
            o.getSimulationDistance().setValue(sim);
            o.getEntityDistanceScaling().setValue(ent);
            o.getParticles().setValue(pm);
            o.getBiomeBlendRadius().setValue(blend);
            o.getEntityShadows().setValue(shadows);
            applied = profile.str();
        }

        @Override public void onEnable(MinecraftClient mc) { if (mc.options != null) apply(mc); }
        @Override public void onDisable(MinecraftClient mc) { applied = ""; restoreFps(mc); }

        private void restoreFps(MinecraftClient mc) {
            if (savedFps != null && mc.options != null) { mc.options.getMaxFps().setValue(savedFps); savedFps = null; }
        }

        @Override public void alwaysTick(MinecraftClient mc) {
            if (!isEnabled() || mc.options == null) return;
            if (!applied.equals(profile.str())) apply(mc);
            boolean in = mc.world != null;
            if (wasIn && !in && gc.bool()) System.gc();
            wasIn = in;
            if (dynFps.bool() && mc.getWindow() != null) {
                boolean focused = GLFW.glfwGetWindowAttrib(mc.getWindow().getHandle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
                if (!focused) {
                    if (savedFps == null) savedFps = mc.options.getMaxFps().getValue();
                    int want = bgFps.integer();
                    if (mc.options.getMaxFps().getValue() != want) mc.options.getMaxFps().setValue(want);
                } else restoreFps(mc);
            } else restoreFps(mc);
        }
    }
}
