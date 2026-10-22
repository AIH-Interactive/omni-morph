package top.aihmc.omnimorph.lua.api.particle;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ParticleFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.LinkedHashMap;
import java.util.List;

/** Frozen arbitrary quads in the same native particle pass and buffer format as Billboard particles. */
final class RibbonRenderState implements ParticleGroupRenderState, SubmitNodeCollector.ParticleGroupRenderer {
    record Layer(SingleQuadParticle.Layer material, ParticleSpriteSheet.Uv uv,
                 float red, float green, float blue, float alpha, int light) {
    }

    private static final QuadParticleRenderState NATIVE_RENDERER = new QuadParticleRenderState();
    private final RibbonMesh.Mesh mesh;
    private final List<Layer> layers;
    private final Vec3 camera;

    RibbonRenderState(RibbonMesh.Mesh mesh, List<Layer> layers, Vec3 camera) {
        this.mesh = mesh;
        this.layers = List.copyOf(layers);
        this.camera = camera;
        for (RibbonMesh.Quad quad : mesh.quads()) {
            validate(quad.tailLeft());
            validate(quad.tailRight());
            validate(quad.headRight());
            validate(quad.headLeft());
        }
    }

    private void validate(RibbonMesh.Vertex vertex) {
        Vec3 relative = vertex.position().subtract(camera);
        if (!Float.isFinite((float) relative.x) || !Float.isFinite((float) relative.y)
                || !Float.isFinite((float) relative.z)) {
            throw new IllegalArgumentException("INVALID_RIBBON_POSITION");
        }
    }

    @Override
    public boolean isEmpty() {
        return mesh.quads().isEmpty();
    }

    @Override
    public void submit(SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (!isEmpty()) {
            collector.submitParticleGroup(this);
        }
    }

    @Override
    public QuadParticleRenderState.PreparedBuffers prepare(ParticleFeatureRenderer.ParticleBufferCache cache,
                                                           boolean translucent) {
        if (!translucent || isEmpty()) {
            return null;
        }
        int bytes = mesh.submittedVertices() * DefaultVertexFormat.PARTICLE.getVertexSize();
        try (var storage = ByteBufferBuilder.exactlySized(bytes)) {
            var builder = new BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            writeVertices(builder);
            try (var data = builder.build()) {
                if (data == null) {
                    return null;
                }
                cache.write(data.vertexBuffer());
                int indexCount = data.drawState().indexCount();
                RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS).getBuffer(indexCount);
                var transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrix(),
                        new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
                var prepared = new LinkedHashMap<SingleQuadParticle.Layer, QuadParticleRenderState.PreparedLayer>();
                int verticesPerLayer = mesh.quads().size() * 4;
                for (int index = 0; index < layers.size(); index++) {
                    prepared.put(layers.get(index).material(), new QuadParticleRenderState.PreparedLayer(
                            index * verticesPerLayer, mesh.quads().size() * 6));
                }
                return new QuadParticleRenderState.PreparedBuffers(indexCount, transforms, prepared);
            }
        }
    }

    /** Shared with the native-buffer test; no Lua, history sampling or budget mutation occurs during replay. */
    void writeVertices(VertexConsumer consumer) {
        for (Layer layer : layers) {
            writeLayer(consumer, layer);
        }
    }

    List<Layer> layers() {
        return layers;
    }

    void writeLayer(VertexConsumer consumer, Layer layer) {
        for (RibbonMesh.Quad quad : mesh.quads()) {
            vertex(consumer, quad.tailLeft(), layer);
            vertex(consumer, quad.tailRight(), layer);
            vertex(consumer, quad.headRight(), layer);
            vertex(consumer, quad.headLeft(), layer);
        }
    }

    private void vertex(VertexConsumer consumer, RibbonMesh.Vertex vertex, Layer layer) {
        Vec3 relative = vertex.position().subtract(camera);
        var uv = layer.uv();
        float u = (float) (uv.u0() + (uv.u1() - uv.u0()) * vertex.u());
        float v = (float) (uv.v0() + (uv.v1() - uv.v0()) * vertex.v());
        consumer.addVertex((float) relative.x, (float) relative.y, (float) relative.z)
                .setUv(u, v).setColor(layer.red(), layer.green(), layer.blue(), layer.alpha() * vertex.alpha())
                .setLight(layer.light());
    }

    @Override
    public void render(QuadParticleRenderState.PreparedBuffers prepared,
                       ParticleFeatureRenderer.ParticleBufferCache cache, RenderPass pass, TextureManager textures) {
        // Native binding/draw honors the ordered material map, offsets, current samplers and particle target.
        NATIVE_RENDERER.render(prepared, cache, pass, textures);
    }
}
