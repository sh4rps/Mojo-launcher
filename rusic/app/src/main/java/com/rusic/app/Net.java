package com.rusic.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

/** Free & legal music from the Internet Archive (public domain / Creative Commons / indie uploads). */
public class Net {
    public static class Item { public String id, title, creator; }
    public static class AFile { public String name, url; public long size; }
    public interface Progress { void on(long done, long total); }

    static HttpURLConnection open(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(40000);
        c.setRequestProperty("User-Agent", "Rusic/1.0 (Android)");
        return c;
    }

    static String get(String u) throws IOException {
        HttpURLConnection c = open(u);
        if (c.getResponseCode() >= 400) throw new IOException("HTTP " + c.getResponseCode());
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder(); String l;
        while ((l = r.readLine()) != null) sb.append(l);
        r.close();
        return sb.toString();
    }

    public static List<Item> search(String q) throws Exception {
        String e = URLEncoder.encode(q.replace("\"", ""), "UTF-8");
        String query = URLEncoder.encode("(title:(" + q.replace("\"", "") + ") OR creator:(" + q.replace("\"", "") + ") OR subject:(" + q.replace("\"", "") + ") OR language:(" + q.replace("\"", "") + ")) AND mediatype:audio", "UTF-8");
        String u = "https://archive.org/advancedsearch.php?q=" + query
                + "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&rows=40&page=1&output=json&sort%5B%5D=downloads+desc";
        JSONArray docs = new JSONObject(get(u)).getJSONObject("response").getJSONArray("docs");
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject d = docs.getJSONObject(i);
            Item it = new Item();
            it.id = d.optString("identifier");
            it.title = firstOf(d, "title");
            it.creator = firstOf(d, "creator");
            if (it.title.isEmpty()) it.title = it.id;
            out.add(it);
        }
        return out;
    }

    static String firstOf(JSONObject d, String k) {
        Object o = d.opt(k);
        if (o instanceof JSONArray) { JSONArray a = (JSONArray) o; return a.length() > 0 ? a.optString(0) : ""; }
        return o == null ? "" : o.toString();
    }

    public static List<AFile> files(String id) throws Exception {
        JSONObject m = new JSONObject(get("https://archive.org/metadata/" + URLEncoder.encode(id, "UTF-8")));
        JSONArray fs = m.optJSONArray("files");
        Map<String, AFile> best = new LinkedHashMap<>();
        if (fs == null) return new ArrayList<>();
        for (int i = 0; i < fs.length(); i++) {
            JSONObject f = fs.getJSONObject(i);
            String n = f.optString("name");
            String low = n.toLowerCase(Locale.US);
            boolean mp3 = low.endsWith(".mp3"), ogg = low.endsWith(".ogg"), m4a = low.endsWith(".m4a");
            if (!(mp3 || ogg || m4a) || n.contains("_spectrogram")) continue;
            String base = n.contains(".") ? n.substring(0, n.lastIndexOf('.')) : n;
            AFile a = new AFile();
            a.name = n;
            a.size = f.optLong("size");
            a.url = "https://archive.org/download/" + URLEncoder.encode(id, "UTF-8").replace("+", "%20") + "/" + encPath(n);
            AFile old = best.get(base);
            if (old == null || mp3) best.put(base, a);
        }
        return new ArrayList<>(best.values());
    }

    static String encPath(String p) throws Exception {
        String[] parts = p.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(URLEncoder.encode(parts[i], "UTF-8").replace("+", "%20"));
        }
        return sb.toString();
    }

    /** Resumable download with retries; writes to .part then renames. */
    public static void download(String url, File out, Progress p) throws Exception {
        File tmp = new File(out.getPath() + ".part");
        IOException last = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                long have = tmp.exists() ? tmp.length() : 0;
                HttpURLConnection c = open(url);
                if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
                int code = c.getResponseCode();
                if (code == 416) { tmp.delete(); throw new IOException("restart"); }
                if (code >= 400) throw new IOException("HTTP " + code);
                boolean resumed = code == 206 && have > 0;
                if (!resumed) have = 0;
                long len = c.getContentLengthLong();
                long total = len > 0 ? have + len : -1;
                InputStream in = c.getInputStream();
                OutputStream o = new FileOutputStream(tmp, resumed);
                try {
                    byte[] buf = new byte[32768]; int n; long last2 = 0;
                    while ((n = in.read(buf)) > 0) {
                        o.write(buf, 0, n); have += n;
                        long now = System.currentTimeMillis();
                        if (p != null && now - last2 > 250) { last2 = now; p.on(have, total); }
                    }
                } finally { o.close(); in.close(); c.disconnect(); }
                if (total > 0 && tmp.length() != total) throw new IOException("connection dropped");
                if (p != null) p.on(tmp.length(), tmp.length());
                if (out.exists()) out.delete();
                if (!tmp.renameTo(out)) throw new IOException("cannot save file");
                return;
            } catch (IOException e) {
                last = e;
                try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) { }
            }
        }
        throw last != null ? last : new IOException("download failed");
    }
}
