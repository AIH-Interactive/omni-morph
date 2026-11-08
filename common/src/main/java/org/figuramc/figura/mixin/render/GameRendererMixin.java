package org.figuramc.figura.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.ducks.GameRendererAccessor;
import top.aihmc.omnimorph.gui.FiguraGuiRenderer;
import top.aihmc.omnimorph.model.library.LibraryFiguraPreviewRenderer;
import top.aihmc.omnimorph.model.library.LibraryPreviewBinding;
import top.aihmc.omnimorph.gui.FiguraPortraitRenderer;
import org.figuramc.figura.lua.api.ClientAPI;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.math.vector.FiguraVec3;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;
import top.aihmc.omnimorph.render.RenderLifetime;
import org.figuramc.figura.utils.EntityUtils;
import org.figuramc.figura.utils.RenderUtils;
import org.figuramc.figura.utils.PlatformUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin implements GameRendererAccessor {

    @Shadow @Final
    private Minecraft minecraft;



    @Shadow @Final private Camera mainCamera;

    @Shadow @Final private CrossFrameResourcePool resourcePool;
    @Shadow @Final private GuiRenderer guiRenderer;
    @Shadow @Final private GameRenderState gameRenderState;

    @Shadow abstract void bobHurt(CameraRenderState cameraRenderState, PoseStack poseStack);
    @Shadow abstract void bobView(CameraRenderState cameraRenderState, PoseStack poseStack);

    @Unique
    private boolean hasShaders;

    @Inject(method = "render", at = @At("HEAD"))
    private void figura$beginYsmRenderFrame(CallbackInfo ci) {
        YsmRenderFrameClock.beginFrame();
        RenderLifetime.INSTANCE.beginFrame();
    }

    @WrapMethod(method = "render")
    private void figura$retireCompositionFrames(Operation<Void> original) {
        try {
            original.call();
        } finally {
            // World/hand submissions also need retirement when GUI rendering is skipped or throws.
            try {
                LibraryPreviewBinding.finishFrame();
            } finally {
                try {
                    top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.finishFrame();
                } finally {
                    top.aihmc.omnimorph.lua.api.particle.CustomParticleRuntime.finishFrame();
                }
            }
        }
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void onRenderLevel(CallbackInfo ci) {
        hasShaders = ClientAPI.hasShaderPack();

        CameraRenderState cameraRenderState = this.gameRenderState.levelRenderState.cameraRenderState;
        Matrix4f instance = cameraRenderState.viewRotationMatrix;

        Avatar avatar = AvatarManager.getAvatar(this.minecraft.getCameraEntity() == null ? this.minecraft.player : this.minecraft.getCameraEntity());
        if (!RenderUtils.vanillaModelAndScript(avatar)) {
            return;
        }

        float z = 0f;

        FiguraVec3 rot = avatar.luaRuntime.renderer.cameraRot;
        if (rot != null)
            z = (float) rot.z;

        FiguraVec3 offset = avatar.luaRuntime.renderer.cameraOffsetRot;
        if (offset != null)
            z += (float) offset.z;

        Matrix4f original = new Matrix4f(instance);
        instance.identity();
        instance.rotate(Axis.ZP.rotationDegrees(z));

        FiguraMat4 mat = avatar.luaRuntime.renderer.cameraMat;
        if (mat != null)
            instance.set(mat.toMatrix4f());

        instance.mul(original);
    }

    @Inject(method = "extract", at = @At("TAIL"))
    private void figura$extractPostShader(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (!gameRenderState.shouldRenderLevel) {
            return;
        }
        Avatar avatar = AvatarManager.getAvatar(minecraft.getCameraEntity());
        if (!RenderUtils.vanillaModelAndScript(avatar)) {
            return;
        }
        Identifier resource = avatar.luaRuntime.renderer.postShader;
        if (resource == null) {
            return;
        }
        if (!minecraft.getShaderManager().isPostEffectValid(resource, LevelTargetBundle.MAIN_TARGETS)) {
            avatar.luaRuntime.renderer.postShader = null;
            return;
        }
        // Vanilla now owns post-chain preparation, execution and reload cleanup.
        Identifier spectatorEffect = minecraft.gameRenderer.spectatedEntityPostEffect();
        gameRenderState.requestedPostEffects.remove(spectatorEffect);
        if (!gameRenderState.requestedPostEffects.contains(resource)) {
            gameRenderState.requestedPostEffects.add(resource);
        }
    }
    @Override @Intrinsic
    public double figura$getFov(Camera camera, float tickDelta, boolean changingFov) {
        return camera.getFov();
    }

    @ModifyArg(method = "renderLevel", index = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private PoseStack renderLevelBobHurt(PoseStack stack) {
        Avatar avatar = AvatarManager.getAvatar(this.minecraft.getCameraEntity() == null ? this.minecraft.player : this.minecraft.getCameraEntity());
        if (!RenderUtils.vanillaModelAndScript(avatar) || hasShaders) return stack;
        stack.pushPose();
        stack.last().pose().identity();
        return stack;
    }

    @ModifyArg(method = "<init>", index = 2,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;<init>(Lnet/minecraft/client/renderer/state/gui/GuiRenderState;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;Ljava/util/List;)V"))
    private List<PictureInPictureRenderer<?>> addPortraitRenderer(List<PictureInPictureRenderer<?>> list) {
        // NeoForge supplies registration records here and registers our factories on its event bus.
        if (PlatformUtils.getModLoader() != PlatformUtils.ModLoader.FABRIC) {
            return list;
        }
        List<PictureInPictureRenderer<?>> newList = new ArrayList<>(list);
        newList.add(new FiguraPortraitRenderer());
        newList.add(new FiguraGuiRenderer());
        newList.add(new LibraryFiguraPreviewRenderer());
        return newList;
    }

    @WrapOperation(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void figura$stopBobView(GameRenderer instance, CameraRenderState cameraRenderState, PoseStack stack, Operation<Void> original) {
        Avatar avatar = AvatarManager.getAvatar(this.minecraft.getCameraEntity() == null ? this.minecraft.player : this.minecraft.getCameraEntity());
        if (!RenderUtils.vanillaModelAndScript(avatar) || hasShaders)
            original.call(instance, cameraRenderState, stack);
    }

    @WrapOperation(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void figura$stopBobHurt(GameRenderer instance, CameraRenderState cameraRenderState, PoseStack stack, Operation<Void> original) {
        Avatar avatar = AvatarManager.getAvatar(this.minecraft.getCameraEntity() == null ? this.minecraft.player : this.minecraft.getCameraEntity());
        if (!RenderUtils.vanillaModelAndScript(avatar) || hasShaders)
            original.call(instance, cameraRenderState, stack);
    }

    @Override
    public GuiRenderer figura$getGuiRenderer() {
        return guiRenderer;
    }
}
