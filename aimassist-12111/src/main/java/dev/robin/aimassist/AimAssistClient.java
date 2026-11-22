package dev.robin.aimassist;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/** Hit a player -> the camera softly follows them. A strong mouse movement (or no hit for a while) releases the lock. */
public class AimAssistClient implements ClientModInitializer {
    static final double BREAK_DEGREES = 5.0;     // mouse movement per frame that counts as "strong"
    static final double MAX_SPEED = 140.0;       // degrees / second the assist may turn at most
    static final double SMOOTH = 7.0;            // higher = snappier follow
    static final double MAX_ERROR = 40.0;        // never pull from further off than this (degrees)
    static final double MAX_DIST = 6.0;          // blocks
    static final long HOLD_MS = 2500;            // lock expires this long after your last hit

    public static volatile boolean internal = false;
    private static PlayerEntity target;
    private static long lastHit;
    private static long lastFrame;
    private boolean enabled = true;

    public static void onUserLook(double dYaw, double dPitch) {
        if (target != null && Math.hypot(dYaw, dPitch) > BREAK_DEGREES) target = null;
    }

    @Override public void onInitializeClient() {
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimassist.toggle", GLFW.GLFW_KEY_Y, KeyBinding.Category.create(Identifier.of("aimassist", "controls"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.wasPressed()) {
                enabled = !enabled;
                if (!enabled) target = null;
                if (mc.player != null) mc.player.sendMessage(Text.literal("Soft aim assist " + (enabled ? "ON" : "OFF")), true);
            }
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (enabled && world.isClient() && entity instanceof PlayerEntity p && p != player && !p.isSpectator()) {
                target = p;
                lastHit = System.currentTimeMillis();
            }
            return ActionResult.PASS;
        });
        // runs every rendered frame, so the follow is smooth at any fps
        HudElementRegistry.addLast(Identifier.of("aimassist", "frame"), (ctx, tick) -> frame());
    }

    private static void frame() {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0.016 : Math.min(0.05, (now - lastFrame) / 1e9);
        lastFrame = now;
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity me = mc.player;
        PlayerEntity t = target;
        if (t == null || me == null || mc.world == null) return;
        if (mc.currentScreen != null || mc.isPaused()
                || !t.isAlive() || t.isRemoved() || System.currentTimeMillis() - lastHit > HOLD_MS) { target = null; return; }
        Vec3d eye = me.getEyePos();
        Vec3d aim = t.getEntityPos().add(0, t.getHeight() * 0.65, 0);
        Vec3d d = aim.subtract(eye);
        double hd = Math.sqrt(d.x * d.x + d.z * d.z);
        if (d.length() > MAX_DIST) { target = null; return; }
        double wantYaw = Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0;
        double wantPitch = -Math.toDegrees(Math.atan2(d.y, hd));
        double eYaw = MathHelper.wrapDegrees(wantYaw - me.getYaw());
        double ePitch = wantPitch - me.getPitch();
        if (Math.hypot(eYaw, ePitch) > MAX_ERROR) { target = null; return; }
        double k = 1.0 - Math.exp(-SMOOTH * dt);
        double cap = MAX_SPEED * dt;
        double sYaw = MathHelper.clamp(eYaw * k, -cap, cap);
        double sPitch = MathHelper.clamp(ePitch * k, -cap, cap);
        internal = true;
        try { me.changeLookDirection(sYaw / 0.15, sPitch / 0.15); } finally { internal = false; }
    }
}
