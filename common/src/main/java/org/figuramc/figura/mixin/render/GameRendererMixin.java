package org.figuramc.figura.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.ducks.GameRendererAccessor;
import org.figuramc.figura.lua.api.ClientAPI;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.utils.EntityUtils;
import org.figuramc.figura.utils.RenderUtils;
import top.aihmc.omnimorph.model.library.LibraryPreviewBinding;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;
import top.aihmc.omnimorph.render.RenderLifetime;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Matrix3f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin implements GameRendererAccessor {

    @Shadow @Final Minecraft minecraft;
    @Shadow PostChain postEffect;
    @Shadow private boolean effectActive;

    @Shadow protected abstract double getFov(Camera camera, float tickDelta, boolean changingFov);
    @Shadow protected abstract void loadEffect(ResourceLocation id);
    @Shadow public abstract void checkEntityPostEffect(Entity entity);

    @Shadow protected abstract void bobHurt(PoseStack poseStack, float f);

    @Shadow protected abstract void bobView(PoseStack poseStack, float f);

    @Shadow public abstract Minecraft getMinecraft();

    @Shadow private int tick;
    @Shadow @Final private Camera mainCamera;
    @Unique
    private boolean avatarPostShader = false;
    @Unique
    private boolean hasShaders;

    @Inject(method = "render", at = @At("HEAD"))
    private void figura$beginYsmRenderFrame(float tickDelta, long finishTimeNano, boolean tick, CallbackInfo ci) {
        YsmRenderFrameClock.beginFrame();
        RenderLifetime.INSTANCE.beginFrame();
    }

    @WrapMethod(method = "render")
    private void figura$retireCompositionFrames(float tickDelta, long finishTimeNano, boolean tick, Operation<Void> original) {
        try {
            original.call(tickDelta, finishTimeNano, tick);
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

    @Inject(method = "close", at = @At("HEAD"))
    private void figura$closeLibraryPreviewFrames(CallbackInfo ci) {
        LibraryPreviewBinding.finishFrame();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;normal()Lorg/joml/Matrix3f;"))
    private void onCameraRotation(float tickDelta, long finishTimeNano, PoseStack view, CallbackInfo ci) {
        Avatar avatar = top.aihmc.omnimorph.model.outfit.VisibleAvatar.forEntity(this.minecraft.getCameraEntity() == null
                ? this.minecraft.player : this.minecraft.getCameraEntity());
        if (!RenderUtils.vanillaModelAndScript(avatar)) {
            return;
        }

        // The old renderer has already applied camera pitch/yaw. Prepend the custom camera transform
        // before it builds the inverse view rotation, including bobbing moved from the projection.
        Matrix4f cameraRotation = new Matrix4f(view.last().pose());
        PoseStack effects = new PoseStack();
        FiguraVec3 rotation = avatar.luaRuntime.renderer.cameraRot;
        FiguraVec3 offset = avatar.luaRuntime.renderer.cameraOffsetRot;
        float roll = (rotation == null ? 0f : (float) rotation.z)
                + (offset == null ? 0f : (float) offset.z);
        effects.mulPose(Axis.ZP.rotationDegrees(roll));
        FiguraMat4 matrix = avatar.luaRuntime.renderer.cameraMat;
        if (matrix != null) {
            effects.last().pose().set(matrix.toMatrix4f());
        }

        if (!hasShaders) {
            bobHurt(effects, tickDelta);
            if (minecraft.options.bobView().get()) {
                bobView(effects, tickDelta);
            }
            float scale = minecraft.options.screenEffectScale().get().floatValue();
            float intensity = Mth.lerp(tickDelta, minecraft.player.oSpinningEffectIntensity,
                    minecraft.player.spinningEffectIntensity) * scale * scale;
            if (intensity > 0f) {
                int speed = minecraft.player.hasEffect(MobEffects.CONFUSION) ? 7 : 20;
                float distortion = 5f / (intensity * intensity + 5f) - intensity * .04f;
                distortion *= distortion;
                Vector3f axis = new Vector3f(0, Mth.SQRT_OF_TWO / 2f, Mth.SQRT_OF_TWO / 2f);
                float angle = (tick + tickDelta) * speed * (float) (Math.PI / 180);
                effects.last().pose().rotate(angle, axis).scale(1f / distortion, 1, 1).rotate(-angle, axis);
            }
        }
        view.last().pose().set(effects.last().pose()).mul(cameraRotation);
        view.last().normal().set(new Matrix3f(view.last().pose()).invert().transpose());
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
    private void render(float tickDelta, long finishTimeNano, boolean tick, CallbackInfo ci) {
        Entity entity = this.minecraft.getCameraEntity();
        Avatar avatar = AvatarManager.getAvatar(entity);
        if (!RenderUtils.vanillaModelAndScript(avatar)) {
            if (avatarPostShader) {
                avatarPostShader = false;
                this.checkEntityPostEffect(entity);
            }
            return;
        }

        ResourceLocation resource = avatar.luaRuntime.renderer.postShader;
        if (resource == null) {
            if (avatarPostShader) {
                avatarPostShader = false;
                this.checkEntityPostEffect(entity);
            }
            return;
        }

        try {
            avatarPostShader = true;
            this.effectActive = true;
            if ((this.postEffect == null || !this.postEffect.getName().equals(resource.toString()))
                    && this.getMinecraft().getResourceManager().getResource(resource).isPresent()) {
                    this.loadEffect(resource);
                }
        } catch (Exception ignored) {
            this.effectActive = false;
            avatar.luaRuntime.renderer.postShader = null;
        }
    }

    @Inject(method = "checkEntityPostEffect", at = @At("HEAD"), cancellable = true)
    private void checkEntityPostEffect(Entity entity, CallbackInfo ci) {
        if (avatarPostShader) {
            ci.cancel();
        }
    }

    @Inject(method = "pick(F)V", at = @At("RETURN"))
    private void pick(float tickDelta, CallbackInfo ci) {
        FiguraMod.pushProfiler(FiguraMod.MOD_ID);
        FiguraMod.pushProfiler("extendedPick");
        FiguraMod.extendedPickEntity = EntityUtils.getViewedEntity(32);
        FiguraMod.popProfiler(2);
    }

    @Override @Intrinsic
    public double figura$getFov(Camera camera, float tickDelta, boolean changingFov) {
        return this.getFov(camera, tickDelta, changingFov);
    }

    // bobbing fix courtesy of Iris; https://github.com/IrisShaders/Iris/blob/1.20.1/src/main/java/net/irisshaders/iris/mixin/MixinModelViewBobbing.java
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void onRenderLevel(float tickDelta, long finishTimeNano, PoseStack poseStack, CallbackInfo ci) {
        hasShaders = ClientAPI.hasShaderPack();
    }

    @WrapOperation(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"))
    private void figura$stopBobView(GameRenderer instance, PoseStack stack, float f, Operation<Void> original) {
        Avatar avatar = top.aihmc.omnimorph.model.outfit.VisibleAvatar.forEntity(this.minecraft.getCameraEntity() == null ? this.minecraft.player : this.minecraft.getCameraEntity());
        if (!RenderUtils.vanillaModelAndScript(avatar) || hasShaders) {
            original.call(instance, stack, f);
        }
    }

    @WrapOperation(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"))
    private void figura$stopBobHurt(GameRenderer instance, PoseStack stack, float f, Operation<Void> original) {
        Avatar avatar = top.aihmc.omnimorph.model.outfit.VisibleAvatar.forEntity(this.minecraft.getCameraEntity() == null ? this.minecraft.player : this.minecraft.getCameraEntity());
        if (!RenderUtils.vanillaModelAndScript(avatar) || hasShaders) {
            original.call(instance, stack, f);
        }
    }


    @WrapOperation(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 1))
    private<T> T figura$disableConfusionOnMatrix(OptionInstance<T> instance, Operation<T> original) {
        Avatar avatar = top.aihmc.omnimorph.model.outfit.VisibleAvatar.forEntity(this.minecraft.getCameraEntity() == null ? this.minecraft.player : this.minecraft.getCameraEntity());
        return (!RenderUtils.vanillaModelAndScript(avatar) || hasShaders) ? original.call(instance) : (T) (Object) 0.0;
    }
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void figura$avatarFov(Camera camera, float delta, boolean changingFov,
                                  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Double> cir) {
        float multiplier = top.aihmc.omnimorph.model.rendering.CameraFov.apply(1f,
                top.aihmc.omnimorph.model.outfit.VisibleAvatar.forEntity(camera.getEntity()),
                camera.getEntity() == this.minecraft.player);
        cir.setReturnValue(cir.getReturnValue() * multiplier);
    }

    @WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V"))
    private void figura$withFirstPersonItems(net.minecraft.client.renderer.ItemInHandRenderer renderer,
                                             float delta, PoseStack pose,
                                             net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,
                                             net.minecraft.client.player.LocalPlayer player, int light,
                                             Operation<Void> original) {
        top.aihmc.omnimorph.model.rendering.FirstPersonItemRender.withAvatar(
                top.aihmc.omnimorph.model.outfit.VisibleAvatar.forEntity(player),
                () -> original.call(renderer, delta, pose, buffers, player, light));
    }

}
