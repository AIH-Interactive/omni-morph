package org.figuramc.figura.mixin.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import org.figuramc.figura.utils.PlatformUtils;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SpriteSet;
import com.google.common.collect.EvictingQueue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import org.figuramc.figura.ducks.ParticleEngineAccessor;
import top.aihmc.omnimorph.lua.api.particle.CustomParticle;
import top.aihmc.omnimorph.lua.api.particle.CustomParticleRuntime;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin implements ParticleEngineAccessor {

    @Shadow @Nullable protected abstract <T extends ParticleOptions> Particle makeParticle(T parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ);

    @Shadow public abstract void add(Particle particle);

    @Shadow
    @Final
    private Map<ResourceLocation, SpriteSet> spriteSets;
    @Unique private final HashMap<Particle, UUID> particleMap = new HashMap<>();

    @Override @Intrinsic
    public <T extends ParticleOptions> Particle figura$makeParticle(T parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        return this.makeParticle(parameters, x, y, z, velocityX, velocityY, velocityZ);
    }

    @Override @Intrinsic
    public void figura$spawnParticle(Particle particle, UUID owner) {
        particleMap.put(particle, owner);
        this.add(particle);
    }

    @Override @Intrinsic
    public void figura$clearParticles(UUID owner) {
        Iterator<Map.Entry<Particle, UUID>> iterator = particleMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Particle, UUID> entry = iterator.next();
            if ((owner == null || entry.getValue().equals(owner))) {
                if (entry.getKey() != null)
                    entry.getKey().remove();
                iterator.remove();
            }
        }
    }

    @Override
    public void figura$removeParticle(Particle particle) {
        particleMap.remove(particle);
    }

    @Override
    public SpriteSet figura$getParticleSprite(ResourceLocation particleID) {
        return spriteSets.get(particleID);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Ljava/util/Queue;add(Ljava/lang/Object;)Z"))
    private boolean figura$releaseEvictedParticle(Queue<Particle> particles, Object added,
                                                 Operation<Boolean> original) {
        if (particles instanceof EvictingQueue<?> queue && queue.remainingCapacity() == 0
                && particles.peek() instanceof CustomParticle evicted) {
            evicted.remove();
            particleMap.remove(evicted);
        }
        return original.call(particles, added);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void figura$releaseDeadParticles(CallbackInfo ci) {
        particleMap.keySet().removeIf(particle -> !particle.isAlive());
    }

    @Inject(method = "clearParticles", at = @At("HEAD"))
    private void figura$clearCustomParticles(CallbackInfo ci) {
        CustomParticleRuntime.clearOwner(null);
        particleMap.keySet().removeIf(particle -> particle instanceof CustomParticle);
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V",
            at = @At("HEAD"))
    private void figura$runParticleFrameCallbacks(LightTexture light,
                                                 Camera camera, float partialTick,
                                                 CallbackInfo ci) {
        if (!PlatformUtils.isModLoaded("neoforge")) {
            CustomParticleRuntime.runFrameCallbacks(partialTick);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V",
            at = @At("TAIL"))
    private void figura$renderCustomParticles(LightTexture light,
                                              Camera camera, float partialTick,
                                              CallbackInfo ci) {
        if (!PlatformUtils.isModLoaded("neoforge")) {
            CustomParticleRuntime.renderWorld(null, camera, partialTick);
        }
    }

}
