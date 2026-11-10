package com.robin.rustmod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** config/rustmod.properties - plain key=value, editable by hand. */
public final class Cfg {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("rustmod.properties");
    private static final Properties P = new Properties();

    public static void load() {
        P.clear();
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) { P.load(in); } catch (IOException ignored) { }
        }
    }

    public static void save() {
        try (OutputStream out = Files.newOutputStream(FILE)) {
            P.store(out, "Rust Mod settings. enabled/adaptive/overlay = true|false. entityCull = blocks (0 = off). "
                    + "particleCap = particles per tick (0 = off). bak.* = your settings before Rust Mod (restored when you turn it off).");
        } catch (IOException ignored) { }
    }

    public static boolean bool(String k, boolean d) { return Boolean.parseBoolean(P.getProperty(k, String.valueOf(d))); }
    public static int num(String k, int d) {
        try { return Integer.parseInt(P.getProperty(k, String.valueOf(d)).trim()); } catch (Exception e) { return d; }
    }
    public static String str(String k) { return P.getProperty(k); }
    public static void set(String k, Object v) { P.setProperty(k, String.valueOf(v)); }
    public static void remove(String k) { P.remove(k); }
    public static boolean has(String k) { return P.containsKey(k); }
}
