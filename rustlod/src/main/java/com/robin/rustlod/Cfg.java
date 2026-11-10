package com.robin.rustlod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** config/rustlod.properties */
public final class Cfg {
    /** Client: how far (chunks) far terrain is drawn. Higher = prettier but slower. Phones: 16-24. */
    public static int radius = 24;
    /** Client: 0..100, how much the far terrain is tinted toward the sky colour at its inner edge. */
    public static int haze = 45;
    /** Client toggle (key). */
    public static boolean enabled = true;
    /** Server: how far (chunks) it streams estimated terrain to each player. */
    public static int serverRadius = 32;
    /** Server: chunks computed per 2 ticks per player. */
    public static int budget = 3;

    public static void load() {
        Path f = FabricLoader.getInstance().getConfigDir().resolve("rustlod.properties");
        Properties p = new Properties();
        if (Files.exists(f)) {
            try (InputStream in = Files.newInputStream(f)) { p.load(in); } catch (IOException ignored) { }
        }
        radius = num(p, "radius", radius, 4, 96);
        haze = num(p, "haze", haze, 0, 100);
        serverRadius = num(p, "serverRadius", serverRadius, 8, 128);
        budget = num(p, "budget", budget, 1, 20);
        enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
        p.setProperty("radius", String.valueOf(radius));
        p.setProperty("haze", String.valueOf(haze));
        p.setProperty("serverRadius", String.valueOf(serverRadius));
        p.setProperty("budget", String.valueOf(budget));
        p.setProperty("enabled", String.valueOf(enabled));
        try (OutputStream out = Files.newOutputStream(f)) {
            p.store(out, "Rust LOD. radius/haze = client, serverRadius/budget = server (chunks)");
        } catch (IOException ignored) { }
    }

    private static int num(Properties p, String k, int d, int lo, int hi) {
        try { return Math.max(lo, Math.min(hi, Integer.parseInt(p.getProperty(k, String.valueOf(d)).trim()))); }
        catch (Exception e) { return d; }
    }
}
