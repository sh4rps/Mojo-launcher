package dev.robin.v1;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

public final class Util {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Util() {
    }

    public static boolean inGame() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    /** Players on the same scoreboard team as you (bedwars / skywars / duels teams). */
    public static boolean isTeammate(EntityPlayer p) {
        EntityPlayerSP me = mc.thePlayer;
        return p == me || me.isOnSameTeam(p);
    }

    public static boolean isValidEnemy(EntityPlayer p) {
        return p != null && p != mc.thePlayer && !p.isDead && p.getHealth() > 0 && !p.isInvisible()
                && !isTeammate(p);
    }

    /** Returns {yaw, pitch} needed to look at the world position. */
    public static float[] rotationsTo(double x, double y, double z) {
        EntityPlayerSP me = mc.thePlayer;
        double dx = x - me.posX;
        double dy = y - (me.posY + me.getEyeHeight());
        double dz = z - me.posZ;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        return new float[] { yaw, MathHelper.clamp_float(pitch, -90f, 90f) };
    }

    public static float wrap(float a) {
        return MathHelper.wrapAngleTo180_float(a);
    }
}
