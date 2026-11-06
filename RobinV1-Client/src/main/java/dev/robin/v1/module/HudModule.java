package dev.robin.v1.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dev.robin.v1.Category;
import dev.robin.v1.Cps;
import dev.robin.v1.Module;
import dev.robin.v1.ModuleManager;
import dev.robin.v1.Setting;
import dev.robin.v1.Util;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class HudModule extends Module {
    private final Setting modules = add(Setting.bool("Module list", true));
    private final Setting fps = add(Setting.bool("FPS", true));
    private final Setting cps = add(Setting.bool("CPS", true));
    private final Setting coords = add(Setting.bool("Coordinates", true));
    private final Setting target = add(Setting.bool("Target info", true));
    private final Setting scale = add(Setting.num("Scale %", 100, 50, 200, 10));
    private final Setting xOff = add(Setting.num("Position X", 4, 0, 400, 2));
    private final Setting yOff = add(Setting.num("Position Y", 4, 0, 300, 2));

    public HudModule() {
        super("HUD", "Info overlay: FPS, CPS, coordinates, target, modules", Category.SETTINGS);
        setEnabled(true);
    }

    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (e.button == 0 && e.buttonstate) {
            Cps.click();
        }
    }

    @SubscribeEvent
    public void onText(RenderGameOverlayEvent.Text e) {
        if (!Util.inGame() || mc.gameSettings.showDebugInfo) {
            return;
        }
        FontRenderer f = mc.fontRendererObj;
        ScaledResolution sr = new ScaledResolution(mc);
        int accent = ModuleManager.get(GuiModule.class).accent();
        float s = (float) (scale.num() / 100.0);

        GlStateManager.pushMatrix();
        GlStateManager.scale(s, s, 1f);
        float x = (float) (xOff.num() / s);
        float y = (float) (yOff.num() / s);
        f.drawStringWithShadow("ROBIN V1", x, y, accent);
        y += 11;
        if (fps.on()) {
            f.drawStringWithShadow("FPS: " + net.minecraft.client.Minecraft.getDebugFPS(), x, y, 0xFFFFFFFF);
            y += 10;
        }
        if (cps.on()) {
            f.drawStringWithShadow("CPS: " + Cps.get(), x, y, 0xFFFFFFFF);
            y += 10;
        }
        if (coords.on()) {
            f.drawStringWithShadow(String.format("XYZ: %.1f / %.1f / %.1f", mc.thePlayer.posX, mc.thePlayer.posY,
                    mc.thePlayer.posZ), x, y, 0xFFFFFFFF);
        }
        if (modules.on()) {
            List<String> lines = new ArrayList<String>();
            for (Module m : ModuleManager.all()) {
                if (m.isEnabled() && m.category != Category.SETTINGS) {
                    String info = m.hudInfo();
                    lines.add(info == null ? m.name : m.name + " §7" + info);
                }
            }
            final FontRenderer ff = f;
            Collections.sort(lines, new Comparator<String>() {
                public int compare(String a, String b) {
                    return ff.getStringWidth(b) - ff.getStringWidth(a);
                }
            });
            float ry = 4 / s;
            for (String line : lines) {
                float rx = sr.getScaledWidth() / s - f.getStringWidth(line) - 4 / s;
                f.drawStringWithShadow(line, rx, ry, accent);
                ry += 10;
            }
        }
        if (target.on()) {
            EntityPlayer t = AimAssist.target;
            if (t == null && mc.objectMouseOver != null
                    && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                    && mc.objectMouseOver.entityHit instanceof EntityPlayer) {
                t = (EntityPlayer) mc.objectMouseOver.entityHit;
            }
            if (t != null) {
                String txt = String.format("%s  %.1f/%.1f HP  %.1fm", t.getName(), t.getHealth(), t.getMaxHealth(),
                        mc.thePlayer.getDistanceToEntity(t));
                f.drawStringWithShadow(txt, sr.getScaledWidth() / 2f / s - f.getStringWidth(txt) / 2f,
                        sr.getScaledHeight() / 2f / s + 18 / s, accent);
            }
        }
        GlStateManager.popMatrix();
    }
}
