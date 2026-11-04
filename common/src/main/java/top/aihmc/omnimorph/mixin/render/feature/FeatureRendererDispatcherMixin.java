package top.aihmc.omnimorph.mixin.render.feature;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRendererMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.aihmc.omnimorph.model.rendering.nodeRenderer.FiguraFeatureRenderer;
import top.aihmc.omnimorph.model.rendering.nodeRenderer.SameFrameFeaturePreparation;
import top.aihmc.omnimorph.model.rendering.nodeRenderer.FeaturePreparationScope;

@Mixin(FeatureRenderDispatcher.class)
public abstract class FeatureRendererDispatcherMixin {
    @Shadow
    @Final
    private FeatureRendererMap featureRenderers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void figura$registerFeatureRenderer(CallbackInfo ci) {
        featureRenderers.put(FiguraFeatureRenderer.TYPE, new FiguraFeatureRenderer());
    }

    @WrapMethod(method = "prepareFrameWithContext")
    private FeatureRenderDispatcher.PreparedFrame figura$withNativeCollector(FeatureFrameContext context,
            SubmitNodeStorage storage, Operation<FeatureRenderDispatcher.PreparedFrame> original) {
        try (var scope = new FeaturePreparationScope(storage)) {
            return original.call(context, storage);
        }
    }

    @Inject(method = "prepareFrameWithContext", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRendererMap;values()Ljava/lang/Iterable;", ordinal = 1))
    private void figura$prepareLateSubmissions(FeatureFrameContext context, SubmitNodeStorage storage,
                                               CallbackInfoReturnable<FeatureRenderDispatcher.PreparedFrame> cir,
                                               @Local FeatureRenderDispatcher.PreparedFrame frame) {
        SameFrameFeaturePreparation.prepareLateSubmissions(storage, frame, context, featureRenderers);
    }
}
