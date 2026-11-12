package com.robin.setting;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences("hz", Context.MODE_PRIVATE); }
    /** 0 = don't touch */
    static int global(Context c) { return sp(c).getInt("global", 0); }
    static void global(Context c, int hz) { sp(c).edit().putInt("global", hz).apply(); }
    static int app(Context c, String pkg) { return sp(c).getInt("app." + pkg, 0); }
    static void app(Context c, String pkg, int hz) {
        SharedPreferences.Editor e = sp(c).edit();
        if (hz == 0) e.remove("app." + pkg); else e.putInt("app." + pkg, hz);
        e.apply();
    }
}
