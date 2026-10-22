package top.aihmc.omnimorph.mixin.particle;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.aihmc.omnimorph.lua.api.particle.ParticleDynamicLights;

@Mixin(EntityRenderer.class)
public abstract class ParticleEntityLightMixin {
    @ModifyReturnValue(method = "getPackedLightCoords", at = @At("RETURN"))
    private int figura$particleEntityLight(int original, Entity entity, float partialTick) {
        var position = entity.getLightProbePosition(partialTick);
        return ParticleDynamicLights.apply(entity.level(), position.x, position.y, position.z, original);
    }
}
