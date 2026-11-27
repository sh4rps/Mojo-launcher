package dev.robin.totemtweak.mixin;

import dev.robin.totemtweak.Cfg;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldTotemMixin {
    @Unique private boolean tt$pushed;

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void tt$begin(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swing, ItemStack stack,
                          float equip, MatrixStack ms, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        tt$pushed = false;
        if (stack == null || !stack.isOf(Items.TOTEM_OF_UNDYING)) return;
        int h = hand == Hand.MAIN_HAND ? 0 : 1;
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == Arm.RIGHT ? 1f : -1f;
        tt$pushed = true;
        ms.push();
        ms.translate(Cfg.x[h] * side, Cfg.y[h], Cfg.z[h]);
        float k = Cfg.size[h];
        // scale around where the hand holds the item, so it grows in place instead of flying to the screen centre
        ms.translate(0.64f * side, -0.6f, -0.72f);
        ms.scale(k, k, k);
        ms.translate(-0.64f * side, 0.6f, 0.72f);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void tt$end(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swing, ItemStack stack,
                        float equip, MatrixStack ms, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (tt$pushed) { ms.pop(); tt$pushed = false; }
    }
}
