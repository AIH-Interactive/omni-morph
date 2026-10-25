package top.aihmc.omnimorph.mixin.particle;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.aihmc.omnimorph.lua.api.particle.CustomParticlePipelines;

import java.util.Map;
import java.util.Set;

@Mixin(QuadParticleRenderState.class)
public class QuadParticleRenderStateMixin {
    /** Particles are extracted camera-relative; shader packs may keep the camera translation in the model-view. */
    @ModifyExpressionValue(method = "prepare", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;getModelViewMatrix()Lorg/joml/Matrix4f;"))
    private Matrix4f figura$stripCameraTranslation(Matrix4f original) {
        Matrix4f stripped = new Matrix4f(original);
        stripped.setTranslation(0f, 0f, 0f);
        return stripped;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;entrySet()Ljava/util/Set;"))
    private <V> Set<Map.Entry<SingleQuadParticle.Layer, V>> figura$orderCustomLayers(
            Map<SingleQuadParticle.Layer, V> layers,
            Operation<Set<Map.Entry<SingleQuadParticle.Layer, V>>> original) {
        return CustomParticlePipelines.orderedEntries(original.call(layers));
    }
}
