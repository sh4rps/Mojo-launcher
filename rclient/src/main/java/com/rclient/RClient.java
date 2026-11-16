package com.rclient;

import com.rclient.core.Config;
import com.rclient.core.ModuleManager;
import com.rclient.modules.HudMods;
import com.rclient.modules.Modules;
import com.rclient.modules.PvpMods;
import com.rclient.ui.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BooleanSupplier;

public class RClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("RClient");
    public static final String VERSION = FabricLoader.getInstance().getModContainer("rclient").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("1.0.0");
    public static final String[] STEPS = {"Registering modules", "Loading configuration", "Applying profile", "Checking compatibility"};
    public static volatile boolean ready;
    private static boolean stepsDone[] = new boolean[STEPS.length];
    private static KeyBinding menuKey;
    private static int tickCount;

    @Override public void onInitializeClient() {
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.rclient.menu", GLFW.GLFW_KEY_RIGHT_SHIFT, KeyBinding.Category.create(Identifier.of("rclient", "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(RClient::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            if (!ready) return;
            var e = mc.getCurrentServerEntry();
            if (e != null) { Config.lastServer = e.address; Config.markDirty(); }
            ModuleManager.join(mc);
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (ready && player == MinecraftClient.getInstance().player) PvpMods.onAttack(entity, hit);
            return ActionResult.PASS;
        });

        HudElementRegistry.addLast(Identifier.of("rclient", "hud"), (g, tc) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!ready || mc.options.hudHidden || mc.currentScreen instanceof HudEditorScreen) return;
            ModuleManager.frame(mc);
            for (var h : ModuleManager.huds()) if (h.isEnabled()) h.render(g, mc, false);
        });
        hide(VanillaHudElements.SCOREBOARD, () -> vh() != null && vh().hideScoreboard.bool());
        hide(VanillaHudElements.BOSS_BAR, () -> vh() != null && vh().hideBossBar.bool());
        hide(VanillaHudElements.OVERLAY_MESSAGE, () -> vh() != null && vh().hideActionBar.bool());
        hide(VanillaHudElements.STATUS_EFFECTS, () -> vh() != null && vh().hideEffects.bool());
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, orig -> (g, tc) -> {
            var cm = ModuleManager.of(PvpMods.CrosshairMod.class);
            if (ready && cm != null && cm.isEnabled()) cm.draw(g, MinecraftClient.getInstance());
            else orig.render(g, tc);
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> { if (ready) Config.saveAll(); }, "rclient-save"));
    }

    private static HudMods.VanillaHud vh() { return ModuleManager.of(HudMods.VanillaHud.class); }

    private static void hide(Identifier id, BooleanSupplier hidden) {
        HudElementRegistry.replaceElement(id, orig -> (g, tc) -> { if (!(ready && hidden.getAsBoolean())) orig.render(g, tc); });
    }

    /** One real initialization step; the loading screen drives these so its progress bar is truthful. */
    public static synchronized void runStep(int i) {
        if (i < 0 || i >= STEPS.length || stepsDone[i]) return;
        switch (i) {
            case 0 -> Modules.registerAll();
            case 1 -> Config.init();
            case 2 -> { /* profile is applied by Config.init; flush a consistent state */ Config.saveAll(); }
            case 3 -> LOG.info("RClient compat: {}", Compat.scan().stream().filter(Compat.Entry::loaded).map(Compat.Entry::name).toList());
            default -> { }
        }
        stepsDone[i] = true;
        boolean all = true;
        for (boolean b : stepsDone) all &= b;
        if (all) { ready = true; LOG.info("RClient {} ready: {} modules", VERSION, ModuleManager.all().size()); }
    }

    private static void tick(MinecraftClient mc) {
        if (!ready) {
            // Not on the title screen path (e.g. quick-play/direct join): initialize without the splash.
            if (mc.player != null) {
                for (int i = 0; i < STEPS.length; i++) runStep(i);
            }
        }
        if (mc.currentScreen instanceof TitleScreen && !(mc.currentScreen instanceof RTitleScreen)) {
            mc.setScreen(ready ? new RTitleScreen() : new RLoadingScreen());
        }
        if (!ready) return;
        while (menuKey.wasPressed()) if (mc.currentScreen == null && mc.player != null) mc.setScreen(new ModMenuScreen(null));
        ModuleManager.tick(mc);
        if (++tickCount % 100 == 0) Config.flushIfDirty();
    }
}
