package top.aihmc.omnimorph.lua.api.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.figuramc.figura.lua.api.entity.EntityAPI;
import org.figuramc.figura.math.vector.FiguraVec3;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Fixed-cost built-in trajectories. Author Lua functions are never used as native target suppliers. */
record ParticleMotion(String mode, Trajectory trajectory, boolean stopOnTargetLost) {
    interface Trajectory {
        Vec3 position(Vec3 current, double elapsedTicks);
    }

    record Orbit(Vec3 center, Vec3 axis, Vec3 u, Vec3 v, double radius,
                 double angularVelocity, double phase, double rise) implements Trajectory {
        @Override
        public Vec3 position(Vec3 current, double elapsedTicks) {
            double angle = phase + angularVelocity * elapsedTicks;
            return center.add(u.scale(radius * Math.cos(angle))).add(v.scale(radius * Math.sin(angle)))
                    .add(axis.scale(rise * elapsedTicks));
        }
    }

    record Bezier(List<Vec3> points, double duration, boolean loop) implements Trajectory {
        Bezier {
            points = List.copyOf(points);
        }

        @Override
        public Vec3 position(Vec3 current, double elapsedTicks) {
            double t = loop ? (elapsedTicks % duration) / duration : Math.clamp(elapsedTicks / duration, 0, 1);
            // De Casteljau interpolation avoids large polynomial coefficients at long world coordinates.
            Vec3 a = points.get(0).lerp(points.get(1), t);
            Vec3 b = points.get(1).lerp(points.get(2), t);
            Vec3 c = points.get(2).lerp(points.get(3), t);
            return a.lerp(b, t).lerp(b.lerp(c, t), t);
        }
    }

    record Tracking(Supplier<Vec3> target, double speed) implements Trajectory {
        @Override
        public Vec3 position(Vec3 current, double elapsedTicks) {
            Vec3 destination = target.get();
            if (destination == null) {
                return null;
            }
            finite(destination);
            Vec3 delta = destination.subtract(current);
            double distance = delta.length();
            if (!Double.isFinite(distance)) {
                throw new LuaError("Custom particle tracking distance must be finite");
            }
            return distance <= speed ? destination : current.add(delta.scale(speed / distance));
        }
    }

    static ParticleMotion read(LuaTable options, Vec3 origin, ClientLevel world) {
        if (options == null) {
            throw new LuaError("Custom particle motion requires a table");
        }
        finite(origin);
        String mode = text(options, "mode", "free").toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "free" -> new ParticleMotion(mode, null, false);
            case "orbit", "helix" -> orbit(options, origin, mode);
            case "bezier" -> bezier(options);
            case "tracking" -> tracking(options, world);
            default -> throw new LuaError("Unknown custom particle motion mode");
        };
    }

    Vec3 position(Vec3 current, double elapsedTicks) {
        finite(current);
        if (!Double.isFinite(elapsedTicks) || elapsedTicks < 0) {
            throw new LuaError("Custom particle motion time must be finite and nonnegative");
        }
        Vec3 result = trajectory == null ? current : trajectory.position(current, elapsedTicks);
        if (result != null) {
            finite(result);
        }
        return result;
    }

    private static ParticleMotion orbit(LuaTable options, Vec3 origin, String mode) {
        Vec3 center = vector(options.rawget("center"), origin);
        Vec3 axis = normalize(vector(options.rawget("axis"), new Vec3(0, 1, 0)));
        Vec3 reference = Math.abs(axis.x) < .9 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
        Vec3 u = normalize(reference.subtract(axis.scale(reference.dot(axis))));
        Vec3 v = axis.cross(u);
        double radius = positive(options, "radius", .5);
        double angularVelocity = Math.toRadians(number(options, "angularVelocity", 15));
        double phase = Math.toRadians(number(options, "phase", 0));
        double rise = mode.equals("helix") ? number(options, "rise", .05) : 0;
        return new ParticleMotion(mode, new Orbit(center, axis, u, v, radius, angularVelocity, phase, rise), false);
    }

    private static ParticleMotion bezier(LuaTable options) {
        var value = options.rawget("points");
        if (!value.istable() || CustomParticleLayer.arrayLength(value.checktable(), 4) != 4) {
            throw new LuaError("Custom particle Bezier motion requires four control points");
        }
        var points = List.of(vector(value.checktable().rawget(1), null), vector(value.checktable().rawget(2), null),
                vector(value.checktable().rawget(3), null), vector(value.checktable().rawget(4), null));
        return new ParticleMotion("bezier", new Bezier(points, positive(options, "duration", 20),
                flag(options, "loop", false)), false);
    }

    private static ParticleMotion tracking(LuaTable options, ClientLevel world) {
        LuaValue value = options.rawget("target");
        Supplier<Vec3> target;
        if (value.isuserdata(EntityAPI.class)) {
            Entity entity = ((EntityAPI<?>) value.touserdata(EntityAPI.class)).getEntity();
            if (world == null || entity == null || entity.level() != world || entity.isRemoved()
                    || world.getEntity(entity.getId()) != entity) {
                throw new LuaError("Custom particle target must be loaded in the current particle world");
            }
            int id = entity.getId();
            var uuid = entity.getUUID();
            var reference = new WeakReference<>(entity);
            target = () -> {
                Entity current = reference.get();
                return current == null || current.isRemoved() || current.level() != world
                        || world.getEntity(id) != current || !uuid.equals(current.getUUID()) ? null : current.position();
            };
        } else {
            Vec3 destination = vector(value, null);
            target = () -> destination;
        }
        String lost = text(options, "onTargetLost", "remove").toLowerCase(Locale.ROOT);
        if (!lost.equals("remove") && !lost.equals("stop")) {
            throw new LuaError("Custom particle onTargetLost must be remove or stop");
        }
        return new ParticleMotion("tracking", new Tracking(target, positive(options, "speed", .1)), lost.equals("stop"));
    }

    private static Vec3 normalize(Vec3 value) {
        double maximum = Math.max(Math.abs(value.x), Math.max(Math.abs(value.y), Math.abs(value.z)));
        if (maximum == 0) {
            throw new LuaError("Custom particle motion axis must be nonzero");
        }
        Vec3 scaled = new Vec3(value.x / maximum, value.y / maximum, value.z / maximum);
        return scaled.scale(1 / scaled.length());
    }

    private static Vec3 vector(LuaValue value, Vec3 fallback) {
        if (value.isnil() && fallback != null) {
            return fallback;
        }
        if (!value.isuserdata(FiguraVec3.class)) {
            throw new LuaError("Custom particle motion vectors require a Vector3");
        }
        var input = (FiguraVec3) value.touserdata(FiguraVec3.class);
        Vec3 result = new Vec3(input.x, input.y, input.z);
        finite(result);
        return result;
    }

    static void finite(Vec3 value) {
        if (value == null || !Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new LuaError("Custom particle motion vectors must be finite");
        }
    }

    private static double positive(LuaTable table, String key, double fallback) {
        double value = number(table, key, fallback);
        if (value <= 0) {
            throw new LuaError("Custom particle " + key + " must be positive");
        }
        return value;
    }

    private static double number(LuaTable table, String key, double fallback) {
        LuaValue value = table.rawget(key);
        if (value.isnil()) {
            return fallback;
        }
        if (value.type() != LuaValue.TNUMBER || !Double.isFinite(value.todouble())) {
            throw new LuaError("Custom particle " + key + " must be a finite number");
        }
        return value.todouble();
    }

    private static String text(LuaTable table, String key, String fallback) {
        LuaValue value = table.rawget(key);
        if (value.isnil()) {
            return fallback;
        }
        if (value.type() != LuaValue.TSTRING) {
            throw new LuaError("Custom particle " + key + " must be a string");
        }
        return value.tojstring();
    }

    private static boolean flag(LuaTable table, String key, boolean fallback) {
        LuaValue value = table.rawget(key);
        if (value.isnil()) {
            return fallback;
        }
        if (!value.isboolean()) {
            throw new LuaError("Custom particle " + key + " must be a boolean");
        }
        return value.toboolean();
    }
}
