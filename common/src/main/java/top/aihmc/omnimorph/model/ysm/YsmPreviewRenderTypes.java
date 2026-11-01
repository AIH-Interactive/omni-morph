package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

/** Native GUI materials without directional lighting or lightmap sampling. */
public final class YsmPreviewRenderTypes extends RenderType {
    public static final ResourceLocation SHADER_ID =
            new ResourceLocation("omni-morph", "ysm_gui_unlit");
    private static ShaderInstance shader;
    private ResourceLocation currentTexture;
    private final RenderType[] materials = new RenderType[4];

    YsmPreviewRenderTypes() {
        super("omnimorph_ysm_gui_unlit", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }

    /** The loader owns shader lifetime and invokes this callback after each resource reload. */
    public static void setShader(ShaderInstance loaded) {
        shader = loaded;
    }

    RenderType get(ResourceLocation texture, boolean cull, boolean translucent) {
        if (!texture.equals(currentTexture)) {
            currentTexture = texture;
            java.util.Arrays.fill(materials, null);
        }
        int index = (cull ? 1 : 0) | (translucent ? 2 : 0);
        if (materials[index] == null) {
            materials[index] = create("omnimorph_ysm_gui_unlit", DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS, 1536, true, translucent, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> shader))
                            .setTextureState(new TextureStateShard(texture, false, false) {
                                @Override
                                public void setupRenderState() {
                                    // The owning FiguraTexture maintains blur and clamp settings.
                                    com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texture);
                                }
                            })
                            .setTransparencyState(translucent ? TRANSLUCENT_TRANSPARENCY : NO_TRANSPARENCY)
                            .setCullState(cull ? CULL : NO_CULL)
                            .setLightmapState(NO_LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_DEPTH_WRITE)
                            .setOutputState(MAIN_TARGET)
                            .createCompositeState(true));
        }
        return materials[index];
    }
}
