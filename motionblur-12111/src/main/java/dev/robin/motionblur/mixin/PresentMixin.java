package dev.robin.motionblur.mixin;

import com.mojang.blaze3d.textures.GpuTextureView;
import dev.robin.motionblur.MotionBlurMod;
import net.minecraft.client.gl.GlCommandEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Last step of every frame: blend the finished frame with the previous ones, then let the game present it as usual. */
@Mixin(GlCommandEncoder.class)
public abstract class PresentMixin {
    @Inject(method = "presentTexture", at = @At("HEAD"))
    private void motionblur$blend(GpuTextureView view, CallbackInfo ci) {
        MotionBlurMod.onPresent(view);
    }
}
