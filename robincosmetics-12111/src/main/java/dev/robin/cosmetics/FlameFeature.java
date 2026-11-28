package dev.robin.cosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerSkinType;

/** Third-person flames: runs after the player model is posed, so the flames follow every arm movement. */
public class FlameFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    public FlameFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> ctx) { super(ctx); }

    @Override
    public void render(MatrixStack ms, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float yaw, float pitch) {
        try {
            var mc = MinecraftClient.getInstance();
            if (mc.player == null || state.id != mc.player.getId() || state.invisible) return;
            boolean slim = state.skinTextures != null && state.skinTextures.model() == PlayerSkinType.SLIM;
            PlayerEntityModel m = getContextModel();
            FlameFx.draw(ms, queue, m.rightArm, true, slim);
            FlameFx.draw(ms, queue, m.leftArm, false, slim);
        } catch (Throwable ignored) { }
    }
}
