package org.figuramc.figura.mixin.render.layers.items;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.model.ParentType;
import org.figuramc.figura.utils.RenderUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aihmc.omnimorph.model.ysm.YsmModelRuntime;
import top.aihmc.omnimorph.render.EntityFrame;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin<T extends LivingEntity, M extends EntityModel<T> & ArmedModel> extends RenderLayer<T, M> {

    public ItemInHandLayerMixin(RenderLayerParent<T, M> renderLayerParent) {
        super(renderLayerParent);
    }

    @Shadow @Final private ItemInHandRenderer itemInHandRenderer;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    protected void renderArmWithItemInject(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext,
                                           HumanoidArm arm, PoseStack poseStack, MultiBufferSource bufferSource,
                                           int light, CallbackInfo ci) {
        if (itemStack.isEmpty()) {
            return;
        }

        EntityFrame frame = EntityFrame.current();
        Avatar avatar = frame != null ? AvatarManager.getAvatar(frame) : AvatarManager.getAvatar(entity);
        boolean left = arm == HumanoidArm.LEFT;

        if (avatar != null && avatar.isYsmNative()) {
            YsmModelRuntime runtime = avatar.getYsmRuntime();
            if (runtime != null) {
                var chains = runtime.handItemChainAnchors(left);
                if (chains.isEmpty()) {
                    poseStack.pushPose();
                    try {
                        if (runtime.applyHandItemTransform(poseStack, entity, itemStack, left)) {
                            itemInHandRenderer.renderItem(entity, itemStack, displayContext, left, poseStack, bufferSource, light);
                        }
                    } finally {
                        poseStack.popPose();
                    }
                } else {
                    // D018: draw once per extra hand chain when there is no direct hand anchor.
                    for (var anchor : chains) {
                        poseStack.pushPose();
                        try {
                            if (runtime.applyHandItemChainTransform(poseStack, entity, itemStack, left, anchor)) {
                                itemInHandRenderer.renderItem(entity, itemStack, displayContext, left, poseStack, bufferSource, light);
                            }
                        } finally {
                            poseStack.popPose();
                        }
                    }
                }
                ci.cancel();
                return;
            }
        }

        if (!RenderUtils.renderArmItem(avatar, left, ci)) {
            return;
        }

        // Immediate rendering consumes the pivots captured by the entity's model render.
        if (avatar.pivotPartRender(left ? ParentType.LeftItemPivot : ParentType.RightItemPivot, stack -> {
            stack.scale(16f, 16f, 16f);
            stack.mulPose(Axis.XP.rotationDegrees(-90f));
            // ItemRendererMixin supplies skull context and dispatches ITEM_RENDER.
            itemInHandRenderer.renderItem(entity, itemStack, displayContext, left, stack, bufferSource, light);
        })) {
            ci.cancel();
        }
    }
}
