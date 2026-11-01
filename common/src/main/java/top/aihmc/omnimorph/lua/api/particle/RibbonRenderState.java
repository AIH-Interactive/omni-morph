package top.aihmc.omnimorph.lua.api.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Frozen arbitrary quads in the same native particle pass and buffer format as Billboard particles. */
final class RibbonRenderState {
    record Layer(CustomParticlePipelines.Material material, ParticleSpriteSheet.Uv uv,
                 float red, float green, float blue, float alpha, int light) {
    }

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

    public boolean isEmpty() {
        return mesh.quads().isEmpty();
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

}
