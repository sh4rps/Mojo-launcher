package com.rclient.mixin;

import com.rclient.modules.MoveVisualMods;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public class CameraMixin {
    /** Overrides the camera rotation for Freelook / Snaplook without touching the player's rotation. */
    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void rclient$rotation(Args args) {
        if (MoveVisualMods.freelookActive) {
            args.set(0, MoveVisualMods.flYaw);
            args.set(1, MoveVisualMods.flPitch);
        } else if (MoveVisualMods.snaplookActive) {
            args.set(0, args.<Float>get(0) + 180f);
        }
    }
}
