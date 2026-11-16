package com.rclient.mixin;

import com.rclient.core.ModuleManager;
import com.rclient.modules.MoveVisualMods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
    /** While Freelook is held, mouse movement rotates the camera instead of the player. */
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void rclient$freelook(double dx, double dy, CallbackInfo ci) {
        if (!MoveVisualMods.freelookActive) return;
        if ((Object) this != MinecraftClient.getInstance().player) return;
        var fl = ModuleManager.of(MoveVisualMods.FreeLook.class);
        float sy = (fl != null && fl.invert()) ? -1f : 1f;
        MoveVisualMods.flYaw += (float) dx * 0.15f;
        MoveVisualMods.flPitch = Math.max(-90f, Math.min(90f, MoveVisualMods.flPitch + (float) dy * 0.15f * sy));
        ci.cancel();
    }
}
