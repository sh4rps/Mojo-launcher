package dev.robin.frameupscale.mixin;

import dev.robin.frameupscale.FrameUpscale;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The game renders into a framebuffer of this (smaller) size; the window itself keeps its real size. */
@Mixin(Window.class)
public abstract class WindowMixin {
    @Inject(method = "getFramebufferWidth", at = @At("RETURN"), cancellable = true)
    private void frameupscale$w(CallbackInfoReturnable<Integer> cir) {
        float s = FrameUpscale.scale();
        if (s < 0.999f) cir.setReturnValue(Math.max(1, Math.round(cir.getReturnValue() * s)));
    }

    @Inject(method = "getFramebufferHeight", at = @At("RETURN"), cancellable = true)
    private void frameupscale$h(CallbackInfoReturnable<Integer> cir) {
        float s = FrameUpscale.scale();
        if (s < 0.999f) cir.setReturnValue(Math.max(1, Math.round(cir.getReturnValue() * s)));
    }
}
