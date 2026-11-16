package com.rclient.modules;

public final class Modules {
    private Modules() { }
    public static void registerAll() {
        PvpMods.register();
        HudMods.register();
        MoveVisualMods.register();
        WorldUtilMods.register();
    }
}
