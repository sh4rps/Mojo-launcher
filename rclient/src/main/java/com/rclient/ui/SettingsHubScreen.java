package com.rclient.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.*;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.text.Text;

public class SettingsHubScreen extends SimplePage {
    public SettingsHubScreen(Screen parent) { super("Settings", parent); }
    @Override protected void build() {
        var o = mc.options;
        buttons.add(new Btn("RClient Mod Menu", () -> mc.setScreen(new ModMenuScreen(this)), true));
        buttons.add(new Btn("HUD Editor", () -> mc.setScreen(new HudEditorScreen(this)), false));
        buttons.add(new Btn("Video / Graphics", () -> mc.setScreen(new VideoOptionsScreen(this, mc, o)), false));
        buttons.add(new Btn("Audio", () -> mc.setScreen(new SoundOptionsScreen(this, o)), false));
        buttons.add(new Btn("Controls & Keybinds", () -> mc.setScreen(new KeybindsScreen(this, o)), false));
        buttons.add(new Btn("Resource Packs", () -> mc.setScreen(new PackScreen(mc.getResourcePackManager(), m -> { o.refreshResourcePacks(m); mc.setScreen(this); }, mc.getResourcePackDir(), Text.translatable("resourcePack.title"))), false));
        buttons.add(new Btn("All Minecraft Options", () -> mc.setScreen(new OptionsScreen(this, o)), false));
    }
}
