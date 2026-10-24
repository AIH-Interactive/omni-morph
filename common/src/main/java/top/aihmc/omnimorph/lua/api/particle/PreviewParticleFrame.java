package top.aihmc.omnimorph.lua.api.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Immutable panel geometry. Deferred draws never sample history, execute Lua or reserve budgets again. */
public final class PreviewParticleFrame {
    private static final PreviewParticleFrame EMPTY = new PreviewParticleFrame(List.of());
    private final List<Batch> batches;

    private PreviewParticleFrame(List<Batch> batches) {
        this.batches = List.copyOf(batches);
    }

    public static PreviewParticleFrame empty() {
        return EMPTY;
    }

    static PreviewParticleFrame extract(CustomParticle[] particles, Vec3 origin, Quaternionf viewRotation,
                                        float partialTick) {
        var camera = new PreviewCamera(origin, viewRotation);
        Vec3 offset = camera.position().subtract(origin);
        var batches = new ArrayList<Batch>();
        for (CustomParticle particle : particles) {
            if (!particle.active()) {
                continue;
            }
            if (particle.ribbon()) {
                var ribbon = particle.extractRibbon(camera, null, partialTick,
                        CustomParticleRuntime.availableRibbonVertices(particle));
                if (ribbon != null) {
                    for (var layer : ribbon.layers()) {
                        capture(batches, particle, layer.material(), offset, consumer -> ribbon.writeLayer(consumer, layer));
                    }
                }
            } else {
                particle.extract(new QuadParticleRenderState() {
                    @Override
                    public void add(SingleQuadParticle.Layer layer, float x, float y, float z,
                                    float qx, float qy, float qz, float qw, float size,
                                    float u0, float u1, float v0, float v1, int color, int light) {
                        capture(batches, particle, layer, offset, consumer -> renderRotatedQuad(consumer,
                                x, y, z, qx, qy, qz, qw, size, u0, u1, v0, v1, color, light));
                    }
                }, camera, partialTick);
            }
        }
        // Match the world particle pass: author layer indices determine compositing order.
        batches.sort(Comparator.comparingInt(Batch::layerIndex));
        return new PreviewParticleFrame(batches);
    }

    private static void capture(List<Batch> batches, CustomParticle particle, SingleQuadParticle.Layer material,
                                Vec3 offset, Consumer<VertexConsumer> writer) {
        var layers = particle.currentLayers();
        for (int index = 0; index < layers.size(); index++) {
            var layer = layers.get(index);
            if (layer.renderLayer() == material) {
                var vertices = new Capture(offset);
                writer.accept(vertices);
                batches.add(new Batch(index, material, layer.texture(), vertices.freeze()));
                return;
            }
        }
        throw new IllegalStateException("PREVIEW_PARTICLE_MATERIAL_MISSING");
    }

    public int vertexCount() {
        return batches.stream().mapToInt(batch -> batch.vertices().size()).sum();
    }

    public void draw(Matrix4f entityToDraw, MultiBufferSource source) {
        if (batches.isEmpty()) {
            return;
        }
        if (source instanceof MultiBufferSource.BufferSource buffers) {
            buffers.endBatch();
        }
        for (Batch batch : batches) {
            RenderType type = batch.renderType();
            batch.write(entityToDraw, source.getBuffer(type));
            if (source instanceof MultiBufferSource.BufferSource buffers) {
                buffers.endBatch(type);
            }
        }
    }

    public void submit(PoseStack stack, SubmitNodeCollector collector) {
        for (Batch batch : batches) {
            collector.order(100 + batch.layerIndex()).submitCustomGeometry(stack, batch.renderType(),
                    (pose, consumer) -> batch.write(pose.pose(), consumer));
        }
    }

    private record Batch(int layerIndex, SingleQuadParticle.Layer material, FiguraTexture texture,
                         List<Vertex> vertices) {
        RenderType renderType() {
            return new RenderType("omni_morph_preview_particle", RenderSetup.builder(material.pipeline())
                    .withTexture("Sampler0", material.textureAtlasLocation(), texture::getSampler)
                    .useLightmap().createRenderSetup());
        }

        void write(Matrix4f transform, VertexConsumer consumer) {
            for (Vertex vertex : vertices) {
                consumer.addVertex(transform, vertex.x(), vertex.y(), vertex.z())
                        .setUv(vertex.u(), vertex.v()).setColor(vertex.color()).setLight(vertex.light());
            }
        }
    }

    private record Vertex(float x, float y, float z, float u, float v, int color, int light) {
    }

    /** The native particle writer emits only the PARTICLE format's position, UV, color and light attributes. */
    private static final class Capture implements VertexConsumer {
        private final Vec3 offset;
        private final List<Vertex> vertices = new ArrayList<>();
        private float x, y, z, u, v;
        private int color, light;
        private boolean started;

        Capture(Vec3 offset) {
            this.offset = offset;
        }

        private void finishVertex() {
            if (started) {
                vertices.add(new Vertex(x, y, z, u, v, color, light));
            }
        }

        List<Vertex> freeze() {
            finishVertex();
            return List.copyOf(vertices);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            finishVertex();
            this.x = (float) (x + offset.x);
            this.y = (float) (y + offset.y);
            this.z = (float) (z + offset.z);
            started = true;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return setColor(alpha << 24 | red << 16 | green << 8 | blue);
        }

        @Override
        public VertexConsumer setColor(int color) {
            this.color = color;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int low, int high) {
            light = low | high << 16;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            throw new UnsupportedOperationException("PARTICLE_FORMAT_HAS_NO_OVERLAY");
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            throw new UnsupportedOperationException("PARTICLE_FORMAT_HAS_NO_NORMAL");
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            throw new UnsupportedOperationException("PARTICLE_FORMAT_HAS_NO_LINE_WIDTH");
        }
    }

    private static final class PreviewCamera extends Camera {
        private final Vec3 position;
        private final Quaternionf rotation;

        PreviewCamera(Vec3 origin, Quaternionf viewRotation) {
            rotation = new Quaternionf(viewRotation).invert();
            Vector3f offset = rotation.transform(new Vector3f(0, 0, 16));
            position = origin.add(offset.x, offset.y, offset.z);
        }

        @Override
        public Vec3 position() {
            return position;
        }

        @Override
        public Quaternionf rotation() {
            return rotation;
        }
    }
}
