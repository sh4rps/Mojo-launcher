package com.rusic.app;

import org.json.JSONObject;

public class Track {
    public String title, artist, uri;
    public long dur;
    public boolean downloaded;

    public Track(String title, String artist, String uri, long dur) {
        this.title = title; this.artist = artist; this.uri = uri; this.dur = dur;
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("t", title); o.put("a", artist); o.put("u", uri); o.put("d", dur);
            return o;
        } catch (Exception e) { return new JSONObject(); }
    }

    public static Track fromJson(JSONObject o) {
        return new Track(o.optString("t"), o.optString("a"), o.optString("u"), o.optLong("d"));
    }

    public static String fmt(long ms) {
        long s = ms / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60);
    }
}
