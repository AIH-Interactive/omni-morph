package org.figuramc.figura.mixin.render.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import org.figuramc.figura.ducks.FiguraSubmitCallBackExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
public class ItemFeatureRendererMixin {

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void figura$preRender(MultiBufferSource.BufferSource bufferSource,
                                  OutlineBufferSource outlineBufferSource,
                                  SubmitNodeStorage.ItemSubmit itemSubmit,
                                  CallbackInfo ci) {
        FiguraSubmitCallBackExtension callBackExtension = (FiguraSubmitCallBackExtension) (Object) itemSubmit;

        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(itemSubmit.pose().pose());
        poseStack.last().normal().set(itemSubmit.pose().normal());

        for (var callback : callBackExtension.figura$getPreRenderingCallbacks()) {
            if (!callback.apply(bufferSource, poseStack)) {
                ci.cancel();
            }
        }

        if (ci.isCancelled()) {
            for (var callback : callBackExtension.figura$getPostRenderingCallbacks())
                callback.run();

            callBackExtension.figura$getPostRenderingCallbacks().clear();
        }

        callBackExtension.figura$getPreRenderingCallbacks().clear();
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void figura$postRender(MultiBufferSource.BufferSource bufferSource,
                                   OutlineBufferSource outlineBufferSource,
                                   SubmitNodeStorage.ItemSubmit itemSubmit,
                                   CallbackInfo ci) {
        FiguraSubmitCallBackExtension callBackExtension = (FiguraSubmitCallBackExtension) (Object) itemSubmit;
        for (var callback : callBackExtension.figura$getPostRenderingCallbacks())
            callback.run();
    }
}
