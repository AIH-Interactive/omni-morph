package org.figuramc.figura.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.model.ysm.YsmModelRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla first-person arm rendering with YSM arm geometry.
 * Follows the same injection-point strategy as Sparkle-Morpher's
 * {@code PlayerRendererHandMixin}.
 *
 * <p>{@code AvatarRenderer.renderRightHand/renderLeftHand} are called from
 * {@code ItemInHandRenderer.renderPlayerArm} whenever vanilla decides to
 * render the first-person arm (both with and without held items).</p>
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Unique
    private static MultiBufferSource singleBuffer(VertexConsumer consumer) {
        return type -> consumer;
    }

    /**
     * Replace the vanilla right arm with the YSM right-arm geometry.
     */
    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
    private void ysm$renderRightHand(PoseStack poseStack, SubmitNodeCollector collector,
                                      int packedLight, Identifier skinTexture, boolean renderSleeve,
                                      CallbackInfo ci) {
        if (ysm$renderArm(poseStack, collector, packedLight, false))
            ci.cancel();
    }

    /**
     * Replace the vanilla left arm with the YSM left-arm geometry.
     */
    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    private void ysm$renderLeftHand(PoseStack poseStack, SubmitNodeCollector collector,
                                     int packedLight, Identifier skinTexture, boolean renderSleeve,
                                     CallbackInfo ci) {
        if (ysm$renderArm(poseStack, collector, packedLight, true))
            ci.cancel();
    }

    @Unique
    private boolean ysm$renderArm(PoseStack poseStack, SubmitNodeCollector collector,
                                   int packedLight, boolean left) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isInvisible())
            return false;

        Avatar avatar = AvatarManager.getAvatarForPlayer(player.getUUID());
        if (avatar == null || !avatar.isYsmNative())
            return false;

        YsmModelRuntime ysm = avatar.getYsmRuntime();
        if (ysm == null)
            return false;

        try {
            // Ensure the YSM texture is uploaded and get its RenderType
            ysm.texture().uploadIfDirty(false, false);
            Identifier texLocation = ysm.texture().getLocation();
            RenderType renderType = RenderTypes.entityCutout(texLocation);

            // Copy the vanilla PoseStack state into a fresh stack.
            // renderFirstPersonArm manages its own pushPose/popPose internally.
            final PoseStack fpStack = new PoseStack();
            fpStack.last().set(poseStack.last());

            // Submit custom geometry via the 26.1 submit/extract pipeline.
            // This is the correct API — the geometry will be rendered during
            // the extraction phase together with other first-person geometry.
            collector.submitCustomGeometry(fpStack, renderType, (pose, buffer) -> {
                ysm.renderer().renderFirstPersonArm(fpStack, singleBuffer(buffer), packedLight, left);
            });

            return true;
        } catch (Exception e) {
            // If anything goes wrong, fall back to vanilla arm rendering
            return false;
        }
    }
}
