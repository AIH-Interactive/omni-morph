package top.aihmc.omnimorph.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.aihmc.omnimorph.lua.api.particle.CustomParticlePipelines;

import java.util.Map;
import java.util.Set;

@Mixin(QuadParticleRenderState.class)
public class QuadParticleRenderStateMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;entrySet()Ljava/util/Set;"))
    private <V> Set<Map.Entry<SingleQuadParticle.Layer, V>> figura$orderCustomLayers(
            Map<SingleQuadParticle.Layer, V> layers,
            Operation<Set<Map.Entry<SingleQuadParticle.Layer, V>>> original) {
        return CustomParticlePipelines.orderedEntries(original.call(layers));
    }
}
