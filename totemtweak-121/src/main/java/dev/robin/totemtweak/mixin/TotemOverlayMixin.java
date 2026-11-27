package dev.robin.totemtweak.mixin;

import dev.robin.totemtweak.Cfg;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public class TotemOverlayMixin {
    /** Totem position: the original centre-of-screen translate, plus the user's offset (slider units = 5% of the screen). */
    @Redirect(method = "renderFloatingItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"))
    private void tt$move(MatrixStack ms, float x, float y, float z) {
        var w = MinecraftClient.getInstance().getWindow();
        ms.translate(x + Cfg.x * w.getScaledWidth() * 0.05f, y - Cfg.y * w.getScaledHeight() * 0.05f, z);
    }

    /** Size; "forward/back" is a zoom here because this version draws the totem without perspective. */
    @Redirect(method = "renderFloatingItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V"))
    private void tt$size(MatrixStack ms, float a, float b, float c) {
        float k = Cfg.size * (float) Math.pow(1.12, Cfg.z);
        ms.scale(a * k, b * k, c * k);
    }
}
