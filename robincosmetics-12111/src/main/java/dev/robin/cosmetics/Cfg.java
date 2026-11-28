package dev.robin.cosmetics;

import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.Path;
import java.util.Properties;

public final class Cfg {
    public static int cape = 0;                    // 0 = none, 1..5
    public static boolean fireLeft = false, fireRight = false;
    public static int color = 0;                   // index into Fire.NAMES
    public static int r = 255, g = 120, b = 0;     // custom colour
    public static boolean sparks = false;          // optional extra particle sparks
    public static int style = 0;                   // index into Fire.STYLES
    public static int amount = 3;                  // particles per hand per tick, 1..6
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("robincosmetics.properties");

    private Cfg() { }

    public static void load() {
        try (InputStream in = new FileInputStream(FILE.toFile())) {
            Properties p = new Properties(); p.load(in);
            cape = i(p, "cape", 0); fireLeft = Boolean.parseBoolean(p.getProperty("fireLeft", "false"));
            fireRight = Boolean.parseBoolean(p.getProperty("fireRight", "false")); color = i(p, "color", 0);
            r = i(p, "r", 255); g = i(p, "g", 120); b = i(p, "b", 0); style = i(p, "style", 0); sparks = Boolean.parseBoolean(p.getProperty("sparks", "false")); amount = i(p, "amount", 3);
        } catch (Exception ignored) { }
        cape = Math.max(0, Math.min(Capes.COUNT, cape)); amount = Math.max(1, Math.min(6, amount));
        color = Math.max(0, Math.min(Fire.NAMES.length - 1, color)); style = Math.max(0, Math.min(Fire.STYLES.length - 1, style));
    }

    private static int i(Properties p, String k, int d) { try { return Integer.parseInt(p.getProperty(k)); } catch (Exception e) { return d; } }

    public static void save() {
        try (OutputStream out = new FileOutputStream(FILE.toFile())) {
            Properties p = new Properties();
            p.setProperty("cape", "" + cape); p.setProperty("fireLeft", "" + fireLeft); p.setProperty("fireRight", "" + fireRight);
            p.setProperty("color", "" + color); p.setProperty("r", "" + r); p.setProperty("g", "" + g); p.setProperty("b", "" + b);
            p.setProperty("amount", "" + amount); p.setProperty("style", "" + style); p.setProperty("sparks", "" + sparks);
            p.store(out, "Robin Cosmetics");
        } catch (Exception ignored) { }
    }
}
