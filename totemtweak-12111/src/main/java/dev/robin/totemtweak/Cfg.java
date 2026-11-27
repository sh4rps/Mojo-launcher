package dev.robin.totemtweak;

import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.Path;
import java.util.Properties;

/** Settings for a TOTEM held in the main hand (index 0) or the off hand (index 1). x/y/z are in view-space blocks. */
public final class Cfg {
    public static final float[] x = new float[2], y = new float[2], z = new float[2];
    public static final float[] size = {1f, 1f};
    public static final float X_MAX = 1f, Y_MAX = 1f, Z_MIN = -1.5f, Z_MAX = 0.5f, S_MIN = 0.2f, S_MAX = 3f;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("totemtweak.properties");

    private Cfg() { }

    public static void load() {
        try (InputStream in = new FileInputStream(FILE.toFile())) {
            Properties p = new Properties(); p.load(in);
            for (int i = 0; i < 2; i++) {
                x[i] = f(p, "x" + i, 0f); y[i] = f(p, "y" + i, 0f); z[i] = f(p, "z" + i, 0f); size[i] = f(p, "size" + i, 1f);
            }
        } catch (Exception ignored) { }
        clamp();
    }

    private static float f(Properties p, String k, float d) { try { return Float.parseFloat(p.getProperty(k)); } catch (Exception e) { return d; } }

    public static void clamp() {
        for (int i = 0; i < 2; i++) {
            x[i] = Math.max(-X_MAX, Math.min(X_MAX, x[i])); y[i] = Math.max(-Y_MAX, Math.min(Y_MAX, y[i]));
            z[i] = Math.max(Z_MIN, Math.min(Z_MAX, z[i])); size[i] = Math.max(S_MIN, Math.min(S_MAX, size[i]));
        }
    }

    public static void save() {
        try (OutputStream out = new FileOutputStream(FILE.toFile())) {
            Properties p = new Properties();
            for (int i = 0; i < 2; i++) {
                p.setProperty("x" + i, String.valueOf(x[i])); p.setProperty("y" + i, String.valueOf(y[i]));
                p.setProperty("z" + i, String.valueOf(z[i])); p.setProperty("size" + i, String.valueOf(size[i]));
            }
            p.store(out, "Totem Tweaker (0 = main hand, 1 = off hand)");
        } catch (Exception ignored) { }
    }

    public static void reset(int h) { x[h] = 0; y[h] = 0; z[h] = 0; size[h] = 1; save(); }
}
