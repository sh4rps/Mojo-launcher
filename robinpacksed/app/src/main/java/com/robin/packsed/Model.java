package com.robin.packsed;

import android.content.Context;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/** Data model + persistence. Frames are ARGB int arrays of size*size. */
final class Model {
    static final class Tex {
        String name = "texture";
        String category = "block";   // block, item, entity, gui, environment, particle, misc, custom path
        int size = 16;
        int frametime = 2;           // ticks (1 tick = 50 ms)
        boolean interpolate = false;
        final List<int[]> frames = new ArrayList<>();
        Tex(String n, String c, int s) { name = n; category = c; size = s; frames.add(new int[s * s]); }
        private Tex() { }
        boolean animated() { return frames.size() > 1; }
        String path() { return "assets/minecraft/textures/" + category + "/" + name + ".png"; }
    }

    static final class Pack {
        String name = "My Pack";
        String desc = "Made with ROBINPACKSED";
        int version = 0;             // index into VERSIONS
        final List<Tex> texs = new ArrayList<>();
    }

    static final String[] VERSIONS = {"1.21.11", "1.21.9 - 1.21.10", "1.21.7 - 1.21.8", "1.21.6", "1.21.5", "1.21.4", "1.21.2 - 1.21.3", "1.21 - 1.21.1", "1.20.5 - 1.20.6", "1.20.3 - 1.20.4", "1.20 - 1.20.1", "1.19.4", "1.18", "1.16.5"};
    static final int[] FORMATS =  {75, 69, 64, 63, 55, 46, 42, 34, 32, 22, 15, 13, 8, 6};

    static final List<Pack> packs = new ArrayList<>();
    private static boolean loaded;

    static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        File f = new File(c.getFilesDir(), "data.bin");
        if (!f.exists()) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            if (in.readInt() != 1) return;
            int np = in.readInt();
            for (int i = 0; i < np; i++) {
                Pack p = new Pack();
                p.name = in.readUTF(); p.desc = in.readUTF(); p.version = Math.max(0, Math.min(VERSIONS.length - 1, in.readInt()));
                int nt = in.readInt();
                for (int j = 0; j < nt; j++) {
                    Tex t = new Tex();
                    t.name = in.readUTF(); t.category = in.readUTF(); t.size = in.readInt();
                    t.frametime = in.readInt(); t.interpolate = in.readBoolean();
                    int nf = in.readInt();
                    for (int k = 0; k < nf; k++) {
                        int[] fr = new int[t.size * t.size];
                        for (int q = 0; q < fr.length; q++) fr[q] = in.readInt();
                        t.frames.add(fr);
                    }
                    if (t.frames.isEmpty()) t.frames.add(new int[t.size * t.size]);
                    p.texs.add(t);
                }
                packs.add(p);
            }
        } catch (Exception e) { /* corrupt file: start empty, keep the broken file untouched */ }
    }

    static synchronized void save(Context c) {
        File f = new File(c.getFilesDir(), "data.bin"), tmp = new File(c.getFilesDir(), "data.bin.tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            out.writeInt(1);
            out.writeInt(packs.size());
            for (Pack p : packs) {
                out.writeUTF(p.name); out.writeUTF(p.desc); out.writeInt(p.version);
                out.writeInt(p.texs.size());
                for (Tex t : p.texs) {
                    out.writeUTF(t.name); out.writeUTF(t.category); out.writeInt(t.size);
                    out.writeInt(t.frametime); out.writeBoolean(t.interpolate);
                    out.writeInt(t.frames.size());
                    for (int[] fr : t.frames) for (int v : fr) out.writeInt(v);
                }
            }
        } catch (Exception e) { return; }
        if (f.exists()) f.delete();
        tmp.renameTo(f);
    }
}
