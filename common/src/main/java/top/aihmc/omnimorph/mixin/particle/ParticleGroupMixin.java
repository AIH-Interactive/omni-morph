package top.aihmc.omnimorph.mixin.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleEngine;
import com.google.common.collect.EvictingQueue;
import top.aihmc.omnimorph.lua.api.particle.CustomParticle;
import top.aihmc.omnimorph.ducks.ParticleEngineAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Queue;

@Mixin(ParticleGroup.class)
public class ParticleGroupMixin {
    @Shadow @Final protected Queue<Particle> particles;
    @Shadow @Final protected ParticleEngine engine;

    @Inject(method = "add", at = @At("HEAD"))
    private void figura$releaseEvictedCustomParticle(Particle added, CallbackInfo ci) {
        if (particles instanceof EvictingQueue<?> queue && queue.remainingCapacity() == 0
                && particles.peek() instanceof CustomParticle evicted) {
            evicted.remove();
            ((ParticleEngineAccessor) engine).figura$removeParticle(evicted);
        }
    }

    // This fixes a conflict with Optifine having slightly different args + it should be more stable in general, capturing Locals is bad practice
    @ModifyVariable(method = "tickParticles", at = @At(value = "INVOKE", target = "Ljava/util/Iterator;remove()V", ordinal = 0))
    private Particle tickParticleList(Particle particle) {
        ((ParticleEngineAccessor)Minecraft.getInstance().particleEngine).figura$removeParticle(particle);
        return particle;
    }
}
