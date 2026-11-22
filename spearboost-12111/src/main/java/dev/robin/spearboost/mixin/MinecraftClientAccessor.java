package dev.robin.spearboost.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Invoker("doAttack") boolean spearboost$doAttack();
    @Accessor("attackCooldown") void spearboost$setAttackCooldown(int v);
}
