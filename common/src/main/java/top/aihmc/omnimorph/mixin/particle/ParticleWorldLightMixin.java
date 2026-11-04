package top.aihmc.omnimorph.mixin.particle;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.aihmc.omnimorph.lua.api.particle.ParticleDynamicLights;

@Mixin(LightCoordsUtil.class)
public abstract class ParticleWorldLightMixin {
    @ModifyReturnValue(method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"))
    private static int figura$particleWorldLight(int original, LightCoordsUtil.BrightnessGetter brightness,
                                                BlockAndLightGetter world, BlockState state, BlockPos position) {
        return ParticleDynamicLights.apply(world, position, original);
    }
}
