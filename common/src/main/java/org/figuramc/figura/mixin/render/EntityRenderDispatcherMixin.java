package org.figuramc.figura.mixin.render;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.figuramc.figura.ducks.NodeCollectorExtension;
import org.figuramc.figura.model.ysm.YsmPartMask;
import org.figuramc.figura.model.ysm.YsmSubEntityInstances;
import org.figuramc.figura.permissions.Permissions;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.ducks.FiguraEntityRenderStateExtension;
import org.figuramc.figura.utils.RenderUtils;
import org.figuramc.figura.utils.ui.UIHelper;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Shadow public Camera camera;
    @Unique private Avatar avatar;

    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitFlame(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lorg/joml/Quaternionf;)V"), cancellable = true)
    private <S extends EntityRenderState> void renderFlame(S entityRenderState, CameraRenderState cameraRenderState, double d, double e, double f, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        Avatar a = AvatarManager.getAvatar(entityRenderState);
        if (RenderUtils.vanillaModelAndScript(a)) {
            if (!a.luaRuntime.renderer.renderFire) {
                ci.cancel();
            } else {
                avatar = a;
            }
        }
    }

    @ModifyArg(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitFlame(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lorg/joml/Quaternionf;)V"))
    private Quaternionf renderFlameRot(Quaternionf f) {
        return UIHelper.paperdoll ? Axis.YP.rotationDegrees(UIHelper.fireRot) : f;
    }

    @ModifyArg(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitShadow(Lcom/mojang/blaze3d/vertex/PoseStack;FLjava/util/List;)V"))
    private static float modifyShadowSize(float h, @Local(argsOnly = true) EntityRenderState entity) {
        Avatar avatar = AvatarManager.getAvatar(entity);
        if (RenderUtils.vanillaModelAndScript(avatar) && avatar.luaRuntime.renderer.shadowRadius != null)
            return avatar.luaRuntime.renderer.shadowRadius;
        return h;
    }

    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void figura$submitVehicle(EntityRenderer<?, ?> renderer, EntityRenderState state, PoseStack stack,
                                      SubmitNodeCollector collector, CameraRenderState cameraState,
                                      Operation<Void> original) {
        if (!figura$submitYsmVehicle(state, stack, collector)) {
            original.call(renderer, state, stack, collector, cameraState);
        }
    }

    @Unique
    private boolean figura$submitYsmVehicle(EntityRenderState state, PoseStack stack, SubmitNodeCollector collector) {
        Entity entity = AvatarManager.getEntity(state);
        if (entity == null) {
            return false;
        }
        Entity passenger = entity.getFirstPassenger();
        Avatar owner;
        if (passenger != null) {
            owner = AvatarManager.getAvatar(passenger);
        } else {
            var assigned = YsmSubEntityInstances.vehicleOwner(entity);
            owner = assigned == null ? null : assigned.owner();
        }
        if (owner == null || owner.permissions.get(Permissions.VANILLA_MODEL_EDIT) == 0) {
            return false;
        }
        var parent = owner.getYsmRuntime();
        if (parent == null || parent.getSubEntityForEntity("vehicle", entity) == null) {
            return false;
        }
        float partialTick = ((FiguraEntityRenderStateExtension) state).figura$getTickDelta();
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        var instance = parent.prepareSubEntity(entity, "vehicle", typeId, partialTick);
        if (instance == null) {
            return false;
        }
        PoseStack submittedStack = new PoseStack();
        submittedStack.last().set(stack.last());
        float yaw = entity instanceof LivingEntity living
                ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot)
                : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        submittedStack.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        var pose = instance.captureRenderPose(false);
        ((NodeCollectorExtension) collector).submitFiguraModel(owner, state, (avatar, submittedState, buffers) -> {
            if (!instance.isClosed() && avatar.getYsmRuntime() == parent
                    && parent.ownsSubEntity(entity, "vehicle") && !entity.isRemoved()) {
                instance.renderer().render(submittedStack, buffers, submittedState.lightCoords, YsmPartMask.DEBUG_ALL, pose);
            }
            return null;
        });
        return true;
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private <E extends Entity, S extends EntityRenderState> void render(S entityRenderState, CameraRenderState cameraRenderState, double d, double e, double f, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (this.camera == null)
            ci.cancel();

        Integer entityId = ((FiguraEntityRenderStateExtension)entityRenderState).figura$getEntityId();
        if (entityId == null)
            return;
        Entity entity = Minecraft.getInstance().level.getEntity(entityId);
        if (entity == null)
            return;
        Entity owner = entity.getFirstPassenger();
        if (owner == null)
            return;

        Avatar avatar = AvatarManager.getAvatar(owner);
        if (RenderUtils.vanillaModelAndScript(avatar) && !avatar.luaRuntime.renderer.renderVehicle)
            ci.cancel();
    }
}
