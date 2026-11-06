package dev.robin.v1.module;

import dev.robin.v1.Category;
import dev.robin.v1.Cps;
import dev.robin.v1.Module;
import dev.robin.v1.Setting;
import dev.robin.v1.Util;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Detects incoming large fireballs only (arrows, pearls and other projectiles are ignored) and,
 * when one on a collision course comes within reach, turns to it and hits it back.
 */
public class AutoFireball extends Module {
    /** Text for the HUD, e.g. "Fireball incoming 4.2m" or "Clear". */
    public static String status = "Clear";

    private final Setting range = add(Setting.num("Detection range", 6, 3, 10, 0.5));
    private final Setting deflect = add(Setting.bool("Auto deflect", true));
    private final Setting rotate = add(Setting.bool("Face fireball", true));
    private long lastHit;

    public AutoFireball() {
        super("AutoFireball", "Detects incoming fireballs and reacts", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        status = "Clear";
    }

    @Override
    public String hudInfo() {
        return status;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        status = "Clear";
        if (!Util.inGame() || mc.currentScreen != null) {
            return;
        }
        EntityPlayerSP me = mc.thePlayer;
        EntityLargeFireball best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity en : mc.theWorld.loadedEntityList) {
            if (!(en instanceof EntityLargeFireball) || en.isDead) {
                continue;
            }
            double dx = me.posX - en.posX;
            double dy = (me.posY + me.height / 2.0) - en.posY;
            double dz = me.posZ - en.posZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > range.num()) {
                continue;
            }
            if (!incoming(en, dx, dy, dz, dist)) {
                continue;
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = (EntityLargeFireball) en;
            }
        }
        if (best == null) {
            return;
        }
        status = String.format("Fireball incoming %.1fm", bestDist);
        if (!deflect.on()) {
            return;
        }
        if (rotate.on()) {
            float[] r = Util.rotationsTo(best.posX, best.posY, best.posZ);
            me.rotationYaw += Util.wrap(r[0] - me.rotationYaw);
            me.rotationPitch = r[1];
        }
        long now = System.currentTimeMillis();
        if (bestDist <= 3.2 && now - lastHit > 120) {
            lastHit = now;
            me.swingItem();
            mc.playerController.attackEntity(me, best);
            Cps.click();
        }
    }

    /** True when the fireball moves towards the player and passes within ~2 blocks of them. */
    private static boolean incoming(Entity fb, double toX, double toY, double toZ, double dist) {
        double vx = fb.motionX, vy = fb.motionY, vz = fb.motionZ;
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (speed < 1.0E-4 || dist < 1.0E-4) {
            return false;
        }
        double dot = (vx * toX + vy * toY + vz * toZ) / (speed * dist);
        if (dot <= 0.35) {
            return false; // moving away or sideways
        }
        double along = (vx * toX + vy * toY + vz * toZ) / speed;
        double miss = Math.sqrt(Math.max(0.0, dist * dist - along * along));
        return miss < 2.0;
    }
}
