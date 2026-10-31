package top.aihmc.omnimorph.model.ysm.animation;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Per-render entity values the YSM animation player samples once per frame.
 * <p>
 * MC 1.21.1 has no entity render state; {@link #capture} derives the same values
 * that {@code LivingEntityRenderer.render} feeds into {@code setupAnim}.
 *
 * @param ageInTicks         entity tick count plus the partial tick
 * @param walkAnimationSpeed limb swing amount, clamped to 1 and zero while riding or dead
 * @param netHeadYaw         head yaw relative to the body, in degrees
 * @param headPitch          head pitch, in degrees
 * @param attackAnim         swing progress of the current attack
 */
public record YsmEntityFrame(
        float ageInTicks,
        float walkAnimationSpeed,
        float netHeadYaw,
        float headPitch,
        float attackAnim
) {
    public static YsmEntityFrame capture(LivingEntity entity, float partialTick) {
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float netHeadYaw = headYaw - bodyYaw;

        if (entity.isPassenger() && entity.getVehicle() instanceof LivingEntity vehicle) {
            bodyYaw = Mth.rotLerp(partialTick, vehicle.yBodyRotO, vehicle.yBodyRot);
            netHeadYaw = headYaw - bodyYaw;
            float clampedYaw = Mth.clamp(Mth.wrapDegrees(netHeadYaw), -85f, 85f);
            bodyYaw = headYaw - clampedYaw;
            if (clampedYaw * clampedYaw > 2500f) {
                bodyYaw += clampedYaw * 0.2f;
            }
            netHeadYaw = headYaw - bodyYaw;
        }

        float headPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        if (LivingEntityRenderer.isEntityUpsideDown(entity)) {
            headPitch *= -1f;
            netHeadYaw *= -1f;
        }
        netHeadYaw = Mth.wrapDegrees(netHeadYaw);

        float walkSpeed = 0f;
        if (!entity.isPassenger() && entity.isAlive()) {
            walkSpeed = Math.min(entity.walkAnimation.speed(partialTick), 1f);
        }

        return new YsmEntityFrame(
                entity.tickCount + partialTick,
                walkSpeed,
                netHeadYaw,
                headPitch,
                entity.getAttackAnim(partialTick)
        );
    }
}
