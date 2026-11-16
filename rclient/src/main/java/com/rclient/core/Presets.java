package com.rclient.core;

import com.google.gson.JsonObject;
import java.nio.file.*;
import java.util.*;

/** First-run profile files. Written as raw JSON so no module hooks fire. */
public final class Presets {
    private Presets() { }

    private static final Map<String, List<String>> SETS = new LinkedHashMap<>();
    static {
        SETS.put("PvP", List.of("cps", "combo", "reach", "armor", "keystrokes", "cooldown", "potions", "fps", "pvpinfo", "togglesprint"));
        SETS.put("Survival", List.of("fps", "coords", "direction", "minimap", "waila", "daycounter", "weatherdisplay", "armor", "togglesprint"));
        SETS.put("SMP", List.of("fps", "ping", "coords", "minimap", "waila", "armor", "serveraddress", "playtime"));
        SETS.put("Performance", List.of("fps", "memory", "performance"));
        SETS.put("Custom", List.of("fps"));
    }

    public static void createDefaults() {
        for (var e : SETS.entrySet()) {
            JsonObject root = new JsonObject();
            JsonObject mods = new JsonObject();
            for (String id : e.getValue()) {
                JsonObject mo = new JsonObject();
                mo.addProperty("enabled", true);
                mods.add(id, mo);
            }
            root.add("modules", mods);
            try {
                Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("rclient").resolve("profiles");
                Files.createDirectories(dir);
                Files.writeString(dir.resolve(Config.safe(e.getKey()) + ".json"), root.toString());
            } catch (Exception ex) { com.rclient.RClient.LOG.error("preset {}", e.getKey(), ex); }
        }
    }
}
