package top.aihmc.omnimorph.mixin.particle;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aihmc.omnimorph.lua.api.particle.ParticleDynamicLights;

@Mixin(LevelRenderer.class)
public abstract class ParticleWorldLightMixin {
    @ModifyReturnValue(method = "getLightCoords(Lnet/minecraft/client/renderer/LevelRenderer$BrightnessGetter;"
            + "Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"))
    private static int figura$particleWorldLight(int original, LevelRenderer.BrightnessGetter brightness,
                                                BlockAndLightGetter world, BlockState state, BlockPos position) {
        return ParticleDynamicLights.apply(world, position, original);
    }

    @Inject(method = "extractLevel", at = @At("HEAD"))
    private void figura$prepareParticleLights(DeltaTracker tracker, Camera camera, float frameParameter, CallbackInfo ci) {
        ParticleDynamicLights.updateFrame(tracker.getGameTimeDeltaPartialTick(false));
    }
}
