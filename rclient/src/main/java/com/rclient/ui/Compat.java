package com.rclient.ui;

import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

/** Reports which optional performance/shader mods are installed. RClient never bundles them. */
public final class Compat {
    public record Entry(String name, String id, String what, boolean loaded, String version) { }
    private Compat() { }

    public static List<Entry> scan() {
        String[][] mods = {
                {"Sodium", "sodium", "Rendering engine optimization (chunk rendering)"},
                {"Lithium", "lithium", "Game logic / tick optimization"},
                {"FerriteCore", "ferritecore", "Memory usage reduction"},
                {"ImmediatelyFast", "immediatelyfast", "HUD/text/immediate-mode rendering speed"},
                {"Entity Culling", "entityculling", "Skips rendering of hidden entities"},
                {"Iris", "iris", "Shader pack support"},
                {"Mod Menu", "modmenu", "Fabric mod list"},
        };
        List<Entry> out = new ArrayList<>();
        for (String[] m : mods) {
            var c = FabricLoader.getInstance().getModContainer(m[1]);
            out.add(new Entry(m[0], m[1], m[2], c.isPresent(), c.map(x -> x.getMetadata().getVersion().getFriendlyString()).orElse("")));
        }
        return out;
    }

    public static boolean has(String id) { return FabricLoader.getInstance().isModLoaded(id); }
}
