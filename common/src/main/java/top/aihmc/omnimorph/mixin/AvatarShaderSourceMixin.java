package top.aihmc.omnimorph.mixin;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.aihmc.omnimorph.model.rendering.material.AvatarMaterial;

/** Keep avatar-owned sources available to native recompilation after cache invalidation. */
@Mixin(ShaderManager.class)
public abstract class AvatarShaderSourceMixin {
    @Inject(method = "getShader", at = @At("HEAD"), cancellable = true)
    private void avatarShader(Identifier id, ShaderType type, CallbackInfoReturnable<String> result) {
        String source = AvatarMaterial.shaderSource(id, type);
        if (source != null) {
            result.setReturnValue(source);
        }
    }
}
