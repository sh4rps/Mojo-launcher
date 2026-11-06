package dev.robin.v1.module;

import dev.robin.v1.Category;
import dev.robin.v1.Draw;
import dev.robin.v1.Module;
import dev.robin.v1.Setting;

/** Always-on pseudo module that holds the GUI appearance settings. */
public class GuiModule extends Module {
    public final Setting red = add(Setting.num("Accent red", 255, 0, 255, 1));
    public final Setting green = add(Setting.num("Accent green", 122, 0, 255, 1));
    public final Setting blue = add(Setting.num("Accent blue", 0, 0, 255, 1));
    public final Setting background = add(Setting.num("Background opacity %", 92, 40, 100, 1));
    public final Setting animations = add(Setting.bool("Animations", true));

    public GuiModule() {
        super("GUI", "Colors and animations of the menu", Category.SETTINGS);
        alwaysOn = true;
    }

    public int accent() {
        return Draw.rgb((int) red.num(), (int) green.num(), (int) blue.num(), 255);
    }
}
