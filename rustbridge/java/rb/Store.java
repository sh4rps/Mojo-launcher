package rb;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import java.io.File;

/** Puts RustBridge's data in /sdcard/games/RustBridge (needs All files access); safe fallback otherwise. */
public final class Store {
    private static File chosen;
    private static boolean fallback;
    private static boolean asked;

    static boolean granted(Context c) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return c.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED;
    }

    /** The launcher's base data folder. */
    public static synchronized File dir(Context c) {
        if (chosen != null) return chosen;
        try {
            if (granted(c)) {
                File g = new File(Environment.getExternalStorageDirectory(), "games/RustBridge");
                g.mkdirs();
                if (g.isDirectory() && g.canWrite()) {
                    chosen = g;
                    fallback = false;
                    return g;
                }
            }
        } catch (Throwable t) { }
        fallback = true;
        File e = c.getExternalFilesDir(null);
        chosen = e != null ? e : new File(c.getFilesDir(), "RustBridge");
        return chosen;
    }

    public static void init(Application app) {
        dir(app);
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity a, Bundle b) { }
            public void onActivityStarted(Activity a) { }
            public void onActivityResumed(final Activity a) {
                try {
                    if (!fallback) return;
                    if (granted(a)) {
                        Toast.makeText(a, "Storage allowed. Restarting RustBridge...", Toast.LENGTH_LONG).show();
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            public void run() { android.os.Process.killProcess(android.os.Process.myPid()); }
                        }, 1200);
                        return;
                    }
                    if (asked) return;
                    asked = true;
                    Toast.makeText(a, "Allow 'All files access' so RustBridge can save in the games folder, then reopen the app.", Toast.LENGTH_LONG).show();
                    if (Build.VERSION.SDK_INT >= 30) {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + a.getPackageName())));
                        } catch (Throwable t) {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        }
                    } else {
                        a.requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_EXTERNAL_STORAGE"}, 77);
                    }
                } catch (Throwable t) { }
            }
            public void onActivityPaused(Activity a) { }
            public void onActivityStopped(Activity a) { }
            public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            public void onActivityDestroyed(Activity a) { }
        });
    }
}
