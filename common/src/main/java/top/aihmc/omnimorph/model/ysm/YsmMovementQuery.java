package top.aihmc.omnimorph.model.ysm;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.avatar.Avatar;

/** Native movement queries using the same captured seek interval as position_delta. */
public final class YsmMovementQuery {
    private static final float EPSILON = 1.0E-4f;

    private YsmMovementQuery() {
    }

    public static float groundSpeed(Avatar.MolangContext context) {
        Vec3 delta = capturedDelta(context);
        float ticks = deltaTicks(context);
        float trackedSpeed = ticks > EPSILON ? 20f * horizontalLength(delta) / ticks : 0f;
        if (usable(trackedSpeed)) {
            return trackedSpeed;
        }
        float limbSpeed = Math.abs(context.ysm_limb_swing_amount);
        if (usable(limbSpeed)) {
            return limbSpeed;
        }
        Entity entity = context.entity;
        if (entity instanceof LivingEntity living) {
            float walkSpeed = Math.abs(living.walkAnimation.speed(context.ysm_partial_tick));
            if (usable(walkSpeed)) {
                return walkSpeed;
            }
        }
        float velocitySpeed = 20f * horizontalLength(sanitize(entity.getDeltaMovement()));
        if (usable(velocitySpeed)) {
            return velocitySpeed;
        }
        float tickSpeed = 20f * horizontalLength(tickDelta(entity));
        return Float.isFinite(tickSpeed) && tickSpeed > 0f ? tickSpeed : 0f;
    }

    public static float verticalSpeed(Avatar.MolangContext context) {
        Vec3 delta = capturedDelta(context);
        float ticks = deltaTicks(context);
        if (ticks > EPSILON && Math.abs(delta.y) > EPSILON) {
            float trackedSpeed = 20f * (float) delta.y / ticks;
            if (Float.isFinite(trackedSpeed)) {
                return trackedSpeed;
            }
        }
        Vec3 velocity = sanitize(context.entity.getDeltaMovement());
        // Small negative on-ground gravity is not vertical travel in the reference query.
        if (Math.abs(velocity.y) > 0.1d) {
            float speed = 20f * (float) velocity.y;
            if (Float.isFinite(speed)) {
                return speed;
            }
        }
        float tickSpeed = 20f * (float) (context.entity.getY() - context.entity.yo);
        return Float.isFinite(tickSpeed) ? tickSpeed : 0f;
    }

    private static float deltaTicks(Avatar.MolangContext context) {
        float ticks = context.ysm_position_delta_ticks;
        return Float.isFinite(ticks) && ticks > EPSILON ? ticks : 0f;
    }

    private static Vec3 capturedDelta(Avatar.MolangContext context) {
        return sanitize(new Vec3(context.pos_delta_x, context.pos_delta_y, context.pos_delta_z));
    }

    private static Vec3 tickDelta(Entity entity) {
        return sanitize(new Vec3(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo));
    }

    private static Vec3 sanitize(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z) ? value : Vec3.ZERO;
    }

    private static float horizontalLength(Vec3 value) {
        return Mth.sqrt((float) (value.x * value.x + value.z * value.z));
    }

    private static boolean usable(float value) {
        return Float.isFinite(value) && value > EPSILON;
    }
}
