package top.aihmc.omnimorph.mixin;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.aihmc.omnimorph.model.rendering.material.AvatarMaterial;

/** Preserve avatar sources through native cache misses and resource reloads. */
@Mixin(PipelineCache.class)
public abstract class AvatarShaderSourceMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static ShaderSource avatarShader(ShaderSource original) {
        return AvatarMaterial.sources(original);
    }
}
