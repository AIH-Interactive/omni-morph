package top.aihmc.omnimorph.lua.api.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.figuramc.figura.utils.FiguraIdentifier;
import top.aihmc.omnimorph.model.rendering.material.NativeShader;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Bounded author-layer descriptors, independent of Minecraft's particle batching API. */
public final class CustomParticlePipelines {
    public enum Blend {
        ALPHA, ADDITIVE, OPAQUE
    }

    static final float DEFAULT_ALPHA_CUTOFF = 0.1f;
    private static final Set<Pipeline> CUTOFF_VARIANTS = new HashSet<>();
    private static final Map<Pipeline, NativeShader> SHADERS =
            new HashMap<>();

    public record Pipeline(int layer, Blend blend, int order, boolean depthTest, boolean cull, float alphaCutoff) {
        public Pipeline(int layer, Blend blend) {
            this(layer, blend, 0, true, false, DEFAULT_ALPHA_CUTOFF);
        }

        public Pipeline {
            if (layer < 0 || layer >= 8 || blend == null || order < -128 || order > 127
                    || !Float.isFinite(alphaCutoff) || alphaCutoff < 0 || alphaCutoff > 1) {
                throw new IllegalArgumentException("CUSTOM_PARTICLE_LAYER_LIMIT");
            }
        }
    }

    /** Materials belong to a layer declaration; no global texture cache outlives its avatar. */
    public static final class Material {
        private final ResourceLocation texture;
        private final Pipeline pipeline;
        private RenderType worldType;
        private RenderType previewType;

        public Material(ResourceLocation texture, Pipeline pipeline) {
            this.texture = texture;
            this.pipeline = pipeline;
        }

        public Pipeline pipeline() {
            return pipeline;
        }

        public ResourceLocation textureAtlasLocation() {
            return texture;
        }

        public RenderType renderType(boolean world) {
            if (world) {
                if (worldType == null) {
                    worldType = ParticleType.create(this, true);
                }
                return worldType;
            }
            if (previewType == null) {
                previewType = ParticleType.create(this, false);
            }
            return previewType;
        }
    }

    private CustomParticlePipelines() {
    }

    public static Pipeline pipeline(int layer, Blend blend) {
        return new Pipeline(layer, blend);
    }

    public static Pipeline pipeline(int layer, Blend blend, int order, boolean depthTest,
                                    boolean cull, float alphaCutoff) {
        var descriptor = new Pipeline(layer, blend, order, depthTest, cull, alphaCutoff);
        if (alphaCutoff != DEFAULT_ALPHA_CUTOFF && !CUTOFF_VARIANTS.contains(descriptor)) {
            if (CUTOFF_VARIANTS.size() >= 4096) {
                throw new IllegalArgumentException("CUSTOM_PARTICLE_PIPELINE_VARIANT_LIMIT");
            }
            CUTOFF_VARIANTS.add(descriptor);
        }
        return descriptor;
    }

    public static int sortOrder(Pipeline pipeline) {
        return pipeline.order() * 8 + pipeline.layer();
    }

    public static void invalidateShaders() {
        SHADERS.values().forEach(NativeShader::close);
    }

    private static ShaderInstance shader(Pipeline pipeline) {
        if (pipeline.alphaCutoff() == DEFAULT_ALPHA_CUTOFF) {
            return GameRenderer.getParticleShader();
        }
        return SHADERS.computeIfAbsent(pipeline, key -> {
            var resources = Minecraft.getInstance().getResourceManager();
            try (var vertex = resources.openAsReader(ResourceLocation.withDefaultNamespace("shaders/core/particle.vsh"));
                 var fragment = resources.openAsReader(FiguraIdentifier.of("shaders/core/custom_particle.fsh"))) {
                String vsh = vertex.lines().collect(Collectors.joining("\n"));
                String fsh = fragment.lines().collect(Collectors.joining("\n"))
                        .replace("ALPHA_CUTOFF", Float.toString(key.alphaCutoff()));
                return new NativeShader(vsh, fsh,
                        DefaultVertexFormat.PARTICLE, List.of("Sampler0", "Sampler2"));
            } catch (IOException error) {
                throw new IllegalStateException("Custom particle shader resource failed", error);
            }
        }).get();
    }

    private static final class ParticleType extends RenderType {
        private static final TransparencyStateShard ADDITIVE = new TransparencyStateShard(
                "omnimorph_particle_additive", () -> {
                    RenderSystem.enableBlend();
                    RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                            GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                }, () -> {
                    RenderSystem.disableBlend();
                    RenderSystem.defaultBlendFunc();
                });

        private ParticleType() {
            super("omnimorph_particle", DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS,
                    256, false, false, () -> {}, () -> {});
        }

        private static RenderType create(Material material, boolean world) {
            TransparencyStateShard transparency = switch (material.pipeline.blend()) {
                case ALPHA -> TRANSLUCENT_TRANSPARENCY;
                case ADDITIVE -> ADDITIVE;
                case OPAQUE -> NO_TRANSPARENCY;
            };
            // Explicit batch order controls compositing, including opaque layers. Never write depth.
            return create("omnimorph_particle_" + material.pipeline.layer(), DefaultVertexFormat.PARTICLE,
                    VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> shader(material.pipeline)))
                            .setTextureState(new TextureStateShard(material.texture, false, false) {
                                @Override
                                public void setupRenderState() {
                                    // FiguraTexture owns filtering/clamp; binding must not reset its settings.
                                    RenderSystem.setShaderTexture(0, material.texture);
                                }
                            })
                            .setTransparencyState(transparency)
                            .setLightmapState(LIGHTMAP)
                            .setCullState(material.pipeline.cull() ? CULL : NO_CULL)
                            .setDepthTestState(material.pipeline.depthTest() ? LEQUAL_DEPTH_TEST : NO_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOutputState(world ? PARTICLES_TARGET : MAIN_TARGET)
                            .createCompositeState(false));
        }
    }
}
