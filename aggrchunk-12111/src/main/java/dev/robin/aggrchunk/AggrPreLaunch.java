package dev.robin.aggrchunk;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Vanilla keeps one core free for worker threads; this lets worldgen use every core. Must run before Minecraft's Util class loads. */
public class AggrPreLaunch implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        try {
            if (System.getProperty("max.bg.threads") == null)
                System.setProperty("max.bg.threads", Integer.toString(Math.max(2, Runtime.getRuntime().availableProcessors())));
        } catch (Throwable ignored) { }
    }
}
