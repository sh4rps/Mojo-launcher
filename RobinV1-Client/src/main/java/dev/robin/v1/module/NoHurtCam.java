package dev.robin.v1.module;

import dev.robin.v1.Category;
import dev.robin.v1.Module;
import dev.robin.v1.Util;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** The damage camera shake is driven by hurtTime; it is zeroed only for the duration of the frame. */
public class NoHurtCam extends Module {
    private int saved;

    public NoHurtCam() {
        super("NoHurtCam", "Disables camera shake when hit", Category.VISUALS);
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent e) {
        if (!Util.inGame()) {
            return;
        }
        if (e.phase == TickEvent.Phase.START) {
            saved = mc.thePlayer.hurtTime;
            mc.thePlayer.hurtTime = 0;
        } else {
            mc.thePlayer.hurtTime = saved;
        }
    }
}
