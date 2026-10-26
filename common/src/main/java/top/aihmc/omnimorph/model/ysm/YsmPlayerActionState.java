package top.aihmc.omnimorph.model.ysm;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * Reference {@code ControllerActionResolver}: one ordered decision for a player's base action.
 * The Molang {@code ctrl.*} states and the native animation state machine both read this table,
 * so a query and the played animation cannot disagree about the current action.
 */
public final class YsmPlayerActionState {
    public static final float MIN_MOVEMENT_SPEED = 0.05f;
    private static final float LADDER_STILLNESS_SPEED = 0.01f;

    public enum State {
        NONE(""),
        RIDE("ride"),
        DEATH("death"),
        RIPTIDE("riptide"),
        SLEEP("sleep"),
        SWIM("swim"),
        CLIMB("climb"),
        CLIMBING("climbing"),
        LADDER_UP("ladder_up"),
        LADDER_STILLNESS("ladder_stillness"),
        LADDER_DOWN("ladder_down"),
        ELYTRA_FLY("elytra_fly"),
        FLY("fly"),
        SWIM_STAND("swim_stand"),
        ATTACKED("attacked"),
        JUMP("jump"),
        SNEAK("sneak"),
        SNEAKING("sneaking"),
        RUN("run"),
        WALK("walk"),
        IDLE("idle");

        private final String animationName;

        State(String animationName) {
            this.animationName = animationName;
        }

        /** Same names the default animation set and the {@code ctrl.*} bindings use. */
        public String animationName() {
            return animationName;
        }
    }

    /**
     * Frame inputs of the decision. The entity path fills every field; the render-state path
     * leaves the flags that a render state does not carry at false.
     */
    public record Snapshot(boolean riding, boolean dead, boolean riptide, boolean sleeping,
                           boolean swimming, boolean swimmingPose, boolean climbing,
                           boolean elytraFlying, boolean flying, boolean inWater, boolean onGround,
                           boolean crouching, boolean sprinting, boolean attacked,
                           float groundSpeed, float verticalSpeed) {
        public boolean moving() {
            return Float.isFinite(groundSpeed) && groundSpeed > MIN_MOVEMENT_SPEED;
        }
    }

    private YsmPlayerActionState() {
    }

    public static Snapshot snapshot(LivingEntity entity, float groundSpeed, float verticalSpeed) {
        Pose pose = entity.getPose();
        boolean elytraFlying = pose == Pose.FALL_FLYING && entity.isFallFlying();
        Entity vehicle = entity.getVehicle();
        return new Snapshot(
                vehicle != null && vehicle.isAlive(),
                entity.isDeadOrDying(),
                entity.isAutoSpinAttack(),
                pose == Pose.SLEEPING,
                entity.isSwimming(),
                pose == Pose.SWIMMING,
                entity.onClimbable(),
                elytraFlying,
                !elytraFlying && entity instanceof Player player && player.getAbilities().flying,
                entity.isInWater(),
                entity.onGround(),
                pose == Pose.CROUCHING,
                entity.isSprinting(),
                entity.hurtTime > 0,
                groundSpeed,
                verticalSpeed);
    }

    /** Ordered like the reference: riding suppresses everything, then death, riptide and sleep. */
    public static State resolve(Snapshot snapshot) {
        if (snapshot.riding()) {
            return State.RIDE;
        }
        if (snapshot.dead()) {
            return State.DEATH;
        }
        if (snapshot.riptide()) {
            return State.RIPTIDE;
        }
        if (snapshot.sleeping()) {
            return State.SLEEP;
        }
        if (snapshot.swimming()) {
            return State.SWIM;
        }
        if (snapshot.swimmingPose()) {
            return snapshot.moving() ? State.CLIMB : State.CLIMBING;
        }
        if (snapshot.climbing()) {
            if (snapshot.verticalSpeed() > LADDER_STILLNESS_SPEED) {
                return State.LADDER_UP;
            }
            if (snapshot.verticalSpeed() < -LADDER_STILLNESS_SPEED) {
                return State.LADDER_DOWN;
            }
            return State.LADDER_STILLNESS;
        }
        if (snapshot.elytraFlying()) {
            return State.ELYTRA_FLY;
        }
        if (snapshot.flying()) {
            return State.FLY;
        }
        if (snapshot.inWater() && !snapshot.onGround()) {
            return State.SWIM_STAND;
        }
        if (snapshot.attacked()) {
            return State.ATTACKED;
        }
        if (!snapshot.onGround() && !snapshot.inWater()) {
            return State.JUMP;
        }
        if (snapshot.onGround() && snapshot.crouching()) {
            return snapshot.moving() ? State.SNEAK : State.SNEAKING;
        }
        if (snapshot.onGround() && snapshot.sprinting() && snapshot.moving()) {
            return State.RUN;
        }
        if (snapshot.onGround() && snapshot.moving()) {
            return State.WALK;
        }
        return State.IDLE;
    }
}
