package top.aihmc.omnimorph.model.ysm;

import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.permissions.Permissions;
import sun.misc.Unsafe;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Compares the native player action machine with the reference ControllerActionResolver table:
 * death, riptide, sleep, swim, crawl, ladder, elytra, water, hurt, airborne, crouch, sprint and
 * idle must select the reference state, with the reference priority order.
 */
public final class YsmNativeStateSmokeTest {
    private static final List<String> STATES = List.of(
            "death", "riptide", "sleep", "swim", "climb", "climbing", "ladder_up", "ladder_stillness",
            "ladder_down", "elytra_fly", "fly", "swim_stand", "attacked", "jump", "fall", "sneak",
            "sneaking", "run", "walk", "idle", "sit", "ride");
    private static final String CLIPS = """
            {"animations":{
              "death":{"loop":false,"animation_length":1},
              "riptide":{"loop":true,"animation_length":1},
              "sleep":{"loop":true,"animation_length":1},
              "swim":{"loop":true,"animation_length":1},
              "climb":{"loop":true,"animation_length":1},
              "climbing":{"loop":true,"animation_length":1},
              "ladder_up":{"loop":true,"animation_length":1},
              "ladder_stillness":{"loop":true,"animation_length":1},
              "ladder_down":{"loop":true,"animation_length":1},
              "elytra_fly":{"loop":true,"animation_length":1},
              "fly":{"loop":true,"animation_length":1},
              "swim_stand":{"loop":true,"animation_length":1},
              "attacked":{"loop":false,"animation_length":1},
              "jump":{"loop":true,"animation_length":1},
              "fall":{"loop":true,"animation_length":1},
              "sneak":{"loop":true,"animation_length":1},
              "sneaking":{"loop":true,"animation_length":1},
              "run":{"loop":true,"animation_length":1},
              "walk":{"loop":true,"animation_length":1},
              "idle":{"loop":true,"animation_length":1},
              "sit":{"loop":true,"animation_length":1},
              "ride":{"loop":true,"animation_length":1}
            }}
            """;

    private static Unsafe unsafe;

    public static void main(String[] arguments) throws Exception {
        Path directory = Files.createTempDirectory("figura-native-state-").toAbsolutePath().normalize();
        System.setProperty("figura.test.directory", directory.toString());
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        Fixture fixture = new Fixture();
        YsmAnimationPlayer player = fixture.runtime.animations();
        player.registerAnimations(YsmAnimationParser.parse(CLIPS,
                MolangEngine.fromCustomBinding(name -> null)));
        Method update = YsmAnimationPlayer.class.getDeclaredMethod("updateNativeStateMachine",
                LivingEntityRenderState.class, LivingEntity.class);
        update.setAccessible(true);
        LivingEntityRenderState renderState = new LivingEntityRenderState();
        NativeEntity entity = allocate(NativeEntity.class);
        NativeEntity vehicle = allocate(NativeEntity.class);
        vehicle.alive = true;

        verifyReferenceTable(player, update, renderState, entity, fixture.context);
        verifyReferencePriorities(player, update, renderState, entity, fixture.context, vehicle);
        System.out.println("PASS native action table: reference states, order and clip selection");
    }

    private static void verifyReferenceTable(YsmAnimationPlayer player, Method update,
                                             LivingEntityRenderState renderState, NativeEntity entity,
                                             Avatar.MolangContext context) throws ReflectiveOperationException {
        expect(player, update, renderState, entity, context, "idle", () -> {
        });
        expect(player, update, renderState, entity, context, "walk", () -> context.ground_speed = 0.1f);
        expect(player, update, renderState, entity, context, "run", () -> {
            entity.sprinting = true;
            context.ground_speed = 0.1f;
        });
        expect(player, update, renderState, entity, context, "sneak", () -> {
            entity.pose = Pose.CROUCHING;
            context.ground_speed = 0.1f;
        });
        expect(player, update, renderState, entity, context, "sneaking", () -> entity.pose = Pose.CROUCHING);
        expect(player, update, renderState, entity, context, "death", () -> entity.dead = true);
        expect(player, update, renderState, entity, context, "riptide", () -> entity.spinAttack = true);
        expect(player, update, renderState, entity, context, "sleep", () -> entity.pose = Pose.SLEEPING);
        expect(player, update, renderState, entity, context, "swim", () -> {
            entity.swimming = true;
            entity.pose = Pose.SWIMMING;
        });
        expect(player, update, renderState, entity, context, "climb", () -> {
            entity.pose = Pose.SWIMMING;
            context.ground_speed = 0.1f;
        });
        expect(player, update, renderState, entity, context, "climbing", () -> entity.pose = Pose.SWIMMING);
        expect(player, update, renderState, entity, context, "ladder_up", () -> {
            entity.climbable = true;
            context.vertical_speed = 0.5f;
        });
        expect(player, update, renderState, entity, context, "ladder_stillness", () -> entity.climbable = true);
        expect(player, update, renderState, entity, context, "ladder_down", () -> {
            entity.climbable = true;
            context.vertical_speed = -0.5f;
        });
        expect(player, update, renderState, entity, context, "elytra_fly", () -> {
            entity.pose = Pose.FALL_FLYING;
            entity.fallFlying = true;
        });
        expect(player, update, renderState, entity, context, "swim_stand", () -> {
            entity.water = true;
            entity.grounded = false;
        });
        expect(player, update, renderState, entity, context, "attacked", () -> {
            entity.grounded = false;
            entity.hurtTime = 1;
        });
        // "fall" is authored too: the reference resolves JUMP and must win over the older fallback.
        expect(player, update, renderState, entity, context, "jump", () -> entity.grounded = false);
    }

    private static void verifyReferencePriorities(YsmAnimationPlayer player, Method update,
                                                  LivingEntityRenderState renderState, NativeEntity entity,
                                                  Avatar.MolangContext context, NativeEntity vehicle)
            throws ReflectiveOperationException {
        expect(player, update, renderState, entity, context, "sit", () -> entity.vehicle = vehicle);
        expect(player, update, renderState, entity, context, "death", () -> {
            entity.dead = true;
            entity.climbable = true;
            context.vertical_speed = 0.5f;
        });
        expect(player, update, renderState, entity, context, "riptide", () -> {
            entity.spinAttack = true;
            entity.pose = Pose.SLEEPING;
        });
        expect(player, update, renderState, entity, context, "ladder_up", () -> {
            entity.climbable = true;
            entity.pose = Pose.FALL_FLYING;
            entity.fallFlying = true;
            context.vertical_speed = 0.5f;
        });
    }

    private static void expect(YsmAnimationPlayer player, Method update, LivingEntityRenderState renderState,
                               NativeEntity entity, Avatar.MolangContext context, String expected,
                               Runnable setup) throws ReflectiveOperationException {
        baseline(entity, context);
        setup.run();
        // updateFrame feeds the render state's limb amount into the movement context; mirror it.
        renderState.walkAnimationSpeed = Math.max(0f, context.ground_speed);
        update.invoke(player, renderState, entity);
        String actual = activeNative(player);
        require(expected.equals(actual), "native action must be " + expected + " but was " + actual);
    }

    private static void baseline(NativeEntity entity, Avatar.MolangContext context) {
        entity.pose = Pose.STANDING;
        entity.dead = false;
        entity.spinAttack = false;
        entity.swimming = false;
        entity.climbable = false;
        entity.vehicle = null;
        entity.grounded = true;
        entity.water = false;
        entity.sprinting = false;
        entity.fallFlying = false;
        entity.hurtTime = 0;
        context.ground_speed = 0f;
        context.vertical_speed = 0f;
    }

    private static String activeNative(YsmAnimationPlayer player) {
        for (String name : STATES) {
            var animation = player.getActiveAnimation(name);
            if (animation != null && animation.targetWeight > 0f) {
                return name;
            }
        }
        return "";
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(unsafe.allocateInstance(type));
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Minimal player entity exposing exactly the resolver's inputs. */
    private static final class NativeEntity extends LivingEntity {
        private Pose pose = Pose.STANDING;
        private boolean dead;
        private boolean spinAttack;
        private boolean swimming;
        private boolean climbable;
        private Entity vehicle;
        private boolean grounded = true;
        private boolean water;
        private boolean sprinting;
        private boolean fallFlying;
        private boolean alive;

        private NativeEntity() {
            super(EntityType.ARMOR_STAND, null);
        }

        @Override
        public Pose getPose() {
            return pose;
        }

        @Override
        public boolean isDeadOrDying() {
            return dead;
        }

        @Override
        public boolean isAutoSpinAttack() {
            return spinAttack;
        }

        @Override
        public boolean isSwimming() {
            return swimming;
        }

        @Override
        public boolean onClimbable() {
            return climbable;
        }

        @Override
        public Entity getVehicle() {
            return vehicle;
        }

        @Override
        public boolean onGround() {
            return grounded;
        }

        @Override
        public boolean isInWater() {
            return water;
        }

        @Override
        public boolean isSprinting() {
            return sprinting;
        }

        @Override
        public boolean isFallFlying() {
            return fallFlying;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public boolean isSleeping() {
            return pose == Pose.SLEEPING;
        }

        @Override
        public net.minecraft.world.entity.HumanoidArm getMainArm() {
            return net.minecraft.world.entity.HumanoidArm.RIGHT;
        }
    }

    private static final class Fixture {
        final Avatar owner;
        final Avatar.MolangContext context;
        final YsmModelRuntime runtime;

        Fixture() throws Exception {
            owner = allocate(Avatar.class);
            set(Avatar.class, owner, "owner", UUID.randomUUID());
            set(Avatar.class, owner, "noPermissions", new HashSet<Permissions>());
            context = new Avatar.MolangContext(owner);
            set(Avatar.class, owner, "molangContext", context);
            var constructor = YsmModelRuntime.class.getDeclaredConstructor(Avatar.class,
                    YsmGeometry.class, YsmGeometry.class, FiguraTexture.class, String.class, boolean.class,
                    String.class, String.class, Map.class, float.class, float.class, boolean.class);
            constructor.setAccessible(true);
            runtime = constructor.newInstance(owner, new YsmGeometry(), null, null,
                    "fixture", false, "NEW", "native-state-fixture", Map.of(), 1f, 1f, false);
            set(Avatar.class, owner, "ysmRuntime", runtime);
            context.entity = allocate(NativeEntity.class);
        }
    }
}
