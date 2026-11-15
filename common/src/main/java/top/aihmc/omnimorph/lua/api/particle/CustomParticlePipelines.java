package top.aihmc.omnimorph.lua.api.particle;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import org.figuramc.figura.utils.FiguraIdentifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Bounded pipeline variants preserve custom priorities without reordering vanilla particles. */
public final class CustomParticlePipelines {
    public enum Blend {
        ALPHA, ADDITIVE, OPAQUE
    }

    private static final int HARD_MAX_LAYERS = 8;
    private static final int MAX_CUTOFF_VARIANTS = 4096;
    private static int cutoffVariants;
    static final float DEFAULT_ALPHA_CUTOFF = 0.1f;
    private static final Map<RenderPipeline, Integer> ORDER = new IdentityHashMap<>();
    private static final Map<Variant, RenderPipeline> VARIANTS = new java.util.LinkedHashMap<>();

    private record Variant(int layer, Blend blend, int order, boolean depthTest, boolean cull, float alphaCutoff) {}

    private CustomParticlePipelines() {
    }

    public static RenderPipeline pipeline(int layer, Blend blend) {
        if (layer < 0 || layer >= HARD_MAX_LAYERS) {
            throw new IllegalArgumentException("CUSTOM_PARTICLE_LAYER_LIMIT");
        }
        return Pipelines.VALUES[layer][blend.ordinal()];
    }

    public static RenderPipeline pipeline(int layer, Blend blend, int order, boolean depthTest) {
        return pipeline(layer, blend, order, depthTest,
                SingleQuadParticle.Layer.TRANSLUCENT.pipeline().isCull(), DEFAULT_ALPHA_CUTOFF);
    }

    public static RenderPipeline pipeline(int layer, Blend blend, int order, boolean depthTest,
                                           boolean cull, float alphaCutoff) {
        if (layer < 0 || layer >= HARD_MAX_LAYERS || order < -128 || order > 127
                || !Float.isFinite(alphaCutoff) || alphaCutoff < 0 || alphaCutoff > 1) {
            throw new IllegalArgumentException("CUSTOM_PARTICLE_STATE_LIMIT");
        }
        if (order == 0 && depthTest && cull == SingleQuadParticle.Layer.TRANSLUCENT.pipeline().isCull()
                && alphaCutoff == DEFAULT_ALPHA_CUTOFF) {
            return pipeline(layer, blend);
        }
        var key = new Variant(layer, blend, order, depthTest, cull, alphaCutoff);
        var existing = VARIANTS.get(key);
        if (existing != null) {
            return existing;
        }
        if (alphaCutoff != DEFAULT_ALPHA_CUTOFF && cutoffVariants >= MAX_CUTOFF_VARIANTS) {
            throw new IllegalArgumentException("CUSTOM_PARTICLE_PIPELINE_VARIANT_LIMIT");
        }
        var pipeline = createPipeline(layer, blend, order, depthTest, cull, alphaCutoff);
        if (org.figuramc.figura.utils.PlatformUtils.isModLoaded("iris")) {
            top.aihmc.omnimorph.compat.IrisCompat.assignParticlePipeline(pipeline, blend);
        }
        VARIANTS.put(key, pipeline);
        if (alphaCutoff != DEFAULT_ALPHA_CUTOFF) {
            cutoffVariants++;
        }
        return pipeline;
    }

    public static int sortOrder(RenderPipeline pipeline) {
        return ORDER.getOrDefault(pipeline, 0);
    }

    /** Every registered pipeline with its blend, so Iris can map it to the matching particle program. */
    public static Map<RenderPipeline, Blend> registered() {
        var pipelines = new java.util.LinkedHashMap<RenderPipeline, Blend>();
        for (int layer = 0; layer < HARD_MAX_LAYERS; layer++) {
            for (Blend blend : Blend.values()) {
                pipelines.put(Pipelines.VALUES[layer][blend.ordinal()], blend);
            }
        }
        VARIANTS.forEach((variant, pipeline) -> pipelines.put(pipeline, variant.blend()));
        return pipelines;
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

    private static RenderPipeline createPipeline(int layer, Blend blend, int order, boolean depthTest,
                                                   boolean cull, float alphaCutoff) {
        RenderPipeline base = SingleQuadParticle.Layer.TRANSLUCENT.pipeline();
        var snippet = new RenderPipeline.Snippet(Optional.of(base.getVertexShader()),
                Optional.of(base.getFragmentShader()), Optional.of(base.getShaderDefines()),
                Optional.of(base.getSamplers()), Optional.of(base.getUniforms()),
                Optional.of(base.getColorTargetState()), Optional.of(base.getDepthStencilState()),
                Optional.of(base.getPolygonMode()), Optional.of(base.isCull()),
                Optional.of(base.getVertexFormat()), Optional.of(base.getVertexFormatMode()));
        ColorTargetState color = switch (blend) {
            case ALPHA -> new ColorTargetState(BlendFunction.TRANSLUCENT);
            case ADDITIVE -> new ColorTargetState(new BlendFunction(SourceFactor.SRC_ALPHA,
                    DestFactor.ONE, SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA));
            case OPAQUE -> ColorTargetState.DEFAULT;
        };
        String location = "pipeline/custom_particle/" + layer + "/"
                + blend.name().toLowerCase(java.util.Locale.ROOT);
        if (order != 0 || !depthTest) {
            location += "/order_" + order + "/depth_" + (depthTest ? "on" : "off");
        }
        if (cull != base.isCull() || alphaCutoff != DEFAULT_ALPHA_CUTOFF) {
            location += "/cull_" + cull + "/alpha_" + Integer.toHexString(Float.floatToIntBits(alphaCutoff));
        }
        // Custom layers share the translucent pass and never write depth, preserving their order.
        var builder = RenderPipeline.builder(snippet)
                .withLocation(new FiguraIdentifier(location))
                .withColorTargetState(color)
                .withCull(cull)
                .withDepthStencilState(new DepthStencilState(depthTest
                        ? base.getDepthStencilState().depthTest() : CompareOp.ALWAYS_PASS, false));
        if (alphaCutoff != DEFAULT_ALPHA_CUTOFF) {
            builder.withFragmentShader(new FiguraIdentifier("core/custom_particle"))
                    .withShaderDefine("ALPHA_CUTOFF", alphaCutoff);
        }
        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        ORDER.put(pipeline, order * HARD_MAX_LAYERS + layer);
        return pipeline;
    }

    private static final class Pipelines {
        private static final RenderPipeline[][] VALUES = create();

        private static RenderPipeline[][] create() {
            var pipelines = new RenderPipeline[HARD_MAX_LAYERS][Blend.values().length];
            for (int layer = 0; layer < HARD_MAX_LAYERS; layer++) {
                for (Blend blend : Blend.values()) {
                    pipelines[layer][blend.ordinal()] = createPipeline(layer, blend, 0, true,
                            SingleQuadParticle.Layer.TRANSLUCENT.pipeline().isCull(), DEFAULT_ALPHA_CUTOFF);
                }
            }
            return pipelines;
        }
    }
}
