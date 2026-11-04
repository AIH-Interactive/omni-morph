package top.aihmc.omnimorph.mixin.render.feature;

import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aihmc.omnimorph.ducks.DeferredMaterialDrawExtension;

@Mixin(PreparedRenderType.class)
public abstract class PreparedRenderTypeMixin implements DeferredMaterialDrawExtension {
    @Unique private java.util.function.BiConsumer<com.mojang.renderpearl.api.commands.RenderPass,
            com.mojang.renderpearl.api.pipeline.RenderPipeline> figura$deferredDraw;
    @Unique private final java.util.Set<com.mojang.renderpearl.api.pipeline.RenderPipeline> figura$executedStages =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    @Unique private boolean figura$hasDeferredDraw;

    @Override
    public void figura$setDeferredDraw(java.util.function.BiConsumer<com.mojang.renderpearl.api.commands.RenderPass,
            com.mojang.renderpearl.api.pipeline.RenderPipeline> draw) {
        figura$deferredDraw = draw;
        figura$hasDeferredDraw = true;
        figura$executedStages.clear();
    }

    @Inject(method = "draw",
            at = @At("HEAD"), cancellable = true)
    private void figura$drawDeferred(StagedVertexBuffer.ExecuteInfo info,
            com.mojang.renderpearl.api.commands.RenderPass pass,
            com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline, CallbackInfo ci) {
        if (figura$hasDeferredDraw) {
            // OIT legitimately executes one prepared material once per distinct stage pipeline.
            ci.cancel();
            if (figura$deferredDraw != null && figura$executedStages.add(pipeline)) {
                figura$deferredDraw.accept(pass, pipeline);
            }
        }
    }
}
