package org.figuramc.figura.mixin.particle;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import org.figuramc.figura.ducks.ParticleEngineAccessor;
import top.aihmc.omnimorph.lua.api.particle.CustomParticle;
import top.aihmc.omnimorph.lua.api.particle.CustomParticleRuntime;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.*;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin implements ParticleEngineAccessor {

    @Shadow @Nullable protected abstract <T extends ParticleOptions> Particle makeParticle(T parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ);

    @Shadow public abstract void add(Particle particle);

    @Shadow
    @Final
    private ParticleResources resourceManager;
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
    public ParticleResources figura$getParticleResources() {
        return resourceManager;
    }

    @Inject(method = "clearParticles", at = @At("HEAD"))
    private void figura$clearCustomParticles(CallbackInfo ci) {
        CustomParticleRuntime.clearOwner(null);
        particleMap.keySet().removeIf(particle -> particle instanceof CustomParticle);
    }

    @Inject(method = "extract", at = @At("TAIL"))
    private void figura$extractRibbons(net.minecraft.client.renderer.state.level.ParticlesRenderState state,
                                       net.minecraft.client.renderer.culling.Frustum frustum,
                                       net.minecraft.client.Camera camera, float partialTick, CallbackInfo ci) {
        CustomParticleRuntime.extractRibbons(state, frustum, camera, partialTick);
    }

    @Inject(method = "extract", at = @At("HEAD"))
    private void figura$runParticleFrameCallbacks(net.minecraft.client.renderer.state.level.ParticlesRenderState state,
                                                  net.minecraft.client.renderer.culling.Frustum frustum,
                                                  net.minecraft.client.Camera camera, float partialTick, CallbackInfo ci) {
        CustomParticleRuntime.runFrameCallbacks(partialTick);
    }
}
