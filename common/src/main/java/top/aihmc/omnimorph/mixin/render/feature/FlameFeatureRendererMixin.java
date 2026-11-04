package top.aihmc.omnimorph.mixin.render.feature;

import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.utils.RenderUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FlameFeatureRenderer.class)
public class FlameFeatureRendererMixin {
    @Unique
    private Avatar figura$avatar;

    @ModifyVariable(method = "prepare", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private TextureAtlasSprite firstFireTexture(TextureAtlasSprite sprite) {
        TextureAtlasSprite replacement = RenderUtils.firstFireLayer(figura$avatar);
        return replacement != null ? replacement : sprite;
    }

    @ModifyVariable(method = "prepare", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private TextureAtlasSprite secondFireTexture(TextureAtlasSprite sprite) {
        TextureAtlasSprite replacement = RenderUtils.secondFireLayer(figura$avatar);
        figura$avatar = null;
        return replacement != null ? replacement : sprite;
    }
}
