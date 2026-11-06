package dev.robin.v1;

import java.util.ArrayDeque;

/** Counts left clicks of the last second (real clicks and AutoClicker clicks). */
public final class Cps {
    private static final ArrayDeque<Long> clicks = new ArrayDeque<Long>();

    private Cps() {
    }

    public static void click() {
        clicks.addLast(System.currentTimeMillis());
        prune();
    }

    public static int get() {
        prune();
        return clicks.size();
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
            clicks.pollFirst();
        }
    }
}
