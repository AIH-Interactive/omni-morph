package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;

/** Both engines submit through the same material-order boundary; unsupported sources remain on Java. */
public final class GpuSubmission {
    @FunctionalInterface
    public interface JavaEmitter {
        void emit(VertexConsumer vertices, int firstVertex, int vertexCount);
    }

    private GpuSubmission() {
    }

    /** Returning false leaves ownership of the batch and its fallback entirely with the caller. */
    public static boolean enqueue(MultiBufferSource source, DrawBatch batch, JavaEmitter fallback) {
        if (!(source instanceof GpuBufferQueue.Owner owner)
                || !AccelerationService.INSTANCE.canDraw(batch)
                || !owner.omniMorph$gpuQueue().hasCapacity(batch.semantics().material(), batch)) {
            return false;
        }
        // Reserve a native builder even for a GPU-only material, so normal endBatch visits its queue.
        if (!owner.omniMorph$gpuQueue().contains(batch.semantics().material())) {
            var vertices = source.getBuffer(batch.semantics().material());
            if (!(vertices instanceof top.aihmc.omnimorph.mixin.render.GpuBufferBuilderAccessor builder)
                    || builder.omniMorph$vertexCount() != 0) {
                return false;
            }
        }
        owner.omniMorph$gpuQueue().add(batch, fallback);
        return true;
    }
}
