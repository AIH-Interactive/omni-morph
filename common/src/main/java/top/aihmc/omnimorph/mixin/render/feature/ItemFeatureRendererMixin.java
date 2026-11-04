package top.aihmc.omnimorph.mixin.render.feature;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.aihmc.omnimorph.ducks.FiguraSubmitCallBackExtension;
import top.aihmc.omnimorph.model.rendering.FiguraVertexSource;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin extends RenderTypeFeatureRenderer<ItemFeatureRenderer.Submit> {
    @Unique
    private final Set<ItemFeatureRenderer.Submit> figura$cancelled = Collections.newSetFromMap(new IdentityHashMap<>());

    @Unique private FiguraVertexSource figura$vertices;

    @WrapMethod(method = "buildGroup")
    private void figura$withGroupCallbacks(FeatureFrameContext context, List<ItemFeatureRenderer.Submit> submits,
                                           Operation<Void> original) {
        var previousVertices = figura$vertices;
        figura$vertices = FiguraVertexSource.nativePreparation(this::getVertexBuilder);
        try {
            original.call(context, submits);
        } finally {
            figura$cancelled.clear();
            figura$vertices = previousVertices;
        }
    }

    @WrapMethod(method = "prepareSubmit")
    private void figura$prepareWithCallbacks(ItemFeatureRenderer.Submit submit, boolean foil,
                                             Operation<Void> original) {
        if (foil) {
            if (!figura$cancelled.remove(submit)) {
                original.call(submit, true);
            }
            return;
        }

        var callbacks = (FiguraSubmitCallBackExtension) (Object) submit;
        PoseStack poseStack = new PoseStack();
        poseStack.last().set(submit.pose());
        boolean render = true;
        for (var callback : callbacks.figura$getPreRenderingCallbacks()) {
            if (!callback.apply(figura$vertices, poseStack)) {
                render = false;
            }
        }
        callbacks.figura$getPreRenderingCallbacks().clear();

        if (render) {
            original.call(submit, false);
        } else {
            // Native preparation visits the same submission again for its foil geometry.
            figura$cancelled.add(submit);
        }
        for (var callback : callbacks.figura$getPostRenderingCallbacks()) {
            callback.run();
        }
        callbacks.figura$getPostRenderingCallbacks().clear();
    }
}
