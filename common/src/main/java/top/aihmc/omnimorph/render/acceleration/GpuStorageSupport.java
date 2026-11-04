package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;

/**
 * Native texel-buffer channel for the existing pose arena. All buffers and bindings belong to Blaze3D.
 * The activation pixel diagnostic validates the channel before these limits enable storage draws.
 */
final class GpuStorageSupport {
    static final int OFFSETS_BYTES = 16;
    static final long MAX_ARENA_BYTES = 16L * 1024 * 1024;

    /** {@code models}: the same data channel also carries the per-batch model records. */
    record Limits(int offsetAlignment, long maxBlockBytes, boolean models) {
    }

    private static volatile String unavailableReason = "not probed";

    private GpuStorageSupport() {
    }

    /** Pose storage blocks stay opt-in (hardware acceleration settings) until acceptance decides the default. */
    static boolean requested() {
        return AccelerationOptions.storageBlocks();
    }

    static String unavailableReason() {
        return unavailableReason;
    }

    /** Native allocation/alignment limits; actual shader and pixel checks still gate activation. */
    static Limits probe() {
        var limits = RenderSystem.getDevice().getDeviceInfo().limits();
        int nativeAlignment = limits.minUniformOffsetAlignment();
        long maxBlock = Math.min(MAX_ARENA_BYTES, limits.maxMemoryAllocationSize()) & -16L;
        if (nativeAlignment <= 0 || Integer.bitCount(nativeAlignment) != 1 || maxBlock < GpuPoseArena.MIN_CAPACITY) {
            return unavailable("native storage limits alignment=" + nativeAlignment + " maxAllocation="
                    + limits.maxMemoryAllocationSize());
        }
        unavailableReason = null;
        return new Limits(Math.max(16, nativeAlignment), maxBlock, true);
    }

    static void writeOffsets(ByteBuffer target, GpuPoseArena.Slice pose,
                             GpuPoseArena.Slice remap, GpuPoseArena.Slice records) {
        target.putInt(pose.offset() / 16);
        target.putInt(remap == null ? 0 : remap.offset() / 16);
        target.putInt(records == null ? 0 : records.offset() / 16);
        target.putInt(0);
    }

    static void bind(RenderPass pass, GpuBuffer arena, GpuPoseArena.Slice offsets) {
        // GL's texel binding ignores slice offsets, while Vulkan uses them. Bind the same whole arena on both.
        pass.setUniform(GpuShaderProgram.STORAGE_DATA, arena);
        pass.setUniform(GpuShaderProgram.STORAGE_OFFSETS, arena.slice(offsets.offset(), OFFSETS_BYTES));
    }

    private static Limits unavailable(String reason) {
        unavailableReason = reason;
        return null;
    }
}
