package top.aihmc.omnimorph.mixin.render.feature;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aihmc.omnimorph.ducks.FiguraSubmitCallBackExtension;
import top.aihmc.omnimorph.model.rendering.FiguraVertexSource;

import java.util.List;

/** Model and Model.Simple submissions now share the native feature preparation path. */
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin extends RenderTypeFeatureRenderer<ModelFeatureRenderer.Submit<?>> {
    @Unique private FiguraVertexSource figura$vertices;

    @WrapMethod(method = "buildGroup")
    private void figura$withGroupCallbacks(FeatureFrameContext context, List<ModelFeatureRenderer.Submit<?>> submits,
                                           Operation<Void> original) {
        var previousVertices = figura$vertices;
        figura$vertices = FiguraVertexSource.nativePreparation(this::getVertexBuilder);
        try {
            original.call(context, submits);
        } finally {
            figura$vertices = previousVertices;
        }
    }

    @Inject(method = "prepareModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V"), cancellable = true)
    private <S> void figura$beforeSetupAnim(ModelFeatureRenderer.Submit<S> submit, CallbackInfo ci) {
        var callbacks = (FiguraSubmitCallBackExtension) (Object) submit;
        if (callbacks.figura$getPreventAnimSetup()) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "prepareModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void figura$renderWithCallbacks(Model<?> model, PoseStack poseStack, VertexConsumer vertices,
                                            int light, int overlay, int color, Operation<Void> original,
                                            @Local(argsOnly = true) ModelFeatureRenderer.Submit<?> submit) {
        var callbacks = (FiguraSubmitCallBackExtension) (Object) submit;
        boolean render = true;
        for (var callback : callbacks.figura$getPreRenderingCallbacks()) {
            if (!callback.apply(figura$vertices, poseStack)) {
                render = false;
            }
        }
        callbacks.figura$getPreRenderingCallbacks().clear();

        if (render) {
            original.call(model, poseStack, vertices, light, overlay, color);
        }
        for (var callback : callbacks.figura$getPostRenderingCallbacks()) {
            callback.run();
        }
        callbacks.figura$getPostRenderingCallbacks().clear();
    }
}
