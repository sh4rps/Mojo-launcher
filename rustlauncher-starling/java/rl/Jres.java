package rl;

import android.content.res.AssetManager;
import android.util.Log;
import java.io.InputStream;
import java.lang.reflect.Method;

/** Installs the Java runtimes that ship inside the APK (17, 21) into the Mojo runtime store, so no download is needed. */
public final class Jres {
    private static final String[][] BUNDLED = {
        {"components/jre-17", "Internal-17"},
        {"components/jre-21", "Internal-21"},
    };
    private static boolean done;

    private Jres() { }

    /** Blocking and idempotent; safe to call from several threads. */
    public static synchronized void ensure(AssetManager am) {
        if (done) return;
        try {
            Class<?> c = Class.forName("git.artdeell.mojo.multirt.MultiRTUtils");
            Method read = c.getMethod("readInternalRuntimeVersion", String.class);
            Method inst = c.getMethod("installRuntimeNamedBinpack",
                    InputStream.class, InputStream.class, String.class, String.class);
            for (String[] b : BUNDLED) {
                try {
                    if (read.invoke(null, b[1]) != null) continue;
                    String ver = "bundled";
                    try (InputStream v = am.open(b[0] + "/version")) {
                        byte[] buf = new byte[64];
                        int n = v.read(buf);
                        if (n > 0) ver = new String(buf, 0, n).trim();
                    } catch (Throwable ignore) { }
                    InputStream uni = am.open(b[0] + "/universal.tar.xz");
                    InputStream bin = am.open(b[0] + "/bin-arm64.tar.xz");
                    inst.invoke(null, uni, bin, b[1], ver);
                    Log.i("Jres", "installed " + b[1]);
                } catch (Throwable t) {
                    Log.e("Jres", "failed " + b[1], t);
                }
            }
            done = true;
        } catch (Throwable t) {
            Log.e("Jres", "ensure failed", t);
        }
    }

    public static void ensureAsync(final AssetManager am) {
        Thread t = new Thread(new Runnable() { public void run() { ensure(am); } }, "jres");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }
}
