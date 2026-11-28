package dev.robin.cosmetics.mixin;

import dev.robin.cosmetics.FlameFeature;
import dev.robin.cosmetics.FlameFx;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerRendererMixin {
    @Unique private boolean rc$slim;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "<init>", at = @At("RETURN"))
    private void rc$init(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
        rc$slim = slim;
        ((LivingRendererInvoker) (Object) this).rc$addFeature(new FlameFeature((FeatureRendererContext) (Object) this));
    }

    /** First person: the hand that is drawn in front of the camera. */
    @Inject(method = "renderArm", at = @At("RETURN"))
    private void rc$firstPerson(MatrixStack ms, OrderedRenderCommandQueue queue, int light, Identifier tex, ModelPart arm, boolean sleeve, CallbackInfo ci) {
        try {
            PlayerEntityModel m = (PlayerEntityModel) ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel();
            FlameFx.draw(ms, queue, arm, arm == m.rightArm, rc$slim);
        } catch (Throwable ignored) { }
    }
}
