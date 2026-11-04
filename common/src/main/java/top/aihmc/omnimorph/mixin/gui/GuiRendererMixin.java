package top.aihmc.omnimorph.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import top.aihmc.omnimorph.ducks.GuiEntityRenderStateExtension;
import top.aihmc.omnimorph.gui.FiguraGuiEntityRenderer;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.model.library.LibraryPreviewBinding;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(GuiRenderer.class)
public class GuiRendererMixin {

    @WrapMethod(method = "render")
    private void figura$retireLibraryPreviewFrames(
            Operation<Void> original) {
        try {
            original.call();
        } finally {
            LibraryPreviewBinding.finishFrame();
        }
    }

    @Shadow @Final
    GuiRenderState renderState;

    @Shadow @Final
    private FeatureRenderDispatcher featureRenderDispatcher;

    @Unique
    FiguraGuiEntityRenderer figura$paperDollRenderer;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void initializePaperDollRenderer(CallbackInfo ci) {
        figura$paperDollRenderer = new FiguraGuiEntityRenderer(Minecraft.getInstance().getEntityRenderDispatcher());
    }

    @Group(name = "figura$paperDoll", min = 1, max = 1)
    @Inject(method = "preparePictureInPictureState(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void renderPaperDoll(
            PictureInPictureRenderState state, int guiScale, CallbackInfo ci) {
        if (figura$preparePaperDoll(state, guiScale)) {
            ci.cancel();
        }
    }

    // NeoForge adds a pool-selection flag and returns whether the state was prepared.
    @Group(name = "figura$paperDoll", min = 1, max = 1)
    @Inject(method = "preparePictureInPictureState(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;IZ)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void figura$renderPaperDollNeoForge(
            PictureInPictureRenderState state, int guiScale, boolean firstPass,
            CallbackInfoReturnable<Boolean> cir) {
        if (figura$preparePaperDoll(state, guiScale)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private boolean figura$preparePaperDoll(PictureInPictureRenderState state, int guiScale) {
        if (state instanceof GuiEntityRenderStateExtension extension
                && extension.getRenderMode() == EntityRenderMode.PAPERDOLL) {
            figura$paperDollRenderer.prepare((GuiEntityRenderState) state, this.renderState,
                    this.featureRenderDispatcher, guiScale);
            return true;
        }
        return false;
    }


    @Inject(method = "close", at = @At("TAIL"))
    private void onClose(CallbackInfo ci) {
        LibraryPreviewBinding.finishFrame();
        figura$paperDollRenderer.close();
    }
}
