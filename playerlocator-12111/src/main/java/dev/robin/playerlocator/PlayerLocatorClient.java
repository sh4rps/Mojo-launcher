package dev.robin.playerlocator;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.waypoint.EntityTickProgress;
import net.minecraft.world.waypoint.TrackedWaypoint;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Player locator strip above the XP bar: skin heads of every player the server shares a waypoint for. */
public class PlayerLocatorClient implements ClientModInitializer {
    static final double MAX_RANGE = 30000.0;   // blocks
    static final double FOV = 75.0;            // degrees shown to each side of the crosshair
    static final int WIDTH = 182;
    private boolean enabled = true;

    private record Entry(UUID id, double yaw, double dist) {}

    @Override public void onInitializeClient() {
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.playerlocator.toggle", GLFW.GLFW_KEY_K, KeyBinding.Category.create(Identifier.of("playerlocator", "controls"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.wasPressed()) {
                enabled = !enabled;
                if (mc.player != null) mc.player.sendMessage(Text.literal("Player locator " + (enabled ? "ON" : "OFF")), true);
            }
        });
        HudElementRegistry.addLast(Identifier.of("playerlocator", "hud"), (ctx, tick) -> render(ctx));
    }

    private void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || mc.player == null || mc.world == null || mc.getNetworkHandler() == null || mc.options.hudHidden) return;
        List<Entry> list = new ArrayList<>();
        final float pt = mc.getRenderTickCounter().getTickProgress(false);
        TrackedWaypoint.YawProvider cam = new TrackedWaypoint.YawProvider() {
            @Override public Vec3d getCameraPos() { return mc.player.getEyePos(); }
            @Override public float getCameraYaw() { return mc.player.getYaw(); }
        };
        EntityTickProgress prog = e -> pt;
        mc.player.networkHandler.getWaypointHandler().forEachWaypoint(mc.player, wp -> {
            UUID id = wp.getSource().left().orElse(null);
            if (id == null || id.equals(mc.player.getUuid())) return;
            double d = Math.sqrt(wp.squaredDistanceTo(mc.player));
            if (!Double.isNaN(d) && !Double.isInfinite(d) && d > MAX_RANGE) return;
            double yaw = wp.getRelativeYaw(mc.world, cam, prog);
            list.add(new Entry(id, yaw, (Double.isNaN(d) || Double.isInfinite(d)) ? -1 : d));
        });
        if (list.isEmpty()) return;

        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int cx = w / 2, y = h - 59;
        ctx.fill(cx - WIDTH / 2, y + 5, cx + WIDTH / 2, y + 6, 0x90FFFFFF);
        ctx.fill(cx - 1, y + 3, cx + 1, y + 8, 0xC0FFFFFF);

        list.sort(Comparator.comparingDouble((Entry e) -> e.dist() < 0 ? Double.MAX_VALUE : e.dist()).reversed());
        Entry nearestCenter = null;
        for (Entry e : list) {
            double off = MathHelper.clamp(e.yaw() / FOV, -1.0, 1.0);
            int x = cx + (int) Math.round(off * (WIDTH / 2.0 - 6));
            PlayerListEntry ple = mc.getNetworkHandler().getPlayerListEntry(e.id());
            ctx.fill(x - 5, y, x + 5, y + 10, 0xFF000000);
            if (ple != null) PlayerSkinDrawer.draw(ctx, ple.getSkinTextures(), x - 4, y + 1, 8);
            else ctx.fill(x - 4, y + 1, x + 4, y + 9, 0xFF888888);
            if (Math.abs(e.yaw()) < 12 && (nearestCenter == null || Math.abs(e.yaw()) < Math.abs(nearestCenter.yaw()))) nearestCenter = e;
        }
        if (nearestCenter != null) {
            PlayerListEntry ple = mc.getNetworkHandler().getPlayerListEntry(nearestCenter.id());
            String name = ple != null ? ple.getProfile().name() : "?";
            String dist = nearestCenter.dist() < 0 ? "far away" : nearestCenter.dist() >= 1000 ? String.format("%.1fk blocks", nearestCenter.dist() / 1000.0) : (int) nearestCenter.dist() + " blocks";
            String s = name + " - " + dist;
            ctx.drawTextWithShadow(mc.textRenderer, s, cx - mc.textRenderer.getWidth(s) / 2, y - 11, 0xFFFFFFFF);
        }
    }
}
