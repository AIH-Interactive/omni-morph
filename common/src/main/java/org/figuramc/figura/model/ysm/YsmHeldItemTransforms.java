package org.figuramc.figura.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.KineticWeapon;
import org.joml.Matrix4f;

/**
 * Shared third-person item transforms for native YSM hand locators.
 *
 * <p>The values match Sparkle-Morpher's authored-locator path. Keeping this
 * separate from locator resolution makes the behavior apply to every YSM
 * model without encoding model or bone names.</p>
 */
public final class YsmHeldItemTransforms {
    private static final float SPEAR_TIRED_TIP_PLANE_ROLL_DEGREES = 90f;
    private static final float SPEAR_ENGAGED_FORWARD_DEGREES = 90f;
    private static final float SPEAR_ENGAGED_TIP_PLANE_COMPENSATION_DEGREES = 60f;
    private static final float SPEAR_DISENGAGED_DOWN_DEGREES = 6f;
    private static final float SPEAR_DISPLAY_PIVOT_Y = 0.125f;
    private static final float SPEAR_DISPLAY_PIVOT_Z = 0.125f;

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
                || itemStack.getUseAnimation() != ItemUseAnimation.SPEAR)
            return;

        KineticWeapon weapon = itemStack.get(DataComponents.KINETIC_WEAPON);
        InteractionHand hand = leftHand == (entity.getMainArm() == HumanoidArm.LEFT)
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
        if (weapon == null || !entity.isUsingItem() || entity.getUsedItemHand() != hand)
            return;

        float useTicks = entity.getTicksUsingItem();
        float engaged = smoothStep(progress(useTicks, 0f, weapon.delayTicks()));
        int tiredStart = weapon.delayTicks();
        int tiredEnd = tiredStart + weapon.dismountConditions().map(KineticWeapon.Condition::maxDurationTicks).orElse(0);
        int disengagedEnd = Math.max(tiredEnd,
                tiredStart + weapon.damageConditions().map(KineticWeapon.Condition::maxDurationTicks).orElse(0));
        float tired = smoothStep(progress(useTicks, tiredStart, tiredEnd));
        float disengaged = smoothStep(progress(useTicks, tiredEnd, disengagedEnd));
        float handSign = leftHand ? -1f : 1f;

        stack.rotateAround(Axis.XP.rotationDegrees(SPEAR_ENGAGED_TIP_PLANE_COMPENSATION_DEGREES * (1f - engaged)),
                0f, SPEAR_DISPLAY_PIVOT_Y, SPEAR_DISPLAY_PIVOT_Z);
        stack.rotateAround(Axis.YP.rotationDegrees(handSign * (SPEAR_ENGAGED_FORWARD_DEGREES + SPEAR_TIRED_TIP_PLANE_ROLL_DEGREES * tired)),
                0f, SPEAR_DISPLAY_PIVOT_Y, SPEAR_DISPLAY_PIVOT_Z);
        stack.rotateAround(Axis.XP.rotationDegrees(SPEAR_DISENGAGED_DOWN_DEGREES * disengaged),
                0f, SPEAR_DISPLAY_PIVOT_Y, SPEAR_DISPLAY_PIVOT_Z);
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

    private static float progress(float value, float start, float end) {
        if (end <= start)
            return value >= end ? 1f : 0f;
        return Math.max(0f, Math.min(1f, (value - start) / (end - start)));
    }

    private static float smoothStep(float value) {
        return value * value * (3f - 2f * value);
    }
}
