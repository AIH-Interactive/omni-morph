package org.figuramc.figura.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.figuramc.figura.permissions.Permissions;
import top.aihmc.omnimorph.model.ysm.YsmPartMask;
import top.aihmc.omnimorph.model.ysm.YsmSubEntityInstances;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import top.aihmc.omnimorph.render.EntityFrame;
import top.aihmc.omnimorph.model.outfit.OutfitRenderBinding;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.ducks.EntityRenderDispatcherAccessor;
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
public class EntityRenderDispatcherMixin implements EntityRenderDispatcherAccessor {

    @Shadow public Camera camera;
    @Shadow private boolean shouldRenderShadow;
    @Unique private Avatar avatar;

    @Override
    public boolean figura$shouldRenderShadow() {
        return shouldRenderShadow;
    }

    @Inject(method = "renderFlame", at = @At("HEAD"), cancellable = true)
    private void renderFlame(PoseStack matrices, MultiBufferSource vertexConsumers, Entity entity, Quaternionf quaternionf, CallbackInfo ci) {
        avatar = null;
        Avatar a = figura$boundAvatar(entity, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
        if (RenderUtils.vanillaModelAndScript(a)) {
            if (!a.luaRuntime.renderer.renderFire) {
                ci.cancel();
            } else {
                avatar = a;
            }
        }
    }

    @ModifyVariable(method = "renderFlame", at = @At(value = "HEAD"), argsOnly = true)
    private Quaternionf renderFlameRot(Quaternionf f) {
        return UIHelper.paperdoll ? Axis.YP.rotationDegrees(UIHelper.fireRot) : f;
    }

    @ModifyVariable(method = "renderFlame", at = @At("STORE"), ordinal = 0)
    private TextureAtlasSprite firstFireTexture(TextureAtlasSprite sprite) {
        TextureAtlasSprite s = RenderUtils.firstFireLayer(avatar);
        return s != null ? s : sprite;
    }

    @ModifyVariable(method = "renderFlame", at = @At("STORE"), ordinal = 1)
    private TextureAtlasSprite secondFireTexture(TextureAtlasSprite sprite) {
        TextureAtlasSprite s = RenderUtils.secondFireLayer(avatar);
        avatar = null;
        return s != null ? s : sprite;
    }

    @ModifyVariable(method = "renderShadow", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private static float modifyShadowSize(float h, PoseStack poseStack, MultiBufferSource multiBufferSource, Entity entity, float f, float g, LevelReader levelReader, float originalRadius) {
        Avatar avatar = figura$boundAvatar(entity, g);
        if (RenderUtils.vanillaModelAndScript(avatar) && avatar.luaRuntime.renderer.shadowRadius != null) {
            return avatar.luaRuntime.renderer.shadowRadius;
        }
        return h;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void render(E entity, double d, double e, double f, float g, float h, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, CallbackInfo ci) {
        if (this.camera == null) {
            ci.cancel();
            return;
        }

        Entity owner = entity.getFirstPassenger();
        if (owner == null) {
            return;
        }

        Avatar avatar = AvatarManager.getAvatar(owner);
        if (RenderUtils.vanillaModelAndScript(avatar) && !avatar.luaRuntime.renderer.renderVehicle) {
            ci.cancel();
        }
    }
    @Unique
    private static Avatar figura$boundAvatar(Entity entity, float partialTick) {
        // EntityRenderer has already closed its frame before dispatcher fire/shadow rendering.
        // Capture the same preview/outfit binding instead of reading the wearer's original avatar.
        EntityFrame frame = new EntityFrame(entity, partialTick);
        OutfitRenderBinding.capture(entity, frame, partialTick, true);
        return AvatarManager.getAvatar(frame);
    }
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private <T extends Entity> void figura$renderVehicle(EntityRenderer<T> renderer, T entity, float yaw,
                                                        float partialTick, PoseStack stack, MultiBufferSource buffers,
                                                        int light, Operation<Void> original) {
        Entity passenger = entity.getFirstPassenger();
        Avatar owner;
        if (passenger != null) {
            owner = AvatarManager.getAvatar(passenger);
        } else {
            var assigned = YsmSubEntityInstances.vehicleOwner(entity);
            owner = assigned == null ? null : assigned.owner();
        }
        if (owner == null || owner.permissions.get(Permissions.VANILLA_MODEL_EDIT) == 0) {
            original.call(renderer, entity, yaw, partialTick, stack, buffers, light);
            return;
        }
        var parent = owner.getYsmRuntime();
        if (parent == null || parent.getSubEntityForEntity("vehicle", entity) == null) {
            original.call(renderer, entity, yaw, partialTick, stack, buffers, light);
            return;
        }
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        var instance = parent.prepareSubEntity(entity, "vehicle", typeId, partialTick);
        if (instance == null) {
            original.call(renderer, entity, yaw, partialTick, stack, buffers, light);
            return;
        }
        PoseStack copy = new PoseStack();
        copy.last().pose().set(stack.last().pose());
        copy.last().normal().set(stack.last().normal());
        float bodyYaw = entity instanceof LivingEntity living
                ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot)
                : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        copy.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        var pose = instance.captureRenderPose(false);
        if (!instance.isClosed() && owner.getYsmRuntime() == parent
                && parent.ownsSubEntity(entity, "vehicle") && !entity.isRemoved()) {
            instance.renderer().render(copy, buffers, light, YsmPartMask.DEBUG_ALL, pose);
        }
    }}
