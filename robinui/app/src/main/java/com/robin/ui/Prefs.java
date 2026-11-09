package com.robin.ui;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

public final class Prefs {
    static SharedPreferences sp;
    static void init(Context c) { sp = c.getSharedPreferences("robinui", 0); }

    public static boolean b(String k, boolean d) { return sp.getBoolean(k, d); }
    public static void setB(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
    public static int i(String k, int d) { return sp.getInt(k, d); }
    public static void setI(String k, int v) { sp.edit().putInt(k, v).apply(); }

    public static boolean island() { return b("island", true); }
    public static boolean idlePill() { return b("idle", true); }
    public static boolean hz() { return b("hz", true); }
    public static boolean labels() { return b("labels", true); }
    public static boolean drawerMode() { return b("drawer_mode", false); }

    public static List<String> list(String k) {
        List<String> l = new ArrayList<>();
        String s = sp.getString(k, "");
        if (!s.isEmpty()) for (String x : s.split("\\|")) if (!x.isEmpty()) l.add(x);
        return l;
    }

    public static void list(String k, List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String x : l) { if (sb.length() > 0) sb.append('|'); sb.append(x); }
        sp.edit().putString(k, sb.toString()).apply();
    }

    public static void toggleIn(String k, String v) {
        List<String> l = list(k);
        if (!l.remove(v)) l.add(v);
        list(k, l);
    }
}
