package dev.robin.v1.module;

import org.lwjgl.input.Mouse;

import dev.robin.v1.Category;
import dev.robin.v1.Module;
import dev.robin.v1.Setting;
import dev.robin.v1.Util;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class AimAssist extends Module {
    public static EntityPlayer target;

    private final Setting horizontal = add(Setting.num("Horizontal speed", 35, 1, 100, 1));
    private final Setting vertical = add(Setting.num("Vertical speed", 20, 0, 100, 1));
    private final Setting range = add(Setting.num("Range", 5, 1, 8, 0.5));
    private final Setting fov = add(Setting.num("FOV", 100, 10, 360, 5));
    private final Setting priority = add(Setting.mode("Priority", 0, "Distance", "Health", "Angle"));
    private final Setting clickOnly = add(Setting.bool("Only while clicking", true));
    private long last = System.nanoTime();

    public AimAssist() {
        super("AimAssist", "Smoothly aims at nearby enemy players", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        target = null;
    }

    @Override
    public String hudInfo() {
        return target != null ? target.getName() : null;
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) {
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min((now - last) / 16_666_667f, 3f); // frame-rate independent
        last = now;
        target = null;
        if (!Util.inGame() || mc.currentScreen != null) {
            return;
        }
        EntityPlayer t = pick();
        target = t;
        if (t == null || (clickOnly.on() && !Mouse.isButtonDown(0))) {
            return;
        }
        EntityPlayerSP me = mc.thePlayer;
        float[] r = Util.rotationsTo(t.posX, t.posY + t.height * 0.75, t.posZ);
        float dyaw = Util.wrap(r[0] - me.rotationYaw);
        float dpitch = r[1] - me.rotationPitch;
        float sy = (float) (dyaw * horizontal.num() / 100.0 * 0.3 * dt);
        float sp = (float) (dpitch * vertical.num() / 100.0 * 0.3 * dt);
        sy = Math.abs(sy) > Math.abs(dyaw) ? dyaw : sy;
        sp = Math.abs(sp) > Math.abs(dpitch) ? dpitch : sp;
        me.rotationYaw += sy;
        me.prevRotationYaw += sy;
        float np = MathHelper.clamp_float(me.rotationPitch + sp, -90f, 90f);
        me.prevRotationPitch += np - me.rotationPitch;
        me.rotationPitch = np;
    }

    private EntityPlayer pick() {
        EntityPlayerSP me = mc.thePlayer;
        EntityPlayer best = null;
        double bestScore = Double.MAX_VALUE;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (!Util.isValidEnemy(p)) {
                continue;
            }
            double dist = me.getDistanceToEntity(p);
            if (dist > range.num()) {
                continue;
            }
            float[] r = Util.rotationsTo(p.posX, p.posY + p.height * 0.75, p.posZ);
            float yawDiff = Math.abs(Util.wrap(r[0] - me.rotationYaw));
            if (yawDiff > fov.num() / 2.0) {
                continue;
            }
            double score;
            switch (priority.idx()) {
                case 1:
                    score = p.getHealth();
                    break;
                case 2:
                    score = yawDiff;
                    break;
                default:
                    score = dist;
            }
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }
}
