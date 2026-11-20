package com.robin.mods;

import android.content.ContentResolver;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Installer {
    public interface Log { void say(String s); }

    static String folder(String type) {
        switch (type) {
            case "shader": return "shaderpacks";
            case "resourcepack": return "resourcepacks";
            default: return "mods";
        }
    }

    static JSONObject primary(JSONObject version) throws Exception {
        JSONArray files = version.getJSONArray("files");
        for (int i = 0; i < files.length(); i++) if (files.getJSONObject(i).optBoolean("primary")) return files.getJSONObject(i);
        return files.getJSONObject(0);
    }

    /** Installs the newest version of a project matching the instance, plus its required dependencies (one level). */
    public static void installProject(ContentResolver cr, Store.Inst inst, String projectId, String type, Log log) throws Exception {
        Uri tree = Uri.parse(inst.tree);
        Set<String> done = new HashSet<>();
        installOne(cr, tree, inst, projectId, type, log, done, true);
    }

    private static void installOne(ContentResolver cr, Uri tree, Store.Inst inst, String pid, String type, Log log, Set<String> done, boolean deps) throws Exception {
        if (!done.add(pid)) return;
        JSONArray vs = Api.versions(pid, type, inst.loader, inst.mc);
        if (vs.length() == 0) throw new Exception("No version for " + inst.mc + " / " + inst.loader);
        JSONObject v = vs.getJSONObject(0);
        JSONObject f = primary(v);
        String name = f.getString("filename");
        log.say("Downloading " + name);
        try (OutputStream o = Saf.open(cr, tree, folder(type) + "/" + name)) { Api.download(f.getString("url"), o, null); }
        if (deps && v.has("dependencies")) {
            JSONArray d = v.getJSONArray("dependencies");
            for (int i = 0; i < d.length(); i++) {
                JSONObject x = d.getJSONObject(i);
                if ("required".equals(x.optString("dependency_type")) && !x.isNull("project_id")) {
                    String dp = x.getString("project_id");
                    String dt = Api.project(dp).optString("project_type", "mod");
                    if (dt.equals("mod")) installOne(cr, tree, inst, dp, "mod", log, done, false);
                }
            }
        }
    }

    /** Reads minecraft version and loader from a .mrpack without installing. */
    public static String[] mrpackInfo(File mrpack) throws Exception {
        try (ZipFile z = new ZipFile(mrpack)) {
            JSONObject idx = index(z);
            JSONObject d = idx.getJSONObject("dependencies");
            String loader = "fabric";
            if (d.has("neoforge")) loader = "neoforge"; else if (d.has("forge")) loader = "forge";
            else if (d.has("quilt-loader")) loader = "quilt"; else if (d.has("fabric-loader")) loader = "fabric";
            return new String[]{d.optString("minecraft", ""), loader, idx.optString("name", "Modpack")};
        }
    }

    private static JSONObject index(ZipFile z) throws Exception {
        ZipEntry e = z.getEntry("modrinth.index.json");
        if (e == null) throw new Exception("Not a Modrinth modpack (.mrpack)");
        try (InputStream in = z.getInputStream(e)) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return new JSONObject(o.toString("UTF-8"));
        }
    }

    /** Installs a .mrpack: downloads every listed file and extracts overrides into the instance folder. */
    public static void installMrpack(ContentResolver cr, Uri tree, File mrpack, Log log) throws Exception {
        try (ZipFile z = new ZipFile(mrpack)) {
            JSONArray files = index(z).getJSONArray("files");
            for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.getJSONObject(i);
                JSONObject env = f.optJSONObject("env");
                if (env != null && "unsupported".equals(env.optString("client"))) continue;
                String path = f.getString("path");
                log.say("(" + (i + 1) + "/" + files.length() + ") " + path);
                JSONArray dl = f.getJSONArray("downloads");
                Exception last = null; boolean ok = false;
                for (int k = 0; k < dl.length() && !ok; k++) {
                    try (OutputStream o = Saf.open(cr, tree, path)) { Api.download(dl.getString(k), o, null); ok = true; }
                    catch (Exception e) { last = e; }
                }
                if (!ok) throw last == null ? new Exception("No download for " + path) : last;
            }
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                String rel = n.startsWith("overrides/") ? n.substring(10) : n.startsWith("client-overrides/") ? n.substring(17) : null;
                if (rel == null || e.isDirectory() || rel.isEmpty()) continue;
                log.say("Config: " + rel);
                try (InputStream in = z.getInputStream(e); OutputStream o = Saf.open(cr, tree, rel)) {
                    byte[] b = new byte[16384]; int c;
                    while ((c = in.read(b)) > 0) o.write(b, 0, c);
                }
            }
        }
    }
}
