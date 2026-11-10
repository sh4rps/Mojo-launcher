package com.robin.rustlod;

import net.fabricmc.api.ModInitializer;

public class RustLod implements ModInitializer {
    @Override
    public void onInitialize() {
        Cfg.load();
        LodServer.init();
    }
}
