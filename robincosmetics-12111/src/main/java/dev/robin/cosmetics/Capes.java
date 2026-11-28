package dev.robin.cosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

/** Five procedurally animated capes, drawn into one dynamic texture that is re-uploaded every other tick. */
public final class Capes {
    public static final int COUNT = 5;
    public static final String[] NAMES = {"Off", "Inferno", "Rainbow", "Ocean", "Galaxy", "Storm"};
    private static final Identifier ID = Identifier.of("robincosmetics", "animated_cape");
    private static NativeImageBackedTexture tex;
    private static AssetInfo.TextureAsset asset;
    private static boolean failed;
    private static int tick;

    private Capes() { }

    /** The cape texture asset for the selected cape, or null when no cape is chosen. */
    public static AssetInfo.TextureAsset asset() {
        if (Cfg.cape <= 0 || failed) return null;
        if (tex == null) init();
        return asset;
    }

    private static void init() {
        try {
            tex = new NativeImageBackedTexture("robincosmetics_cape", 64, 32, true);
            MinecraftClient.getInstance().getTextureManager().registerTexture(ID, tex);
            asset = new AssetInfo.TextureAssetInfo(ID, ID);
            paint(0);
            tex.upload();
        } catch (Throwable t) { failed = true; t.printStackTrace(); }
    }

    public static void tick() {
        if (Cfg.cape <= 0 || tex == null || failed) return;
        if ((++tick & 1) != 0) return;
        try { paint(tick); tex.upload(); } catch (Throwable t) { failed = true; t.printStackTrace(); }
    }

    private static void paint(int t) {
        NativeImage img = tex.getImage();
        if (img == null) return;
        int style = Cfg.cape;
        for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) img.setColorArgb(x, y, 0xFF000000 | pixel(style, x, y, t));
    }

    // ---- patterns: each returns 0xRRGGBB ----
    private static int pixel(int style, int x, int y, int t) {
        double v = Math.min(y, 16) / 16.0;                 // 0 top .. 1 bottom of the cape
        double time = t * 0.5;
        switch (style) {
            case 1: { // Inferno: flames licking upwards
                double wob = Math.sin(x * 0.85 + time * 0.55 + Math.sin(y * 0.45 - time * 0.35) * 1.6);
                double heat = (1.0 - v) * 0.55 + 0.45 * (0.5 + 0.5 * wob) + 0.25 * Math.sin(y * 0.7 - time * 0.9 + x * 0.3);
                heat = clamp(heat * (1.2 - 0.3 * v));
                int rr = (int) (255 * clamp(heat * 1.6)), gg = (int) (255 * clamp(heat * 1.6 - 0.55)), bb = (int) (255 * clamp(heat * 2.2 - 1.5));
                return (rr << 16) | (gg << 8) | bb;
            }
            case 2: { // Rainbow: moving diagonal hue bands
                return hsv(((x * 7 + y * 5 + t * 4) % 360 + 360) % 360, 0.85, 1.0);
            }
            case 3: { // Ocean: layered waves with light crests
                double w = Math.sin(x * 0.5 + time * 0.4) * 0.5 + Math.sin(y * 0.6 - time * 0.5 + x * 0.2) * 0.5;
                double d = clamp(0.35 + 0.35 * w + 0.25 * (1.0 - v));
                int rr = (int) (20 + 60 * d * d), gg = (int) (90 + 140 * d), bb = (int) (150 + 105 * d);
                if (w > 0.82) { rr += 90; gg += 60; bb += 30; }     // foam
                return (Math.min(255, rr) << 16) | (Math.min(255, gg) << 8) | Math.min(255, bb);
            }
            case 4: { // Galaxy: purple nebula + twinkling stars
                double n = 0.5 + 0.5 * Math.sin(x * 0.35 + Math.sin(y * 0.4 + time * 0.08) * 2 + time * 0.06) * Math.cos(y * 0.3 - time * 0.05);
                int rr = (int) (30 + 90 * n), gg = (int) (10 + 30 * n), bb = (int) (60 + 150 * n);
                int h = hash(x, y, t / 6);
                if ((h & 31) == 0) { int s = 140 + (h >> 8 & 115); return (s << 16) | (s << 8) | s; }
                return (rr << 16) | (gg << 8) | bb;
            }
            default: { // Storm: dark clouds with travelling lightning
                double cl = 0.5 + 0.5 * Math.sin(x * 0.4 + time * 0.2) * Math.sin(y * 0.5 - time * 0.15);
                int base = (int) (25 + 35 * cl);
                int rr = base, gg = base + 5, bb = base + 25;
                double bolt = Math.abs(Math.sin((x + y * 0.6) * 0.8 - time * 1.3));
                boolean flash = (t / 4) % 9 < 2;
                if (bolt < 0.07 + (flash ? 0.12 : 0.0)) { rr = 170; gg = 220; bb = 255; }
                else if (flash) { rr += 25; gg += 30; bb += 40; }
                return (rr << 16) | (gg << 8) | bb;
            }
        }
    }

    private static double clamp(double v) { return v < 0 ? 0 : Math.min(v, 1); }
    private static int hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177; h = (h ^ (h >>> 13)) * 1274126177; return h ^ (h >>> 16);
    }
    private static int hsv(double h, double s, double v) {
        double c = v * s, hh = h / 60.0, xx = c * (1 - Math.abs(hh % 2 - 1)), m = v - c, r = 0, g = 0, b = 0;
        if (hh < 1) { r = c; g = xx; } else if (hh < 2) { r = xx; g = c; } else if (hh < 3) { g = c; b = xx; }
        else if (hh < 4) { g = xx; b = c; } else if (hh < 5) { r = xx; b = c; } else { r = c; b = xx; }
        return ((int) ((r + m) * 255) << 16) | ((int) ((g + m) * 255) << 8) | (int) ((b + m) * 255);
    }
}
