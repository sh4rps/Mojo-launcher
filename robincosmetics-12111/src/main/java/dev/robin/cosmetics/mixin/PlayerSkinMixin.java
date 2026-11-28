package dev.robin.cosmetics.mixin;

import dev.robin.cosmetics.Capes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class PlayerSkinMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void rc$cape(CallbackInfoReturnable<SkinTextures> cir) {
        try {
            if ((Object) this != MinecraftClient.getInstance().player) return;
            AssetInfo.TextureAsset cape = Capes.asset();
            if (cape == null) return;
            SkinTextures s = cir.getReturnValue();
            cir.setReturnValue(new SkinTextures(s.body(), cape, cape, s.model(), s.secure()));
        } catch (Throwable ignored) { }
    }
}
