package com.robin.packsed;

import android.graphics.Bitmap;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** PNG / mcmeta / resource-pack ZIP writers. */
final class Export {
    private Export() { }

    static Bitmap strip(Model.Tex t) {
        int n = t.frames.size();
        Bitmap b = Bitmap.createBitmap(t.size, t.size * n, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < n; i++) b.setPixels(t.frames.get(i), 0, t.size, 0, i * t.size, t.size, t.size);
        return b;
    }

    static byte[] png(Model.Tex t) {
        Bitmap b = strip(t);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, o);
        b.recycle();
        return o.toByteArray();
    }

    static String mcmeta(Model.Tex t) {
        return "{\n  \"animation\": {\n    \"frametime\": " + Math.max(1, t.frametime) + ",\n    \"interpolate\": " + t.interpolate + "\n  }\n}\n";
    }

    static String packMcmeta(Model.Pack p) {
        int fmt = Model.FORMATS[p.version];
        String desc = p.desc.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
        if (fmt >= 69) // 1.21.9+ declares a format range
            return "{\n  \"pack\": {\n    \"description\": \"" + desc + "\",\n    \"min_format\": " + fmt + ",\n    \"max_format\": " + fmt + "\n  }\n}\n";
        return "{\n  \"pack\": {\n    \"pack_format\": " + fmt + ",\n    \"description\": \"" + desc + "\"\n  }\n}\n";
    }

    private static void put(ZipOutputStream z, String name, byte[] data) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }

    private static void addTex(ZipOutputStream z, Model.Tex t) throws IOException {
        put(z, t.path(), png(t));
        if (t.animated()) put(z, t.path() + ".mcmeta", mcmeta(t).getBytes(StandardCharsets.UTF_8));
    }

    static void packZip(Model.Pack p, OutputStream os) throws IOException {
        try (ZipOutputStream z = new ZipOutputStream(os)) {
            put(z, "pack.mcmeta", packMcmeta(p).getBytes(StandardCharsets.UTF_8));
            for (Model.Tex t : p.texs) addTex(z, t);
        }
    }

    /** One texture as a ready-to-merge ZIP (png + mcmeta in the right folders). */
    static void texZip(Model.Tex t, OutputStream os) throws IOException {
        try (ZipOutputStream z = new ZipOutputStream(os)) { addTex(z, t); }
    }
}
