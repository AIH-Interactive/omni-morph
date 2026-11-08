package top.aihmc.omnimorph.testing;

import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records ordinary Java geometry at the platform draw boundary. */
@Mixin(value = PreparedRenderType.class, priority = 900)
public abstract class OfflineMaterialDrawMixin {
    @Inject(method = "drawFromBuffer(Lnet/minecraft/client/renderer/StagedVertexBuffer$ExecuteInfo;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/PreparedRenderType;drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V"),
            cancellable = true)
    private void recordJavaDraw(StagedVertexBuffer.ExecuteInfo info, CallbackInfo callback) {
        NativeFeatureVertices.drawJava();
        callback.cancel();
    }
}
