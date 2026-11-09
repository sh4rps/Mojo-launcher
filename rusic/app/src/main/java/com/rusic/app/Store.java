package com.rusic.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Playlists + download metadata, saved on the device (works fully offline). */
public class Store {
    private final SharedPreferences sp;

    public Store(Context c) { sp = c.getSharedPreferences("rusic", 0); }

    public Map<String, List<Track>> playlists() {
        Map<String, List<Track>> m = new LinkedHashMap<>();
        try {
            JSONObject o = new JSONObject(sp.getString("playlists", "{}"));
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONArray a = o.getJSONArray(k);
                List<Track> l = new ArrayList<>();
                for (int i = 0; i < a.length(); i++) l.add(Track.fromJson(a.getJSONObject(i)));
                m.put(k, l);
            }
        } catch (Exception e) { }
        return m;
    }

    private void save(Map<String, List<Track>> m) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, List<Track>> e : m.entrySet()) {
                JSONArray a = new JSONArray();
                for (Track t : e.getValue()) a.put(t.toJson());
                o.put(e.getKey(), a);
            }
            sp.edit().putString("playlists", o.toString()).apply();
        } catch (Exception e) { }
    }

    public void create(String name) {
        Map<String, List<Track>> m = playlists();
        if (!m.containsKey(name)) { m.put(name, new ArrayList<Track>()); save(m); }
    }

    public void delete(String name) { Map<String, List<Track>> m = playlists(); m.remove(name); save(m); }

    public void rename(String from, String to) {
        Map<String, List<Track>> m = playlists(), n = new LinkedHashMap<>();
        for (Map.Entry<String, List<Track>> e : m.entrySet()) n.put(e.getKey().equals(from) ? to : e.getKey(), e.getValue());
        save(n);
    }

    public void add(String name, Track t) {
        Map<String, List<Track>> m = playlists();
        List<Track> l = m.get(name);
        if (l == null) { l = new ArrayList<>(); m.put(name, l); }
        for (Track x : l) if (x.uri.equals(t.uri)) return;
        l.add(t);
        save(m);
    }

    public void remove(String name, Track t) {
        Map<String, List<Track>> m = playlists();
        List<Track> l = m.get(name);
        if (l == null) return;
        for (int i = 0; i < l.size(); i++) if (l.get(i).uri.equals(t.uri)) { l.remove(i); break; }
        save(m);
    }

    // downloaded-file metadata: file name -> "title\nartist"
    public void putMeta(String file, String title, String artist) { sp.edit().putString("m_" + file, title + "\n" + artist).apply(); }

    public String[] meta(String file) {
        String s = sp.getString("m_" + file, null);
        return s == null ? null : s.split("\n", 2);
    }

    public void dropMeta(String file) { sp.edit().remove("m_" + file).apply(); }
}
