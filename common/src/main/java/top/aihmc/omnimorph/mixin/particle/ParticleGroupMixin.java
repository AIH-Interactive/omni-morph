package top.aihmc.omnimorph.mixin.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleEngine;
import top.aihmc.omnimorph.lua.api.particle.CustomParticle;
import org.figuramc.figura.ducks.ParticleEngineAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleGroup.class)
public class ParticleGroupMixin {
    @Shadow @Final protected ParticleEngine engine;

    @Inject(method = "add", at = @At("RETURN"))
    private void figura$releaseRejectedCustomParticle(Particle added, CallbackInfoReturnable<Boolean> cir) {
        // Native admission can reject at capacity or by reservoir sampling; accepted particles stay alive.
        if (!cir.getReturnValueZ() && added instanceof CustomParticle rejected) {
            rejected.remove();
            ((ParticleEngineAccessor) engine).figura$removeParticle(rejected);
        }
    }

    // This fixes a conflict with Optifine having slightly different args + it should be more stable in general, capturing Locals is bad practice
    @ModifyVariable(method = "tickParticles", at = @At(value = "INVOKE", target = "Ljava/util/Iterator;remove()V", ordinal = 0))
    private Particle tickParticleList(Particle particle) {
        ((ParticleEngineAccessor)Minecraft.getInstance().particleEngine).figura$removeParticle(particle);
        return particle;
    }
}
