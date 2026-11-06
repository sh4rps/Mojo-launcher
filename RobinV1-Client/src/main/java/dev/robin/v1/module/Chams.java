package dev.robin.v1.module;

import org.lwjgl.opengl.GL11;

import dev.robin.v1.Category;
import dev.robin.v1.Module;
import dev.robin.v1.Setting;
import dev.robin.v1.Util;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class Chams extends Module {
    private final Setting enemyR = add(Setting.num("Enemy red", 255, 0, 255, 1));
    private final Setting enemyG = add(Setting.num("Enemy green", 60, 0, 255, 1));
    private final Setting enemyB = add(Setting.num("Enemy blue", 60, 0, 255, 1));
    private final Setting teamR = add(Setting.num("Team red", 60, 0, 255, 1));
    private final Setting teamG = add(Setting.num("Team green", 255, 0, 255, 1));
    private final Setting teamB = add(Setting.num("Team blue", 120, 0, 255, 1));
    private final Setting alpha = add(Setting.num("Transparency %", 85, 20, 100, 5));

    public Chams() {
        super("Chams", "See players through walls", Category.VISUALS);
    }

    @SubscribeEvent
    public void pre(RenderPlayerEvent.Pre e) {
        if (e.entityPlayer == mc.thePlayer) {
            return;
        }
        boolean team = Util.isTeammate(e.entityPlayer);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(1.0f, -1000000.0f);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        float a = (float) (alpha.num() / 100.0);
        if (team) {
            GlStateManager.color((float) teamR.num() / 255f, (float) teamG.num() / 255f, (float) teamB.num() / 255f, a);
        } else {
            GlStateManager.color((float) enemyR.num() / 255f, (float) enemyG.num() / 255f, (float) enemyB.num() / 255f,
                    a);
        }
    }

    @SubscribeEvent
    public void post(RenderPlayerEvent.Post e) {
        if (e.entityPlayer == mc.thePlayer) {
            return;
        }
        GL11.glPolygonOffset(1.0f, 1000000.0f);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
