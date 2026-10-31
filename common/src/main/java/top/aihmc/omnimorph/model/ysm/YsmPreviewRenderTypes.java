package top.aihmc.omnimorph.model.ysm;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Render types for YSM GUI previews that request no lighting.
 * <p>
 * MC 1.21.1 entity shaders always apply the directional GUI lights, and unlit
 * variants need bundled core shaders. Until those are registered, previews keep
 * the selected material's blending and face-culling policy with vanilla lighting.
 */
final class YsmPreviewRenderTypes {
    RenderType get(ResourceLocation texture, boolean cull, boolean translucent) {
        if (translucent) {
            return cull ? RenderType.entityTranslucentCull(texture) : RenderType.entityTranslucent(texture);
        }
        return cull ? RenderType.entityCutout(texture) : RenderType.entityCutoutNoCull(texture);
    }
}
