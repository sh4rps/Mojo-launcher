package dev.robin.lagguard.mixin;

import dev.robin.lagguard.LagGuard;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderManager.class)
public abstract class BlockEntityRenderManagerMixin {
    /** No render state = the block entity is simply not drawn this frame (the world data is untouched). */
    @Inject(method = "getRenderState", at = @At("HEAD"), cancellable = true)
    private void lagguard$distance(BlockEntity be, float tickProgress, @Coerce Object crumbling, CallbackInfoReturnable<BlockEntityRenderState> cir) {
        if (LagGuard.blockEntityTooFar(be)) cir.setReturnValue(null);
    }
}
