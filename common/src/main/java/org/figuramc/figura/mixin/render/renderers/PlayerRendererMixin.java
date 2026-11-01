package org.figuramc.figura.mixin.render.renderers;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.avatar.Badges;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.ducks.EntityRendererAccessor;
import org.figuramc.figura.lua.api.nameplate.EntityNameplateCustomization;
import org.figuramc.figura.lua.api.vanilla_model.VanillaPart;
import org.figuramc.figura.permissions.Permissions;
import org.figuramc.figura.utils.RenderUtils;
import org.figuramc.figura.utils.TextUtils;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.aihmc.omnimorph.model.outfit.OutfitComposition;
import top.aihmc.omnimorph.model.outfit.OutfitNativeRenderer;
import top.aihmc.omnimorph.model.outfit.OutfitRenderBinding;
import top.aihmc.omnimorph.model.ysm.animation.YsmEntityFrame;
import top.aihmc.omnimorph.render.EntityFrame;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import java.util.regex.Pattern;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> implements EntityRendererAccessor {

    public PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> entityModel, float shadowRadius) {
        super(context, entityModel, shadowRadius);
    }

    @Unique
    private Avatar avatar;

    @Unique
    boolean isNameRendering, hasScore;

    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", ordinal = 1))
    private void enableModifyPlayerName(AbstractClientPlayer player, Component text, PoseStack stack, MultiBufferSource multiBufferSource, int light, float delta,  CallbackInfo ci) {
        // render name
        FiguraMod.popPushProfiler("name");
        isNameRendering = true;
    }

    @Override
    public boolean figura$isRenderingName() {
        return isNameRendering;
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At(value = "TAIL"))
    private void disableModifyPlayerName(AbstractClientPlayer player, Component text, PoseStack stack, MultiBufferSource multiBufferSource, int light, float delta, CallbackInfo ci) {
        isNameRendering = false;
    }


    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/Scoreboard;getDisplayObjective(Lnet/minecraft/world/scores/DisplaySlot;)Lnet/minecraft/world/scores/Objective;"))
    private void setHasScore(AbstractClientPlayer player, Component text, PoseStack stack, MultiBufferSource multiBufferSource, int light, float delta, CallbackInfo ci) {
        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME);
        hasScore = objective != null;
    }


    @Override
    public boolean figura$hasScore() {
        return hasScore;
    }

    @ModifyArg(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", ordinal = 1))
    private Component modifyPlayerNameText(Component text, @Local(argsOnly = true) AbstractClientPlayer player) {
        int config = Configs.ENTITY_NAMEPLATE.value;
        if (config == 0 || AvatarManager.panic)
            return text;

        // text
        Avatar avatar = AvatarManager.getAvatarForPlayer(player.getUUID());
        EntityNameplateCustomization custom = avatar == null || avatar.luaRuntime == null ? null : avatar.luaRuntime.nameplate.ENTITY;

        // customization boolean, which also is the permission check
        boolean hasCustom = custom != null && avatar.permissions.get(Permissions.NAMEPLATE_EDIT) == 1;

        Component name = Component.literal(player.getName().getString());
        FiguraMod.popPushProfiler("text");

        Component replacement = hasCustom && custom.getJson() != null ? custom.getJson().copy() : name;

        // name
        replacement = TextUtils.replaceInText(replacement, "\\$\\{name\\}", name);

        // badges
        FiguraMod.popPushProfiler("badges");
        replacement = Badges.appendBadges(replacement, player.getUUID(), config > 1);

        FiguraMod.popPushProfiler("applyName");
        text = TextUtils.replaceInText(text, "\\b" + Pattern.quote(player.getName().getString()) + "\\b", replacement);

        return text;
    }

    // Push for scoreboard rendering
    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V"))
    private void pushProfilerForRender(AbstractClientPlayer player, Component text, PoseStack stack, MultiBufferSource multiBufferSource, int light, float delta, CallbackInfo ci) {
        FiguraMod.popPushProfiler("render");
        FiguraMod.pushProfiler("scoreboard");
    }

    // Pop the profiler after everything's done
    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At(value = "TAIL"))
    private void popProfiler(AbstractClientPlayer player, Component text, PoseStack stack, MultiBufferSource multiBufferSource, int light, float delta, CallbackInfo ci) {
        FiguraMod.popProfiler(5);
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At("HEAD"), cancellable = true)
    private void renderNameTag(AbstractClientPlayer player, Component text, PoseStack stack, MultiBufferSource multiBufferSource, int light, float delta, CallbackInfo ci) {
        // return on config or high entity distance
        int config = Configs.ENTITY_NAMEPLATE.value;
        if (config == 0 || AvatarManager.panic || this.entityRenderDispatcher.distanceToSqr(player) > 4096)
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



    @Unique
    private OutfitComposition.Frame figura$armOutfit;

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void figura$renderYsmHand(PoseStack stack, MultiBufferSource buffers, int light,
                                     AbstractClientPlayer player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        float delta = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        avatar = AvatarManager.getAvatarForPlayer(player.getUUID());
        figura$armOutfit = null;
        if (OutfitRenderBinding.hasComposition(player)) {
            EntityFrame frame = new EntityFrame(player, delta);
            OutfitRenderBinding.capture(player, frame, delta, true);
            figura$armOutfit = OutfitRenderBinding.composition(frame);
            // A local wardrobe draft owns its own base and instruction budget.
            avatar = OutfitRenderBinding.avatar(frame);
        }
        if (avatar == null || !avatar.isYsmNative()) {
            return;
        }
        var runtime = avatar.getYsmRuntime();
        if (runtime == null) {
            return;
        }

        boolean left = arm == getModel().leftArm;
        PoseStack copy = new PoseStack();
        copy.last().pose().set(stack.last().pose());
        copy.last().normal().set(stack.last().normal());
        copy.translate(left ? 0.25d : -0.25d, 1.8d, 0d);
        copy.scale(-1f, -1f, 1f);
        runtime.updateAnimations(YsmEntityFrame.capture(player, delta), player, true);
        var pose = runtime.captureRenderPose(true);
        var outfit = figura$armOutfit;
        if (outfit == null) {
            runtime.renderFirstPersonArm(copy, buffers, light, left, pose);
        } else {
            var basePose = outfit.captureYsmArm(runtime, new Matrix4f(), left, pose);
            OutfitNativeRenderer.draw(outfit, basePose, YsmEntityFrame.capture(player, delta), light, delta,
                    copy.last().pose(), buffers, EntityRenderMode.FIRST_PERSON);
            outfit.drawYsmBase(runtime.renderBudgetOwner(),
                    () -> runtime.renderFirstPersonArm(copy, buffers, light, left, pose));
        }
        avatar = null;
        figura$armOutfit = null;
        ci.cancel();
    }

    @Inject(at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/model/PlayerModel;setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"), method = "renderHand")
    private void onRenderHand(PoseStack stack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                              ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        if (avatar != null && avatar.luaRuntime != null) {
            VanillaPart part = avatar.luaRuntime.vanilla_model.PLAYER;
            var model = getModel();
            part.save(model);
            if (avatar.permissions.get(Permissions.VANILLA_MODEL_EDIT) == 1) {
                part.preTransform(model);
                part.posTransform(model);
            }
        }
    }

    @Inject(at = @At("RETURN"), method = "renderHand")
    private void postRenderHand(PoseStack stack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        float delta = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        var outfit = figura$armOutfit;
        Avatar armAvatar = avatar;
        var model = getModel();
        try {
            if (armAvatar == null) {
                if (outfit != null) {
                    OutfitNativeRenderer.drawVanillaArm(outfit, model, YsmEntityFrame.capture(player, delta),
                            light, delta, stack.last().pose(), buffers, arm == model.leftArm);
                }
                return;
            }
            Runnable drawArm = () -> armAvatar.firstPersonRender(stack, buffers, player, model, arm, light, delta);
            if (outfit == null || armAvatar.renderer == null) {
                drawArm.run();
            } else {
                outfit.prepareFiguraBase(armAvatar);
                outfit.drawFiguraBase(armAvatar, () -> {
                    var pose = outfit.captureFiguraArm(armAvatar, new Matrix4f(stack.last().pose()).invert(),
                            arm == model.leftArm, drawArm);
                    OutfitNativeRenderer.draw(outfit, pose, YsmEntityFrame.capture(player, delta), light, delta,
                            stack.last().pose(), buffers, EntityRenderMode.FIRST_PERSON);
                });
            }
        } finally {
            if (armAvatar != null && armAvatar.luaRuntime != null) {
                armAvatar.luaRuntime.vanilla_model.PLAYER.restore(model);
            }
            avatar = null;
            figura$armOutfit = null;
        }
    }
    @Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
    private void setupRotations(AbstractClientPlayer entity, PoseStack poseStack, float f, float f2, float f3, float f4, CallbackInfo cir) {
        Avatar avatar = AvatarManager.getAvatar(entity);
        if (RenderUtils.vanillaModelAndScript(avatar) && !avatar.luaRuntime.renderer.getRootRotationAllowed()) {
            cir.cancel();
        }
    }
}
