package dev.robin.hud;

import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.Path;
import java.util.Properties;

public final class Cfg {
    /** Modules: 0 = potion, 1 = armor, 2 = inventory. Position is 0..100 % of the free screen space. */
    public static final boolean[] on = {true, true, false};
    public static final int[] x = {1, 1, 99}, y = {12, 45, 40};
    public static final int[] scale = {100, 100, 100};       // percent, 50..200
    public static boolean fullbright = false;
    public static boolean hideVanillaEffects = true;
    public static boolean armorNumbers = true;
    public static boolean armorHeld = true;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("robinhud.properties");

    private Cfg() { }

    public static void load() {
        try (InputStream in = new FileInputStream(FILE.toFile())) {
            Properties p = new Properties(); p.load(in);
            for (int i = 0; i < 3; i++) {
                on[i] = Boolean.parseBoolean(p.getProperty("on" + i, "" + on[i]));
                x[i] = n(p, "x" + i, x[i]); y[i] = n(p, "y" + i, y[i]); scale[i] = n(p, "scale" + i, scale[i]);
            }
            fullbright = Boolean.parseBoolean(p.getProperty("fullbright", "false"));
            hideVanillaEffects = Boolean.parseBoolean(p.getProperty("hideVanillaEffects", "true"));
            armorNumbers = Boolean.parseBoolean(p.getProperty("armorNumbers", "true"));
            armorHeld = Boolean.parseBoolean(p.getProperty("armorHeld", "true"));
        } catch (Exception ignored) { }
        for (int i = 0; i < 3; i++) { x[i] = Math.max(0, Math.min(100, x[i])); y[i] = Math.max(0, Math.min(100, y[i])); scale[i] = Math.max(50, Math.min(200, scale[i])); }
    }

    private static int n(Properties p, String k, int d) { try { return Integer.parseInt(p.getProperty(k)); } catch (Exception e) { return d; } }

    public static void save() {
        try (OutputStream out = new FileOutputStream(FILE.toFile())) {
            Properties p = new Properties();
            for (int i = 0; i < 3; i++) { p.setProperty("on" + i, "" + on[i]); p.setProperty("x" + i, "" + x[i]); p.setProperty("y" + i, "" + y[i]); p.setProperty("scale" + i, "" + scale[i]); }
            p.setProperty("fullbright", "" + fullbright); p.setProperty("hideVanillaEffects", "" + hideVanillaEffects);
            p.setProperty("armorNumbers", "" + armorNumbers); p.setProperty("armorHeld", "" + armorHeld);
            p.store(out, "Robin HUD");
        } catch (Exception ignored) { }
    }
}
