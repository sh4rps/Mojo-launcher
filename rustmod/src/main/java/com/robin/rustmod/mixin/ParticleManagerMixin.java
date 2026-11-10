package com.robin.rustmod.mixin;

import com.robin.rustmod.Perf;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Caps how many particles may be spawned per tick, which stops explosion/TNT/arrow spam from tanking FPS. */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void rustmod$cap(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz,
                             CallbackInfoReturnable<Particle> cir) {
        int cap = Perf.particleCap;
        if (!Perf.on || cap <= 0) return;
        if (++Perf.particlesThisTick > cap) cir.setReturnValue(null);
    }
}
