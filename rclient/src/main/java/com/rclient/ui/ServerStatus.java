package com.rclient.ui;

import com.rclient.core.Config;

import java.net.InetSocketAddress;
import java.net.Socket;

/** Real TCP reachability probe for the last joined server. One short-lived thread at a time. */
public final class ServerStatus {
    public static volatile String text = "No server joined yet";
    public static volatile int color = Ui.MUTED;
    private static volatile boolean busy;
    private static long last;

    private ServerStatus() { }

    public static void refresh(boolean force) {
        String addr = Config.lastServer;
        if (addr == null || addr.isEmpty()) { text = "No server joined yet"; color = Ui.MUTED; return; }
        long now = System.currentTimeMillis();
        if (busy || (!force && now - last < 30000)) return;
        busy = true; last = now;
        Thread t = new Thread(() -> {
            try {
                String host = addr; int port = 25565;
                int c = addr.lastIndexOf(':');
                if (c > 0 && addr.indexOf(':') == c) { host = addr.substring(0, c); try { port = Integer.parseInt(addr.substring(c + 1)); } catch (NumberFormatException ignored) { } }
                long t0 = System.nanoTime();
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(host, port), 2500);
                }
                long ms = (System.nanoTime() - t0) / 1_000_000;
                text = addr + ": online (" + ms + " ms)"; color = Ui.GOOD;
            } catch (Exception e) {
                text = addr + ": unreachable"; color = Ui.BAD;
            } finally { busy = false; }
        }, "rclient-ping");
        t.setDaemon(true);
        t.start();
    }
}
