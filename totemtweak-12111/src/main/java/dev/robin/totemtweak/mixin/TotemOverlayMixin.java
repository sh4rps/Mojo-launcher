package dev.robin.totemtweak.mixin;

import dev.robin.totemtweak.Cfg;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class TotemOverlayMixin {
    @Inject(method = "renderFloatingItem", at = @At("HEAD"))
    private void tt$begin(MatrixStack ms, float tickDelta, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        ms.push();
        ms.translate(Cfg.x, Cfg.y, Cfg.z);
    }

    @Inject(method = "renderFloatingItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V", shift = At.Shift.AFTER))
    private void tt$scale(MatrixStack ms, float tickDelta, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        ms.scale(Cfg.size, Cfg.size, Cfg.size);
    }

    @Inject(method = "renderFloatingItem", at = @At("RETURN"))
    private void tt$end(MatrixStack ms, float tickDelta, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        ms.pop();
    }
}
