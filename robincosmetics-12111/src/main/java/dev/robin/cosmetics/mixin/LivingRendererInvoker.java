package dev.robin.cosmetics.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface LivingRendererInvoker {
    @Invoker("addFeature")
    boolean rc$addFeature(FeatureRenderer<?, ?> feature);
}
