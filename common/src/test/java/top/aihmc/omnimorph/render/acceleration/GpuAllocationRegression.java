package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.joml.Matrix4f;

import java.lang.management.ManagementFactory;
import java.util.List;

/** Large transparent production sorting must reuse its workspace instead of allocating per-face objects. */
public final class GpuAllocationRegression {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RenderSystem.initRenderThread();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.DISTANCE_TO_ORIGIN);
        int faces = 16_384;
        double[] vertices = new double[faces * 4 * MeshHandle.STRIDE];
        for (int face = 0; face < faces; face++) {
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = (face * 4 + vertex) * MeshHandle.STRIDE;
                vertices[offset] = vertex == 1 || vertex == 2 ? .01 : 0;
                vertices[offset + 1] = vertex >= 2 ? .01 : 0;
                vertices[offset + 2] = face % 97;
                vertices[offset + 3] = 1;
                vertices[offset + 6] = 1;
            }
        }
        var material = RenderType.entityTranslucent(ResourceLocation.parse("omni-morph:allocation-test"));
        var semantics = new DrawSemantics(material, null, DrawSemantics.Layer.PRIMARY, "world", 0, faces,
                0, 0, 1, 1, 1, 1, 0, true);
        var batch = new DrawBatch(AccelerationService.INSTANCE.capture(new AccelerationOwner(), "world"),
                new MeshHandle(vertices, 4), new PoseSnapshot(FiguraMat4.of(), FiguraMat3.of()), semantics);
        List<DrawBatch> batches = List.of(batch);
        var backend = new OpenGlAccelerationBackend();
        var centers = new org.joml.Vector3f[faces];
        for (int face = 0; face < faces; face++) {
            centers[face] = new org.joml.Vector3f(.005f, .005f, face % 97);
        }
        for (VertexSorting sorting : List.of(VertexSorting.DISTANCE_TO_ORIGIN, VertexSorting.ORTHOGRAPHIC_Z)) {
            RenderSystem.setProjectionMatrix(new Matrix4f(), sorting);
            int[] expected = sorting.sort(centers);
            long[] actual = backend.faceOrder(batches);
            for (int face = 0; face < faces; face++) {
                if ((int) actual[face] != expected[face]) {
                    throw new AssertionError("Production sort differs from vanilla, including equal-key order");
                }
            }
        }
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.DISTANCE_TO_ORIGIN);
        for (int warmup = 0; warmup < 20; warmup++) {
            backend.faceOrder(batches);
        }
        var allocations = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocations.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long before = allocations.getThreadAllocatedBytes(thread);
        for (int frame = 0; frame < 30; frame++) {
            long[] order = backend.faceOrder(batches);
            if ((int) order[0] % 97 != 96) {
                throw new AssertionError("Transparent faces must remain sorted far to near");
            }
        }
        long allocated = allocations.getThreadAllocatedBytes(thread) - before;
        if (allocated > 256 * 1024) {
            throw new AssertionError("Production face sorting allocated " + allocated
                    + " bytes across 30 warmed large-model draws; workspace reuse is required");
        }
        System.out.println("PASS warmed production transparent sort allocation bytes=" + allocated);
    }
}
