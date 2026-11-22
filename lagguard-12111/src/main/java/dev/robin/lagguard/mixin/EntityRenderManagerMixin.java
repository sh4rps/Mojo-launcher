package dev.robin.lagguard.mixin;

import dev.robin.lagguard.LagGuard;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderManager.class)
public abstract class EntityRenderManagerMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void lagguard$distance(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (LagGuard.tooFar(entity, x, y, z)) cir.setReturnValue(false);
    }
}
