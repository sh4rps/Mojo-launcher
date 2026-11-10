package com.robin.rustlod;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** config/rustlod.properties */
public final class Cfg {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("rustlod.properties");
    private static final Properties P = new Properties();
    public static volatile boolean enabled = true;
    public static volatile int radius = 24;   // chunks
    public static volatile int yOffset = 2;   // blocks the far terrain is lowered so real chunks always win

    public static void load() {
        try {
            if (Files.exists(FILE)) try (InputStream in = Files.newInputStream(FILE)) { P.load(in); }
        } catch (Exception ignored) { }
        enabled = Boolean.parseBoolean(P.getProperty("enabled", "true"));
        radius = clamp(num("radius", 24), 6, 64);
        yOffset = clamp(num("yOffset", 2), 0, 8);
        save();
    }

    public static void save() {
        P.setProperty("enabled", String.valueOf(enabled));
        P.setProperty("radius", String.valueOf(radius));
        P.setProperty("yOffset", String.valueOf(yOffset));
        try (OutputStream out = Files.newOutputStream(FILE)) {
            P.store(out, "Rust LOD. radius = far terrain distance in chunks (6-64). yOffset = blocks lowered (0-8).");
        } catch (Exception ignored) { }
    }

    private static int num(String k, int d) {
        try { return Integer.parseInt(P.getProperty(k, String.valueOf(d)).trim()); } catch (Exception e) { return d; }
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
