package top.aihmc.omnimorph.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import top.aihmc.omnimorph.gui.FiguraGuiEntityRenderer;
import top.aihmc.omnimorph.gui.FiguraPortraitRenderer;
import top.aihmc.omnimorph.gui.PipRenderStateScope;
import top.aihmc.omnimorph.model.library.LibraryFiguraPreviewRenderer;

/** Keep native PIP resource ownership while protecting OMP's inherited preparation paths. */
@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureRendererMixin {
    @Shadow @Final private SubmitNodeStorage submitNodeStorage;
    @Unique private boolean figura$guardActive;
    @Unique private boolean figura$nativeMatrixPushed;

    @WrapMethod(method = "prepare")
    private void figura$prepareOwnedPip(PictureInPictureRenderState state, GuiRenderState gui,
                                       FeatureRenderDispatcher dispatcher, int guiScale, Operation<Void> original) {
        Object renderer = this;
        if (!(renderer instanceof FiguraGuiEntityRenderer)
                && !(renderer instanceof FiguraPortraitRenderer)
                && !(renderer instanceof LibraryFiguraPreviewRenderer)) {
            original.call(state, gui, dispatcher, guiScale);
            return;
        }

        boolean previousGuard = figura$guardActive;
        boolean previousPush = figura$nativeMatrixPushed;
        figura$guardActive = true;
        figura$nativeMatrixPushed = false;
        try (var scope = new PipRenderStateScope()) {
            try {
                original.call(state, gui, dispatcher, guiScale);
            } finally {
                if (figura$nativeMatrixPushed) {
                    com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().popMatrix();
                }
                submitNodeStorage.getSubmitsPerOrder().clear();
            }
        } finally {
            figura$guardActive = previousGuard;
            figura$nativeMatrixPushed = previousPush;
        }
    }

    @WrapOperation(method = "prepare", at = @At(value = "INVOKE",
            target = "Lorg/joml/Matrix4fStack;pushMatrix()Lorg/joml/Matrix4fStack;"))
    private Matrix4fStack figura$trackNativePush(Matrix4fStack stack, Operation<Matrix4fStack> original) {
        Matrix4fStack result = original.call(stack);
        if (figura$guardActive) {
            figura$nativeMatrixPushed = true;
        }
        return result;
    }

    @WrapOperation(method = "prepare", at = @At(value = "INVOKE",
            target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"))
    private Matrix4fStack figura$trackNativePop(Matrix4fStack stack, Operation<Matrix4fStack> original) {
        Matrix4fStack result = original.call(stack);
        if (figura$guardActive) {
            figura$nativeMatrixPushed = false;
        }
        return result;
    }
}
