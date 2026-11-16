package com.rclient.core;

import net.minecraft.client.MinecraftClient;
import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public final String id, name, desc;
    public final Category category;
    public final List<Setting> settings = new ArrayList<>();
    private boolean enabled;
    public int toggleKey = -1;

    protected Module(String id, String name, Category cat, String desc) {
        this.id = id; this.name = name; this.category = cat; this.desc = desc;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean on) {
        if (on == enabled) return;
        enabled = on;
        try { if (on) onEnable(MinecraftClient.getInstance()); else onDisable(MinecraftClient.getInstance()); }
        catch (Throwable t) { com.rclient.RClient.LOG.error("Module {} toggle failed", id, t); }
        Config.markDirty();
    }
    public void toggle() { setEnabled(!enabled); }
    /** Load enabled flag without running hooks twice for unchanged state. */
    public void loadEnabled(boolean on) { setEnabled(on); }

    public boolean isHud() { return false; }

    protected Setting add(Setting s) { settings.add(s); return s; }
    public Setting find(String sid) { for (Setting s : settings) if (s.id.equals(sid)) return s; return null; }

    public void onEnable(MinecraftClient mc) { }
    public void onDisable(MinecraftClient mc) { }
    /** Called every client tick while enabled and in a world. */
    public void onTick(MinecraftClient mc) { }
    /** Called every rendered HUD frame while enabled. */
    public void onFrame(MinecraftClient mc) { }
    /** Called on every tick regardless of enabled state (cheap trackers only). */
    public void alwaysTick(MinecraftClient mc) { }
    /** Called on world join. */
    public void onJoin(MinecraftClient mc) { }
}
