package com.robinui.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public final class Prefs {
    static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences("robinui", 0); }
    public static boolean b(Context c, String k, boolean d) { return sp(c).getBoolean(k, d); }
    public static void b(Context c, String k, boolean v) { sp(c).edit().putBoolean(k, v).apply(); }
    public static int i(Context c, String k, int d) { return sp(c).getInt(k, d); }
    public static void i(Context c, String k, int v) { sp(c).edit().putInt(k, v).apply(); }
    public static String s(Context c, String k, String d) { return sp(c).getString(k, d); }
    public static void s(Context c, String k, String v) { sp(c).edit().putString(k, v).apply(); }
    public static Set<String> set(Context c, String k) { return new HashSet<>(sp(c).getStringSet(k, new HashSet<String>())); }
    public static void set(Context c, String k, Set<String> v) { sp(c).edit().putStringSet(k, v).apply(); }
}
