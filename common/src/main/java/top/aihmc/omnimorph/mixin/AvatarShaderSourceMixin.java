package top.aihmc.omnimorph.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aihmc.omnimorph.model.rendering.material.AvatarMaterial;
import top.aihmc.omnimorph.lua.api.particle.CustomParticlePipelines;

/** Retire native shader programs on reload while retaining their author-owned sources. */
@Mixin(GameRenderer.class)
public abstract class AvatarShaderSourceMixin {
    @Inject(method = "reloadShaders", at = @At("HEAD"))
    private void avatarShaders(ResourceProvider resources, CallbackInfo callback) {
        AvatarMaterial.invalidateShaders();
        CustomParticlePipelines.invalidateShaders();
    }
}
