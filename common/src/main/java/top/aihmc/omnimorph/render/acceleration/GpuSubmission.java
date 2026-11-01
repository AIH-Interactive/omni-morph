package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;

/** GPU submission is unavailable on 1.21.1; callers retain their native Java emission path. */
public final class GpuSubmission {
    @FunctionalInterface
    public interface JavaEmitter {
        void emit(VertexConsumer vertices, int firstVertex, int vertexCount);
    }

    private GpuSubmission() {
    }

    /** Returning false leaves ownership of the batch and its fallback entirely with the caller. */
    public static boolean enqueue(MultiBufferSource source, DrawBatch batch, JavaEmitter fallback) {
        return false;
    }
}
