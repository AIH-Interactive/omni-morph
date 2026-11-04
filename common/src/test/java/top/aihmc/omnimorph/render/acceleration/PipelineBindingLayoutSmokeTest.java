package top.aihmc.omnimorph.render.acceleration;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import top.aihmc.omnimorph.lua.api.particle.CustomParticlePipelines;
import top.aihmc.omnimorph.model.rendering.texture.FiguraRenderTypes;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates real production layouts with the native validator; no graphics device or window. */
public final class PipelineBindingLayoutSmokeTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        RenderPipeline solid = FiguraRenderTypes.FiguraRenderPipelines.FIGURA_SOLID;
        verify(solid);
        Set<String> solidUniforms = BindGroupLayout.flattenUniforms(solid.getBindGroupLayouts()).stream()
                .map(BindGroupLayout.UniformDescription::name).collect(Collectors.toSet());
        require(solidUniforms.equals(Set.of("Globals", "DynamicTransforms", "Projection", "Fog")),
                "solid retains all native global, matrix and fog bindings");

        CustomParticlePipelines.registered();
        // These lazy classes register pipelines only when their preview features are first used.
        // Initialize them without drawing or replacing production code with fixture pipelines.
        Class.forName("top.aihmc.omnimorph.model.ysm.YsmPreviewRenderTypes");
        Class.forName("top.aihmc.omnimorph.model.library.PreviewBoneHighlight$Lines");

        int registered = 0;
        Set<String> locations = new HashSet<>();
        for (RenderPipeline pipeline : java.util.stream.Stream.concat(
                RenderPipelines.requiredPipelines().stream(), RenderPipelines.optionalPipelines().stream()).toList()) {
            if (pipeline.getLocation().getNamespace().equals("omni-morph")) {
                verify(pipeline);
                locations.add(pipeline.getLocation().getPath());
                registered++;
            }
        }
        require(locations.contains("pipeline/solid")
                        && locations.contains("pipeline/custom_particle/0/alpha")
                        && locations.contains("pipeline/ysm_gui_unlit_cutout")
                        && locations.contains("pipeline/wardrobe_bone_selection"),
                "layout audit includes solid, custom particles, YSM previews and bone highlights");

        RenderPipeline original = RenderPipelines.ENTITY_CUTOUT_CULL;
        verify(GpuShaderProgram.pipeline(original, Identifier.parse("omni-morph:layout_check/uniform"), true));
        verify(GpuShaderProgram.pipeline(original, Identifier.parse("omni-morph:layout_check/storage"), false));
        System.out.println("PASS native bind-group compatibility: " + registered
                + " registered production pipelines and uniform/storage acceleration variants; no GPU/window");
    }

    private static void verify(RenderPipeline pipeline) {
        BindGroupLayout.ensureCompatible(pipeline.getBindGroupLayouts());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
