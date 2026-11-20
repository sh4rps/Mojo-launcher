package com.robin.mods;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Store {
    public static class Inst { public String name, mc, loader, tree; }

    public static List<Inst> load(Context c) {
        List<Inst> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences("s", 0).getString("inst", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Inst x = new Inst();
                x.name = o.getString("name"); x.mc = o.getString("mc"); x.loader = o.getString("loader"); x.tree = o.getString("tree");
                l.add(x);
            }
        } catch (Exception ignored) { }
        return l;
    }

    public static void save(Context c, List<Inst> l) {
        JSONArray a = new JSONArray();
        try {
            for (Inst x : l) a.put(new JSONObject().put("name", x.name).put("mc", x.mc).put("loader", x.loader).put("tree", x.tree));
        } catch (Exception ignored) { }
        c.getSharedPreferences("s", 0).edit().putString("inst", a.toString()).apply();
    }
}
