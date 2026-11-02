package top.aihmc.omnimorph.mixin.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.aihmc.omnimorph.render.acceleration.GpuBufferQueue;

@Mixin(MultiBufferSource.BufferSource.class)
public abstract class GpuBufferSourceMixin implements GpuBufferQueue.Owner {
    @Unique
    private final GpuBufferQueue omniMorph$queue = new GpuBufferQueue();

    @Unique
    private boolean omniMorph$endingAllBatches;

    @Override
    public GpuBufferQueue omniMorph$gpuQueue() {
        return omniMorph$queue;
    }

    @Inject(method = "getBuffer", at = @At("HEAD"))
    private void omniMorph$beforeNativeVertices(RenderType material,
                                                CallbackInfoReturnable<VertexConsumer> cir) {
        if (!omniMorph$endingAllBatches && omniMorph$queue.contains(material)) {
            // Mixed native/GPU geometry must participate in one vanilla quad sort.
            omniMorph$queue.emitNative((MultiBufferSource) (Object) this, material);
        }
    }

    @WrapMethod(method = "endBatch()V")
    private void omniMorph$endAllBatches(Operation<Void> original) {
        // The native no-argument flush calls getBuffer only to find its shared builder.
        // That lookup must not turn a GPU-only batch into mixed native geometry.
        boolean previous = omniMorph$endingAllBatches;
        omniMorph$endingAllBatches = true;
        try {
            original.call();
        } finally {
            omniMorph$endingAllBatches = previous;
        }
    }

    @WrapOperation(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;end(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lcom/mojang/blaze3d/vertex/VertexSorting;)V"))
    private void omniMorph$endMaterial(RenderType material, BufferBuilder vertices, VertexSorting sorting,
                                      Operation<Void> original) {
        if (((GpuBufferBuilderAccessor) vertices).omniMorph$vertexCount() != 0) {
            // A retained native consumer may have written without another getBuffer call.
            omniMorph$queue.emitNative(vertices, material);
        }
        original.call(material, vertices, sorting);
        omniMorph$queue.flush(material, vertices);
    }
}
