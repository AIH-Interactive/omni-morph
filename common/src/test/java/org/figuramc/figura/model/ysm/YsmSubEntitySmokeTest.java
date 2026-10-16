package org.figuramc.figura.model.ysm;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.model.ysm.animation.YsmRenderFrameClock;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

/** Runs inside the existing native-object fixture, with no window, GPU or audio device. */
final class YsmSubEntitySmokeTest {
    private YsmSubEntitySmokeTest() {
    }

    static void run(Unsafe unsafe, YsmModelRuntime parent, byte[] fallbackTexture) throws Exception {
        ListTag templates = new ListTag();
        for (String domain : java.util.List.of("player", "projectile", "vehicle")) {
            CompoundTag function = new CompoundTag();
            function.putString("path", "functions/@" + domain + "_ctrl_main.molang");
            function.putByteArray("data", bytes("v." + domain + "_events=v." + domain + "_events+1;"));
            templates.add(function);
        }
        Field input = YsmModelRuntime.class.getDeclaredField("functionInput");
        input.setAccessible(true);
        ((ListTag) input.get(parent)).addAll(templates.copy());
        parent.functions().load(templates);
        parent.animations().registerFunctionEventControllers(parent.functions().controllerEventNames());
        YsmRenderFrameClock.beginFrame();
        parent.updateAnimations(new net.minecraft.client.renderer.entity.state.LivingEntityRenderState(), null);
        equal(1f, number(parent, "player_events"), "main-player controller callback executes on the main runtime");
        require(parent.owner().getMolangContext().variables.getPath("projectile_events") == null
                        && parent.owner().getMolangContext().variables.getPath("vehicle_events") == null,
                "main runtime does not dispatch physical sub-entity controller callbacks");
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", "projectile");
        tag.putString("id", "arrow");
        ListTag matches = new ListTag();
        matches.add(StringTag.valueOf("minecraft:arrow"));
        tag.put("match_ids", matches);
        tag.putByteArray("model", bytes("""
                {"minecraft:geometry":[{"bones":[{"name":"root"}]}]}
                """));
        tag.putByteArray("animation", bytes("""
                {"animations":{"move":{"loop":true,"animation_length":4,
                  "bones":{"root":{"position":["v.bias+q.anim_time*16",0,0]}},
                  "timeline":{"0":"v.hits=v.hits+1;"}}}}
                """));
        tag.putByteArray("controller", bytes("""
                {"animation_controllers":{"projectile.main":{"initial_state":"air","states":{
                  "air":{"transitions":[{"land":"q.is_on_ground"}]},
                  "land":{"on_entry":["v.land_count=v.land_count+1;"]}}}}}
                """));
        tag.putByteArray("normal_texture", fallbackTexture);
        tag.putByteArray("specular_texture", fallbackTexture);
        YsmSubEntity definition = YsmSubEntity.fromNbt(parent.owner(), tag, fallbackTexture);
        var material = (YsmMaterialTexture) definition.texture();
        var normal = material.normalTexture();
        var specular = material.specularTexture();
        var add = YsmModelRuntime.class.getDeclaredMethod("addSubEntity", YsmSubEntity.class);
        add.setAccessible(true);
        add.invoke(parent, definition);
        Level firstWorld = (ClientLevel) unsafe.allocateInstance(ClientLevel.class);
        Level secondWorld = (ClientLevel) unsafe.allocateInstance(ClientLevel.class);
        TestArrow controller = arrow(unsafe, firstWorld, 1, 0);
        controller.setUUID(parent.owner().owner);
        TestArrow first = arrow(unsafe, firstWorld, 71, 20);
        first.controller = controller;
        TestArrow second = arrow(unsafe, secondWorld, 71, 40);
        second.controller = controller;
        var manager = parent.subEntityInstances();
        YsmModelRuntime a = manager.acquire(first, "projectile", "minecraft:arrow");
        YsmModelRuntime b = manager.acquire(second, "projectile", "minecraft:arrow");
        require(a != b && manager.size() == 2, "same entity ID in different worlds owns independent instances");
        require(a == manager.acquire(first, "projectile", "minecraft:arrow"), "repeat passes reuse one physical entity instance");
        require(a.geometry() == b.geometry() && a.texture() == b.texture(), "definitions share immutable geometry and image resources");
        require(a.getPart("root") != b.getPart("root"), "instances own their mutable bone poses");
        parent.owner().getMolangContext().variables.setPath("bias", 77f);
        a.owner().getMolangContext().variables.setPath("bias", 1f);
        b.owner().getMolangContext().variables.setPath("bias", 9f);
        a.animations().play("move", null, 1f, 0f);
        b.animations().play("move", null, 1f, 0f);
        YsmRenderFrameClock.beginFrame();
        a.updateSubEntity(first, 0f);
        b.updateSubEntity(second, 0f);
        first.tickCount += 2;
        second.tickCount += 4;
        second.grounded = true;
        YsmRenderFrameClock.beginFrame();
        a.updateSubEntity(first, 0f);
        b.updateSubEntity(second, 0f);
        equal(0.15f, a.animations().getActiveAnimation("move").time, "first entity advances by its own age");
        equal(0.25f, b.animations().getActiveAnimation("move").time, "second entity advances by its own age");
        equal(3.4f, (float) a.getPart("root").animPosRaw().x, "first entity samples its bound variables and time");
        equal(13f, (float) b.getPart("root").animPosRaw().x, "second entity samples its bound variables and time");
        require("air".equals(a.animations().controllerRuntime().currentState("projectile.main"))
                        && "land".equals(b.animations().controllerRuntime().currentState("projectile.main")),
                "controllers read each physical entity query and transition independently");
        equal(1f, number(b, "land_count"), "controller entry executes once in its own context");
        equal(2f, number(a, "projectile_events"), "each projectile executes only its own domain callback once per frame");
        equal(2f, number(b, "projectile_events"), "same-resource projectile callback counters remain independent");
        require(a.owner().getMolangContext().variables.getPath("player_events") == null
                        && a.owner().getMolangContext().variables.getPath("vehicle_events") == null,
                "shared named-function inputs cannot automatically dispatch another model domain's callbacks");
        equal(77f, number(parent, "bias"), "children do not overwrite main-player variables");
        require(parent.owner().getMolangContext().variables.getPath("hits") == null,
                "child timeline events never enter the main-player context");
        float life = b.owner().getMolangContext().life_time;
        b.updateSubEntity(second, 0.75f);
        equal(0.25f, b.animations().getActiveAnimation("move").time, "repeat pass does not advance the child clock");
        equal(life, b.owner().getMolangContext().life_time, "repeat pass does not repopulate time queries");
        TestArrow replacement = arrow(unsafe, firstWorld, 71, 0);
        replacement.controller = controller;
        YsmModelRuntime next = manager.acquire(replacement, "projectile", "minecraft:arrow");
        require(a.isClosed() && next != a && !b.isClosed(), "entity-ID reuse retires only the displaced physical entity");
        Field subject = org.figuramc.figura.model.ysm.animation.YsmAnimationPlayer.class.getDeclaredField("currentEntity");
        subject.setAccessible(true);
        require(a.owner().getMolangContext().entity == null && subject.get(a.animations()) == null,
                "retained retired runtime drops both query and player references to its physical entity");
        require(!image(definition.texture()).isClosed() && !image(normal).isClosed() && !image(specular).isClosed(),
                "retiring an instance leaves the shared definition base and PBR channel images alive");
        field(Entity.class, replacement, "removalReason", Entity.RemovalReason.DISCARDED);
        manager.tick(firstWorld);
        require(next.isClosed() && b.isClosed() && manager.size() == 0,
                "tick removes destroyed entities and previous-world instances without a render pass");
        testProjectileDefaults(unsafe, parent, tag, fallbackTexture, firstWorld, controller);
        testVehicle(unsafe, parent, tag, fallbackTexture, firstWorld, controller);
        TestArrow survivor = arrow(unsafe, firstWorld, 72, 0);
        survivor.controller = controller;
        YsmModelRuntime last = manager.acquire(survivor, "projectile", "minecraft:arrow");
        parent.close();
        require(last.isClosed() && manager.size() == 0 && image(definition.texture()).isClosed()
                        && image(normal).isClosed() && image(specular).isClosed(),
                "model unload closes instances before releasing shared definition images");
        require(manager.acquire(survivor, "projectile", "minecraft:arrow") == null,
                "retained instance managers cannot revive an unloaded model");
        System.out.println("M6 sub-entity instance foundation: native entity identity, independent clocks/queries/controllers/variables/events, ID reuse, native Boat/tag/passenger lifetime and unload PASS; rendering and native audio lifecycle remain separate.");
    }

    private static void testProjectileDefaults(Unsafe unsafe, YsmModelRuntime parent, CompoundTag original,
                                               byte[] texture, Level world, Entity owner) throws Exception {
        CompoundTag tag = original.copy();
        tag.putString("id", "minecraft:spectral_arrow");
        tag.put("match_ids", new ListTag());
        tag.putByteArray("controller", new byte[0]);
        tag.putByteArray("animation", bytes("""
                {"animations":{
                  "air":{"loop":true,"bones":{"root":{"position":[1,0,0]}},"timeline":{"0":"v.air=v.air+1;"}},
                  "ground":{"loop":true,"bones":{"root":{"position":[2,0,0]}}},
                  "fire":{"loop":true,"bones":{"root":{"position":[3,0,0]}}},
                  "water":{"loop":true,"bones":{"root":{"position":[4,0,0]}}}}}
                """));
        YsmSubEntity definition = YsmSubEntity.fromNbt(parent.owner(), tag, texture);
        var add = YsmModelRuntime.class.getDeclaredMethod("addSubEntity", YsmSubEntity.class);
        add.setAccessible(true);
        add.invoke(parent, definition);
        TestArrow entity = arrow(unsafe, world, 75, 0);
        entity.type = EntityType.SPECTRAL_ARROW;
        entity.controller = owner;
        YsmRenderFrameClock.beginFrame();
        YsmModelRuntime instance = parent.prepareSubEntity(entity, "projectile", "minecraft:spectral_arrow", 0f);
        require(instance != null, "native projectile default selection prepares its matched instance");
        equal(1f, (float) instance.getPart("root").animPosRaw().x, "air is the reference default, despite its unused fly declaration name");
        entity.embedded = true;
        entity.tickCount += 2;
        YsmRenderFrameClock.beginFrame();
        instance.updateSubEntity(entity, 0f);
        equal(2f, (float) instance.getPart("root").animPosRaw().x, "embedded projectile selects ground");
        entity.fire = true;
        entity.tickCount += 2;
        YsmRenderFrameClock.beginFrame();
        instance.updateSubEntity(entity, 0f);
        equal(3f, (float) instance.getPart("root").animPosRaw().x, "fire precedes embedded state");
        entity.water = true;
        entity.tickCount += 2;
        YsmRenderFrameClock.beginFrame();
        instance.updateSubEntity(entity, 0f);
        equal(4f, (float) instance.getPart("root").animPosRaw().x, "water precedes fire and embedded state");
        entity.water = entity.fire = entity.embedded = false;
        entity.tickCount += 2;
        YsmRenderFrameClock.beginFrame();
        instance.updateSubEntity(entity, 0f);
        equal(2f, number(instance, "air"), "leaving and re-entering a default slot starts a new owned playback and event");
        field(Entity.class, entity, "removalReason", Entity.RemovalReason.DISCARDED);
        parent.subEntityInstances().tick(world);
        require(instance.isClosed(), "default-animation instance releases on physical removal");
        testTrident(unsafe, parent, tag, texture, world, owner);
    }

    private static void testTrident(Unsafe unsafe, YsmModelRuntime parent, CompoundTag projectileTag,
                                    byte[] texture, Level world, Entity owner) throws Exception {
        CompoundTag tag = projectileTag.copy();
        tag.putString("id", "minecraft:trident");
        var definition = YsmSubEntity.fromNbt(parent.owner(), tag, texture);
        var add = YsmModelRuntime.class.getDeclaredMethod("addSubEntity", YsmSubEntity.class);
        add.setAccessible(true);
        add.invoke(parent, definition);
        TestTrident trident = (TestTrident) unsafe.allocateInstance(TestTrident.class);
        trident.controller = owner;
        trident.setId(76);
        trident.setUUID(new java.util.UUID(0, 76));
        field(Entity.class, trident, "type", EntityType.TRIDENT);
        field(Entity.class, trident, "level", world);
        field(Entity.class, trident, "position", new Vec3(1, 2, 3));
        field(Entity.class, trident, "deltaMovement", Vec3.ZERO);
        field(Entity.class, trident, "passengers", com.google.common.collect.ImmutableList.of());
        YsmRenderFrameClock.beginFrame();
        var instance = parent.prepareSubEntity(trident, "projectile", "minecraft:trident", 0f);
        require(instance != null && instance.owner().getMolangContext().entity == trident,
                "native ThrownTrident receives its own matched runtime and physical query subject");
        equal(1f, (float) instance.getPart("root").animPosRaw().x, "thrown trident samples its declared air clip");
        trident.embedded = true;
        trident.tickCount += 2;
        YsmRenderFrameClock.beginFrame();
        instance.updateSubEntity(trident, 0f);
        equal(2f, (float) instance.getPart("root").animPosRaw().x, "embedded trident samples its declared ground clip");
        field(Entity.class, trident, "removalReason", Entity.RemovalReason.DISCARDED);
        parent.subEntityInstances().tick(world);
        require(instance.isClosed(), "trident removal closes its physical runtime");
    }

    private static void testVehicle(Unsafe unsafe, YsmModelRuntime parent, CompoundTag projectileTag,
                                    byte[] texture, Level world, Entity passenger) throws Exception {
        CompoundTag tag = projectileTag.copy();
        tag.putString("kind", "vehicle");
        tag.putString("id", "#figura:ysm_fixture_vehicles");
        tag.put("match_ids", new ListTag());
        tag.putByteArray("animation", bytes("""
                {"animations":{
                  "idle":{"loop":true,"bones":{"root":{"position":[0,1,0]}}},
                  "forward":{"loop":true,"bones":{"root":{"position":[0,2,0]}}},
                  "has_ride":{"loop":true,"bones":{"root":{"position":[0,0,3]}}},
                  "not_ride":{"loop":true,"bones":{"root":{"position":[0,0,4]}}}}}
                """));
        tag.putByteArray("controller", bytes(new String(tag.getByteArray("controller").orElseThrow(),
                StandardCharsets.UTF_8).replace("projectile.main", "vehicle.main")));
        YsmSubEntity definition = YsmSubEntity.fromNbt(parent.owner(), tag, texture);
        var add = YsmModelRuntime.class.getDeclaredMethod("addSubEntity", YsmSubEntity.class);
        add.setAccessible(true);
        add.invoke(parent, definition);
        TestBoat boat = (TestBoat) unsafe.allocateInstance(TestBoat.class);
        field(Entity.class, boat, "type", EntityType.OAK_BOAT);
        field(Entity.class, boat, "level", world);
        field(Entity.class, boat, "position", Vec3.ZERO);
        field(Entity.class, boat, "deltaMovement", Vec3.ZERO);
        field(Entity.class, boat, "passengers", com.google.common.collect.ImmutableList.of(passenger));
        boat.setId(81);
        boat.setUUID(new java.util.UUID(0, 81));
        var holder = EntityType.OAK_BOAT.builtInRegistryHolder();
        var originalTags = holder.tags().toList();
        var bind = net.minecraft.core.Holder.Reference.class.getDeclaredMethod("bindTags", java.util.Collection.class);
        bind.setAccessible(true);
        var vehicleTag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("figura", "ysm_fixture_vehicles"));
        var tags = new java.util.ArrayList<>(originalTags);
        tags.add(vehicleTag);
        try {
            require(parent.getSubEntityForEntity("vehicle", boat) == null,
                    "a declared tag does not match a type absent from the live registry tag");
            bind.invoke(holder, tags);
            require(parent.getSubEntityForEntity("vehicle", boat) == definition,
                    "vehicle matching uses the actual native entity-type tag holder");
            require(parent.getSubEntityForEntity("projectile", boat) == null,
                    "a vehicle tag cannot cross into projectile resources");
            YsmRenderFrameClock.beginFrame();
            YsmModelRuntime instance = parent.prepareSubEntity(boat, "vehicle", "minecraft:oak_boat", 0f);
            require(instance != null && instance.owner().getMolangContext().entity == boat,
                    "vehicle preparation binds the physical Boat to its independent context");
            TestBoat collision = (TestBoat) unsafe.allocateInstance(TestBoat.class);
            collision.setId(boat.getId());
            collision.setUUID(new java.util.UUID(0, 82));
            field(Entity.class, collision, "level", unsafe.allocateInstance(ClientLevel.class));
            require(boat.equals(collision) && YsmSubEntityInstances.vehicleOwner(collision) == null,
                    "native equal entity IDs across worlds cannot borrow another vehicle assignment");
            collision.setUUID(boat.getUUID());
            require(YsmSubEntityInstances.vehicleOwner(collision) == null
                            && YsmSubEntityInstances.vehicleOwner(boat) == parent,
                    "even a reused UUID requires the original physical entity identity");
            equal(1f, instance.owner().getMolangContext().has_rider, "vehicle query reads the native passenger list");
            equal(1f, number(instance, "vehicle_events"), "vehicle controller dispatches its own domain callback");
            require(instance.owner().getMolangContext().variables.getPath("player_events") == null
                            && instance.owner().getMolangContext().variables.getPath("projectile_events") == null,
                    "vehicle controller never dispatches player or projectile callbacks");
            equal(1f, (float) instance.getPart("root").animPosRaw().y, "stationary vehicle selects idle");
            equal(3f, (float) instance.getPart("root").animPosRaw().z, "occupied vehicle selects has_ride");
            field(Entity.class, boat, "deltaMovement", new Vec3(0.1, 0, 0));
            boat.tickCount += 2;
            YsmRenderFrameClock.beginFrame();
            parent.prepareSubEntity(boat, "vehicle", "minecraft:oak_boat", 0f);
            equal(2f, (float) instance.getPart("root").animPosRaw().y, "vehicle movement above reference threshold selects forward");
            bind.invoke(holder, originalTags);
            require(parent.subEntityInstances().acquire(boat, "vehicle", "minecraft:oak_boat") == null
                            && instance.isClosed(), "tag removal retires a now-unmatched resource instance");
            bind.invoke(holder, tags);
            YsmModelRuntime replaced = parent.prepareSubEntity(boat, "vehicle", "minecraft:oak_boat", 0f);
            field(Entity.class, boat, "passengers", com.google.common.collect.ImmutableList.of());
            parent.subEntityInstances().tick(world);
            require(!replaced.isClosed() && parent.subEntityInstances().size() == 1,
                    "unoccupied vehicle retains its locally observed model assignment");
            boat.tickCount += 2;
            YsmRenderFrameClock.beginFrame();
            require(parent.prepareSubEntity(boat, "vehicle", "minecraft:oak_boat", 0f) == replaced,
                    "unoccupied vehicle resolves its prior model owner");
            equal(0f, replaced.owner().getMolangContext().has_rider, "unoccupied query reads the empty native passenger list");
            equal(4f, (float) replaced.getPart("root").animPosRaw().z, "unoccupied model consumes the reference not_ride animation");
            field(Entity.class, boat, "passengers", com.google.common.collect.ImmutableList.of(passenger));
            YsmModelRuntime changed = parent.prepareSubEntity(boat, "vehicle", "minecraft:oak_boat", 0f);
            TestArrow otherOwner = arrow(unsafe, world, 82, 0);
            otherOwner.setUUID(new java.util.UUID(0, 1234));
            field(Entity.class, boat, "passengers", com.google.common.collect.ImmutableList.of(otherOwner));
            parent.subEntityInstances().tick(world);
            require(changed.isClosed() && parent.subEntityInstances().size() == 0
                            && YsmSubEntityInstances.vehicleOwner(boat) == null,
                    "changing the controlling passenger releases the previous owner's instance and assignment");
        } finally {
            bind.invoke(holder, originalTags);
        }
    }

    private static TestArrow arrow(Unsafe unsafe, Level world, int id, int age) throws Exception {
        TestArrow entity = (TestArrow) unsafe.allocateInstance(TestArrow.class);
        field(Entity.class, entity, "level", world);
        field(Entity.class, entity, "position", Vec3.ZERO);
        field(Entity.class, entity, "deltaMovement", Vec3.ZERO);
        entity.setId(id);
        entity.tickCount = age;
        return entity;
    }

    private static void field(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static NativeImage image(FiguraTexture texture) throws Exception {
        Field field = FiguraTexture.class.getDeclaredField("nativeImageTexture");
        field.setAccessible(true);
        return (NativeImage) field.get(texture);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static float number(YsmModelRuntime runtime, String name) {
        return ((Number) runtime.owner().getMolangContext().variables.getPath(name)).floatValue();
    }

    private static void equal(float expected, float actual, String message) {
        require(Math.abs(expected - actual) < 0.0001f, message + ": expected=" + expected + " actual=" + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestTrident extends net.minecraft.world.entity.projectile.arrow.ThrownTrident
            implements org.figuramc.figura.mixin.AbstractArrowAccessor {
        private Entity controller;
        private boolean embedded;

        private TestTrident() {
            super(EntityType.TRIDENT, null);
        }

        @Override
        public Entity getOwner() {
            return controller;
        }

        @Override
        public boolean figura$isInGround() {
            return embedded;
        }

        @Override
        public boolean isInWaterOrRain() {
            return false;
        }

        @Override
        public boolean isOnFire() {
            return false;
        }

        @Override
        public boolean isSprinting() {
            return false;
        }

        @Override
        public boolean isSwimming() {
            return false;
        }
    }

    private static final class TestBoat extends net.minecraft.world.entity.vehicle.boat.Boat {
        private TestBoat() {
            super(EntityType.OAK_BOAT, null, () -> net.minecraft.world.item.Items.OAK_BOAT);
        }

        @Override
        public boolean isInWaterOrRain() {
            return false;
        }

        @Override
        public boolean isOnFire() {
            return false;
        }

        @Override
        public boolean isSprinting() {
            return false;
        }

        @Override
        public boolean isSwimming() {
            return false;
        }
    }

    private static final class TestArrow extends Arrow implements org.figuramc.figura.mixin.AbstractArrowAccessor {
        private boolean grounded;
        private Entity controller;
        private EntityType<?> type;
        private boolean embedded;
        private boolean fire;
        private boolean water;

        private TestArrow() {
            super(EntityType.ARROW, null);
        }

        @Override
        public Entity getOwner() {
            return controller;
        }

        @Override
        public EntityType<?> getType() {
            return type == null ? EntityType.ARROW : type;
        }

        @Override
        public boolean figura$isInGround() {
            return embedded;
        }

        @Override
        public boolean isInWater() {
            return water;
        }

        @Override
        public boolean onGround() {
            return grounded;
        }

        @Override
        public boolean isInWaterOrRain() {
            return false;
        }

        @Override
        public boolean isOnFire() {
            return fire;
        }

        @Override
        public boolean isSprinting() {
            return false;
        }

        @Override
        public boolean isSwimming() {
            return false;
        }

        @Override
        public boolean isVehicle() {
            return false;
        }
    }
}
