package dev.robin.v1;

import org.lwjgl.input.Keyboard;

import dev.robin.v1.gui.RobinGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;

@Mod(modid = RobinV1.MODID, name = "RobinV1", version = "1.0.0", clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]")
public class RobinV1 {
    public static final String MODID = "robinv1";
    private static KeyBinding openGui;

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        ModuleManager.init();
        Config.load();
        openGui = new KeyBinding("Open RobinV1 GUI", Keyboard.KEY_RSHIFT, "RobinV1");
        ClientRegistry.registerKeyBinding(openGui);
        FMLCommonHandler.instance().bus().register(this);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                Config.save();
            }
        });
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (openGui.isPressed() && Minecraft.getMinecraft().currentScreen == null) {
            Minecraft.getMinecraft().displayGuiScreen(new RobinGui());
        }
    }
}
