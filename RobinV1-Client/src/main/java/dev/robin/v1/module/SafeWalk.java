package dev.robin.v1.module;

import dev.robin.v1.Category;
import dev.robin.v1.Module;
import dev.robin.v1.Util;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Stops you from stepping off an edge. After each client tick, if the player just walked off the
 * ground (without jumping) the move is undone and the player stays on the block.
 */
public class SafeWalk extends Module {
    private boolean wasOnGround;
    private double lx, ly, lz;

    public SafeWalk() {
        super("SafeWalk", "Never walk off block edges", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        wasOnGround = false;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Util.inGame()) {
            return;
        }
        EntityPlayerSP p = mc.thePlayer;
        if (p.capabilities.isFlying || p.isInWater() || p.isInLava() || p.isOnLadder()) {
            wasOnGround = false;
            return;
        }
        boolean jumping = mc.gameSettings.keyBindJump.isKeyDown() || p.motionY > 0;
        if (wasOnGround && !p.onGround && !jumping && p.motionY < 0) {
            p.setPosition(lx, ly, lz);
            p.motionX = 0;
            p.motionZ = 0;
            p.motionY = 0;
            p.onGround = true;
            p.fallDistance = 0;
        }
        wasOnGround = p.onGround;
        if (p.onGround) {
            lx = p.posX;
            ly = p.posY;
            lz = p.posZ;
        }
    }
}
