package org.figuramc.figura.mixin.render.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractSkullBlock;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.ducks.SkullBlockRendererAccessor;
import org.figuramc.figura.lua.api.vanilla_model.VanillaModelPart;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.model.ysm.YsmModelRuntime;
import org.figuramc.figura.utils.RenderUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Shadow private ItemStack mainHandItem;

    @Shadow
    protected abstract void renderPlayerArm(PoseStack matrices, SubmitNodeCollector submitNodeCollector, int light, float equipProgress, float swingProgress, HumanoidArm arm);

    @Unique Avatar avatar;

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void onRenderHandsWithItems(float tickDelta, PoseStack matrices, SubmitNodeCollector submitNodeCollector, LocalPlayer player, int light, CallbackInfo ci) {
        avatar = AvatarManager.getAvatarForPlayer(player.getUUID());
        if (avatar == null)
            return;

        FiguraMod.pushProfiler(FiguraMod.MOD_ID);
        FiguraMod.pushProfiler(avatar);
        FiguraMod.pushProfiler("renderEvent");
        avatar.renderMode = EntityRenderMode.FIRST_PERSON;
        avatar.renderEvent(tickDelta, new FiguraMat4().set(matrices.last().pose()));
        FiguraMod.popProfiler(3);
    }

    @Inject(method = "renderHandsWithItems", at = @At(value = "RETURN"))
    private void afterRenderHandsWithItems(float tickDelta, PoseStack matrices, SubmitNodeCollector submitNodeCollector, LocalPlayer player, int light, CallbackInfo ci) {
        if (avatar == null)
            return;

        FiguraMod.pushProfiler(FiguraMod.MOD_ID);
        FiguraMod.pushProfiler(avatar);
        FiguraMod.pushProfiler("postRenderEvent");
        avatar.postRenderEvent(tickDelta, new FiguraMat4().set(matrices.last().pose()));
        avatar = null;
        FiguraMod.popProfiler(3);
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void renderArmWithItem(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector submitNodeCollector, int light, CallbackInfo ci) {
        if (player.isScoping())
            return;

        // YSM first-person: block vanilla arm and item rendering, submit YSM arm directly.
        // submitFiguraModel doesn't work here because FiguraFeatureRenderer only
        // processes submissions during entity (third-person) rendering, not first-person.
        if (avatar != null && avatar.isYsmNative()) {
            YsmModelRuntime ysm = avatar.getYsmRuntime();
            if (ysm != null) {
                boolean main = hand == InteractionHand.MAIN_HAND;
                HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
                boolean left = arm == HumanoidArm.LEFT;

                // Let vanilla handle the item rendering when the player holds an item.
                // We only replace the ARM geometry.
                if (!player.isInvisible()) {
                    final PoseStack fpStack = new PoseStack();
                    fpStack.pushPose();
                    fpStack.last().set(matrices.last());

                    try {
                        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
                        ysm.renderFirstPersonArm(fpStack, bufferSource, light, left);
                        bufferSource.endBatch();
                    } catch (Exception e) {
                        FiguraMod.LOGGER.error("Failed to render YSM first-person arm", e);
                    }
                }

                if (!item.isEmpty()) {
                    // Player is holding an item — let vanilla render the item.
                    // Don't cancel; vanilla will render the item (but not the arm,
                    // because the player model's arm visibility is already hidden).
                    // We need to prevent vanilla from rendering its own arm though.
                    // Pass through: vanilla renderArmWithItem renders both arm+item,
                    // but if we already rendered the YSM arm above, the item will
                    // still render over it.
                    return;
                }
                ci.cancel();
                return;
            }
        }

        if (avatar == null || avatar.luaRuntime == null)
            return;

        boolean main = hand == InteractionHand.MAIN_HAND;
        HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
        Boolean armVisible = arm == HumanoidArm.LEFT ? avatar.luaRuntime.renderer.renderLeftArm : avatar.luaRuntime.renderer.renderRightArm;

        boolean willRenderItem = !item.isEmpty();
        boolean willRenderArm = (!willRenderItem && main) || item.is(Items.FILLED_MAP) || (!willRenderItem && this.mainHandItem.is(Items.FILLED_MAP));

        // hide arm
        if (willRenderArm && !willRenderItem && armVisible != null && !armVisible) {
            ci.cancel();
            return;
        }
        // render arm
        if (!willRenderArm && !player.isInvisible() && armVisible != null && armVisible) {
            matrices.pushPose();
            this.renderPlayerArm(matrices, submitNodeCollector, light, equipProgress, swingProgress, arm);
            matrices.popPose();
        }

        // hide item
        VanillaModelPart part = arm == HumanoidArm.LEFT ? avatar.luaRuntime.vanilla_model.LEFT_ITEM : avatar.luaRuntime.vanilla_model.RIGHT_ITEM;
        if (willRenderItem && !part.checkVisible()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext itemDisplayContext, PoseStack matrices, SubmitNodeCollector submitNodeCollector, int light, CallbackInfo ci) {
        if (stack.getItem() instanceof BlockItem bl && bl.getBlock() instanceof AbstractSkullBlock) {
            SkullBlockRendererAccessor.setEntity(entity);
            SkullBlockRendererAccessor.setRenderMode(switch (itemDisplayContext) {
                case FIRST_PERSON_LEFT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.FIRST_PERSON_LEFT_HAND;
                case FIRST_PERSON_RIGHT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.FIRST_PERSON_RIGHT_HAND;
                case THIRD_PERSON_LEFT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_LEFT_HAND;
                case THIRD_PERSON_RIGHT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_RIGHT_HAND;
                default -> itemDisplayContext.leftHand() ? SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_LEFT_HAND
                        : SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_RIGHT_HAND;
            });
        }
    }
}
