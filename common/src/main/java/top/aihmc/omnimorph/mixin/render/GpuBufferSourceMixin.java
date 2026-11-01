package top.aihmc.omnimorph.mixin.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.aihmc.omnimorph.render.acceleration.GpuBufferQueue;

@Mixin(MultiBufferSource.BufferSource.class)
public abstract class GpuBufferSourceMixin implements GpuBufferQueue.Owner {
    @Unique
    private final GpuBufferQueue omniMorph$queue = new GpuBufferQueue();

    @Override
    public GpuBufferQueue omniMorph$gpuQueue() {
        return omniMorph$queue;
    }

    @Inject(method = "getBuffer", at = @At("HEAD"))
    private void omniMorph$beforeNativeVertices(RenderType material,
                                                CallbackInfoReturnable<VertexConsumer> cir) {
        if (omniMorph$queue.contains(material)) {
            // Mixed native/GPU geometry must participate in one vanilla quad sort.
            omniMorph$queue.emitNative((MultiBufferSource) (Object) this, material);
        }
    }

    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V",
            at = @At("HEAD"))
    private void omniMorph$mergeRetainedConsumer(RenderType material, BufferBuilder vertices, CallbackInfo ci) {
        if (((GpuBufferBuilderAccessor) vertices).omniMorph$vertexCount() != 0) {
            // A retained native consumer may have written without another getBuffer call.
            omniMorph$queue.emitNative(vertices, material);
        }
    }

    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V",
            at = @At("RETURN"))
    private void omniMorph$afterNativeBatch(RenderType material, BufferBuilder vertices, CallbackInfo ci) {
        omniMorph$queue.flush(material);
    }
}
