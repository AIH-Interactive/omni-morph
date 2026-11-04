package top.aihmc.omnimorph.mixin.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aihmc.omnimorph.lua.api.particle.ParticleDynamicLights;

@Mixin(LevelExtractor.class)
public abstract class ParticleLightExtractionMixin {
    @Inject(method = "extract", at = @At("HEAD"))
    private void figura$prepareParticleLights(DeltaTracker tracker, Camera camera, float frameParameter, CallbackInfo ci) {
        ParticleDynamicLights.updateFrame(tracker.getGameTimeDeltaPartialTick(false));
    }
}
