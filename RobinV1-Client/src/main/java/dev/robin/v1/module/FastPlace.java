package dev.robin.v1.module;

import java.lang.reflect.Field;

import dev.robin.v1.Category;
import dev.robin.v1.Module;
import dev.robin.v1.Setting;
import dev.robin.v1.Util;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public class FastPlace extends Module {
    private final Setting delay = add(Setting.num("Delay (ticks)", 0, 0, 4, 1));
    private Field timer;

    public FastPlace() {
        super("FastPlace", "Place blocks faster", Category.PLAYER);
        try {
            timer = ReflectionHelper.findField(net.minecraft.client.Minecraft.class, "rightClickDelayTimer",
                    "field_71467_ac");
        } catch (Throwable t) {
            timer = null;
        }
    }

    @Override
    public String hudInfo() {
        return String.valueOf((int) delay.num());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START || timer == null || !Util.inGame()) {
            return;
        }
        try {
            if (timer.getInt(mc) > delay.num()) {
                timer.setInt(mc, (int) delay.num());
            }
        } catch (Throwable t) {
            timer = null;
        }
    }
}
