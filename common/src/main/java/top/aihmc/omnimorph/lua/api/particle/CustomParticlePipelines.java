package top.aihmc.omnimorph.lua.api.particle;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import top.aihmc.omnimorph.utils.FiguraIdentifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Fixed pipelines preserve author layer order without changing vanilla particle ordering. */
public final class CustomParticlePipelines {
    public enum Blend {
        ALPHA, ADDITIVE, OPAQUE
    }

    private static final int HARD_MAX_LAYERS = 8;
    private static final Map<RenderPipeline, Integer> ORDER = new IdentityHashMap<>();

    private CustomParticlePipelines() {
    }

    public static RenderPipeline pipeline(int layer, Blend blend) {
        if (layer < 0 || layer >= HARD_MAX_LAYERS) {
            throw new IllegalArgumentException("CUSTOM_PARTICLE_LAYER_LIMIT");
        }
        return Pipelines.VALUES[layer][blend.ordinal()];
    }

    /** Only custom entries exchange slots. All vanilla entries keep their existing relative positions. */
    public static <V> Set<Map.Entry<SingleQuadParticle.Layer, V>> orderedEntries(
            Set<Map.Entry<SingleQuadParticle.Layer, V>> entries) {
        if (ORDER.isEmpty()) {
            return entries;
        }
        var custom = new ArrayList<Map.Entry<SingleQuadParticle.Layer, V>>();
        for (var entry : entries) {
            if (ORDER.containsKey(entry.getKey().pipeline())) {
                custom.add(entry);
            }
        }
        if (custom.size() < 2) {
            return entries;
        }
        custom.sort(Comparator.comparingInt(entry -> ORDER.get(entry.getKey().pipeline())));
        var sorted = custom.iterator();
        var result = new LinkedHashSet<Map.Entry<SingleQuadParticle.Layer, V>>(entries.size());
        for (var entry : entries) {
            result.add(ORDER.containsKey(entry.getKey().pipeline()) ? sorted.next() : entry);
        }
        return result;
    }

    private static final class Pipelines {
        private static final RenderPipeline[][] VALUES = create();

        private static RenderPipeline[][] create() {
            RenderPipeline base = SingleQuadParticle.Layer.TRANSLUCENT.pipeline();
            var snippet = new RenderPipeline.Snippet(Optional.of(base.getVertexShader()),
                    Optional.of(base.getFragmentShader()), Optional.of(base.getShaderDefines()),
                    Optional.of(base.getSamplers()), Optional.of(base.getUniforms()),
                    Optional.of(base.getColorTargetState()), Optional.of(base.getDepthStencilState()),
                    Optional.of(base.getPolygonMode()), Optional.of(base.isCull()),
                    Optional.of(base.getVertexFormat()), Optional.of(base.getVertexFormatMode()));
            var pipelines = new RenderPipeline[HARD_MAX_LAYERS][Blend.values().length];
            for (int layer = 0; layer < HARD_MAX_LAYERS; layer++) {
                for (Blend blend : Blend.values()) {
                    ColorTargetState color = switch (blend) {
                        case ALPHA -> new ColorTargetState(BlendFunction.TRANSLUCENT);
                        case ADDITIVE -> new ColorTargetState(new BlendFunction(SourceFactor.SRC_ALPHA,
                                DestFactor.ONE, SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA));
                        case OPAQUE -> ColorTargetState.DEFAULT;
                    };
                    // All custom layers share the translucent pass, with depth testing but no depth writes.
                    // Otherwise a later opaque layer would be moved before an earlier alpha layer.
                    RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(snippet)
                            .withLocation(new FiguraIdentifier("pipeline/custom_particle/" + layer + "/"
                                    + blend.name().toLowerCase(java.util.Locale.ROOT)))
                            .withColorTargetState(color)
                            .withDepthStencilState(new DepthStencilState(base.getDepthStencilState().depthTest(), false))
                            .build());
                    pipelines[layer][blend.ordinal()] = pipeline;
                    ORDER.put(pipeline, layer);
                }
            }
            return pipelines;
        }
    }
}
