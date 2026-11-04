package top.aihmc.omnimorph.mixin.render.nodeRenderer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.apache.commons.lang3.function.TriFunction;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aihmc.omnimorph.ducks.FiguraSubmitCallBackExtension;
import top.aihmc.omnimorph.ducks.FlameSubmitExtension;
import top.aihmc.omnimorph.ducks.NodeCollectorExtension;
import top.aihmc.omnimorph.model.rendering.FiguraVertexSource;
import top.aihmc.omnimorph.model.rendering.nodeRenderer.FiguraSubmission;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin implements NodeCollectorExtension {
    @Shadow
    @Final
    public SimpleFeatureRenderPhase solid;

    @Override
    public <S extends EntityRenderState> void submitFiguraModel(Avatar avatar, S renderState,
            TriFunction<Avatar, S, FiguraVertexSource, Void> renderer) {
        solid.submit(new FiguraSubmission(avatar, renderState,
                (submittedAvatar, submittedState, vertices) -> renderer.apply(submittedAvatar, (S) submittedState, vertices)));
    }

    @Inject(method = "submitModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;waterMask()Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private <S> void figura$onSubmitModel(Model<? super S> model, S state, PoseStack poseStack,
                                         RenderType renderType, int light, int overlay, int color,
                                         @Nullable net.minecraft.client.renderer.texture.UvMapping sprite, int outlineColor,
                                         CallbackInfo ci, @Local ModelFeatureRenderer.Submit<S> submit) {
        var destination = (FiguraSubmitCallBackExtension) (Object) submit;
        // OrderedSubmitNodeCollector now wraps submitted ModelParts in Model.Simple.
        if (model instanceof Model.Simple simple) {
            figura$transferCallbacks((FiguraSubmitCallBackExtension) (Object) simple.root(), destination);
        }
        figura$transferCallbacks((FiguraSubmitCallBackExtension) model, destination);
    }

    @ModifyExpressionValue(method = "submitItem", at = @At(value = "NEW",
            target = "net/minecraft/client/renderer/feature/ItemFeatureRenderer$Submit", ordinal = 0))
    private ItemFeatureRenderer.Submit figura$onSubmitItem(ItemFeatureRenderer.Submit submit) {
        figura$transferCallbacks((FiguraSubmitCallBackExtension) (Object) submit.displayContext(),
                (FiguraSubmitCallBackExtension) (Object) submit);
        return submit;
    }

    @ModifyExpressionValue(method = "submitFlame", at = @At(value = "NEW",
            target = "net/minecraft/client/renderer/feature/FlameFeatureRenderer$Submit"))
    private FlameFeatureRenderer.Submit figura$onSubmitFlame(FlameFeatureRenderer.Submit submit) {
        ((FlameSubmitExtension) (Object) submit).figura$setAvatar(AvatarManager.getAvatar(submit.entityRenderState()));
        return submit;
    }

    @Unique
    private static void figura$transferCallbacks(FiguraSubmitCallBackExtension source,
                                                  FiguraSubmitCallBackExtension destination) {
        for (var callback : source.figura$getPreRenderingCallbacks()) {
            destination.figura$addPreRenderingCallback(callback);
        }
        for (var callback : source.figura$getPostRenderingCallbacks()) {
            destination.figura$addPostRenderingCallback(callback);
        }
        destination.figura$setPreventAnimSetup(source.figura$getPreventAnimSetup());
        source.figura$setPreventAnimSetup(false);
        source.figura$getPreRenderingCallbacks().clear();
        source.figura$getPostRenderingCallbacks().clear();
    }
}
