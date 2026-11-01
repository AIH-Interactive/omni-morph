package top.aihmc.omnimorph.lua.api.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Bounded author-layer descriptors, independent of Minecraft's particle batching API. */
public final class CustomParticlePipelines {
    public enum Blend {
        ALPHA, ADDITIVE, OPAQUE
    }

    public record Pipeline(int layer, Blend blend) {
        public Pipeline {
            if (layer < 0 || layer >= 8 || blend == null) {
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
                            .setShaderState(new ShaderStateShard(GameRenderer::getParticleShader))
                            .setTextureState(new TextureStateShard(material.texture, false, false) {
                                @Override
                                public void setupRenderState() {
                                    // FiguraTexture owns filtering/clamp; binding must not reset its settings.
                                    RenderSystem.setShaderTexture(0, material.texture);
                                }
                            })
                            .setTransparencyState(transparency)
                            .setLightmapState(LIGHTMAP)
                            .setCullState(NO_CULL)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOutputState(world ? PARTICLES_TARGET : MAIN_TARGET)
                            .createCompositeState(false));
        }
    }
}
