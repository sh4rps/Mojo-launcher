package com.robin.mods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** Thin client for the public Modrinth API (https://docs.modrinth.com). */
public class Api {
    static final String BASE = "https://api.modrinth.com/v2";
    static final String UA = "sh4rps/RobinMods/1.0 (android)";

    public interface Progress { void on(long done, long total); }

    static HttpURLConnection open(String url) throws Exception {
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("User-Agent", UA);
            c.setConnectTimeout(15000); c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) { url = c.getHeaderField("Location"); c.disconnect(); continue; }
            if (code != 200) throw new Exception("HTTP " + code + " for " + url);
            return c;
        }
        throw new Exception("Too many redirects");
    }

    static String get(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return o.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    static byte[] bytes(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        } finally { c.disconnect(); }
    }

    static void download(String url, OutputStream out, Progress p) throws Exception {
        HttpURLConnection c = open(url);
        long total = c.getContentLengthLong();
        try (InputStream in = c.getInputStream()) {
            byte[] b = new byte[16384]; int n; long done = 0;
            while ((n = in.read(b)) > 0) { out.write(b, 0, n); done += n; if (p != null) p.on(done, total); }
        } finally { c.disconnect(); }
    }

    static void downloadTo(String url, File f) throws Exception {
        try (OutputStream o = new FileOutputStream(f)) { download(url, o, null); }
    }

    static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    static JSONArray search(String q, String type, String loader, String mc, int offset) throws Exception {
        JSONArray facets = new JSONArray();
        facets.put(new JSONArray().put("project_type:" + type));
        if (mc != null && !mc.isEmpty()) facets.put(new JSONArray().put("versions:" + mc));
        if (loader != null && !loader.isEmpty() && (type.equals("mod") || type.equals("modpack"))) facets.put(new JSONArray().put("categories:" + loader));
        String url = BASE + "/search?query=" + enc(q) + "&facets=" + enc(facets.toString()) + "&limit=20&offset=" + offset;
        return new JSONObject(get(url)).getJSONArray("hits");
    }

    static JSONArray versions(String projectId, String type, String loader, String mc) throws Exception {
        StringBuilder u = new StringBuilder(BASE + "/project/" + projectId + "/version?");
        if (loader != null && !loader.isEmpty() && type.equals("mod")) u.append("loaders=").append(enc(new JSONArray().put(loader).toString())).append("&");
        if (mc != null && !mc.isEmpty()) u.append("game_versions=").append(enc(new JSONArray().put(mc).toString()));
        return new JSONArray(get(u.toString()));
    }

    static JSONObject project(String id) throws Exception { return new JSONObject(get(BASE + "/project/" + id)); }

    static java.util.List<String> gameVersions() throws Exception {
        JSONArray a = new JSONArray(get(BASE + "/tag/game_version"));
        java.util.List<String> l = new java.util.ArrayList<>();
        for (int i = 0; i < a.length() && l.size() < 40; i++) {
            JSONObject o = a.getJSONObject(i);
            if ("release".equals(o.optString("version_type"))) l.add(o.getString("version"));
        }
        return l;
    }
}
