package dev.robin.v1;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;

/**
 * Base class of every feature. A module only listens to Forge events while it is enabled,
 * so disabled modules cost nothing.
 */
public abstract class Module {
    protected static final Minecraft mc = Minecraft.getMinecraft();

    public final String name;
    public final String description;
    public final Category category;
    public final List<Setting> settings = new ArrayList<Setting>();
    public boolean alwaysOn;
    public float anim; // GUI switch animation 0..1
    private boolean enabled;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <T extends Setting> T add(T s) {
        settings.add(s);
        return s;
    }

    public boolean isEnabled() {
        return enabled || alwaysOn;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean value) {
        if (alwaysOn || enabled == value) {
            return;
        }
        enabled = value;
        if (value) {
            MinecraftForge.EVENT_BUS.register(this);
            FMLCommonHandler.instance().bus().register(this);
            onEnable();
        } else {
            MinecraftForge.EVENT_BUS.unregister(this);
            FMLCommonHandler.instance().bus().unregister(this);
            onDisable();
        }
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /** Text shown after the name in the HUD module list, or null. */
    public String hudInfo() {
        return null;
    }
}
