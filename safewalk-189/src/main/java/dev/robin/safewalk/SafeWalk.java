package dev.robin.safewalk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/** V toggles. At a block edge you sneak automatically and a block is placed so the floor keeps going. */
@Mod(modid = "safewalk", name = "Robin SafeWalk", version = "1.0.0", acceptedMinecraftVersions = "[1.8.9]", clientSideOnly = true)
public class SafeWalk {
    private final Minecraft mc = Minecraft.getMinecraft();
    private KeyBinding toggleKey;
    private boolean enabled = false;
    private boolean forcing = false;
    private int placeCooldown = 0;

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        toggleKey = new KeyBinding("Toggle SafeWalk", Keyboard.KEY_V, "Robin SafeWalk");
        ClientRegistry.registerKeyBinding(toggleKey);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.START) return;
        EntityPlayerSP p = mc.thePlayer;
        if (toggleKey.isPressed() && p != null) {
            enabled = !enabled;
            p.addChatMessage(new ChatComponentText("§aSafeWalk " + (enabled ? "ON" : "OFF")));
            if (!enabled) release();
        }
        if (p == null || mc.theWorld == null || mc.currentScreen != null) { forcing = false; return; }
        if (placeCooldown > 0) placeCooldown--;
        if (!enabled) return;

        int sneakCode = mc.gameSettings.keyBindSneak.getKeyCode();
        boolean edge = p.onGround && !p.capabilities.isFlying && atEdge(p);
        if (edge) {
            KeyBinding.setKeyBindState(sneakCode, true);
            forcing = true;
            tryPlace(p);
        } else if (forcing) {
            release();
        }
    }

    /** Give the sneak key back to the real keyboard state. */
    private void release() {
        int code = mc.gameSettings.keyBindSneak.getKeyCode();
        KeyBinding.setKeyBindState(code, code > 0 && Keyboard.isKeyDown(code));
        forcing = false;
    }

    /** True when the next step would leave the player with no support under the feet (same idea as vanilla sneaking). */
    private boolean atEdge(EntityPlayerSP p) {
        AxisAlignedBB bb = p.getEntityBoundingBox().offset(p.motionX * 2.0, -0.6, p.motionZ * 2.0);
        return mc.theWorld.getCollidingBoundingBoxes(p, bb).isEmpty();
    }

    /** Extend the floor: place the held block on the side of the block under you, in the direction you are moving. */
    private void tryPlace(EntityPlayerSP p) {
        if (placeCooldown > 0) return;
        ItemStack held = p.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) return;
        double mx = p.motionX, mz = p.motionZ;
        if (Math.abs(mx) + Math.abs(mz) < 0.005) return;
        EnumFacing f = Math.abs(mx) > Math.abs(mz) ? (mx > 0 ? EnumFacing.EAST : EnumFacing.WEST) : (mz > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH);
        BlockPos support = new BlockPos(p.posX, p.posY - 1.0, p.posZ);
        if (mc.theWorld.getBlockState(support).getBlock().getMaterial().isReplaceable()) return;
        if (!mc.theWorld.isAirBlock(support.offset(f))) return;
        Vec3 hit = new Vec3(support.getX() + 0.5 + f.getFrontOffsetX() * 0.5, support.getY() + 0.5, support.getZ() + 0.5 + f.getFrontOffsetZ() * 0.5);
        if (mc.playerController.onPlayerRightClick(p, mc.theWorld, held, support, f, hit)) {
            p.swingItem();
            placeCooldown = 2;
        }
    }
}
