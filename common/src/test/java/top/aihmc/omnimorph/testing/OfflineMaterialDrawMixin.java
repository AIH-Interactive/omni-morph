package top.aihmc.omnimorph.testing;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after the production placeholder interceptor; ordinary Java geometry goes to the test device. */
@Mixin(value = PreparedRenderType.class, priority = 900)
public abstract class OfflineMaterialDrawMixin {
    @Inject(method = "draw", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/RenderPass;pushDebugGroup(Ljava/util/function/Supplier;)V"),
            cancellable = true)
    private void recordJavaDraw(StagedVertexBuffer.ExecuteInfo info, RenderPass pass,
                                RenderPipeline pipeline, CallbackInfo callback) {
        NativeFeatureVertices.drawJava();
        callback.cancel();
    }
}
