package org.figuramc.figura.model.ysm;

import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.molang.runtime.ExecutionContext;
import org.figuramc.figura.molang.runtime.Function;
import org.figuramc.figura.molang.runtime.MolangExecutionBudget;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/** Captured reference particle parameters; emission planning does not allocate game particles. */
public record YsmParticleRequest(String id, Vec3 offset, Vec3 delta, double speed,
                                 int count, int lifetime, boolean absolute) {
    public static final int MAX_PARTICLES_PER_CALL = 1024;

    public YsmParticleRequest {
        if (id == null || id.isBlank() || !finite(offset) || !finite(delta) || !Double.isFinite(speed)) {
            throw new IllegalArgumentException("Invalid YSM particle parameters");
        }
        if (count < 0 || count > MAX_PARTICLES_PER_CALL || lifetime < 1) {
            throw new IllegalArgumentException("YSM particle count/lifetime budget exceeded");
        }
    }

    public static YsmParticleRequest parse(ExecutionContext<?> context, Function.ArgumentCollection arguments,
                                           boolean absolute) {
        if (arguments.size() == 0) {
            return null;
        }
        String id = arguments.getAsString(context, 0);
        if (id == null || id.isBlank()) {
            return null;
        }
        Vec3 offset = new Vec3(number(context, arguments, 1), number(context, arguments, 2), number(context, arguments, 3));
        Vec3 delta = new Vec3(number(context, arguments, 4), number(context, arguments, 5), number(context, arguments, 6));
        double speed = number(context, arguments, 7);
        int count = arguments.size() > 8 ? Math.max(arguments.getAsInt(context, 8), 0) : 0;
        int lifetime = arguments.size() > 9 ? Math.max(arguments.getAsInt(context, 9), 1) : 20;
        return new YsmParticleRequest(id, offset, delta, speed, count, lifetime, absolute);
    }

    private static double number(ExecutionContext<?> context, Function.ArgumentCollection arguments, int index) {
        return arguments.size() > index ? arguments.getAsDouble(context, index) : 0d;
    }

    public record Emission(Vec3 position, Vec3 velocity, int lifetime) {
    }

    public void emit(Vec3 entityPosition, boolean player, float bodyYaw, float entityYaw,
                     DoubleSupplier gaussian, MolangExecutionBudget budget, Consumer<Emission> sink) {
        int amount = count == 0 ? 1 : count;
        for (int index = 0; index < amount; index++) {
            if (budget != null) {
                budget.consume();
            }
            Vec3 positionOffset = offset;
            Vec3 velocity;
            if (count == 0) {
                velocity = delta.scale(speed);
            } else {
                positionOffset = offset.add(gaussian.getAsDouble() * delta.x,
                        gaussian.getAsDouble() * delta.y, gaussian.getAsDouble() * delta.z);
                velocity = new Vec3(gaussian.getAsDouble() * speed,
                        gaussian.getAsDouble() * speed, gaussian.getAsDouble() * speed);
            }
            if (!absolute) {
                // Reference singles use player body yaw; reference batches always use entity yaw.
                float yaw = count == 0 && player ? bodyYaw : entityYaw;
                positionOffset = positionOffset.yRot(-yaw * 0.017453292f);
            }
            Vec3 position = entityPosition.add(positionOffset);
            if (!finite(position) || !finite(velocity)) {
                throw new IllegalArgumentException("Invalid YSM particle emission");
            }
            sink.accept(new Emission(position, velocity, lifetime));
        }
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
