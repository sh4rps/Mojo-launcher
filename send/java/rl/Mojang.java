package rl;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Minimal Mojang / Minecraft Services client (profile, skin upload, capes, public skin lookup). */
final class Mojang {
    static final String API = "https://api.minecraftservices.com";

    static class Err extends IOException {
        final int code;
        Err(int code, String m) { super(m); this.code = code; }
    }

    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        return o.toByteArray();
    }

    static String call(String method, String url, String token, byte[] body, String type) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        c.setRequestMethod(method);
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("User-Agent", "SendLauncher");
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", type);
            OutputStream o = c.getOutputStream(); o.write(body); o.close();
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String s = in == null ? "" : new String(read(in), "UTF-8");
        if (code >= 400) throw new Err(code, s);
        return s;
    }

    static JSONObject profile(String token) throws Exception {
        return new JSONObject(call("GET", API + "/minecraft/profile", token, null, null));
    }

    static void uploadSkin(String token, byte[] png, boolean slim) throws Exception {
        String bd = "----feather" + System.currentTimeMillis();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        String nl = "\r\n";
        o.write(("--" + bd + nl + "Content-Disposition: form-data; name=\"variant\"" + nl + nl + (slim ? "slim" : "classic") + nl).getBytes("UTF-8"));
        o.write(("--" + bd + nl + "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"" + nl + "Content-Type: image/png" + nl + nl).getBytes("UTF-8"));
        o.write(png);
        o.write((nl + "--" + bd + "--" + nl).getBytes("UTF-8"));
        call("POST", API + "/minecraft/profile/skins", token, o.toByteArray(), "multipart/form-data; boundary=" + bd);
    }

    /** capeId == null hides the cape. */
    static void setCape(String token, String capeId) throws Exception {
        if (capeId == null) call("DELETE", API + "/minecraft/profile/capes/active", token, null, null);
        else call("PUT", API + "/minecraft/profile/capes/active", token,
                new JSONObject().put("capeId", capeId).toString().getBytes("UTF-8"), "application/json");
    }

    static Bitmap bitmap(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url.replace("http://", "https://")).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        InputStream in = c.getInputStream();
        try { return BitmapFactory.decodeStream(in); } finally { in.close(); }
    }

    /** Public lookup: returns {skinUrl, "slim"|"classic", capeUrl or null}. */
    static String[] playerSkin(String name) throws Exception {
        JSONObject p = new JSONObject(call("GET", "https://api.mojang.com/users/profiles/minecraft/" + name.trim(), null, null, null));
        JSONObject prof = new JSONObject(call("GET", "https://sessionserver.mojang.com/session/minecraft/profile/" + p.getString("id"), null, null, null));
        JSONArray props = prof.getJSONArray("properties");
        for (int i = 0; i < props.length(); i++) {
            JSONObject pr = props.getJSONObject(i);
            if (!"textures".equals(pr.getString("name"))) continue;
            JSONObject tex = new JSONObject(new String(Base64.decode(pr.getString("value"), Base64.DEFAULT), "UTF-8")).getJSONObject("textures");
            JSONObject skin = tex.getJSONObject("SKIN");
            String model = skin.has("metadata") && "slim".equals(skin.getJSONObject("metadata").optString("model")) ? "slim" : "classic";
            String cape = tex.has("CAPE") ? tex.getJSONObject("CAPE").getString("url") : null;
            return new String[]{skin.getString("url"), model, cape};
        }
        throw new IOException("no skin");
    }
}
