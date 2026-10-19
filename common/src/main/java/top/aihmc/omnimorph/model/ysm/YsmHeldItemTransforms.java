package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.effects.SpearAnimations;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

/**
 * Shared third-person item transforms for native YSM hand locators.
 *
 * <p>Authored locators own the grip, while item-space use effects follow the
 * current Minecraft implementation. This applies to every YSM model without
 * encoding model or bone names.</p>
 */
public final class YsmHeldItemTransforms {
    private YsmHeldItemTransforms() {
    }

    public static void apply(PoseStack stack, LivingEntity entity, ItemStack itemStack, boolean leftHand, boolean directHandBone) {
        // Figura's native bone chain ends in model coordinates, unlike
        // Sparkle's pre-normalized locator matrix. Every path needs this basis.
        applyItemBasis(stack, itemStack, directHandBone);
        normalizeBowScale(stack, itemStack);
        applyKineticSpearUseTransform(stack, entity, itemStack, leftHand);
    }

    private static void applyItemBasis(PoseStack stack, ItemStack itemStack, boolean directHandBone) {
        stack.translate(0d, -0.0625d, -0.1d);
        stack.mulPose(Axis.XP.rotationDegrees(-90f));

        if (itemStack == null || itemStack.isEmpty())
            return;

        if (itemStack.is(Items.TRIDENT)) {
            if (!directHandBone)
                stack.translate(0d, 0d, -0.0125d);
        } else if (itemStack.is(Items.MACE)) {
            stack.translate(0d, directHandBone ? -0.0125d : 0d, 0.01875d);
        } else if (isSpearHeldItem(itemStack)) {
            stack.translate(0d, directHandBone ? -0.01875d : -0.0125d, -0.025d);
        }
    }

    private static void normalizeBowScale(PoseStack stack, ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || itemStack.getUseAnimation() != ItemUseAnimation.BOW)
            return;

        Matrix4f matrix = stack.last().pose();
        float scale = Math.max(axisLength(matrix.m00(), matrix.m01(), matrix.m02()),
                Math.max(axisLength(matrix.m10(), matrix.m11(), matrix.m12()),
                        axisLength(matrix.m20(), matrix.m21(), matrix.m22())));
        if (scale > 1f && Float.isFinite(scale))
            stack.scale(1f / scale, 1f / scale, 1f / scale);
    }

    private static void applyKineticSpearUseTransform(PoseStack stack, LivingEntity entity, ItemStack itemStack, boolean leftHand) {
        if (entity == null || itemStack == null || itemStack.isEmpty()
                || itemStack.getUseAnimation() != ItemUseAnimation.SPEAR
                || !itemStack.has(DataComponents.KINETIC_WEAPON))
            return;

        InteractionHand hand = leftHand == (entity.getMainArm() == HumanoidArm.LEFT)
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
        if (!entity.isUsingItem() || entity.getUsedItemHand() != hand)
            return;

        // Use the current game's item-space transform after the authored YSM grip.
        // Its pivots, charge phases, movement and hit feedback must stay in sync with vanilla.
        var state = new ArmedEntityRenderState();
        state.attackTime = entity.getAttackAnim(0f);
        state.ticksSinceKineticHitFeedback = entity.getTicksSinceLastKineticHitFeedback(0f);
        SpearAnimations.thirdPersonUseItem(state, stack, entity.getTicksUsingItem(),
                leftHand ? HumanoidArm.LEFT : HumanoidArm.RIGHT, itemStack);
    }

    /**
     * The hold anchor depends on the item's display orientation, not on whether
     * it exposes charge timing data. Vanilla spears and modded lances may not
     * carry {@link DataComponents#KINETIC_WEAPON}, but still need this basis.
     */
    private static boolean isSpearHeldItem(ItemStack itemStack) {
        return !itemStack.is(Items.TRIDENT)
                && itemStack.getUseAnimation() == ItemUseAnimation.SPEAR;
    }

    private static float axisLength(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }
}
