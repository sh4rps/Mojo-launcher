package dev.robin.aimassist.mixin;

import dev.robin.aimassist.AimAssistClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityLookMixin {
    /** Real mouse movement of the local player: lets the assist release on a strong flick. */
    @Inject(method = "changeLookDirection", at = @At("HEAD"))
    private void aimassist$onLook(double dx, double dy, CallbackInfo ci) {
        if ((Object) this == MinecraftClient.getInstance().player && !AimAssistClient.internal)
            AimAssistClient.onUserLook(dx * 0.15, dy * 0.15);
    }
}
