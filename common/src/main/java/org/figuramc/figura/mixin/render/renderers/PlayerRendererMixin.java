package org.figuramc.figura.mixin.render.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.avatar.Badges;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.ducks.EntityRendererAccessor;
import top.aihmc.omnimorph.ducks.FiguraSubmitCallBackExtension;
import top.aihmc.omnimorph.ducks.NodeCollectorExtension;
import org.figuramc.figura.lua.api.nameplate.EntityNameplateCustomization;
import org.figuramc.figura.lua.api.vanilla_model.VanillaPart;
import top.aihmc.omnimorph.model.outfit.OutfitRenderBinding;
import top.aihmc.omnimorph.model.outfit.OutfitNativeRenderer;
import top.aihmc.omnimorph.model.outfit.OutfitComposition;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.model.ysm.YsmModelRuntime;
import org.figuramc.figura.permissions.Permissions;
import org.figuramc.figura.utils.RenderUtils;
import org.figuramc.figura.utils.TextUtils;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, AvatarRenderState, PlayerModel> implements EntityRendererAccessor {

    public PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel entityModel, float shadowRadius) {
        super(context, entityModel, shadowRadius);
    }

    @Unique
    private Avatar avatar;

    @Unique
    boolean isNameRendering, hasScore;

    @Unique
    private Component originalNameTag;

    @Unique
    private static MultiBufferSource figura$singleBuffer(VertexConsumer consumer) {
        return type -> consumer;
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V"))
    private void enableModifyPlayerName(AvatarRenderState avatarRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        // render name
        FiguraMod.popPushProfiler("name");
        isNameRendering = true;
    }

    @Override
    public boolean figura$isRenderingName() {
        return isNameRendering;
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At(value = "TAIL"))
    private void disableModifyPlayerName(AvatarRenderState avatarRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        isNameRendering = false;
        if (originalNameTag != null) {
            avatarRenderState.nameTag = originalNameTag;
            originalNameTag = null;
        }
    }


    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
    private void setHasScore(AvatarRenderState playerRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        hasScore = playerRenderState.scoreText != null;
    }


    @Override
    public boolean figura$hasScore() {
        return hasScore;
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V"))
    private void modifyPlayerNameText(AvatarRenderState player, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        int config = Configs.ENTITY_NAMEPLATE.value;
        if (config == 0 || AvatarManager.panic)
            return;

        Component name = player.nameTag;
        if (name == null)
            return;

        // text
        Avatar avatar = AvatarManager.getAvatar(player);
        EntityNameplateCustomization custom = avatar == null || avatar.luaRuntime == null ? null : avatar.luaRuntime.nameplate.ENTITY;

        // customization boolean, which also is the permission check
        boolean hasCustom = custom != null && avatar.permissions.get(Permissions.NAMEPLATE_EDIT) == 1;

        originalNameTag = name;
        FiguraMod.popPushProfiler("text");

        Component replacement = hasCustom && custom.getJson() != null ? custom.getJson().copy() : name;

        // name
        replacement = TextUtils.replaceInText(replacement, "\\$\\{name\\}", name);

        // badges
        FiguraMod.popPushProfiler("badges");
        if (Minecraft.getInstance().level.getEntity(player.id) != null) { // null while dead
			replacement = Badges.appendBadges(replacement, Minecraft.getInstance().level.getEntity(player.id).getUUID(), config > 1);
		}

        FiguraMod.popPushProfiler("applyName");
        player.nameTag = TextUtils.replaceInText(player.nameTag, "\\b" + Pattern.quote(name.getString()) + "\\b", replacement);
    }

    // Push for scoreboard rendering
    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V"))
    private void pushProfilerForRender(AvatarRenderState avatarRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        FiguraMod.popPushProfiler("render");
        FiguraMod.pushProfiler("scoreboard");
    }

    // Pop the profiler after everything's done
    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At(value = "TAIL"))
    private void popProfiler(AvatarRenderState avatarRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        FiguraMod.popProfiler(5);
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void renderNameTag(AvatarRenderState playerRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        // return on config or high entity distance
        int config = Configs.ENTITY_NAMEPLATE.value;
        Entity entity = Minecraft.getInstance().level.getEntity(playerRenderState.id);

        if (config == 0 || AvatarManager.panic || !(entity instanceof Player player) || this.entityRenderDispatcher.distanceToSqr(player) > 4096)
            return;

        // get customizations
        Avatar avatar = AvatarManager.getAvatarForPlayer(player.getUUID());
        EntityNameplateCustomization custom = avatar == null || avatar.luaRuntime == null ? null : avatar.luaRuntime.nameplate.ENTITY;

        // customization boolean, which also is the permission check
        boolean hasCustom = custom != null && avatar.permissions.get(Permissions.NAMEPLATE_EDIT) == 1;
        if (custom != null && avatar.permissions.get(Permissions.NAMEPLATE_EDIT) == 0) {
            avatar.noPermissions.add(Permissions.NAMEPLATE_EDIT);
        } else if (avatar != null) {
            avatar.noPermissions.remove(Permissions.NAMEPLATE_EDIT);
        }

        // enabled
        if (hasCustom && !custom.visible) {
            ci.cancel();
            return;
        }

        // If the user has an avatar equipped, figura nameplate rendering will be enabled so the profiler is pushed
        if (hasCustom) {
            FiguraMod.pushProfiler(FiguraMod.MOD_ID);
            FiguraMod.pushProfiler(player.getName().getString());
            FiguraMod.pushProfiler("nameplate");
        }
    }



    @Inject(at = @At("HEAD"), method = "renderHand", cancellable = true)
    private void ysm$renderHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, Identifier resourceLocation, ModelPart arm, boolean renderSleeve, CallbackInfo ci) {
        if (Minecraft.getInstance().player == null)
            return;

        var player = Minecraft.getInstance().player;
        float delta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Avatar localAvatar = AvatarManager.getAvatarForPlayer(player.getUUID());
        AvatarRenderState outfitState = null;
        OutfitComposition.Frame outfit = null;
        if (OutfitRenderBinding.hasComposition(player)) {
            outfitState = this.createRenderState(player, delta);
            outfitState.lightCoords = light;
            OutfitRenderBinding.capture(player, outfitState);
            outfit = OutfitRenderBinding.composition(outfitState);
            // The composition's budget belongs to its own base, which is the local draft preview while editing.
            Avatar compositionBase = OutfitRenderBinding.avatar(outfitState);
            if (compositionBase != null) {
                localAvatar = compositionBase;
            }
        }
        if (localAvatar == null || !localAvatar.isYsmNative())
            return;

        YsmModelRuntime ysm = localAvatar.getYsmRuntime();
        if (ysm == null)
            return;

        PlayerModel playerModel = getModel();
        boolean left = arm == playerModel.leftArm;
        PoseStack copy = new PoseStack();
        copy.last().set(poseStack.last());
        copy.translate(left ? 0.25d : -0.25d, 1.8d, 0.0d);
        copy.scale(-1.0f, -1.0f, 1.0f);

        ysm.texture().uploadIfDirty(false, false);
        RenderType renderType = ysm.isTextureTranslucent()
                ? RenderTypes.entityTranslucent(ysm.texture().getLocation())
                : RenderTypes.entityCutout(ysm.texture().getLocation());
        var submittedPose = ysm.captureRenderPose(true);
        OutfitComposition.Frame retainedOutfit = null;
        if (outfit != null) {
            var frame = outfit;
            retainedOutfit = frame;
            var basePose = frame.captureYsmArm(ysm, new Matrix4f(), left, submittedPose);
            ((NodeCollectorExtension) submitNodeCollector).submitFiguraModel(localAvatar, outfitState,
                    (owner, state, buffers) -> {
                        if (owner.getYsmRuntime() == ysm) {
                            OutfitNativeRenderer.draw(frame, basePose, state, delta,
                                    copy.last().pose(), buffers, EntityRenderMode.FIRST_PERSON);
                        }
                        return null;
                    });
        }
        var outfitFrame = retainedOutfit;
        var armAvatar = localAvatar;
        submitNodeCollector.submitCustomGeometry(copy, renderType, (pose, buffer) -> {
            if (armAvatar.getYsmRuntime() == ysm) {
                if (outfitFrame == null) {
                    ysm.renderFirstPersonArm(copy, figura$singleBuffer(buffer), light, left, submittedPose);
                } else {
                    outfitFrame.drawYsmBase(ysm.renderBudgetOwner(), () -> ysm.renderFirstPersonArm(
                            copy, figura$singleBuffer(buffer), light, left, submittedPose));
                }
            }
        });

        ci.cancel();
    }

    @Inject(at = @At(value = "INVOKE", shift = At.Shift.BEFORE, target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"), method = "renderHand")
    private void onRenderHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, Identifier resourceLocation, ModelPart modelPart, boolean bl, CallbackInfo ci) {
        avatar = AvatarManager.getAvatarForPlayer(Minecraft.getInstance().player.getUUID());


        PlayerModel model = this.getModel();
        Map<ModelPart, PartPose> modelState = RenderUtils.captureModelState(model);

        Avatar localAvatar = avatar;
        var player = Minecraft.getInstance().player;
        float delta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        AvatarRenderState outfitState = localAvatar == null && player != null && OutfitRenderBinding.hasComposition(player)
                ? this.createRenderState(player, delta) : null;
        if (outfitState != null) {
            outfitState.lightCoords = i;
            OutfitRenderBinding.capture(player, outfitState);
        }
        var outfit = outfitState == null ? null : OutfitRenderBinding.composition(outfitState);
        Matrix4f armToDraw = new Matrix4f(poseStack.last().pose());
        BiFunction<MultiBufferSource, PoseStack, Boolean> lambda = (bufferSource, stack) -> {
            if (outfit != null && localAvatar == null) {
                RenderUtils.restoreModelPoseState(model, modelState);
                OutfitNativeRenderer.drawVanillaArm(outfit, model, outfitState, delta,
                        armToDraw, bufferSource, modelPart == model.leftArm);
            }
            if (localAvatar != null && localAvatar.luaRuntime != null) {
                VanillaPart part = localAvatar.luaRuntime.vanilla_model.PLAYER;
                RenderUtils.restoreModelPoseState(model, modelState);
                part.save(model);

                if (localAvatar.permissions.get(Permissions.VANILLA_MODEL_EDIT) == 1) {
                    part.preTransform(model);
                    part.posTransform(model);
                }
            }

            return true;
        };

        ((FiguraSubmitCallBackExtension)(Object)modelPart).figura$addPreRenderingCallback(lambda);
        ((FiguraSubmitCallBackExtension)(Object)modelPart).figura$addPostRenderingCallback(() -> {
            if (localAvatar != null && localAvatar.luaRuntime != null)
                localAvatar.luaRuntime.vanilla_model.PLAYER.restore(model);
            }
        );
    }

    @Inject(at = @At("RETURN"), method = "renderHand")
    private void postRenderHand(PoseStack stack, SubmitNodeCollector submitNodeCollector, int light, Identifier resourceLocation, ModelPart arm, boolean bl, CallbackInfo ci) {
        if (avatar == null)
            return;

        NodeCollectorExtension nodeCollectorExt = (NodeCollectorExtension) submitNodeCollector;

        float delta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);

        PoseStack copy = new PoseStack();
        copy.pushPose(); // save the current stack
        copy.last().set(stack.last());

        PlayerModel playerModel = getModel();

        Map<ModelPart, PartPose> modelState = RenderUtils.captureModelState(playerModel);

        var player = Minecraft.getInstance().player;
        AvatarRenderState outfitState = player != null && OutfitRenderBinding.hasComposition(player)
                ? this.createRenderState(player, delta) : null;
        if (outfitState != null) {
            outfitState.lightCoords = light;
            OutfitRenderBinding.capture(player, outfitState);
        }

        var submittedArmOutfit = outfitState == null ? null : OutfitRenderBinding.composition(outfitState);
        Avatar armAvatar = avatar;
        Avatar armCompositionBase = outfitState == null ? null : OutfitRenderBinding.avatar(outfitState);
        // The first-person arm path only knows how to capture a Figura base skeleton. A YSM base has its own
        // hand renderer; prepareFiguraBase would reject it and crash the frame.
        boolean figuraArmBase = submittedArmOutfit != null && armCompositionBase != null
                && !armCompositionBase.isYsmNative() && armCompositionBase.renderer != null;
        if (figuraArmBase) {
            armAvatar = armCompositionBase;
            submittedArmOutfit.prepareFiguraBase(armAvatar);
        }
        var preparedArmOutfit = figuraArmBase ? submittedArmOutfit : null;

        nodeCollectorExt.submitFiguraModel(armAvatar, outfitState, (playerAvatar, state, bufferSource) -> {
            Runnable drawSubmittedArm = () -> {

                RenderUtils.restoreModelPoseState(playerModel, modelState);

                if (playerAvatar != null && playerAvatar.luaRuntime != null) {
                    VanillaPart part = playerAvatar.luaRuntime.vanilla_model.PLAYER;
                    PlayerModel model = this.getModel();

                    part.save(model);

                    if (playerAvatar.permissions.get(Permissions.VANILLA_MODEL_EDIT) == 1) {
                        part.preTransform(model);
                        part.posTransform(model);
                    }
                }

                Runnable drawArm = () -> playerAvatar.firstPersonRender(copy, bufferSource,
                        Minecraft.getInstance().player, playerModel, arm, light, delta);
                var outfit = figuraArmBase ? OutfitRenderBinding.composition(state) : null;
                if (outfit == null) {
                    drawArm.run();
                } else {
                    var pose = outfit.captureFiguraArm(playerAvatar, new Matrix4f(copy.last().pose()).invert(),
                            arm == playerModel.leftArm, drawArm);
                    OutfitNativeRenderer.draw(outfit, pose, state, delta,
                            copy.last().pose(), bufferSource, EntityRenderMode.FIRST_PERSON);
                }

                if (playerAvatar.luaRuntime != null)
                    playerAvatar.luaRuntime.vanilla_model.PLAYER.restore(playerModel);
            };
            if (preparedArmOutfit == null) {
                drawSubmittedArm.run();
            } else {
                preparedArmOutfit.drawFiguraBase(playerAvatar, drawSubmittedArm);
            }
            return null;
        });

        avatar = null;
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("HEAD"), cancellable = true)
    private void setupRotations(AvatarRenderState playerRenderState, PoseStack matrices, float f, float g, CallbackInfo cir) {
        Avatar avatar = AvatarManager.getAvatar(playerRenderState);
        if (RenderUtils.vanillaModelAndScript(avatar) && !avatar.luaRuntime.renderer.getRootRotationAllowed()) {
            cir.cancel();
        }
    }
}
