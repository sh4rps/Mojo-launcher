package dev.robin.v1.module;

import dev.robin.v1.Category;
import dev.robin.v1.Module;
import dev.robin.v1.Util;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class FullBright extends Module {
    private float old = 1f;

    public FullBright() {
        super("FullBright", "Maximum brightness, no darkness", Category.VISUALS);
    }

    @Override
    protected void onEnable() {
        old = mc.gameSettings.gammaSetting;
        mc.gameSettings.gammaSetting = 100f;
    }

    @Override
    protected void onDisable() {
        mc.gameSettings.gammaSetting = old > 1f ? 1f : old;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END && mc.gameSettings.gammaSetting < 100f) {
            mc.gameSettings.gammaSetting = 100f;
        }
    }
}
