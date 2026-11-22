package dev.robin.frameupscale.mixin;

import dev.robin.frameupscale.FrameUpscale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlCommandEncoder;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** The final "present to screen" copy is 1:1; stretch it to the real window size with linear filtering. */
@Mixin(GlCommandEncoder.class)
public abstract class CommandEncoderMixin {
    @ModifyArgs(method = "presentTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gl/BufferManager;setupBlitFramebuffer(IIIIIIIIIIII)V"))
    private void frameupscale$stretch(Args args) {
        if (FrameUpscale.scale() >= 0.999f) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        int[] w = new int[1], h = new int[1];
        GLFW.glfwGetFramebufferSize(mc.getWindow().getHandle(), w, h);
        if (w[0] <= 0 || h[0] <= 0) return;
        args.set(8, w[0]);
        args.set(9, h[0]);
        args.set(11, 9729); // GL_LINEAR
    }
}
