package com.robin.cord;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Settings snapshot + persistence. */
final class Cfg {
    static final int[] RES = {1080, 720, 480, 360};
    static final int[] FPS = {30, 60, 90};
    static final int[] BR = {48, 36, 24, 16, 12, 8, 4, 2};
    static final String[] CODEC = {"H.264 (AVC)", "H.265 (HEVC)"};
    static final String[] ORIENT = {"Landscape", "Portrait", "Auto (as screen is now)"};
    static final String[] INTERNAL = {"Off", "Internal sound (media + game)", "Game sound only"};
    static final String[] MICSRC = {"Mic (default)", "Unprocessed (best for loudspeaker pickup)", "Camcorder", "Voice call (try if mic is muted in Discord)", "Voice recognition (best during calls)"};
    static final String[] STORE = {"Internal storage (Movies/Robincord)", "SD card / external memory"};

    int res, fps, br, codec, orient, internal, micSrc, store, countdown;
    boolean mic, expVoice;
    int gainMic, gainInt;
    String sdTree;

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("rc", Context.MODE_PRIVATE); }

    static Cfg load(Context c) {
        SharedPreferences p = sp(c);
        Cfg g = new Cfg();
        g.res = p.getInt("res", 0);
        g.fps = p.getInt("fps", 1);
        g.br = p.getInt("br", 3);
        g.codec = p.getInt("codec", 0);
        g.orient = p.getInt("orient", 0);
        g.internal = p.getInt("internal", 1);
        g.mic = p.getBoolean("mic", true);
        g.micSrc = p.getInt("micsrc", 0);
        g.store = p.getInt("store", 0);
        g.countdown = p.getInt("countdown", 3);
        g.expVoice = p.getBoolean("exp", false);
        g.gainMic = p.getInt("gmic", 100);
        g.gainInt = p.getInt("gint", 100);
        g.sdTree = p.getString("sdtree", null);
        return g;
    }

    boolean audio() { return mic || internal != 0; }

    // ---- recent recordings ----
    static JSONArray recent(Context c) {
        try { return new JSONArray(sp(c).getString("recent", "[]")); } catch (Throwable t) { return new JSONArray(); }
    }

    static void addRecent(Context c, String uri, String name, long size) {
        try {
            JSONArray old = recent(c), n = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("uri", uri); o.put("name", name); o.put("size", size); o.put("ts", System.currentTimeMillis());
            n.put(o);
            for (int i = 0; i < old.length() && n.length() < 15; i++) n.put(old.get(i));
            sp(c).edit().putString("recent", n.toString()).apply();
        } catch (Throwable ignore) { }
    }

    static void removeRecent(Context c, String uri) {
        try {
            JSONArray old = recent(c), n = new JSONArray();
            for (int i = 0; i < old.length(); i++) if (!old.getJSONObject(i).getString("uri").equals(uri)) n.put(old.get(i));
            sp(c).edit().putString("recent", n.toString()).apply();
        } catch (Throwable ignore) { }
    }
}
