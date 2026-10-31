package org.figuramc.figura.mixin.render.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.gui.PopupMenu;
import org.figuramc.figura.lua.api.vanilla_model.VanillaPart;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.model.rendering.PartFilterScheme;
import org.figuramc.figura.permissions.Permissions;
import org.figuramc.figura.utils.RenderUtils;
import org.figuramc.figura.utils.ui.UIHelper;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.aihmc.omnimorph.gui.AvatarLoadingRenderer;
import top.aihmc.omnimorph.mixin.render.renderers.LivingEntityRendererAccessor;
import top.aihmc.omnimorph.model.outfit.OutfitNativeRenderer;
import top.aihmc.omnimorph.model.outfit.OutfitRenderBinding;
import top.aihmc.omnimorph.model.ysm.YsmModelRuntime;
import top.aihmc.omnimorph.model.ysm.YsmRenderer;
import top.aihmc.omnimorph.model.ysm.animation.YsmEntityFrame;
import top.aihmc.omnimorph.render.EntityFrame;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> extends EntityRenderer<T> implements RenderLayerParent<T, M> {

    protected LivingEntityRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Shadow @Final protected List<RenderLayer<T, M>> layers;

    @Shadow protected abstract boolean isBodyVisible(T livingEntity);

    @Shadow
    public static int getOverlayCoords(LivingEntity entity, float whiteOverlayProgress) {
        return 0;
    }

    @Shadow protected abstract float getWhiteOverlayProgress(T entity, float tickDelta);

    @Unique
    private Avatar currentAvatar;
    @Unique
    private Matrix4f lastPose;
    @Unique
    private EntityFrame currentFrame;

    @Inject(at = @At("HEAD"), method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", cancellable = true)
    private void onRender(T entity, float yaw, float delta, PoseStack poseStack, MultiBufferSource bufferSource, int light, CallbackInfo ci) {
        currentFrame = EntityFrame.begin(entity, delta);
        AvatarLoadingRenderer.render(currentFrame, poseStack, bufferSource, light);
        currentAvatar = AvatarManager.getAvatar(currentFrame);
        if (currentAvatar == null) {
            lastPose = OutfitRenderBinding.composition(currentFrame) == null
                    ? null : new Matrix4f(poseStack.last().pose());
            return;
        }

        if (currentAvatar.isYsmNative()) {
            if (!Avatar.firstPerson) {
                figura$renderYsmNative(currentAvatar, entity, delta, poseStack, bufferSource, light);
            }
            // The first-person matrices pass renders the player from the camera position only to refresh
            // part matrices. A YSM body has no Figura part matrices to refresh, so it draws nothing there.
            figura$finishFrame();
            ci.cancel();
            return;
        }

        lastPose = poseStack.last().pose();
    }

    @ModifyArg(
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
            ),
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            index = 3
    )
    private int customOverlay(int thing) {
        return LivingEntityRendererAccessor.overrideOverlay.orElse(thing);
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER), method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", cancellable = true)
    private void preRender(T entity, float yaw, float delta, PoseStack poseStack, MultiBufferSource bufferSource, int light, CallbackInfo ci) {
        if (currentAvatar == null) {
            figura$renderVanillaOutfit(entity, delta, poseStack, bufferSource, light);
            return;
        }

        if (org.figuramc.figura.utils.PlatformUtils.isModLoaded("iris")
                && top.aihmc.omnimorph.compat.IrisCompat.isShadowPass()) {
            // Iris renders entities again with the shadow camera. Capturing/binding the outfit
            // skeleton against that pass can observe a different native part graph and fail the frame.
            currentAvatar = null;
            lastPose = null;
            return;
        }

        M model = getModel();
        if (Avatar.firstPerson) {
            currentAvatar.updateMatrices(model, poseStack);
            currentAvatar = null;
            lastPose = null;
            poseStack.popPose();
            figura$finishFrame();
            ci.cancel();
            return;
        }

        Avatar avatar = currentAvatar;
        figura$transformParts(avatar, model);

        Minecraft minecraft = Minecraft.getInstance();
        boolean showBody = this.isBodyVisible(entity);
        boolean translucent = !showBody && minecraft.player != null && !entity.isInvisibleTo(minecraft.player);
        boolean glowing = !showBody && minecraft.shouldEntityAppearGlowing(entity);
        boolean invisible = !translucent && !showBody && !glowing;

        // When viewed 3rd person, render all non-world parts.
        PartFilterScheme filter = invisible ? PartFilterScheme.PIVOTS : PartFilterScheme.MODEL;
        int overlay = getOverlayCoords(entity, getWhiteOverlayProgress(entity, delta));

        // Populate pivot customizations before the layers run so held items use this frame's transforms.
        avatar.updateMatrices(model, poseStack);

        var outfit = OutfitRenderBinding.composition(currentFrame);
        if (outfit != null) {
            outfit.prepareFiguraBase(avatar);
        }

        Matrix4f entityToDraw = new Matrix4f(lastPose);
        Runnable drawFrame = () -> {
            FiguraMod.pushProfiler(FiguraMod.MOD_ID);
            FiguraMod.pushProfiler(avatar);

            FiguraMod.pushProfiler("calculateMatrix");
            Matrix4f diff = new Matrix4f(entityToDraw).invert().mul(poseStack.last().pose());
            FiguraMat4 poseMatrix = new FiguraMat4().set(diff);

            FiguraMod.popPushProfiler("renderEvent");
            avatar.renderEvent(delta, poseMatrix);

            FiguraMod.popPushProfiler("render");
            Runnable drawBase = () -> avatar.render(entity, yaw, delta, translucent ? 0.15f : 1f, poseStack,
                    bufferSource, light, overlay, model, filter, translucent, glowing);
            if (outfit == null) {
                drawBase.run();
            } else {
                var basePose = outfit.captureFiguraBase(avatar, new Matrix4f(entityToDraw).invert(), drawBase);
                OutfitNativeRenderer.draw(outfit, basePose, YsmEntityFrame.capture(entity, delta), light, delta,
                        entityToDraw, bufferSource, EntityRenderMode.RENDER);
            }

            FiguraMod.popPushProfiler("postRenderEvent");
            avatar.postRenderEvent(delta, poseMatrix);

            FiguraMod.popProfiler(3);
        };
        // The outfit scopes the base avatar's script budget around its events and drawing.
        if (outfit == null) {
            drawFrame.run();
        } else {
            outfit.drawFiguraBase(avatar, drawFrame);
        }

        figura$doPosTransform(avatar, model);
    }

    @Inject(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"), method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void endRender(T entity, float yaw, float delta, PoseStack matrices, MultiBufferSource bufferSource, int light, CallbackInfo ci) {
        if (currentAvatar != null && currentAvatar.luaRuntime != null
                && currentAvatar.permissions.get(Permissions.VANILLA_MODEL_EDIT) == 1) {
            currentAvatar.luaRuntime.vanilla_model.PLAYER.restore(getModel());
        }
        figura$finishFrame();
    }

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void shouldShowName(T livingEntity, CallbackInfoReturnable<Boolean> cir) {
        if (UIHelper.paperdoll)
            cir.setReturnValue(Configs.PREVIEW_NAMEPLATE.value);
        else if (!Minecraft.renderNames() || livingEntity.getUUID().equals(PopupMenu.getEntityId()))
            cir.setReturnValue(false);
        else if (!AvatarManager.panic) {
            if (Configs.SELF_NAMEPLATE.value && livingEntity == Minecraft.getInstance().player)
                cir.setReturnValue(true);
            else if (Configs.NAMEPLATE_RENDER.value == 2 || (Configs.NAMEPLATE_RENDER.value == 1 && livingEntity != FiguraMod.extendedPickEntity))
                cir.setReturnValue(false);
        }
    }

    @Inject(method = "isEntityUpsideDown", at = @At("HEAD"), cancellable = true)
    private static void isEntityUpsideDown(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        Avatar avatar = AvatarManager.getAvatar(entity);
        if (RenderUtils.vanillaModelAndScript(avatar)) {
            Boolean upsideDown = avatar.luaRuntime.renderer.upsideDown;
            if (upsideDown != null)
                cir.setReturnValue(upsideDown);
        }
    }

    @Unique
    private void figura$finishFrame() {
        if (currentFrame != null) {
            EntityFrame.end(currentFrame);
            currentFrame = null;
        }
        currentAvatar = null;
        lastPose = null;
    }

    /** A vanilla skin wearing accessories draws them against the posed PlayerModel. */
    @Unique
    private void figura$renderVanillaOutfit(T entity, float delta, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        var outfit = OutfitRenderBinding.composition(currentFrame);
        if (outfit == null || !OutfitRenderBinding.isVanilla(currentFrame) || lastPose == null || Avatar.firstPerson
                || !(getModel() instanceof PlayerModel<?> playerModel)) {
            return;
        }
        OutfitNativeRenderer.drawVanilla(outfit, playerModel, YsmEntityFrame.capture(entity, delta), light, delta,
                new Matrix4f(lastPose), poseStack.last().pose(), bufferSource, EntityRenderMode.RENDER);
    }

    @Unique
    private void figura$renderYsmNative(Avatar avatar, T entity, float delta, PoseStack poseStack,
                                        MultiBufferSource bufferSource, int light) {
        PoseStack ysmStack = new PoseStack();
        ysmStack.last().pose().set(poseStack.last().pose());
        ysmStack.last().normal().set(poseStack.last().normal());
        float bodyYaw = Mth.rotLerp(delta, entity.yBodyRotO, entity.yBodyRot);
        YsmRenderer.applyEntityRotation(ysmStack, entity, bodyYaw);

        var attachedRuntime = avatar.getYsmRuntime();
        YsmModelRuntime runtime = attachedRuntime == null ? null : attachedRuntime.forRenderPass();
        if (runtime == null) {
            return;
        }
        runtime.updateAnimations(YsmEntityFrame.capture(entity, delta), entity);
        var renderedPose = runtime.captureRenderPose(false);
        var outfit = OutfitRenderBinding.composition(currentFrame);
        Matrix4f entityToDraw = new Matrix4f(poseStack.last().pose());
        var outfitPose = outfit == null ? null : outfit.captureYsmBase(runtime,
                new Matrix4f(entityToDraw).invert().mul(ysmStack.last().pose()), renderedPose);

        // render_layers_first draws the held items before the body; otherwise the body goes first.
        boolean itemsFirst = runtime.renderer().bodySubmitOrder() > 0;
        if (itemsFirst) {
            figura$renderYsmHandItems(runtime.owner(), entity, ysmStack, bufferSource, light);
        }
        if (!runtime.isClosed() && avatar.getYsmRuntime() == attachedRuntime) {
            if (outfitPose == null) {
                runtime.renderer().renderWithAttachments(ysmStack, bufferSource, light, renderedPose);
            } else {
                outfit.drawYsmBase(runtime.renderBudgetOwner(), () -> runtime.renderer().renderWithAttachments(
                        ysmStack, bufferSource, light, renderedPose));
                OutfitNativeRenderer.draw(outfit, outfitPose, YsmEntityFrame.capture(entity, delta), light, delta,
                        entityToDraw, bufferSource, EntityRenderMode.RENDER);
            }
        }
        if (!itemsFirst) {
            figura$renderYsmHandItems(runtime.owner(), entity, ysmStack, bufferSource, light);
        }
    }

    @Unique
    void figura$transformParts(Avatar currentAvatar, M model) {
        if (currentAvatar.luaRuntime != null) {
            VanillaPart part = currentAvatar.luaRuntime.vanilla_model.PLAYER;
            part.save(model);
            if (currentAvatar.permissions.get(Permissions.VANILLA_MODEL_EDIT) == 1) {
                // this one basically does transformations such as rotation
                part.preTransform(model);
            }
        }
    }

    @Unique
    void figura$doPosTransform(Avatar currentAvatar, M model) {
        // this is responsible for visibility stuff
        if (currentAvatar.luaRuntime != null && currentAvatar.permissions.get(Permissions.VANILLA_MODEL_EDIT) == 1)
            currentAvatar.luaRuntime.vanilla_model.PLAYER.posTransform(model);
    }

    @Unique
    void figura$renderYsmHandItems(Avatar avatar, LivingEntity entity, PoseStack baseStack, MultiBufferSource bufferSource, int light) {
        HumanoidArm mainArm = entity.getMainArm();
        figura$renderYsmHandItem(avatar, entity, entity.getMainHandItem(), mainArm, baseStack, bufferSource, light);
        figura$renderYsmHandItem(avatar, entity, entity.getOffhandItem(), mainArm.getOpposite(), baseStack, bufferSource, light);
    }

    @Unique
    void figura$renderYsmHandItem(Avatar avatar, LivingEntity entity, ItemStack itemStack, HumanoidArm arm,
                                  PoseStack baseStack, MultiBufferSource bufferSource, int light) {
        if (itemStack.isEmpty() || avatar.getYsmRuntime() == null)
            return;

        boolean left = arm == HumanoidArm.LEFT;
        var runtime = avatar.getYsmRuntime();
        var chains = runtime.handItemChainAnchors(left);
        if (chains.isEmpty()) {
            PoseStack itemStackPose = figura$copyPose(baseStack);
            if (runtime.applyHandItemTransform(itemStackPose, entity, itemStack, left))
                figura$renderYsmHandItemAt(entity, itemStack, left, itemStackPose, bufferSource, light);
            return;
        }
        // D018: without a direct hand anchor the item is drawn once per extra hand chain.
        for (var anchor : chains) {
            PoseStack itemStackPose = figura$copyPose(baseStack);
            if (runtime.applyHandItemChainTransform(itemStackPose, entity, itemStack, left, anchor))
                figura$renderYsmHandItemAt(entity, itemStack, left, itemStackPose, bufferSource, light);
        }
    }

    @Unique
    private static PoseStack figura$copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    @Unique
    void figura$renderYsmHandItemAt(LivingEntity entity, ItemStack itemStack, boolean left, PoseStack itemStackPose,
                                    MultiBufferSource bufferSource, int light) {
        ItemDisplayContext context = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        // ItemRenderer.renderStatic also covers special-rendered items such as tridents in MC 1.21.1.
        Minecraft.getInstance().getItemRenderer().renderStatic(entity, itemStack, context, left, itemStackPose,
                bufferSource, entity.level(), light, OverlayTexture.NO_OVERLAY, entity.getId() + context.ordinal());
    }
}
