package dev.robin.v1.module;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

import org.lwjgl.input.Mouse;

import dev.robin.v1.Category;
import dev.robin.v1.Cps;
import dev.robin.v1.Module;
import dev.robin.v1.Setting;
import dev.robin.v1.Util;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public class AutoClicker extends Module {
    private final Setting minCps = add(Setting.num("Min CPS", 8, 1, 30, 1));
    private final Setting maxCps = add(Setting.num("Max CPS", 12, 1, 30, 1));
    private final Setting left = add(Setting.bool("Left click", true));
    private final Setting right = add(Setting.bool("Right click", false));
    private final Random rnd = new Random();
    private long nextLeft, nextRight;
    private Method rightClickMouse;
    private Field rightDelay;

    public AutoClicker() {
        super("AutoClicker", "Clicks for you while a mouse button is held (max 30 CPS)", Category.COMBAT);
        try {
            rightClickMouse = ReflectionHelper.findMethod(net.minecraft.client.Minecraft.class, mc,
                    new String[] { "rightClickMouse", "func_147121_ag" });
            rightDelay = ReflectionHelper.findField(net.minecraft.client.Minecraft.class, "rightClickDelayTimer",
                    "field_71467_ac");
        } catch (Throwable t) {
            rightClickMouse = null;
        }
    }

    @Override
    public String hudInfo() {
        return (int) minCps.num() + "-" + (int) maxCps.num();
    }

    private long delay() {
        double lo = Math.min(minCps.num(), maxCps.num());
        double hi = Math.max(minCps.num(), maxCps.num());
        double cps = lo + rnd.nextDouble() * (hi - lo);
        cps = Math.min(30.0, Math.max(1.0, cps));
        double ms = 1000.0 / cps * (0.85 + rnd.nextDouble() * 0.3); // randomized interval
        return (long) (ms * 1_000_000L);
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !Util.inGame() || mc.currentScreen != null) {
            return;
        }
        long now = System.nanoTime();
        if (left.on() && Mouse.isButtonDown(0)) {
            if (now >= nextLeft) {
                nextLeft = now + delay();
                clickLeft();
            }
        } else {
            nextLeft = 0;
        }
        if (right.on() && Mouse.isButtonDown(1) && rightClickMouse != null) {
            if (now >= nextRight) {
                nextRight = now + delay();
                try {
                    rightDelay.setInt(mc, 0);
                    rightClickMouse.invoke(mc);
                } catch (Throwable t) {
                    rightClickMouse = null;
                }
            }
        } else {
            nextRight = 0;
        }
    }

    private void clickLeft() {
        if (mc.thePlayer.isUsingItem()) {
            return;
        }
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && hit.entityHit != null) {
            mc.thePlayer.swingItem();
            mc.playerController.attackEntity(mc.thePlayer, hit.entityHit);
            Cps.click();
        } else if (hit == null || hit.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) {
            mc.thePlayer.swingItem();
            Cps.click();
        }
        // when aiming at a block vanilla handles breaking while the button is held
    }
}
