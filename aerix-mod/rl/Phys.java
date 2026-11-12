package rl;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

/** Separate "physical mouse sensitivity" multiplier (quick settings), applied to captured-pointer mouse movement. */
public class Phys {
    static final String KEY = "physMouseSens";
    static volatile float f = 1f;

    public static void load(Context c) {
        try {
            SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(c);
            f = clamp(p.getInt(KEY, 100)) / 100f;
        } catch (Throwable t) { }
    }

    public static float scale(float v) { return v * f; }

    static int clamp(int v) { return Math.max(10, Math.min(500, v)); }

    private static int id(View root, String name) {
        return root.getResources().getIdentifier(name, "id", root.getContext().getPackageName());
    }

    /** Wires the slider that dialog_quick_setting.xml contains. */
    public static void attach(View root) {
        try {
            final Context ctx = root.getContext();
            final SeekBar sb = (SeekBar) root.findViewById(id(root, "physMouse_seekbar"));
            final TextView pct = (TextView) root.findViewById(id(root, "physMouse_textView_percent"));
            if (sb == null) return;
            final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(ctx);
            sb.setMax(500);
            int cur = clamp(p.getInt(KEY, 100));
            sb.setProgress(cur);
            if (pct != null) pct.setText(cur + "%");
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int prog, boolean user) {
                    int v = clamp(prog);
                    f = v / 100f;
                    if (pct != null) pct.setText(v + "%");
                    p.edit().putInt(KEY, v).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar s) { }
                @Override public void onStopTrackingTouch(SeekBar s) { }
            });
        } catch (Throwable t) { }
    }
}
