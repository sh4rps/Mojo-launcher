package com.robin.rustmod.mixin;

import com.robin.rustmod.Perf;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hard distance cap for entity rendering (players, bosses and big mobs are never culled). */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void rustmod$cull(E entity, Frustum frustum, double x, double y, double z,
                                                 CallbackInfoReturnable<Boolean> cir) {
        int cap = Perf.entityCullBlocks;
        if (!Perf.on || cap <= 0) return;
        if (entity instanceof PlayerEntity || entity instanceof EnderDragonEntity || entity instanceof WitherEntity) return;
        if (entity.squaredDistanceTo(x, y, z) > (double) cap * cap) cir.setReturnValue(false);
    }
}
