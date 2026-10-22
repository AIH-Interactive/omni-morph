package top.aihmc.omnimorph.lua.api.particle;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.lua.LuaTypeManager;
import top.aihmc.omnimorph.lua.api.entity.EntityAPI;
import top.aihmc.omnimorph.math.vector.FiguraVec3;
import top.aihmc.omnimorph.model.rendering.texture.FiguraTexture;
import sun.misc.Unsafe;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static top.aihmc.omnimorph.lua.api.particle.ParticleMotionSmokeTest.near;
import static top.aihmc.omnimorph.lua.api.particle.ParticleMotionSmokeTest.options;
import static top.aihmc.omnimorph.lua.api.particle.ParticleMotionSmokeTest.reject;
import static top.aihmc.omnimorph.lua.api.particle.ParticleMotionSmokeTest.require;

/** Native tick/extract and actual EntityAPI target identity; uses no real game pixels. */
final class ParticleMotionNativeSmokeTest {
    static void run(Unsafe unsafe, Avatar owner, Runnable drain) throws Exception {
        var client = Minecraft.getInstance();
        var previousWorld = client.level;
        var world = (MotionLevel) unsafe.allocateInstance(MotionLevel.class);
        world.entities = new HashMap<>();
        var texture = new FiguraTexture(owner, "particle-motion", new NativeImage(1, 1, true));
        client.level = world;
        var particle = new CustomParticle(owner, world, texture, Vec3.ZERO, new Vec3(1, 0, 0));
        try {
            require(particle.activate(), "motion fixture uses ordinary custom particle lifecycle");
            particle.setLifetime(100);
            var types = new LuaTypeManager();
            types.generateMetatableFor(LuaParticle.class);
            types.generateMetatableFor(FiguraVec3.class);
            LuaValue wrapper = types.javaToLua(new LuaParticle("motion", particle, owner)).arg1();
            var acceleration = FiguraVec3.of(.25, .5, -.25);
            wrapper.get("setAcceleration").call(wrapper, types.javaToLua(acceleration).arg1());
            acceleration.x = 999;
            near(particle.acceleration(), new Vec3(.25, .5, -.25));
            wrapper.get("setRotation").call(wrapper, LuaValue.valueOf(350));
            wrapper.get("setAngularVelocity").call(wrapper, LuaValue.valueOf(20));
            particle.tick();
            near(position(particle), new Vec3(1.25, .5, -.25));
            require(wrapper.get("getRotation").call(wrapper).todouble() == 10
                            && wrapper.get("getAngularVelocity").call(wrapper).tofloat() == 20,
                    "Lua angle properties use degrees and preserve crossing-360 interpolation");
            var state = new RotationQuads();
            particle.extract(state, new FixtureCamera(), .5f);
            require(Math.abs(state.qz) < 1e-5 && Math.abs(Math.abs(state.qw) - 1) < 1e-5,
                    "native extraction sees 360 degrees halfway through the crossing, not a backwards 180-degree turn");
            particle.tick();
            near(position(particle), new Vec3(2.75, 1.5, -.75));
            particle.setAcceleration(Vec3.ZERO);
            setGravity(particle, 5);
            wrapper.get("setMotion").call(wrapper, options("mode", "orbit", "center", FiguraVec3.of(10, 20, 30),
                    "radius", 2, "angularVelocity", 90));
            near(position(particle), new Vec3(12, 20, 30));
            particle.tick();
            near(position(particle), new Vec3(10, 20, 28));
            require(wrapper.get("getMotionMode").call(wrapper).tojstring().equals("orbit"), "Lua selects native orbit");
            wrapper.get("setMotion").call(wrapper, options("mode", "free"));
            particle.tick();
            require(position(particle).distanceTo(new Vec3(8, 19.8, 26)) < 1e-6,
                    "switching back to free motion restores original gravity and retains the last velocity");
            wrapper.get("setMotion").call(wrapper, options("mode", "helix", "center", FiguraVec3.of(0, 0, 0),
                    "radius", 1, "angularVelocity", 90, "rise", .5));
            var stoppedByCollision = Particle.class.getDeclaredField("stoppedByCollision");
            stoppedByCollision.setAccessible(true);
            stoppedByCollision.setBoolean(particle, true);
            particle.tick();
            near(position(particle), new Vec3(0, .5, -1));
            require(stoppedByCollision.getBoolean(particle),
                    "kinematic movement ignores native collision stop without rewriting private free-motion state");
            LuaTable points = new LuaTable();
            for (int index = 1; index <= 4; index++) {
                FiguraVec3 point = switch (index) {
                    case 1 -> FiguraVec3.of(0, 0, 0);
                    case 2 -> FiguraVec3.of(0, 2, 0);
                    case 3 -> FiguraVec3.of(2, 2, 0);
                    default -> FiguraVec3.of(2, 0, 0);
                };
                points.set(index, types.javaToLua(point).arg1());
            }
            var curve = options("mode", "bezier", "duration", 4);
            curve.set("points", points);
            wrapper.get("setMotion").call(wrapper, curve);
            particle.tick();
            particle.tick();
            near(position(particle), new Vec3(1, 1.5, 0));
            particle.setPos(0, 0, 0);
            wrapper.get("setMotion").call(wrapper, options("mode", "tracking", "target", FiguraVec3.of(3, 4, 0), "speed", 2));
            particle.tick();
            near(position(particle), new Vec3(1.2, 1.6, 0));
            reject(() -> particle.setMotion(options("mode", "orbit", "axis", FiguraVec3.of(0, 0, 0))));
            require(particle.motionMode().equals("tracking"), "invalid declaration preserves the previous motion");
            liveTargets(unsafe, owner, world, texture, particle, wrapper);
            var overflow = new CustomParticle(owner, world, texture, Vec3.ZERO, new Vec3(Double.MAX_VALUE, 0, 0));
            require(overflow.activate(), "overflow fixture starts within live capacity");
            overflow.setAcceleration(new Vec3(Double.MAX_VALUE, 0, 0));
            overflow.tick();
            require(!overflow.active() && "NON_FINITE_MOTION".equals(overflow.getMotionError()),
                    "runtime overflow retires just the particle before invalid geometry reaches extraction");
        } finally {
            particle.remove();
            CustomParticleRuntime.clearOwner(owner.owner);
            CustomParticleRuntime.finishFrame();
            texture.close();
            drain.run();
            client.level = previousWorld;
        }
        System.out.println("PASS native particle motion: Lua properties, real acceleration/gravity/tick, all built-ins, "
                + "native roll interpolation, actual EntityAPI target movement/ID reuse/loss and overflow cleanup");
    }

    private static void liveTargets(Unsafe unsafe, Avatar owner, MotionLevel world, FiguraTexture texture,
                                    CustomParticle particle, LuaValue wrapper) throws Exception {
        var target = entity(unsafe, world, UUID.randomUUID());
        world.entities.put(7, target);
        particle.setPos(0, 0, 0);
        wrapper.get("setMotion").call(wrapper, targetOptions(target));
        particle.tick();
        near(position(particle), new Vec3(1, 0, 0));
        target.location = new Vec3(0, 10, 0);
        particle.tick();
        Vec3 expected = new Vec3(1, 0, 0).add(new Vec3(-1, 10, 0).normalize());
        near(position(particle), expected);
        var replacement = entity(unsafe, world, target.uuid);
        world.entities.put(7, replacement);
        particle.tick();
        require(!particle.active() && "TARGET_UNAVAILABLE".equals(particle.getMotionError()),
                "a different entity instance with even the same ID and UUID never hijacks an existing target");
        var stopped = new CustomParticle(owner, world, texture, Vec3.ZERO, Vec3.ZERO);
        try {
            require(stopped.activate(), "stopped-target fixture activates");
            var options = targetOptions(replacement);
            options.set("onTargetLost", "stop");
            stopped.setMotion(options);
            setGravity(stopped, 5);
            world.entities.clear();
            stopped.tick();
            stopped.tick();
            require(stopped.active() && stopped.motionMode().equals("stopped")
                            && "TARGET_UNAVAILABLE".equals(stopped.getMotionError()),
                    "stop policy releases the target and freezes position without removing the ordinary lifetime");
            near(position(stopped), Vec3.ZERO);
            stopped.setLifetime(1);
            stopped.tick();
            require(!stopped.active(), "target-loss stop cannot extend lifetime indefinitely");
        } finally {
            stopped.remove();
        }
    }

    private static LuaTable targetOptions(TrackedEntity target) {
        var options = options("mode", "tracking", "speed", 1);
        options.set("target", LuaValue.userdataOf(new EntityAPI<>(target)));
        return options;
    }

    private static TrackedEntity entity(Unsafe unsafe, MotionLevel world, UUID uuid) throws Exception {
        var entity = (TrackedEntity) unsafe.allocateInstance(TrackedEntity.class);
        entity.world = world;
        entity.uuid = uuid;
        entity.location = new Vec3(10, 0, 0);
        return entity;
    }

    private static Vec3 position(Particle particle) {
        var bounds = particle.getBoundingBox();
        return new Vec3(bounds.getCenter().x, bounds.minY, bounds.getCenter().z);
    }

    private static void setGravity(Particle particle, float value) throws Exception {
        var field = Particle.class.getDeclaredField("gravity");
        field.setAccessible(true);
        field.setFloat(particle, value);
    }

    private static final class RotationQuads extends QuadParticleRenderState {
        private float qz, qw;

        @Override
        public void add(SingleQuadParticle.Layer layer, float x, float y, float z, float qx, float qy, float qz, float qw,
                        float size, float u0, float u1, float v0, float v1, int color, int light) {
            this.qz = qz;
            this.qw = qw;
            super.add(layer, x, y, z, qx, qy, qz, qw, size, u0, u1, v0, v1, color, light);
        }
    }

    private static final class FixtureCamera extends Camera {
        @Override
        public Quaternionf rotation() {
            return new Quaternionf();
        }

        @Override
        public Vec3 position() {
            return Vec3.ZERO;
        }
    }

    private static final class MotionLevel extends ClientLevel {
        private Map<Integer, Entity> entities;

        private MotionLevel() {
            super(null, null, null, null, 0, 0, null, false, 0L, 0);
        }

        @Override
        public Entity getEntity(int id) {
            return entities.get(id);
        }

        @Override
        public boolean hasChunkAt(net.minecraft.core.BlockPos position) {
            return false;
        }
    }

    private static final class TrackedEntity extends ArmorStand {
        private MotionLevel world;
        private UUID uuid;
        private Vec3 location;

        private TrackedEntity() {
            super(null, 0, 0, 0);
        }

        @Override
        public int getId() {
            return 7;
        }

        @Override
        public UUID getUUID() {
            return uuid;
        }

        @Override
        public Level level() {
            return world;
        }

        @Override
        public Vec3 position() {
            return location;
        }
    }
}
