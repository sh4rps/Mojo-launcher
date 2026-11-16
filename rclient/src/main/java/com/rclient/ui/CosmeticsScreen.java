package com.rclient.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.SkinOptionsScreen;

public class CosmeticsScreen extends SimplePage {
    public CosmeticsScreen(Screen parent) { super("Cosmetics", parent); }
    @Override protected void build() {
        lines.add("Skin layers, cape visibility and main hand are");
        lines.add("controlled by Minecraft's skin settings (works online).");
        lines.add("Custom capes, wings, hats, emotes and a 3D preview");
        lines.add("need a texture/asset server and model renderer that");
        lines.add("RClient 1.0 does not include. They are not faked here.");
        buttons.add(new Btn("Skin Customization", () -> mc.setScreen(new SkinOptionsScreen(this, mc.options)), true));
    }
}
