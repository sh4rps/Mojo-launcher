package com.rclient.modules;

import com.rclient.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.LivingEntity;

public final class HudMods {
    private HudMods() { }
    static final long START = System.currentTimeMillis();

    public static void register() {
        ModuleManager.register(new Fps());
        ModuleManager.register(new Coords());
        ModuleManager.register(new Direction());
        ModuleManager.register(new Ping());
        ModuleManager.register(new Memory());
        ModuleManager.register(new Playtime());
        ModuleManager.register(new Stopwatch());
        ModuleManager.register(new DayCounter());
        ModuleManager.register(new ItemCounter());
        ModuleManager.register(new ItemTracker());
        ModuleManager.register(new ServerAddress());
        ModuleManager.register(new PackDisplay());
        ModuleManager.register(new InventoryHud());
        ModuleManager.register(new PotionCounter());
        ModuleManager.register(new Momentum());
        ModuleManager.register(new WeatherDisplay());
        ModuleManager.register(new HeightLimit());
        ModuleManager.register(new Waila());
        ModuleManager.register(new VanillaHud());
    }

    public static final class Fps extends TextHud {
        final Setting prefix = add(Setting.string("prefix", "Label", "FPS"));
        public Fps() { super("fps", "FPS Counter", Category.HUD, "Current frames per second.", 0.01, 0.01); }
        @Override protected String text(MinecraftClient mc) { return prefix.str() + ": " + mc.getCurrentFps(); }
        @Override protected String sample() { return prefix.str() + ": 144"; }
    }

    public static final class Coords extends TextHud {
        final Setting dec = add(Setting.num("decimals", "Decimals", 0, 0, 2, 1));
        final Setting nether = add(Setting.bool("nether", "Show Nether/Overworld equivalent", false));
        public Coords() { super("coords", "Coordinates HUD", Category.HUD, "Your XYZ position.", 0.01, 0.06); }
        @Override protected String text(MinecraftClient mc) {
            if (mc.player == null) return null;
            String f = "%." + dec.integer() + "f";
            String s = String.format("XYZ: " + f + " / " + f + " / " + f, mc.player.getX(), mc.player.getY(), mc.player.getZ());
            if (nether.bool()) {
                boolean inNether = mc.world.getRegistryKey().getValue().getPath().equals("the_nether");
                double k = inNether ? 8 : 0.125;
                s += String.format("\n%s: " + f + " / " + f, inNether ? "Overworld" : "Nether", mc.player.getX() * k, mc.player.getZ() * k);
            }
            return s;
        }
        @Override protected String sample() { return "XYZ: 128 / 64 / -302"; }
    }

    public static final class Direction extends TextHud {
        public Direction() { super("direction", "Direction HUD", Category.HUD, "Facing direction and yaw.", 0.01, 0.11); }
        @Override protected String text(MinecraftClient mc) {
            if (mc.player == null) return null;
            float yaw = ((mc.player.getYaw() % 360) + 360) % 360;
            String d = mc.player.getHorizontalFacing().name();
            return d.charAt(0) + d.substring(1).toLowerCase() + String.format(" (%.0f°)", yaw > 180 ? yaw - 360 : yaw);
        }
        @Override protected String sample() { return "South (0°)"; }
    }

    public static final class Ping extends TextHud {
        public Ping() { super("ping", "Ping Display", Category.HUD, "Latency to the server (multiplayer only).", 0.01, 0.16); }
        @Override protected String text(MinecraftClient mc) {
            if (mc.player == null || mc.getNetworkHandler() == null || mc.isInSingleplayer()) return null;
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            return e == null ? null : "Ping: " + e.getLatency() + " ms";
        }
        @Override protected String sample() { return "Ping: 42 ms"; }
    }

    public static final class Memory extends TextHud {
        final Setting percent = add(Setting.bool("percent", "Show percent", true));
        public Memory() { super("memory", "Memory Usage", Category.HUD, "Java heap used and maximum.", 0.01, 0.21); }
        @Override protected String text(MinecraftClient mc) {
            Runtime r = Runtime.getRuntime();
            long used = (r.totalMemory() - r.freeMemory()) >> 20, max = r.maxMemory() >> 20;
            return "Mem: " + used + "/" + max + " MB" + (percent.bool() ? " (" + (used * 100 / Math.max(1, max)) + "%)" : "");
        }
        @Override protected String sample() { return "Mem: 812/2048 MB (39%)"; }
    }

    public static final class Playtime extends TextHud {
        final Setting scope = add(Setting.choice("scope", "Counts", "Game session", "Game session", "Current world"));
        private long joined = System.currentTimeMillis();
        public Playtime() { super("playtime", "Playtime Counter", Category.HUD, "How long you have been playing.", 0.01, 0.26); }
        @Override public void onJoin(MinecraftClient mc) { joined = System.currentTimeMillis(); }
        @Override protected String text(MinecraftClient mc) {
            long from = scope.str().equals("Current world") ? joined : START;
            return "Time: " + U.mmss((System.currentTimeMillis() - from) / 1000);
        }
        @Override protected String sample() { return "Time: 12:34"; }
    }

    public static final class Stopwatch extends TextHud {
        final Setting start = add(Setting.key("start", "Start / pause key", -1));
        final Setting reset = add(Setting.key("reset", "Reset key", -1));
        private long acc, since;
        private boolean running;
        public Stopwatch() { super("stopwatch", "Stopwatch", Category.HUD, "Keybind-controlled stopwatch.", 0.01, 0.31); }
        @Override public void alwaysTick(MinecraftClient mc) {
            if (!isEnabled()) return;
            if (U.edge(mc, start)) { if (running) acc += System.currentTimeMillis() - since; else since = System.currentTimeMillis(); running = !running; }
            if (U.edge(mc, reset)) { acc = 0; since = System.currentTimeMillis(); }
        }
        @Override protected String text(MinecraftClient mc) {
            long t = acc + (running ? System.currentTimeMillis() - since : 0);
            return String.format("%s.%d", U.mmss(t / 1000), t / 100 % 10) + (running ? "" : " (paused)");
        }
        @Override protected String sample() { return "0:00.0"; }
    }

    public static final class DayCounter extends TextHud {
        public DayCounter() { super("daycounter", "Day Counter", Category.HUD, "World day number and time of day.", 0.01, 0.36); }
        @Override protected String text(MinecraftClient mc) {
            if (mc.world == null) return null;
            long t = mc.world.getTimeOfDay();
            long tod = t % 24000;
            int hh = (int) ((tod / 1000 + 6) % 24), mm = (int) (tod % 1000 * 60 / 1000);
            return String.format("Day %d  %02d:%02d", t / 24000 + 1, hh, mm);
        }
        @Override protected String sample() { return "Day 12  14:30"; }
    }

    public static final class ItemCounter extends TextHud {
        public ItemCounter() { super("itemcounter", "Item Counter", Category.HUD, "Total of the item you are holding across your inventory.", 0.45, 0.8); }
        @Override protected String text(MinecraftClient mc) {
            if (mc.player == null) return null;
            ItemStack held = mc.player.getMainHandStack();
            if (held.isEmpty()) return null;
            String id = U.id(held);
            return held.getName().getString() + ": " + U.countInv(mc, s -> U.id(s).equals(id));
        }
        @Override protected String sample() { return "Golden Apple: 12"; }
    }

    public static final class ItemTracker extends TextHud {
        final Setting item = add(Setting.string("item", "Item id", "minecraft:golden_apple"));
        public ItemTracker() { super("itemtracker", "Item Tracker", Category.HUD, "Counts a chosen item (by id) in your inventory.", 0.45, 0.75); }
        @Override protected String text(MinecraftClient mc) {
            if (mc.player == null) return null;
            String id = item.str().contains(":") ? item.str() : "minecraft:" + item.str();
            return item.str() + ": " + U.countInv(mc, s -> U.id(s).equals(id));
        }
        @Override protected String sample() { return "golden_apple: 6"; }
    }

    public static final class ServerAddress extends TextHud {
        public ServerAddress() { super("serveraddress", "Server Address", Category.HUD, "Address of the server you are connected to.", 0.01, 0.41); }
        @Override protected String text(MinecraftClient mc) {
            if (mc.player == null) return null;
            var e = mc.getCurrentServerEntry();
            return e != null ? e.address : (mc.isInSingleplayer() ? "Singleplayer" : null);
        }
        @Override protected String sample() { return "play.example.net"; }
    }

    public static final class PackDisplay extends TextHud {
        public PackDisplay() { super("packdisplay", "Pack Display", Category.HUD, "Currently enabled resource packs.", 0.88, 0.01); }
        @Override protected String text(MinecraftClient mc) {
            StringBuilder sb = new StringBuilder();
            for (ResourcePackProfile p : mc.getResourcePackManager().getEnabledProfiles()) {
                String id = p.getId();
                if (id.equals("vanilla") || id.equals("fabric") || id.startsWith("mod_") || id.equals("programmer_art")) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(p.getDisplayName().getString());
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        @Override protected String sample() { return "Faithful 32x"; }
    }

    public static final class InventoryHud extends HudModule {
        public InventoryHud() { super("inventoryhud", "Inventory HUD", Category.HUD, "Shows your main inventory (27 slots) on screen.", 0.62, 0.7); }
        @Override protected void draw(DrawContext g, MinecraftClient mc, boolean editing) {
            w = 9 * 18; h = 3 * 18;
            if (mc.player == null) return;
            PlayerInventory inv = mc.player.getInventory();
            for (int i = 0; i < 27; i++) {
                ItemStack s = inv.getStack(9 + i);
                int x = (i % 9) * 18 + 1, y = (i / 9) * 18 + 1;
                if (!s.isEmpty()) { g.drawItem(s, x, y); g.drawStackOverlay(mc.textRenderer, s, x, y); }
            }
        }
    }

    public static final class PotionCounter extends TextHud {
        public PotionCounter() { super("potioncounter", "Potion Counter", Category.HUD, "Number of potions (drinkable, splash, lingering) in your inventory.", 0.88, 0.2); }
        @Override protected String text(MinecraftClient mc) {
            if (mc.player == null) return null;
            int n = U.countInv(mc, s -> U.id(s).endsWith("potion"));
            return "Potions: " + n;
        }
        @Override protected String sample() { return "Potions: 24"; }
    }

    public static final class Momentum extends TextHud {
        final Setting unit = add(Setting.choice("unit", "Unit", "Blocks/s", "Blocks/s", "km/h"));
        private double px, pz; private double speed;
        public Momentum() { super("momentum", "Momentum Display", Category.MOVEMENT, "Your horizontal speed.", 0.01, 0.46); }
        @Override public void onTick(MinecraftClient mc) {
            double dx = mc.player.getX() - px, dz = mc.player.getZ() - pz;
            px = mc.player.getX(); pz = mc.player.getZ();
            double v = Math.sqrt(dx * dx + dz * dz) * 20;
            speed = speed * 0.5 + v * 0.5;
        }
        @Override protected String text(MinecraftClient mc) {
            if (mc.player == null) return null;
            return unit.str().equals("km/h") ? String.format("Speed: %.1f km/h", speed * 3.6) : String.format("Speed: %.2f b/s", speed);
        }
        @Override protected String sample() { return "Speed: 5.61 b/s"; }
    }

    public static final class WeatherDisplay extends TextHud {
        public WeatherDisplay() { super("weatherdisplay", "Weather Display", Category.WORLD, "Current weather in your world.", 0.01, 0.51); }
        @Override protected String text(MinecraftClient mc) {
            if (mc.world == null) return null;
            return "Weather: " + (mc.world.isThundering() ? "Thunder" : mc.world.isRaining() ? "Rain" : "Clear");
        }
        @Override protected String sample() { return "Weather: Clear"; }
    }

    public static final class HeightLimit extends TextHud {
        public HeightLimit() { super("heightlimit", "Height Limit Display", Category.WORLD, "Blocks left until the build limit and the world bottom.", 0.01, 0.56); }
        @Override protected String text(MinecraftClient mc) {
            if (mc.player == null || mc.world == null) return null;
            int top = mc.world.getBottomY() + mc.world.getHeight();
            int y = (int) Math.floor(mc.player.getY());
            return "Y " + y + "  ↑" + (top - 1 - y) + "  ↓" + (y - mc.world.getBottomY());
        }
        @Override protected String sample() { return "Y 64  ↑255  ↓128"; }
    }

    public static final class Waila extends TextHud {
        final Setting coords = add(Setting.bool("coords", "Show block position", false));
        public Waila() { super("waila", "WAILA", Category.WORLD, "Name of the block or entity you are looking at.", 0.4, 0.05); }
        @Override protected String text(MinecraftClient mc) {
            if (mc.world == null) return null;
            HitResult h = mc.crosshairTarget;
            if (h instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
                var st = mc.world.getBlockState(b.getBlockPos());
                String s = st.getBlock().getName().getString();
                if (coords.bool()) s += "\n" + b.getBlockPos().getX() + " " + b.getBlockPos().getY() + " " + b.getBlockPos().getZ();
                return s;
            }
            if (h instanceof EntityHitResult e) {
                String s = e.getEntity().getName().getString();
                if (e.getEntity() instanceof LivingEntity le) s += String.format("  %.1f/%.0f HP", le.getHealth(), le.getMaxHealth());
                return s;
            }
            return null;
        }
        @Override protected String sample() { return "Oak Log"; }
    }

    /** Controls which vanilla HUD pieces are shown (applied by replaceElement in RClient). */
    public static final class VanillaHud extends Module {
        public final Setting hideScoreboard = add(Setting.bool("scoreboard", "Hide scoreboard", false));
        public final Setting hideBossBar = add(Setting.bool("bossbar", "Hide boss bars", false));
        public final Setting hideActionBar = add(Setting.bool("actionbar", "Hide action bar text", false));
        public final Setting hideEffects = add(Setting.bool("effects", "Hide vanilla effect icons", false));
        public VanillaHud() { super("vanillahud", "Vanilla HUD Control", Category.HUD, "Hide the vanilla scoreboard, boss bars, action bar or effect icons."); }
    }
}
