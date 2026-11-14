package com.robin.togglesprint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/** Press O to toggle auto-sprint. While ON, you sprint whenever you walk forward. */
@Mod(modid = ToggleSprint.MODID, name = "Toggle Sprint", version = "1.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class ToggleSprint {
    public static final String MODID = "togglesprint";
    private KeyBinding key;
    private boolean on;

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        key = new KeyBinding("Toggle Sprint", Keyboard.KEY_O, "Toggle Sprint");
        ClientRegistry.registerKeyBinding(key);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        while (key.isPressed()) on = !on;
        EntityPlayerSP p = mc.thePlayer;
        if (!on || p == null || mc.currentScreen != null) return;
        if (p.movementInput.moveForward > 0 && !p.isSneaking() && !p.isUsingItem()
                && !p.isCollidedHorizontally && p.getFoodStats().getFoodLevel() > 6 && !p.isSprinting()) {
            p.setSprinting(true);
        }
    }

    @SubscribeEvent
    public void onHud(RenderGameOverlayEvent.Post ev) {
        if (!on || ev.type != RenderGameOverlayEvent.ElementType.TEXT) return;
        Minecraft mc = Minecraft.getMinecraft();
        mc.fontRendererObj.drawStringWithShadow("[Sprint: Toggled]", 4, 4, 0x55FF55);
    }
}
