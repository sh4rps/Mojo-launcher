package com.rclient.modules;

import com.rclient.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.option.Perspective;

public final class MoveVisualMods {
    private MoveVisualMods() { }

    public static void register() {
        ModuleManager.register(new ToggleSprint());
        ModuleManager.register(new ToggleSneak());
        ModuleManager.register(new Zoom());
        ModuleManager.register(new FreeLook());
        ModuleManager.register(new SnapLook());
        ModuleManager.register(new FovChanger());
        ModuleManager.register(new TimeChanger());
        ModuleManager.register(new WeatherChanger());
        ModuleManager.register(new ParticleCustomizer());
        ModuleManager.register(new HurtCam());
        ModuleManager.register(new MenuBlur());
        ModuleManager.register(new Lighting());
    }

    // ---- state read by mixins ----
    public static boolean freelookActive, snaplookActive;
    public static float flYaw, flPitch;
    public static double zoomFactor = 1.0;

    public static final class ToggleSprint extends Mod {
        public ToggleSprint() { super("togglesprint", "Toggle Sprint", Category.MOVEMENT, "Keeps you sprinting while moving forward, no key holding needed."); }
        @Override public void onTick(MinecraftClient mc) {
            if (mc.currentScreen != null) return;
            if (mc.options.forwardKey.isPressed() && !mc.player.isSneaking() && !mc.player.isUsingItem()) mc.player.setSprinting(true);
        }
    }

    public static final class ToggleSneak extends Mod {
        final Setting keepOnRelease = add(Setting.bool("hold", "Also hold while key is down", true));
        private boolean toggled, prev;
        public ToggleSneak() { super("togglesneak", "Toggle Sneak", Category.MOVEMENT, "Press the sneak key once to sneak until you press it again."); }
        private boolean physical(MinecraftClient mc) {
            try {
                int code = net.minecraft.client.util.InputUtil.fromTranslationKey(mc.options.sneakKey.getBoundKeyTranslationKey()).getCode();
                return code > 0 && net.minecraft.client.util.InputUtil.isKeyPressed(mc.getWindow(), code);
            } catch (Throwable t) { return false; }
        }
        @Override public void onTick(MinecraftClient mc) {
            if (mc.currentScreen != null) return;
            boolean now = physical(mc);
            if (now && !prev) toggled = !toggled;
            prev = now;
            mc.options.sneakKey.setPressed(toggled || now);
        }
        @Override public void onDisable(MinecraftClient mc) { toggled = false; if (mc.options != null) mc.options.sneakKey.setPressed(false); }
    }

    public static final class Zoom extends Mod {
        final Setting hold = add(Setting.key("hold", "Zoom key (hold)", org.lwjgl.glfw.GLFW.GLFW_KEY_C));
        final Setting factor = add(Setting.num("factor", "Zoom level", 4.0, 1.5, 10, 0.5));
        final Setting scroll = add(Setting.bool("smooth", "Smooth transition", true));
        public Zoom() { super("zoom", "Zoom", Category.MOVEMENT, "Hold a key to zoom in. Needs the game-renderer mixin."); }
        @Override public void alwaysTick(MinecraftClient mc) {
            double target = isEnabled() && U.held(mc, hold) ? factor.num() : 1.0;
            zoomFactor = scroll.bool() ? zoomFactor + (target - zoomFactor) * 0.35 : target;
            if (Math.abs(zoomFactor - target) < 0.01) zoomFactor = target;
        }
        @Override public void onDisable(MinecraftClient mc) { zoomFactor = 1.0; }
    }

    public static final class FreeLook extends Mod {
        final Setting hold = add(Setting.key("hold", "Freelook key (hold)", org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT));
        final Setting third = add(Setting.bool("third", "Switch to third person while active", true));
        final Setting invertY = add(Setting.bool("invert", "Invert vertical", false));
        private Perspective saved;
        public FreeLook() { super("freelook", "Freelook", Category.MOVEMENT, "Hold a key to look around without turning your character."); }
        public boolean invert() { return invertY.bool(); }
        @Override public void alwaysTick(MinecraftClient mc) {
            boolean want = isEnabled() && mc.player != null && mc.currentScreen == null && U.held(mc, hold) && !snaplookActive;
            if (want && !freelookActive) {
                freelookActive = true; flYaw = mc.player.getYaw(); flPitch = mc.player.getPitch();
                if (third.bool()) { saved = mc.options.getPerspective(); mc.options.setPerspective(Perspective.THIRD_PERSON_BACK); }
            } else if (!want && freelookActive) {
                freelookActive = false;
                if (saved != null) { mc.options.setPerspective(saved); saved = null; }
            }
        }
        @Override public void onDisable(MinecraftClient mc) { freelookActive = false; }
    }

    public static final class SnapLook extends Mod {
        final Setting hold = add(Setting.key("hold", "Snaplook key (hold)", org.lwjgl.glfw.GLFW.GLFW_KEY_B));
        public SnapLook() { super("snaplook", "Snaplook", Category.PVP, "Hold a key to look straight behind you without turning."); }
        @Override public void alwaysTick(MinecraftClient mc) {
            snaplookActive = isEnabled() && mc.player != null && mc.currentScreen == null && U.held(mc, hold) && !freelookActive;
        }
        @Override public void onDisable(MinecraftClient mc) { snaplookActive = false; }
    }

    public static final class FovChanger extends Mod {
        final Setting fov = add(Setting.num("fov", "FOV", 90, 30, 110, 1));
        private Integer saved;
        public FovChanger() { super("fov", "FOV Changer", Category.MOVEMENT, "Sets the field of view (30-110, the engine's valid range)."); }
        @Override public void onEnable(MinecraftClient mc) { if (mc.options != null) saved = mc.options.getFov().getValue(); }
        @Override public void alwaysTick(MinecraftClient mc) {
            if (isEnabled() && mc.options != null && mc.options.getFov().getValue() != fov.integer()) mc.options.getFov().setValue(fov.integer());
        }
        @Override public void onDisable(MinecraftClient mc) { if (saved != null && mc.options != null) mc.options.getFov().setValue(saved); }
    }

    public static final class TimeChanger extends Mod {
        final Setting mode = add(Setting.choice("mode", "Time", "Custom", "Day", "Noon", "Sunset", "Night", "Midnight", "Custom"));
        final Setting custom = add(Setting.num("ticks", "Custom time (ticks)", 6000, 0, 24000, 250));
        public TimeChanger() { super("timechanger", "Time Changer", Category.VISUAL, "Client-side time of day. Does not change the server's time."); }
        @Override public void onTick(MinecraftClient mc) {
            long t = switch (mode.str()) { case "Day" -> 1000; case "Noon" -> 6000; case "Sunset" -> 12500; case "Night" -> 14000; case "Midnight" -> 18000; default -> custom.integer(); };
            mc.world.setTime(mc.world.getTime(), t, false);
        }
    }

    public static final class WeatherChanger extends Mod {
        final Setting mode = add(Setting.choice("mode", "Weather", "Clear", "Clear", "Rain", "Thunder"));
        public WeatherChanger() { super("weatherchanger", "Weather Changer", Category.VISUAL, "Client-side weather. Does not change the server's weather."); }
        @Override public void onTick(MinecraftClient mc) {
            float r = mode.str().equals("Clear") ? 0f : 1f, t = mode.str().equals("Thunder") ? 1f : 0f;
            mc.world.setRainGradient(r); mc.world.setThunderGradient(t);
        }
    }

    public static final class ParticleCustomizer extends Mod {
        final Setting level = add(Setting.choice("level", "Particles", "Decreased", "All", "Decreased", "Minimal"));
        private ParticlesMode saved;
        public ParticleCustomizer() { super("particles", "Particle Customizer", Category.VISUAL, "Overrides the particle amount."); }
        @Override public void onEnable(MinecraftClient mc) { if (mc.options != null) saved = mc.options.getParticles().getValue(); }
        @Override public void alwaysTick(MinecraftClient mc) {
            if (!isEnabled() || mc.options == null) return;
            ParticlesMode want = switch (level.str()) { case "All" -> ParticlesMode.ALL; case "Minimal" -> ParticlesMode.MINIMAL; default -> ParticlesMode.DECREASED; };
            if (mc.options.getParticles().getValue() != want) mc.options.getParticles().setValue(want);
        }
        @Override public void onDisable(MinecraftClient mc) { if (saved != null && mc.options != null) mc.options.getParticles().setValue(saved); }
    }

    public static final class HurtCam extends Mod {
        final Setting strength = add(Setting.num("strength", "Camera shake on hit", 0, 0, 1, 0.1));
        private Double saved;
        public HurtCam() { super("hurtcam", "Hurt Camera Toggle", Category.VISUAL, "Reduce or remove the camera tilt when you take damage."); }
        @Override public void onEnable(MinecraftClient mc) { if (mc.options != null) saved = mc.options.getDamageTiltStrength().getValue(); }
        @Override public void alwaysTick(MinecraftClient mc) {
            if (!isEnabled() || mc.options == null) return;
            if (Math.abs(mc.options.getDamageTiltStrength().getValue() - strength.num()) > 0.001) mc.options.getDamageTiltStrength().setValue(strength.num());
        }
        @Override public void onDisable(MinecraftClient mc) { if (saved != null && mc.options != null) mc.options.getDamageTiltStrength().setValue(saved); }
    }

    public static final class MenuBlur extends Mod {
        final Setting amount = add(Setting.num("amount", "Blur strength", 0, 0, 10, 1));
        private Integer saved;
        public MenuBlur() { super("menublur", "Menu Blur", Category.VISUAL, "Sets the menu background blur (0 disables it)."); }
        @Override public void onEnable(MinecraftClient mc) { if (mc.options != null) saved = mc.options.getMenuBackgroundBlurriness().getValue(); }
        @Override public void alwaysTick(MinecraftClient mc) {
            if (isEnabled() && mc.options != null && mc.options.getMenuBackgroundBlurriness().getValue() != amount.integer())
                mc.options.getMenuBackgroundBlurriness().setValue(amount.integer());
        }
        @Override public void onDisable(MinecraftClient mc) { if (saved != null && mc.options != null) mc.options.getMenuBackgroundBlurriness().setValue(saved); }
    }

    public static final class Lighting extends Mod {
        final Setting gamma = add(Setting.num("gamma", "Brightness", 1.0, 0, 1, 0.05));
        private Double saved;
        public Lighting() { super("lighting", "Lighting Customizer", Category.VISUAL, "Sets the brightness option (engine range 0-100%)."); }
        @Override public void onEnable(MinecraftClient mc) { if (mc.options != null) saved = mc.options.getGamma().getValue(); }
        @Override public void alwaysTick(MinecraftClient mc) {
            if (isEnabled() && mc.options != null && Math.abs(mc.options.getGamma().getValue() - gamma.num()) > 0.001) mc.options.getGamma().setValue(gamma.num());
        }
        @Override public void onDisable(MinecraftClient mc) { if (saved != null && mc.options != null) mc.options.getGamma().setValue(saved); }
    }
}
