package org.figuramc.figura.model.ysm;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.WakeParticle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.control.AvatarControlRuntime;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.molang.MolangEngine;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.ducks.ParticleEngineAccessor;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.permissions.Permissions;
import org.figuramc.figura.utils.RefilledNumber;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/** CPU native API/owner-quota test; the engine adapter records submissions without rendering. */
public final class YsmParticleNativeSmokeTest {
    public static void main(String[] args) throws Exception {
        Path environment = Files.createTempDirectory("figura-native-particles-").toAbsolutePath().normalize();
        String previousDirectory = System.getProperty("figura.test.directory");
        System.setProperty("figura.test.directory", environment.toString());
        Field instanceField = Minecraft.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        Object previousClient = instanceField.get(null);
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            runTests(instanceField);
        } finally {
            instanceField.set(null, previousClient);
            if (previousDirectory == null) {
                System.clearProperty("figura.test.directory");
            } else {
                System.setProperty("figura.test.directory", previousDirectory);
            }
            try (var files = Files.walk(environment)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(environment)) {
                        throw new IllegalStateException("Unexpected fixture cleanup path");
                    }
                    Files.delete(path);
                }
            }
        }
    }

    private static void runTests(Field instanceField) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        RecordingClient client = (RecordingClient) unsafe.allocateInstance(RecordingClient.class);
        client.pending = new ArrayDeque<>();
        RecordingEngine engine = (RecordingEngine) unsafe.allocateInstance(RecordingEngine.class);
        set(Minecraft.class, client, "particleEngine", engine);
        instanceField.set(null, client);
        Avatar first = owner(unsafe, 2);
        Avatar second = owner(unsafe, 1);
        YsmModelRuntime runtime = runtime(first);
        Method spawn = YsmParticleRuntime.class.getDeclaredMethod("spawn", Minecraft.class, Avatar.class,
                ParticleOptions.class, String.class, YsmParticleRequest.Emission.class);
        spawn.setAccessible(true);
        YsmParticleRequest.Emission emission = new YsmParticleRequest.Emission(
                new Vec3(1, 2, 3), new Vec3(4, 5, 6), 120);

        engine.next = (Particle) unsafe.allocateInstance(FixtureParticle.class);
        first.noPermissions.add(Permissions.PARTICLES);
        spawn.invoke(runtime.particles(), client, first, ParticleTypes.FLAME, "minecraft:flame", emission);
        require(engine.created == 1 && engine.spawned == 1, "production creation and LuaParticle submission both execute");
        require(engine.submitted == engine.next && engine.lifetimeAtSpawn == 120,
                "native lifetime is set before submission");
        require(engine.ownerAtSpawn.equals(first.owner), "LuaParticle submission carries the actual Avatar UUID");
        require(first.particlesRemaining.peek() == 1 && second.particlesRemaining.peek() == 1,
                "real quota consumes exactly one from the owning Avatar");
        require(!first.noPermissions.contains(Permissions.PARTICLES), "successful native submission clears denial");
        require(engine.position.equals(emission.position()) && engine.velocity.equals(emission.velocity()),
                "captured production emission reaches the engine unchanged");

        engine.next = (Particle) unsafe.allocateInstance(WakeParticle.class);
        spawn.invoke(runtime.particles(), client, first, ParticleTypes.FLAME, "fixture:wake", emission);
        require(engine.lifetimeAtSpawn == 120 && engine.next.getLifetime() == 120,
                "YSM sets native WakeParticle lifetime without the Lua setter's 60-tick clamp");
        require(first.particlesRemaining.peek() == 0 && engine.spawned == 2, "second submission exhausts exact owner quota");
        spawn.invoke(runtime.particles(), client, first, ParticleTypes.FLAME, "minecraft:flame", emission);
        require(engine.created == 2 && engine.spawned == 2 && first.noPermissions.contains(Permissions.PARTICLES),
                "exhausted quota refuses creation and marks particle denial");

        spawn.invoke(runtime.particles(), client, second, ParticleTypes.FLAME, "minecraft:flame", emission);
        require(engine.spawned == 3 && second.particlesRemaining.peek() == 0,
                "another Avatar can submit despite the first Avatar's exhausted quota");
        for (int tick = 0; tick < 20; tick++) {
            first.particlesRemaining.tick();
        }
        require(first.particlesRemaining.peek() == 2, "real permission counter refills with client ticks");
        engine.next = null;
        spawn.invoke(runtime.particles(), client, first, ParticleTypes.FLAME, "minecraft:flame", emission);
        require(engine.created == 4 && engine.spawned == 3 && first.particlesRemaining.peek() == 2,
                "missing native particle provider does not consume quota");
        set(Minecraft.class, client, "pause", true);
        spawn.invoke(runtime.particles(), client, first, ParticleTypes.FLAME, "minecraft:flame", emission);
        require(engine.created == 4 && engine.spawned == 3 && first.particlesRemaining.peek() == 2,
                "paused client refuses creation and preserves quota");
        runtime.particles().close();
        testPublicBinding(unsafe, client, engine);
        System.out.println("M4 production particle spawn: native lifetime/Wake lifetime, real owner quota/refill, denial, pause and null-provider contracts passed (CPU recording engine).");
    }

    private static void testPublicBinding(Unsafe unsafe, RecordingClient client, RecordingEngine engine) throws Exception {
        set(Minecraft.class, client, "pause", false);
        engine.next = (Particle) unsafe.allocateInstance(FixtureParticle.class);
        engine.created = engine.spawned = 0;
        FixtureLevel level = (FixtureLevel) unsafe.allocateInstance(FixtureLevel.class);
        client.level = level;
        ArmorStand entity = (ArmorStand) unsafe.allocateInstance(ArmorStand.class);
        set(Entity.class, entity, "level", level);
        set(Entity.class, entity, "position", new Vec3(10, 20, 30));
        Avatar owner = owner(unsafe, 16);
        Avatar.MolangContext context = new Avatar.MolangContext(owner);
        context.entity = entity;
        set(Avatar.class, owner, "molangContext", context);
        set(Avatar.class, owner, "controls", new AvatarControlRuntime());
        YsmModelRuntime runtime = runtime(owner);
        set(Avatar.class, owner, "ysmRuntime", runtime);
        owner.renderMode = EntityRenderMode.WORLD;
        MolangEngine bindings = MolangEngine.fromCustomBinding(owner.getAvatarBindings());
        var evaluator = ExpressionEvaluator.evaluator(context);
        try (var effects = runtime.functions().beginClientEffects("native-particle-fixture")) {
            require(Boolean.TRUE.equals(evaluator.eval(bindings.parse(
                    "ysm.particle('minecraft:flame',1,2,3,4,5,6,2,0,120);").getFirst())),
                    "public Molang binding accepts native particle registry parameters");
            require(client.pending.size() == 1 && engine.created == 0 && owner.particlesRemaining.peek() == 16,
                    "public binding captures a queued request before engine work/quota consumption");
            client.drain();
            require(engine.created == 1 && engine.spawned == 1 && engine.options == ParticleTypes.FLAME,
                    "native ParticleArgument registry lookup reaches production submission");
            require(engine.position.equals(new Vec3(11, 22, 33)) && engine.velocity.equals(new Vec3(8, 10, 12))
                            && engine.lifetimeAtSpawn == 120 && owner.particlesRemaining.peek() == 15,
                    "public single request preserves entity offset, scaled velocity, lifetime and owner quota");
            require(Boolean.TRUE.equals(evaluator.eval(bindings.parse(
                    "ysm.abs_particle('minecraft:flame',0,0,0,0,0,0,0,2,37);").getFirst())),
                    "public absolute batch binding accepts count/lifetime");
            require(client.pending.size() == 2, "batch queues exactly the requested count");
            client.drain();
            require(engine.spawned == 3 && engine.position.equals(new Vec3(10, 20, 30))
                            && engine.velocity.x == 0d && engine.velocity.y == 0d && engine.velocity.z == 0d
                            && engine.lifetimeAtSpawn == 37
                            && owner.particlesRemaining.peek() == 13,
                    "absolute batch uses entity position and per-particle native quota/lifetime: spawned="
                            + engine.spawned + " position=" + engine.position + " velocity=" + engine.velocity
                            + " lifetime=" + engine.lifetimeAtSpawn + " quota=" + owner.particlesRemaining.peek());
            evaluator.eval(bindings.parse("ysm.particle('minecraft:flame');").getFirst());
            client.level = null;
            client.drain();
            require(engine.spawned == 3 && owner.particlesRemaining.peek() == 13,
                    "captured request is cancelled after a world change");
            client.level = level;
            evaluator.eval(bindings.parse("ysm.particle('minecraft:flame');").getFirst());
            set(Avatar.class, owner, "ysmRuntime", null);
            client.drain();
            require(engine.spawned == 3, "captured request is cancelled after model replacement");
            set(Avatar.class, owner, "ysmRuntime", runtime);
            owner.renderMode = EntityRenderMode.FIGURA_GUI;
            require(evaluator.eval(bindings.parse("ysm.particle('minecraft:flame',v.particle_side_effect=1);").getFirst()) == null
                            && context.variables.getPath("particle_side_effect") == null && client.pending.isEmpty(),
                    "preview suppresses public binding before argument effects or queue submission");
            owner.renderMode = EntityRenderMode.WORLD;
            try {
                evaluator.eval(bindings.parse("ysm.particle('fixture_missing:private_particle');").getFirst());
                throw new AssertionError("Unknown native particle ID must fail");
            } catch (IllegalArgumentException failure) {
                require(failure.getMessage().equals("Encrypted YSM operation failed: runtime/particle")
                                && failure.getCause() == null,
                        "public registry failure preserves encrypted diagnostic privacy");
            }
            require(Boolean.TRUE.equals(evaluator.eval(bindings.parse("ysm.particle('minecraft:flame');").getFirst())),
                    "public binding recovers after registry failure");
            client.drain();
            require(engine.spawned == 4 && owner.particlesRemaining.peek() == 12 && engine.lifetimeAtSpawn == 20,
                    "recovery request has reference default lifetime and native quota");
            evaluator.eval(bindings.parse("ysm.particle('minecraft:flame');").getFirst());
            runtime.particles().close();
            client.drain();
            require(engine.spawned == 4 && owner.particlesRemaining.peek() == 12,
                    "unload cancels a publicly queued request before quota consumption");
        }
        runtime.particles().close();
        client.level = null;
    }

    private static Avatar owner(Unsafe unsafe, int quota) throws Exception {
        Avatar owner = (Avatar) unsafe.allocateInstance(Avatar.class);
        set(Avatar.class, owner, "owner", UUID.randomUUID());
        set(Avatar.class, owner, "particlesRemaining", new RefilledNumber(quota));
        set(Avatar.class, owner, "noPermissions", new HashSet<Permissions>());
        return owner;
    }

    private static YsmModelRuntime runtime(Avatar owner) throws Exception {
        Constructor<YsmModelRuntime> constructor = YsmModelRuntime.class.getDeclaredConstructor(Avatar.class,
                YsmGeometry.class, YsmGeometry.class, FiguraTexture.class, String.class, boolean.class,
                String.class, String.class, Map.class, float.class, float.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(owner, new YsmGeometry(), null, null,
                "fixture", false, "NEW", "particle-native-fixture", Map.of(), 1f, 1f, true);
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class FixtureParticle extends Particle {
        private FixtureParticle() {
            super(null, 0, 0, 0);
        }

        @Override
        public ParticleRenderType getGroup() {
            throw new UnsupportedOperationException("CPU fixture does not render");
        }
    }

    private static final class RecordingClient extends Minecraft {
        private ArrayDeque<Runnable> pending;

        private RecordingClient() {
            super(null);
        }

        @Override
        public void execute(Runnable action) {
            pending.add(action);
        }

        private void drain() {
            while (!pending.isEmpty()) {
                pending.removeFirst().run();
            }
        }
    }

    private static final class FixtureLevel extends ClientLevel {
        private FixtureLevel() {
            super(null, null, null, null, 0, 0, null, false, 0L, 0);
        }

        @Override
        public RegistryAccess registryAccess() {
            return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        }
    }

    private static final class RecordingEngine extends ParticleEngine implements ParticleEngineAccessor {
        private Particle next;
        private Particle submitted;
        private int created;
        private int spawned;
        private int lifetimeAtSpawn;
        private UUID ownerAtSpawn;
        private Vec3 position;
        private Vec3 velocity;
        private ParticleOptions options;

        private RecordingEngine() {
            super(null, null);
        }

        @Override
        public <T extends ParticleOptions> Particle figura$makeParticle(T options, double x, double y, double z,
                                                                       double vx, double vy, double vz) {
            created++;
            this.options = options;
            position = new Vec3(x, y, z);
            velocity = new Vec3(vx, vy, vz);
            return next;
        }

        @Override
        public void figura$spawnParticle(Particle particle, UUID owner) {
            submitted = particle;
            ownerAtSpawn = owner;
            lifetimeAtSpawn = particle.getLifetime();
            spawned++;
        }

        @Override
        public void figura$clearParticles(UUID owner) {
            throw new UnsupportedOperationException("Not used by this fixture");
        }

        @Override
        public SpriteSet figura$getParticleSprite(Identifier id) {
            throw new UnsupportedOperationException("Not used by this fixture");
        }

        @Override
        public void figura$removeParticle(Particle particle) {
            throw new UnsupportedOperationException("Not used by this fixture");
        }

        @Override
        public ParticleResources figura$getParticleResources() {
            throw new UnsupportedOperationException("Not used by this fixture");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
