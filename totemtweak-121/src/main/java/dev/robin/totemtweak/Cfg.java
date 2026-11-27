package dev.robin.totemtweak;

import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.Path;
import java.util.Properties;

public final class Cfg {
    public static float x = 0f, y = 0f, z = 0f, size = 1f;
    public static final float X_MAX = 10f, Y_MAX = 10f, Z_MIN = -10f, Z_MAX = 5f, S_MIN = 0.2f, S_MAX = 3f;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("totemtweak.properties");

    private Cfg() { }

    public static void load() {
        try (InputStream in = new FileInputStream(FILE.toFile())) {
            Properties p = new Properties(); p.load(in);
            x = f(p, "x", 0f); y = f(p, "y", 0f); z = f(p, "z", 0f); size = f(p, "size", 1f);
        } catch (Exception ignored) { }
        clamp();
    }

    private static float f(Properties p, String k, float d) { try { return Float.parseFloat(p.getProperty(k)); } catch (Exception e) { return d; } }

    public static void clamp() {
        x = Math.max(-X_MAX, Math.min(X_MAX, x)); y = Math.max(-Y_MAX, Math.min(Y_MAX, y));
        z = Math.max(Z_MIN, Math.min(Z_MAX, z)); size = Math.max(S_MIN, Math.min(S_MAX, size));
    }

    public static void save() {
        try (OutputStream out = new FileOutputStream(FILE.toFile())) {
            Properties p = new Properties();
            p.setProperty("x", String.valueOf(x)); p.setProperty("y", String.valueOf(y));
            p.setProperty("z", String.valueOf(z)); p.setProperty("size", String.valueOf(size));
            p.store(out, "Totem Tweaker");
        } catch (Exception ignored) { }
    }

    public static void reset() { x = 0; y = 0; z = 0; size = 1; save(); }
}
