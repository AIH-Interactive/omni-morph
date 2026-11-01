package top.aihmc.omnimorph.mixin.neoforge;

import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aihmc.omnimorph.lua.api.particle.CustomParticleRuntime;

import java.util.function.Predicate;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixinNeoForge {
    // NeoForge renders selected particle types in separate passes; only the translucent pass owns ours.
    @Inject(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At("HEAD"), remap = false)
    private void figura$runNeoForgeParticleCallbacks(LightTexture light,
                                                    Camera camera, float partialTick,
                                                    Frustum frustum,
                                                    Predicate<ParticleRenderType> types,
                                                    CallbackInfo ci) {
        if (types.test(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT)) {
            CustomParticleRuntime.runFrameCallbacks(partialTick);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At("TAIL"), remap = false)
    private void figura$renderNeoForgeCustomParticles(LightTexture light,
                                                     Camera camera, float partialTick,
                                                     Frustum frustum,
                                                     Predicate<ParticleRenderType> types,
                                                     CallbackInfo ci) {
        if (types.test(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT)) {
            CustomParticleRuntime.renderWorld(frustum, camera, partialTick);
        }
    }
}
