package top.aihmc.omnimorph.lua.api.particle;

import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;
import top.aihmc.omnimorph.math.vector.FiguraVec3;

import java.util.concurrent.atomic.AtomicReference;

/** Pure trajectory math and real Lua declaration parsing, without advancing clocks from render frequency. */
final class ParticleMotionSmokeTest {
    static void run() {
        orbitAndHelix();
        bezier();
        tracking();
        invalidAndRawReads();
        System.out.println("PASS particle motion: arbitrary axes, orbit/helix, immutable cubic Bezier, bounded tracking, "
                + "fixed Lua reads and invalid values; live entity/rendering checks separate");
    }

    private static void orbitAndHelix() {
        var options = options("mode", "orbit", "center", FiguraVec3.of(1, 2, 3), "radius", 2,
                "angularVelocity", 90);
        var orbit = ParticleMotion.read(options, Vec3.ZERO, null);
        near(orbit.position(Vec3.ZERO, 0), new Vec3(3, 2, 3));
        near(orbit.position(Vec3.ZERO, 1), new Vec3(1, 2, 1));
        near(orbit.position(Vec3.ZERO, 2), new Vec3(-1, 2, 3));
        options.set("mode", "helix");
        options.set("rise", LuaValue.valueOf(.5));
        var helix = ParticleMotion.read(options, Vec3.ZERO, null);
        near(helix.position(Vec3.ZERO, 2), new Vec3(-1, 3, 3));
        for (double time = 0; time < 4; time += .125) {
            near(orbit.position(Vec3.ZERO, time), orbit.position(new Vec3(999, 1, 2), time));
        }
        var axis = FiguraVec3.of(1, 2, 3);
        options.set("axis", LuaValue.userdataOf(axis));
        options.set("rise", LuaValue.ZERO);
        var tilted = ParticleMotion.read(options, Vec3.ZERO, null);
        axis.x = 999;
        Vec3 radial = tilted.position(Vec3.ZERO, .75).subtract(new Vec3(1, 2, 3));
        near(radial.length(), 2);
        near(radial.dot(new Vec3(1, 2, 3).normalize()), 0);
        var extreme = ParticleMotion.read(options("mode", "orbit", "axis",
                FiguraVec3.of(Double.MAX_VALUE, Double.MAX_VALUE, 0)), Vec3.ZERO, null);
        near(extreme.position(Vec3.ZERO, 1).length(), .5);
    }

    private static void bezier() {
        var first = FiguraVec3.of(0, 0, 0);
        var points = new LuaTable();
        points.set(1, LuaValue.userdataOf(first));
        points.set(2, LuaValue.userdataOf(FiguraVec3.of(0, 2, 0)));
        points.set(3, LuaValue.userdataOf(FiguraVec3.of(2, 2, 0)));
        points.set(4, LuaValue.userdataOf(FiguraVec3.of(2, 0, 0)));
        var settings = options("mode", "bezier", "duration", 4);
        settings.set("points", points);
        var path = ParticleMotion.read(settings, Vec3.ZERO, null);
        first.x = 999;
        near(path.position(Vec3.ZERO, 0), Vec3.ZERO);
        near(path.position(Vec3.ZERO, 2), new Vec3(1, 1.5, 0));
        near(path.position(Vec3.ZERO, 4), new Vec3(2, 0, 0));
        near(path.position(Vec3.ZERO, 100), new Vec3(2, 0, 0));
        first.x = 0;
        settings.set("loop", LuaValue.TRUE);
        var loop = ParticleMotion.read(settings, Vec3.ZERO, null);
        near(loop.position(Vec3.ZERO, 6), new Vec3(1, 1.5, 0));
        points.set(5, LuaValue.userdataOf(FiguraVec3.of(1, 1, 1)));
        reject(() -> ParticleMotion.read(settings, Vec3.ZERO, null));
    }

    private static void tracking() {
        var destination = FiguraVec3.of(3, 4, 0);
        var path = ParticleMotion.read(options("mode", "tracking", "target", destination, "speed", 2),
                Vec3.ZERO, null);
        destination.x = 999;
        Vec3 position = path.position(Vec3.ZERO, 1);
        near(position, new Vec3(1.2, 1.6, 0));
        position = path.position(position, 2);
        near(path.position(position, 3), new Vec3(3, 4, 0));
        var target = new AtomicReference<>(new Vec3(1, 0, 0));
        var moving = new ParticleMotion("tracking", new ParticleMotion.Tracking(target::get, .5), false);
        near(moving.position(Vec3.ZERO, 1), new Vec3(.5, 0, 0));
        target.set(new Vec3(-1, 0, 0));
        near(moving.position(Vec3.ZERO, 2), new Vec3(-.5, 0, 0));
        target.set(null);
        require(moving.position(Vec3.ZERO, 3) == null, "unavailable targets have an explicit non-position result");
    }

    private static void invalidAndRawReads() {
        reject(() -> ParticleMotion.read(options("mode", "unknown"), Vec3.ZERO, null));
        reject(() -> ParticleMotion.read(options("mode", "orbit", "axis", FiguraVec3.of(0, 0, 0)), Vec3.ZERO, null));
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            reject(() -> ParticleMotion.read(options("mode", "orbit", "radius", invalid), Vec3.ZERO, null));
        }
        reject(() -> ParticleMotion.read(options("mode", "orbit", "angularVelocity", "2"), Vec3.ZERO, null));
        reject(() -> ParticleMotion.read(options("mode", "tracking", "target", FiguraVec3.of(0, 0, 0),
                "onTargetLost", "ignore"), Vec3.ZERO, null));
        reject(() -> ParticleMotion.read(options("mode", "tracking", "target", "remote-url"), Vec3.ZERO, null));
        var metatable = new LuaTable();
        metatable.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue receiver, LuaValue key) {
                throw new AssertionError("Motion configuration must not run author metamethods");
            }
        });
        var raw = options("mode", "orbit");
        raw.setmetatable(metatable);
        raw.rawset(Integer.MAX_VALUE, LuaValue.ONE);
        require(ParticleMotion.read(raw, Vec3.ZERO, null).mode().equals("orbit"), "fixed option reads ignore sparse excess");
    }

    static LuaTable options(Object... pairs) {
        var table = new LuaTable();
        for (int index = 0; index < pairs.length; index += 2) {
            Object value = pairs[index + 1];
            table.set((String) pairs[index], value instanceof Number number ? LuaValue.valueOf(number.doubleValue())
                    : value instanceof String text ? LuaValue.valueOf(text) : LuaValue.userdataOf(value));
        }
        return table;
    }

    static void near(Vec3 actual, Vec3 expected) {
        near(actual.x, expected.x);
        near(actual.y, expected.y);
        near(actual.z, expected.z);
    }

    private static void near(double actual, double expected) {
        require(Math.abs(actual - expected) < 1e-7, "Expected " + expected + ", actual " + actual);
    }

    static void reject(Runnable action) {
        try {
            action.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Expected invalid motion to be rejected");
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
