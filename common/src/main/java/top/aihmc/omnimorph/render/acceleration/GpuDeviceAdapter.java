package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.rendertype.RenderType;

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

    interface Indices extends Resource {
    }

    record Material(RenderPipeline original, RenderPipeline accelerated) {
    }

    void assertThread();

    boolean isCurrent();

    Material compile(RenderPipeline original);

    default boolean supportsRigidGroups() {
        return false;
    }

    default Material compileRigidGroup(RenderPipeline original) {
        throw new UnsupportedOperationException("Rigid group pipeline unavailable");
    }

    /**
     * Compiles a pose block variant holding {@code parts} parts (one of {@link GpuBatchData#PART_TIERS}).
     * Devices without smaller variants keep using the full layout.
     */
    default Material compileVariant(RenderPipeline original, boolean grouped, int parts) {
        throw new UnsupportedOperationException("Pose block variants unavailable");
    }

    /** Limits of pose storage blocks once probing and the self-test passed; null keeps uniform pose blocks. */
    default GpuStorageSupport.Limits storageLimits() {
        return null;
    }

    /** One frame arena of pose storage blocks; draws bind ranges of it. */
    interface Storage extends Resource {
    }

    /**
     * A pose living in the frame arena instead of its own buffer. The backend sets {@link #storage} after
     * flushing the arena and before every draw; the arena owns the buffer, so closing this pose does nothing.
     */
    final class StoragePose implements Pose {
        final GpuPoseArena.Slice pose;
        final GpuPoseArena.Slice remap;
        /** R4-3 model draws only: one record per batch. */
        final GpuPoseArena.Slice records;
        Storage storage;

        StoragePose(GpuPoseArena.Slice pose, GpuPoseArena.Slice remap) {
            this(pose, remap, null);
        }

        StoragePose(GpuPoseArena.Slice pose, GpuPoseArena.Slice remap, GpuPoseArena.Slice records) {
            this.pose = pose;
            this.remap = remap;
            this.records = records;
        }

        @Override
        public long bytes() {
            return pose.bytes() + (remap == null ? 0 : remap.bytes()) + (records == null ? 0 : records.bytes());
        }

        @Override
        public void close() {
        }
    }

    default Storage createStorage(long capacity) {
        throw new UnsupportedOperationException("Pose storage blocks unavailable");
    }

    /**
     * Writes {@code data} at {@code offset} of an arena. The region is either in an arena whose fence completed
     * or not yet bound by any draw of the current frame.
     */
    default void writeStorage(Storage storage, long offset, ByteBuffer data) {
        throw new UnsupportedOperationException("Pose storage blocks unavailable");
    }

    /** Compiles the R4-3 model variant; only called when {@link #storageLimits()} reports model support. */
    default Material compileStorageModel(RenderPipeline original) {
        throw new UnsupportedOperationException("Model storage draws unavailable");
    }

    /** Compiles the storage block variant of an entity pipeline; only called when {@link #storageLimits()} is set. */
    default Material compileStorage(RenderPipeline original, boolean grouped) {
        throw new UnsupportedOperationException("Pose storage blocks unavailable");
    }

    Mesh uploadGeometry(ByteBuffer vertices, ByteBuffer indices);

    Pose uploadPose(ByteBuffer data);

    /** Returns false when this device does not support updating a retired pose buffer. */
    default boolean updatePose(Pose pose, ByteBuffer data) {
        return false;
    }

    default Indices uploadIndices(ByteBuffer data) {
        throw new UnsupportedOperationException("Sorted index upload unavailable");
    }

    GpuSubmission.Scope openScope(RenderType material);

    default void drawJava(RenderType material, MeshData geometry) {
        material.draw(geometry);
    }

    /** Marks the draw immediately before issuing it: an uncertain/partially submitted draw must not be replayed. */
    void draw(Material material, Mesh mesh, Pose pose, GpuDrawContext context,
              int firstIndex, int indexCount, Runnable issuingDraw);

    default void drawWithIndices(Material material, Mesh mesh, Pose pose, Indices indices, GpuDrawContext context,
                                 int firstIndex, int indexCount, Runnable issuingDraw) {
        throw new UnsupportedOperationException("Sorted rigid group drawing unavailable");
    }

    /**
     * One render pass for consecutive draws of the same material: pipeline, scissor, default uniforms and textures
     * are set once; each draw only binds its buffers and pose. No buffer may be written while it is open.
     */
    interface DrawPass extends AutoCloseable {
        Material material();

        /** Marks the draw immediately before issuing it, like {@link #draw}. */
        void draw(Mesh mesh, Pose pose, Indices indices, int firstIndex, int indexCount, Runnable issuingDraw);

        @Override
        void close();
    }

    /** Devices without a shared pass issue each draw through {@link #draw} / {@link #drawWithIndices}. */
    default DrawPass openDrawPass(Material material, GpuDrawContext context) {
        GpuDeviceAdapter device = this;
        return new DrawPass() {
            @Override
            public Material material() {
                return material;
            }

            @Override
            public void draw(Mesh mesh, Pose pose, Indices indices, int firstIndex, int indexCount, Runnable issuingDraw) {
                if (indices == null) {
                    device.draw(material, mesh, pose, context, firstIndex, indexCount, issuingDraw);
                } else {
                    device.drawWithIndices(material, mesh, pose, indices, context, firstIndex, indexCount, issuingDraw);
                }
            }

            @Override
            public void close() {
            }
        };
    }

    GpuFence fence();

    /** Bounded activation-only diagnostic draw/readback. Never called in the per-model frame path. */
    void selfTest();
}
