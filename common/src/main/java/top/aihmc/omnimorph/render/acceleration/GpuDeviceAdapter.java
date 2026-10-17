package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import java.nio.ByteBuffer;

/** Device operations are isolated so fast tests can exercise failures/ownership without claiming GPU pixels. */
interface GpuDeviceAdapter {
    interface Resource extends AutoCloseable {
        long bytes();

        @Override
        void close();
    }

    interface Mesh extends Resource {
    }

    interface Pose extends Resource {
    }

    record Material(RenderPipeline original, RenderPipeline accelerated) {
    }

    void assertThread();

    boolean isCurrent();

    Material compile(RenderPipeline original);

    Mesh uploadGeometry(ByteBuffer vertices, ByteBuffer indices);

    Pose uploadPose(ByteBuffer data);

    /** Marks the draw immediately before issuing it: an uncertain/partially submitted draw must not be replayed. */
    void draw(Material material, Mesh mesh, Pose pose, GpuDrawContext context,
              int firstIndex, int indexCount, Runnable issuingDraw);

    GpuFence fence();

    /** Bounded activation-only diagnostic draw/readback. Never called in the per-model frame path. */
    void selfTest();
}
