package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.ResourceLocation;
import org.figuramc.figura.utils.FiguraIdentifier;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Unlit preview variants keep the selected material's blending and face-culling policy. */
final class YsmPreviewRenderTypes {
    private static final RenderPipeline CUTOUT = pipeline(false, false);
    private static final RenderPipeline CUTOUT_CULL = pipeline(true, false);
    private static final RenderPipeline TRANSLUCENT = pipeline(false, true);
    private static final RenderPipeline TRANSLUCENT_CULL = pipeline(true, true);

    private ResourceLocation texture;
    private boolean cull;
    private boolean translucent;
    private RenderType cached;

    RenderType get(ResourceLocation texture, boolean cull, boolean translucent) {
        if (cached == null || !texture.equals(this.texture) || cull != this.cull || translucent != this.translucent) {
            RenderPipeline pipeline;
            if (translucent) {
                pipeline = cull ? TRANSLUCENT_CULL : TRANSLUCENT;
            } else {
                pipeline = cull ? CUTOUT_CULL : CUTOUT;
            }
            var setup = RenderSetup.builder(pipeline)
                    .withTexture("Sampler0", texture)
                    .setOutputTarget(RenderTypes.entityTranslucent(texture).outputTarget())
                    .useOverlay()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE);
            if (translucent) {
                setup.sortOnUpload();
            }
            cached = new RenderType("ysm_gui_unlit", setup.createRenderSetup());
            this.texture = texture;
            this.cull = cull;
            this.translucent = translucent;
        }
        return cached;
    }

    private static RenderPipeline pipeline(boolean cull, boolean translucent) {
        RenderPipeline source = RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE;
        // Emissive disables the lightmap; directional shading needs its own define.
        // PER_FACE_LIGHTING takes priority over NO_CARDINAL_LIGHTING in the native shader.
        Set<String> flags = new LinkedHashSet<>(source.getShaderDefines().flags());
        flags.remove("PER_FACE_LIGHTING");
        flags.add("NO_CARDINAL_LIGHTING");
        var defines = new ShaderDefines(source.getShaderDefines().values(), Set.copyOf(flags));
        RenderPipeline material = translucent ? RenderPipelines.ENTITY_TRANSLUCENT : RenderPipelines.ENTITY_CUTOUT;
        // Public snippets reuse the native shaders and uniforms without private-field access or copied assets.
        var snippet = new RenderPipeline.Snippet(
                Optional.of(source.getVertexShader()),
                Optional.of(source.getFragmentShader()),
                Optional.of(defines),
                Optional.of(source.getSamplers()),
                Optional.of(source.getUniforms()),
                Optional.of(source.getColorTargetState()),
                Optional.ofNullable(source.getDepthStencilState()),
                Optional.of(source.getPolygonMode()),
                Optional.of(source.isCull()),
                Optional.of(source.getVertexFormat()),
                Optional.of(source.getVertexFormatMode()));
        String suffix = (translucent ? "translucent" : "cutout") + (cull ? "_cull" : "");
        return RenderPipelines.register(RenderPipeline.builder(snippet)
                .withLocation(FiguraIdentifier.of("pipeline/ysm_gui_unlit_" + suffix))
                .withCull(cull)
                .withDepthStencilState(material.getDepthStencilState())
                .withColorTargetState(translucent ? source.getColorTargetState() : ColorTargetState.DEFAULT)
                .build());
    }
}
