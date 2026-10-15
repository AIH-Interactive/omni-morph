package org.figuramc.figura.model.ysm;

import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ElytraAnimationState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.molang.MolangEngine;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.molang.runtime.Function;
import org.figuramc.figura.molang.runtime.Variable;
import org.figuramc.figura.molang.runtime.binding.ObjectBinding;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class YsmVanillaStateSmokeTest {
    public static void main(String[] args) throws Exception {
        Path environment = Files.createTempDirectory("figura-vanilla-state-").toAbsolutePath().normalize();
        String previous = System.getProperty("figura.test.directory");
        System.setProperty("figura.test.directory", environment.toString());
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            runTests();
        } finally {
            if (previous == null) {
                System.clearProperty("figura.test.directory");
            } else {
                System.setProperty("figura.test.directory", previous);
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

    private static void runTests() throws Exception {
        Avatar.MolangContext context = new Avatar.MolangContext(null);
        var bindings = Avatar.createMolangBindings(context);
        MolangEngine engine = MolangEngine.fromCustomBinding(bindings);
        ObjectBinding stateBindings = (ObjectBinding) bindings.getProperty("ysm");
        for (String name : new String[]{"has_elytra", "elytra_rot_x", "elytra_rot_y", "elytra_rot_z", "is_close_eyes", "in_shield_block_cooldown"}) {
            require(eval(engine, context, "ysm." + name + ";") == null, "living entity gate: " + name);
            Object binding = stateBindings.getProperty(name);
            require(binding instanceof Variable && !(binding instanceof Function), "reference scalar variable signature: " + name);
        }
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        // Typed CPU entity shell; inventory/sleep/UUID are explicit fixture inputs, not a loaded world.
        FixtureEntity entity = (FixtureEntity) ((Unsafe) unsafeField.get(null)).allocateInstance(FixtureEntity.class);
        entity.chest = ItemStack.EMPTY;
        entity.id = new UUID(0, 0);
        ElytraAnimationState state = new ElytraAnimationState(entity);
        Field stateField = LivingEntity.class.getDeclaredField("elytraAnimationState");
        stateField.setAccessible(true);
        stateField.set(entity, state);
        context.entity = entity;
        entity.pose = Pose.STANDING;
        require(eval(engine, context, "ctrl.bcombat_attack_animation;") == null,
                "Better Combat default requires an AbstractClientPlayer rather than any LivingEntity");
        require("".equals(eval(engine, context, "ctrl.parcool_state;"))
                        && Boolean.FALSE.equals(eval(engine, context, "ctrl.tac_hold_gun;")),
                "other optional Fabric defaults accept LivingEntity and preserve string/boolean types");
        require(eval(engine, context, "ysm.in_shield_block_cooldown;") == null,
                "a LivingEntity that is not a Player cannot expose the shield player capability");
        // 26.1 item components normally arrive from loaded data packs; this CPU fixture binds explicit inputs.
        Items.ELYTRA.builtInRegistryHolder().bindComponents(
                DataComponentMap.builder().set(DataComponents.GLIDER, Unit.INSTANCE).build());
        Items.DIAMOND_CHESTPLATE.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        require(Boolean.FALSE.equals(eval(engine, context, "ysm.has_elytra;")), "empty chest has no glider");
        entity.chest = new ItemStack(Items.ELYTRA);
        require(Boolean.TRUE.equals(eval(engine, context, "ysm.has_elytra;")), "elytra fixture carries native GLIDER component");
        entity.chest.setCount(0);
        require(Boolean.FALSE.equals(eval(engine, context, "ysm.has_elytra;")), "zero-count glider stack is not equipped");
        entity.chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        require(Boolean.FALSE.equals(eval(engine, context, "ysm.has_elytra;")), "ordinary armor has no glider");
        entity.chest.set(DataComponents.GLIDER, Unit.INSTANCE);
        require(Boolean.TRUE.equals(eval(engine, context, "ysm.has_elytra;")), "glider is detected by component rather than item ID");

        rotations(state, "X", 10f, 30f);
        rotations(state, "Y", -40f, 20f);
        rotations(state, "Z", -15f, -45f);
        context.setYsmRenderTime(8.25f, 8);
        number(15d, eval(engine, context, "ysm.elytra_rot_x;"), "native X interpolation in degrees");
        number(-25d, eval(engine, context, "ysm.elytra_rot_y;"), "native Y interpolation in degrees");
        number(-22.5d, eval(engine, context, "ysm.elytra_rot_z;"), "native Z interpolation in degrees");
        context.setYsmRenderTime(9f, 8);
        number(30d, eval(engine, context, "ysm.elytra_rot_x;"), "native current rotation endpoint");
        context.setYsmRenderTime(Float.NaN, 8);
        number(10d, eval(engine, context, "ysm.elytra_rot_x;"), "missing render time uses previous rotation endpoint");
        for (float tick : new float[]{85f, 85.25f, 89.75f, 90f}) {
            context.setYsmRenderTime(tick, (int) tick);
            boolean expected = tick > 85f && tick < 90f;
            require(Boolean.valueOf(expected).equals(eval(engine, context, "ysm.is_close_eyes;")), "blink interval boundary: " + tick);
        }
        entity.id = new UUID(0, -3);
        context.setYsmRenderTime(83f, 83);
        require(Boolean.TRUE.equals(eval(engine, context, "ysm.is_close_eyes;")), "absolute UUID phase offset");
        entity.sleeping = true;
        context.setYsmRenderTime(0f, 0);
        require(Boolean.TRUE.equals(eval(engine, context, "ysm.is_close_eyes;")), "sleep forces closed eyes outside blink interval");
        Avatar.MolangContext other = new Avatar.MolangContext(null);
        require(eval(engine, other, "ysm.is_close_eyes;") == null, "another context does not inherit entity/blink state");
        testRelativeBlocks(engine, context, entity);
        testWorldQueries(engine, context, entity);
        testLivingQueries(engine, context, entity);
        testEntityMotionQueries(engine, context, entity);
        testEntityStateQueries(engine, context, entity);
        testYsmEntityQueries(engine, context, entity);
        testInputQueries(engine, context, entity);
        testMovementQueries(engine, context, entity);
        testHandQueryContracts(engine, context, entity);
        testEquipmentFunctionContracts(engine, context, entity);
        testWorldTagContracts(engine, context, entity);
        testEntityIdentityQueries(engine, context, entity);
        testBoatQueries(engine, context, entity);
        testCameraGeometryQueries(engine, context, entity);
        testEnchantments(engine, context, entity);
        testTraversal(engine, context, entity);
        testCoreActions(engine, context, entity);
        testEquipmentQueries(engine, context, entity);
        testControllerEquipmentContracts(engine, context, entity);
        testHandTypeMatching(engine, context, entity);
        testHandActions(engine, context, entity);
        testRideQuery(engine, context, entity);
        testProjectileFiringItem(engine, context, entity);
        context.reset();
        require(context.ysm_render_ticks == 0f && context.ysm_partial_tick == 0f && context.entity == null,
                "context reset releases entity and render timing");
        System.out.println("M4 vanilla state bindings: native glider components, native elytra interpolation/degrees, blink boundaries/types and context isolation passed.");
    }

    private static void testProjectileFiringItem(MolangEngine engine, Avatar.MolangContext context,
                                                 FixtureEntity entity) throws Exception {
        ObjectBinding bindings = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("ysm");
        require(bindings.getProperty("shoot_item_id") instanceof Variable
                        && !(bindings.getProperty("shoot_item_id") instanceof Function), "firing item is a scalar variable");
        context.entity = null;
        require(eval(engine, context, "ysm.shoot_item_id;") == null, "firing item requires an arrow subject");
        context.entity = entity;
        require(eval(engine, context, "ysm.shoot_item_id;") == null, "a living shooter's hands are not the arrow firing item");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        var arrow = (net.minecraft.world.entity.projectile.arrow.Arrow)
                unsafe.allocateInstance(net.minecraft.world.entity.projectile.arrow.Arrow.class);
        Field weapon = net.minecraft.world.entity.projectile.arrow.AbstractArrow.class.getDeclaredField("firedFromWeapon");
        weapon.setAccessible(true);
        try {
            context.entity = arrow;
            require("".equals(eval(engine, context, "ysm.shoot_item_id;")), "unassigned native firing stack returns empty String");
            weapon.set(arrow, ItemStack.EMPTY);
            require("".equals(eval(engine, context, "ysm.shoot_item_id;")), "empty native firing stack returns empty String");
            Items.BOW.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
            Items.CROSSBOW.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
            weapon.set(arrow, new ItemStack(Items.BOW));
            require("minecraft:bow".equals(eval(engine, context, "ysm.shoot_item_id;")), "native arrow retains its firing bow ID");
            weapon.set(arrow, new ItemStack(Items.CROSSBOW));
            require("minecraft:crossbow".equals(eval(engine, context, "ysm.shoot_item_id;")), "native firing stack changes are read directly");
            var spectral = (Entity) unsafe.allocateInstance(net.minecraft.world.entity.projectile.arrow.SpectralArrow.class);
            weapon.set(spectral, new ItemStack(Items.BOW));
            Avatar.MolangContext other = new Avatar.MolangContext(null);
            other.entity = spectral;
            require("minecraft:bow".equals(eval(engine, other, "ysm.shoot_item_id;"))
                            && "minecraft:crossbow".equals(eval(engine, context, "ysm.shoot_item_id;")),
                    "abstract-arrow subclasses and separate contexts retain independent firing items");
            other.entity = (Entity) unsafe.allocateInstance(net.minecraft.world.entity.item.ItemEntity.class);
            require(eval(engine, other, "ysm.shoot_item_id;") == null, "other projectile-like entities do not satisfy the arrow gate");
        } finally {
            context.entity = entity;
        }
    }

    private static void testRelativeBlocks(MolangEngine engine, Avatar.MolangContext context, FixtureEntity entity) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        FixtureLevel world = (FixtureLevel) ((Unsafe) unsafeField.get(null)).allocateInstance(FixtureLevel.class);
        world.block = Blocks.STONE.defaultBlockState();
        Field level = Entity.class.getDeclaredField("level");
        level.setAccessible(true);
        level.set(entity, world);
        Field position = Entity.class.getDeclaredField("position");
        position.setAccessible(true);
        position.set(entity, new Vec3(-0.25d, 64.75d, 2.5d));
        require("minecraft:stone".equals(eval(engine, context, "ysm.relative_block_name(0,0,0);")),
                "formal entity/level/block registry path returns a raw block ID");
        require(world.position.equals(new BlockPos(-1, 64, 2)), "reference round(entity+offset-0.5) on fractional/negative coordinates");
        require(Boolean.TRUE.equals(eval(engine, context,
                        "ysm.relative_block_name_any(0,0,0,'dirt','stone',v.late_match=1);")),
                "any query supports default namespace and short circuits at a match");
        require(context.variables.getPath("late_match") == null, "arguments after a match are not evaluated");
        require(Boolean.FALSE.equals(eval(engine, context, "ysm.relative_block_name_any(0,0,0,42,'bad id','dirt');")),
                "invalid identifiers/non-string matches do not create false positives");
        world.block = Blocks.AIR.defaultBlockState();
        require(Boolean.TRUE.equals(eval(engine, context, "ysm.relative_block_name(0,0,0)=='minecraft:air';")),
                "block return string participates in literal comparisons");
        int reads = world.reads;
        require(eval(engine, context, "ysm.relative_block_name_any(v.x=6,v.y=2,v.z=3,v.late_range=1);") == null,
                "relative lookup rejects an offset outside the reference five-block bound");
        require(world.reads == reads && context.variables.getPath("late_range") == null,
                "out-of-range query neither reads the world nor evaluates match names");
        require(context.variables.getPath("y") instanceof Number y && y.floatValue() == 2f
                        && context.variables.getPath("z") instanceof Number z && z.floatValue() == 3f,
                "all three offsets are captured before range checking, in reference order");
        require("minecraft:air".equals(eval(engine, context, "ysm.relative_block_name(-5,5,5);")), "range boundary is inclusive");
        require(world.position.equals(new BlockPos(-6, 69, 7)), "relative offsets use world axes, not body rotation");
        context.entity = null;
        require(eval(engine, context, "ysm.relative_block_name(v.missing_entity=1,0,0);") == null
                        && context.variables.getPath("missing_entity") == null,
                "entity gate precedes offset evaluation");
        Function name = (Function) ((ObjectBinding) Avatar.createMolangBindings(context).getProperty("ysm"))
                .getProperty("relative_block_name");
        Function any = (Function) ((ObjectBinding) Avatar.createMolangBindings(context).getProperty("ysm"))
                .getProperty("relative_block_name_any");
        require(!name.validateArgumentSize(2) && name.validateArgumentSize(3) && !name.validateArgumentSize(4)
                        && !any.validateArgumentSize(3) && any.validateArgumentSize(4) && any.validateArgumentSize(8),
                "reference exact-three/minimum-four signatures");
    }

    private static void testEnchantments(MolangEngine engine, Avatar.MolangContext context, FixtureEntity entity) throws Exception {
        FixtureLevel world = (FixtureLevel) entity.level();
        MappedRegistry<Enchantment> registry = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
        var definition = Enchantment.definition(HolderSet.direct(Items.DIAMOND_CHESTPLATE.builtInRegistryHolder()),
                1, 5, Enchantment.constantCost(1), Enchantment.constantCost(1), 1, EquipmentSlotGroup.CHEST);
        var first = registry.register(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse("fixture:first")),
                new Enchantment(Component.literal("first"), definition, HolderSet.direct(), DataComponentMap.EMPTY), RegistrationInfo.BUILT_IN);
        var second = registry.register(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse("fixture:second")),
                new Enchantment(Component.literal("second"), definition, HolderSet.direct(), DataComponentMap.EMPTY), RegistrationInfo.BUILT_IN);
        registry.freeze();
        world.registries = new RegistryAccess.ImmutableRegistryAccess(List.of(registry));
        context.entity = entity;
        entity.chest = ItemStack.EMPTY;
        require(Integer.valueOf(0).equals(eval(engine, context, "ysm.equipped_enchantment_level('chest',v.empty_names=1);")),
                "empty equipment returns Integer zero before reading enchantment arguments");
        require(context.variables.getPath("empty_names") == null, "empty slot has no enchantment argument effects");
        entity.chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(first, 3);
        enchantments.set(second, 2);
        entity.chest.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        require(EnchantmentHelper.getItemEnchantmentLevel(Holder.direct(first.value()), entity.chest) == 0,
                "direct holder loses the registry key used by native item enchantments");
        require(Integer.valueOf(5).equals(eval(engine, context,
                        "ysm.equipped_enchantment_level('CHEST','fixture:first','fixture:second');")),
                "native registry holders return summed levels and case-insensitive canonical slot");
        require(Integer.valueOf(6).equals(eval(engine, context,
                        "ysm.equipped_enchantment_level('chest','fixture:first','fixture:first');")),
                "duplicate enchantment arguments are summed in reference order");
        require(Integer.valueOf(3).equals(eval(engine, context,
                        "ysm.equipped_enchantment_level('chest',42,'bad id','fixture:missing','fixture:first');")),
                "invalid and missing IDs do not hide a later valid enchantment");
        require(eval(engine, context, "ysm.equipped_enchantment_level('helmet',v.invalid_slot=1);") == null
                        && context.variables.getPath("invalid_slot") == null,
                "reference slot names do not accept unrelated armor aliases");
        enchantments.set(first, 1);
        entity.chest.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        require(Integer.valueOf(1).equals(eval(engine, context, "ysm.equipped_enchantment_level('chest','fixture:first');")),
                "updated item component is read live");
        context.entity = null;
        require(eval(engine, context, "ysm.equipped_enchantment_level(v.no_entity=1,'fixture:first');") == null
                        && context.variables.getPath("no_entity") == null,
                "living entity gate precedes all argument effects");
        Function function = (Function) ((ObjectBinding) Avatar.createMolangBindings(context).getProperty("ysm"))
                .getProperty("equipped_enchantment_level");
        require(!function.validateArgumentSize(1) && function.validateArgumentSize(2) && function.validateArgumentSize(5),
                "equipped enchantment requires a slot and one or more IDs");
    }

    private static void testTraversal(MolangEngine engine, Avatar.MolangContext context, FixtureEntity entity) throws Exception {
        context.entity = null;
        for (String name : new String[]{"climb", "climbing", "ladder_up", "ladder_stillness", "ladder_down"}) {
            require(eval(engine, context, "ctrl." + name + ";") == null, "traversal LivingEntity gate: " + name);
            Object binding = ((ObjectBinding) Avatar.createMolangBindings(context).getProperty("ctrl")).getProperty(name);
            require(binding instanceof Variable && !(binding instanceof Function), "traversal reference Variable signature: " + name);
        }
        context.entity = entity;
        entity.pose = Pose.SWIMMING;
        entity.climbable = true;
        context.ground_speed = 0.051f;
        context.vertical_speed = 1f;
        traversal(engine, context, "climb");
        context.ground_speed = 0.05f;
        traversal(engine, context, "climbing");
        entity.swimming = true;
        traversal(engine, context, "none");
        entity.swimming = false;
        entity.dead = true;
        traversal(engine, context, "none");
        entity.dead = false;
        entity.spinAttack = true;
        traversal(engine, context, "none");
        entity.spinAttack = false;
        entity.pose = Pose.SLEEPING;
        traversal(engine, context, "none");
        entity.pose = Pose.STANDING;
        float[] speeds = {-0.011f, -0.01f, 0f, 0.01f, 0.011f};
        String[] expected = {"ladder_down", "ladder_stillness", "ladder_stillness", "ladder_stillness", "ladder_up"};
        for (int index = 0; index < speeds.length; index++) {
            context.vertical_speed = speeds[index];
            traversal(engine, context, expected[index]);
        }
        entity.climbable = false;
        traversal(engine, context, "none");
        entity.climbable = true;
        entity.vehicle = entity;
        entity.alive = true;
        traversal(engine, context, "none");
        entity.alive = false;
        traversal(engine, context, "ladder_up");
        entity.vehicle = null;
        Avatar.MolangContext other = new Avatar.MolangContext(null);
        other.entity = entity;
        other.vertical_speed = -0.02f;
        traversal(engine, other, "ladder_down");
        traversal(engine, context, "ladder_up");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Avatar owner = (Avatar) ((Unsafe) unsafeField.get(null)).allocateInstance(Avatar.class);
        Avatar.MolangContext preview = new Avatar.MolangContext(owner);
        preview.entity = entity;
        preview.vertical_speed = 0.02f;
        owner.renderMode = EntityRenderMode.FIGURA_GUI;
        traversal(engine, preview, "none");
        owner.renderMode = EntityRenderMode.WORLD;
        traversal(engine, preview, "ladder_up");
    }

    private static void traversal(MolangEngine engine, Avatar.MolangContext context, String expected) throws Exception {
        for (String name : new String[]{"climb", "climbing", "ladder_up", "ladder_stillness", "ladder_down"}) {
            require(Boolean.valueOf(name.equals(expected)).equals(eval(engine, context, "ctrl." + name + ";")),
                    "exclusive traversal state " + expected + ": " + name);
        }
    }

    private static void testRideQuery(MolangEngine engine, Avatar.MolangContext context, FixtureEntity entity) throws Exception {
        ObjectBinding registry = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("ctrl");
        Function function = (Function) registry.getProperty("ride");
        require(!function.validateArgumentSize(1) && function.validateArgumentSize(2)
                        && function.validateArgumentSize(3) && !function.validateArgumentSize(4), "ride reference arity");
        context.entity = null;
        require(eval(engine, context, "ctrl.ride(v.ride_early='vehicle','$armor_stand');") == null
                        && context.variables.getPath("ride_early") == null,
                "ride requires LivingEntity before evaluating arguments");
        context.entity = entity;
        entity.vehicle = null;
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.ride('vehicle','$armor_stand');")), "missing vehicle returns Integer zero");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        FixtureEntity vehicle = (FixtureEntity) ((Unsafe) unsafeField.get(null)).allocateInstance(FixtureEntity.class);
        FixtureEntity passenger = (FixtureEntity) ((Unsafe) unsafeField.get(null)).allocateInstance(FixtureEntity.class);
        vehicle.alive = passenger.alive = true;
        vehicle.entityType = EntityType.ARMOR_STAND;
        passenger.entityType = EntityType.PIG;
        entity.vehicle = vehicle;
        entity.passenger = passenger;
        require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.ride('vehicle','$minecraft:armor_stand',v.ride_unused=99);"))
                        && context.variables.getPath("ride_unused") == null,
                "vehicle match returns Integer one and ignores optional third argument");
        require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.ride('vehicle','$armor_stand');")), "ride ID prefix supports native default namespace");
        require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.ride('passenger','$pig');"))
                        && Integer.valueOf(0).equals(eval(engine, context, "ctrl.ride('vehicle','$pig');")),
                "vehicle and passenger selectors use separate entity targets");
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.ride('invalid',v.ride_seen='$armor_stand');"))
                        && "$armor_stand".equals(org.figuramc.figura.molang.runtime.binding.ValueConversions.asString(
                                context.variables.getPath("ride_seen"))),
                "invalid selector is not a vehicle alias and reference evaluates ID before selector validation");
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.ride('VEHICLE','$armor_stand');")), "ride selector is reference case-sensitive");
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.ride('vehicle','$invalid space');")), "malformed native ID is a mismatch");
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.ride('vehicle','armor_stand');")), "unprefixed match is not a ride matcher");
        var holder = EntityType.ARMOR_STAND.builtInRegistryHolder();
        var previous = holder.tags().toList();
        var bindTags = holder.getClass().getDeclaredMethod("bindTags", java.util.Collection.class);
        bindTags.setAccessible(true);
        try {
            var tag = net.minecraft.tags.TagKey.create(Registries.ENTITY_TYPE, Identifier.parse("fixture:ride_target"));
            bindTags.invoke(holder, List.of(tag));
            require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.ride('vehicle','#fixture:ride_target');"))
                            && Integer.valueOf(0).equals(eval(engine, context, "ctrl.ride('passenger','#fixture:ride_target');")),
                    "ride tag matching uses the selected native entity-type holder");
        } finally {
            bindTags.invoke(holder, previous);
        }
        vehicle.alive = false;
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.ride('vehicle','$armor_stand');")), "dead vehicle does not match");
        entity.passenger = null;
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.ride('passenger','$pig');")), "removed passenger is visible without rebuilding bindings");
        entity.vehicle = null;
    }

    private static void testHandActions(MolangEngine engine, Avatar.MolangContext context, FixtureEntity entity) throws Exception {
        entity.sleeping = false;
        entity.swinging = true;
        entity.swingingArm = net.minecraft.world.InteractionHand.MAIN_HAND;
        entity.equipment.put(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE));
        entity.equipment.put(EquipmentSlot.OFFHAND, new ItemStack(Items.DIRT));
        context.is_swinging = 0f;
        require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.swing('mainhand','$minecraft:stone');"))
                        && Integer.valueOf(0).equals(eval(engine, context, "ctrl.swing('offhand','$minecraft:dirt');")),
                "native swingingArm controls the exact hand independently of stale context flags");
        entity.swingingArm = net.minecraft.world.InteractionHand.OFF_HAND;
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.swing('mainhand','$minecraft:stone');"))
                        && Integer.valueOf(1).equals(eval(engine, context, "ctrl.swing('offhand','$minecraft:dirt');")),
                "native swing hand changes without rebuilding bindings");
        entity.swinging = false;
        context.is_swinging = 1f;
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.swing('offhand','$minecraft:dirt');")),
                "stale aggregate context flag cannot invent an offhand swing");
        entity.attackProgress = 0.5f;
        require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.swing('mainhand','$minecraft:stone');"))
                        && Integer.valueOf(0).equals(eval(engine, context, "ctrl.swing('offhand','$minecraft:dirt');")),
                "non-local native attack progress is a main-hand fallback only");
        entity.attackProgress = 0f;
        entity.using = true;
        entity.usedHand = net.minecraft.world.InteractionHand.OFF_HAND;
        context.is_using_item = 0f;
        require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.use('offhand','$minecraft:dirt');"))
                        && Integer.valueOf(0).equals(eval(engine, context, "ctrl.use('mainhand','$minecraft:stone');")),
                "native use state and used hand control the exact queried hand");
        entity.using = false;
        context.is_using_item = 1f;
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.use('offhand','$minecraft:dirt');")),
                "stale aggregate context cannot invent a use action");
        entity.sleeping = true;
        entity.using = entity.swinging = true;
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.use('offhand','$minecraft:dirt');"))
                        && Integer.valueOf(0).equals(eval(engine, context, "ctrl.swing('offhand','$minecraft:dirt');")),
                "native action predicates reject sleeping entities without a local pulse");
        entity.sleeping = entity.using = entity.swinging = false;
        var pulses = new YsmHandActionState();
        pulses.prepare(entity, true);
        pulses.mouseEvent(0, 1);
        require(pulses.swinging(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true)
                        && !pulses.swinging(entity, net.minecraft.world.InteractionHand.OFF_HAND, true)
                        && !pulses.swinging(entity, net.minecraft.world.InteractionHand.MAIN_HAND, false),
                "local mouse pulse is hand-specific and cannot be borrowed by an observer");
        pulses.tick(true);
        pulses.tick(true);
        for (int tick = 0; tick < 7; tick++) {
            pulses.tick(false);
        }
        require(pulses.swinging(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true),
                "raw/key dedup preserves the original pulse through tick nine");
        pulses.tick(false);
        require(!pulses.swinging(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true),
                "held attack key does not duplicate or extend the ten-tick raw pulse");
        pulses.tick(true);
        require(pulses.swinging(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true),
                "new keyboard attack edge creates a local pulse");
        pulses.prepare(entity, false);
        require(!pulses.swinging(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true), "focus loss clears pending pulses");
        pulses.prepare(entity, true);
        entity.equipment.put(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        pulses.mouseEvent(1, 1);
        require(pulses.using(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true)
                        && !pulses.using(entity, net.minecraft.world.InteractionHand.MAIN_HAND, false),
                "continuous-use item has a local pending pulse only");
        pulses.tick(false);
        require(pulses.using(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true), "pending use survives one client tick");
        pulses.tick(false);
        require(!pulses.using(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true), "pending use expires after two ticks");
        pulses.mouseEvent(1, 1);
        entity.equipment.put(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE));
        require(!pulses.using(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true), "item replacement invalidates pending use");
        entity.equipment.put(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        pulses.mouseEvent(1, 1);
        pulses.mouseEvent(1, 0);
        require(!pulses.using(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true), "mouse release clears pending use immediately");
        pulses.mouseEvent(1, 1);
        entity.using = true;
        entity.usedHand = net.minecraft.world.InteractionHand.MAIN_HAND;
        pulses.tick(false);
        require(pulses.using(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true), "native use takes over the pending input pulse");
        entity.using = false;
        require(!pulses.using(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true), "native release does not resurrect consumed pending input");
        entity.equipment.put(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        entity.equipment.put(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
        pulses.mouseEvent(1, 1);
        require(pulses.using(entity, net.minecraft.world.InteractionHand.OFF_HAND, true)
                        && !pulses.using(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true),
                "right click selects the nonempty offhand when the main hand is empty");
        pulses.prepare(entity, false);
        pulses.prepare(entity, true);
        entity.equipment.put(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        entity.using = true;
        entity.usedHand = net.minecraft.world.InteractionHand.OFF_HAND;
        entity.swinging = true;
        entity.swingingArm = net.minecraft.world.InteractionHand.MAIN_HAND;
        pulses.mouseEvent(0, 1);
        require(!pulses.swinging(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true),
                "active native local offhand shield suppresses both raw attack capture and native main-hand swing");
        entity.using = entity.swinging = false;
        pulses.mouseEvent(0, 1);
        entity.sleeping = true;
        require(pulses.swinging(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true)
                        && !pulses.swinging(entity, net.minecraft.world.InteractionHand.MAIN_HAND, false),
                "reference ctrl local-model pulse may override a rejected native predicate, never for an observer");
        pulses.prepare(entity, false);
        require(!pulses.swinging(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true),
                "readiness loss clears the model-pulse override");
        entity.sleeping = false;
        entity.attackProgress = 0.5f;
        require(!pulses.swinging(entity, net.minecraft.world.InteractionHand.MAIN_HAND, true),
                "focus loss does not reclassify the local player as an observer with attack-progress fallback");
        entity.attackProgress = 0f;
        entity.equipment.clear();
        context.is_swinging = context.is_using_item = 0f;
    }

    private static void testHandTypeMatching(MolangEngine engine, Avatar.MolangContext context, FixtureEntity entity) throws Exception {
        entity.equipment.put(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE));
        entity.equipment.put(EquipmentSlot.OFFHAND, new ItemStack(Items.DIRT));
        require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.hold('mainhand','$minecraft:stone',v.hold_unused=99);"))
                        && context.variables.getPath("hold_unused") == null,
                "hold ID match ignores the optional third argument");
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.hold('offhand','$minecraft:stone');"))
                        && Integer.valueOf(1).equals(eval(engine, context, "ctrl.hold('offhand','$minecraft:dirt');")),
                "hold ID matching reads the requested hand independently");
        Map<net.minecraft.world.item.Item, List<String>> nativeTypes = Map.of(
                Items.TRIDENT, List.of("spear", "trident"), Items.MACE, List.of("mace"),
                Items.SHIELD, List.of("shield"), Items.CROSSBOW, List.of("crossbow"),
                Items.BOW, List.of("bow"), Items.FISHING_ROD, List.of("fishing_rod"),
                Items.SPLASH_POTION, List.of("throwable_potion"), Items.LINGERING_POTION, List.of("throwable_potion"));
        for (var entry : nativeTypes.entrySet()) {
            entry.getKey().builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
            entity.equipment.put(EquipmentSlot.MAINHAND, new ItemStack(entry.getKey()));
            for (String type : entry.getValue()) {
                require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.hold('mainhand',':" + type + "');")),
                        "native reference item type: " + type);
                require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.hold('mainhand',':" + type.toUpperCase(java.util.Locale.ROOT) + "');")),
                        "reference type names are case-sensitive: " + type);
            }
        }
        entity.equipment.put(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.hold('mainhand',':none');")),
                "weapon branch has priority over generic use-animation fallback");
        entity.equipment.put(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE));
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.hold('mainhand',':stone');")),
                "item ID text is not an item type classifier");
        require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.hold('mainhand',':none');")),
                "non-weapon item can match its actual native use-animation enum");
        Map<String, net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> tools = Map.of(
                "sword", net.minecraft.tags.ItemTags.SWORDS, "axe", net.minecraft.tags.ItemTags.AXES,
                "pickaxe", net.minecraft.tags.ItemTags.PICKAXES, "shovel", net.minecraft.tags.ItemTags.SHOVELS,
                "hoe", net.minecraft.tags.ItemTags.HOES);
        var holder = Items.STONE.builtInRegistryHolder();
        var previous = holder.tags().toList();
        var bindTags = holder.getClass().getDeclaredMethod("bindTags", java.util.Collection.class);
        bindTags.setAccessible(true);
        try {
            for (var entry : tools.entrySet()) {
                bindTags.invoke(holder, List.of(entry.getValue()));
                require(Integer.valueOf(1).equals(eval(engine, context,
                                "ctrl.hold('mainhand','#" + entry.getValue().location() + "');")),
                        "hold tag prefix uses actual registry membership: " + entry.getKey());
                require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.hold('mainhand',':" + entry.getKey() + "');")),
                        "native tool tag determines classification: " + entry.getKey());
                if (entry.getKey().equals("shovel")) {
                    require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.hold('mainhand',':spade');")), "legacy shovel alias is preserved");
                }
            }
            Map<String, String> compat = Map.ofEntries(
                    Map.entry("tridents", "trident"), Map.entry("pike", "lance"), Map.entry("mace", "mace"),
                    Map.entry("swords", "sword"), Map.entry("axes", "axe"), Map.entry("pickaxes", "pickaxe"),
                    Map.entry("shovels", "shovel"), Map.entry("hoes", "hoe"), Map.entry("shields", "shield"),
                    Map.entry("bows", "bow"), Map.entry("crossbows", "crossbow"),
                    Map.entry("fishing_rods", "fishing_rod"), Map.entry("throwable_potion", "throwable_potion"));
            for (var entry : compat.entrySet()) {
                var tag = net.minecraft.tags.TagKey.create(Registries.ITEM,
                        Identifier.fromNamespaceAndPath("sparkle_morpher", entry.getKey()));
                bindTags.invoke(holder, List.of(tag));
                require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.hold('mainhand',':" + entry.getValue() + "');")),
                        "current reference compatibility tag: " + entry.getKey());
            }
            var trident = net.minecraft.tags.TagKey.create(Registries.ITEM, Identifier.parse("sparkle_morpher:tridents"));
            bindTags.invoke(holder, List.of(trident, net.minecraft.tags.ItemTags.SWORDS));
            require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.hold('mainhand',':spear');"))
                            && Integer.valueOf(0).equals(eval(engine, context, "ctrl.hold('mainhand',':sword');")),
                    "weapon classification takes priority over tool tags");
        } finally {
            bindTags.invoke(holder, previous);
        }
        var weapon = new net.minecraft.world.item.component.KineticWeapon(0, 0,
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                1f, 1f, java.util.Optional.empty(), java.util.Optional.empty());
        Items.WOODEN_SPEAR.builtInRegistryHolder().bindComponents(
                DataComponentMap.builder().set(DataComponents.KINETIC_WEAPON, weapon).build());
        entity.equipment.put(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SPEAR));
        require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.hold('mainhand',':lance');"))
                        && Integer.valueOf(0).equals(eval(engine, context, "ctrl.hold('mainhand',':spear');")),
                "native minecraft spear with SPEAR use animation uses reference lance class");
        entity.equipment.clear();
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.hold('mainhand',':none');")), "empty stack has no type classification");
    }

    private static void testControllerEquipmentContracts(MolangEngine engine, Avatar.MolangContext context, FixtureEntity entity) throws Exception {
        ObjectBinding registry = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("ctrl");
        context.entity = null;
        for (String name : List.of("hold", "use", "swing", "armor")) {
            Function function = (Function) registry.getProperty(name);
            require(!function.validateArgumentSize(0) && !function.validateArgumentSize(1)
                            && function.validateArgumentSize(2) && function.validateArgumentSize(3)
                            && !function.validateArgumentSize(4), "reference equipment arity: " + name);
            require(eval(engine, context, "ctrl." + name + "('head',v.controller_item_side_effect=99);") == null,
                    "missing LivingEntity rejects arguments before effects: " + name);
        }
        require(context.variables.getPath("controller_item_side_effect") == null, "missing entity does not evaluate item arguments");
        context.entity = entity;
        entity.equipment.clear();
        require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.armor('head','empty');")), "empty armor returns reference Integer one");
        require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.armor('head','$minecraft:air');")), "empty stack retains native air ID matching");
        require(eval(engine, context, "ctrl.armor('mainhand',v.controller_item_side_effect=99);") == null,
                "non-armor slot returns null before evaluating armor item argument");
        require(eval(engine, context, "ctrl.armor('invalid','empty');") == null, "invalid armor slot returns null");
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.hold('head','empty');")), "hand matcher rejects armor slots with Integer zero");
        ItemStack stack = new ItemStack(Items.STONE);
        entity.equipment.put(EquipmentSlot.HEAD, stack);
        require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.armor('head','$minecraft:stone',v.controller_item_side_effect=99);")),
                "armor ID match returns Integer one and ignores reference third argument");
        require(context.variables.getPath("controller_item_side_effect") == null, "invalid slot and unused third parameter remain unevaluated");
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.armor('head','$stone');")), "ctrl ID prefix uses exact native ID, not convenience short-ID matching");
        require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.armor('head',':stone');")), "armor has no hand-item type-prefix semantics");
        var tag = net.minecraft.tags.TagKey.create(Registries.ITEM, Identifier.parse("fixture:controller_armor"));
        var holder = Items.STONE.builtInRegistryHolder();
        var previousTags = holder.tags().toList();
        var bindTags = holder.getClass().getDeclaredMethod("bindTags", java.util.Collection.class);
        bindTags.setAccessible(true);
        try {
            bindTags.invoke(holder, List.of(tag));
            require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.armor('head','#fixture:controller_armor');")), "armor uses native item tag membership");
            require(Integer.valueOf(0).equals(eval(engine, context, "ctrl.armor('head','#fixture:missing');")), "missing native tag is a mismatch");
        } finally {
            bindTags.invoke(holder, previousTags);
        }
        stack.setCount(0);
        require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.armor('head','empty');")), "zero-count armor stack matches empty");
        entity.equipment.clear();
        require(Integer.valueOf(1).equals(eval(engine, context, "ctrl.hold('offhand','empty');")), "hold raw match uses Integer type");
    }

    private static void testEquipmentQueries(MolangEngine engine, Avatar.MolangContext context, FixtureEntity entity) throws Exception {
        // These 26.1 item components normally arrive from data packs; this CPU fixture binds its explicit items.
        Items.STONE.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.DIRT.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Map<String, EquipmentSlot> slots = Map.of(
                "has_mainhand", EquipmentSlot.MAINHAND, "has_offhand", EquipmentSlot.OFFHAND,
                "has_helmet", EquipmentSlot.HEAD, "has_chest_plate", EquipmentSlot.CHEST,
                "has_leggings", EquipmentSlot.LEGS, "has_boots", EquipmentSlot.FEET);
        context.entity = null;
        ObjectBinding registry = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("ysm");
        for (String name : slots.keySet()) {
            require(registry.getProperty(name) instanceof Variable && !(registry.getProperty(name) instanceof Function),
                    "equipment presence is a scalar Variable: " + name);
            require(eval(engine, context, "ysm." + name + ";") == null, "equipment presence requires LivingEntity: " + name);
        }
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        context.entity = (net.minecraft.world.entity.item.ItemEntity)
                ((Unsafe) unsafeField.get(null)).allocateInstance(net.minecraft.world.entity.item.ItemEntity.class);
        for (String name : slots.keySet()) {
            require(eval(engine, context, "ysm." + name + ";") == null, "non-living Entity cannot report equipment: " + name);
        }
        context.entity = entity;
        entity.chest = ItemStack.EMPTY;
        entity.equipment = new java.util.EnumMap<>(EquipmentSlot.class);
        for (var selected : slots.entrySet()) {
            entity.equipment.clear();
            ItemStack stack = new ItemStack(Items.STONE);
            entity.equipment.put(selected.getValue(), stack);
            for (String name : slots.keySet()) {
                require(Boolean.valueOf(name.equals(selected.getKey())).equals(eval(engine, context, "ysm." + name + ";")),
                        "typed presence query reads the exact equipment slot: " + name);
            }
            stack.setCount(0);
            require(Boolean.FALSE.equals(eval(engine, context, "ysm." + selected.getKey() + ";")),
                    "zero-count stack clears presence: " + selected.getKey());
        }
        entity.equipment.clear();
        entity.equipment.put(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE));
        entity.equipment.put(EquipmentSlot.OFFHAND, new ItemStack(Items.DIRT));
        entity.equipment.put(EquipmentSlot.HEAD, new ItemStack(Items.STONE));
        require("minecraft:stone".equals(eval(engine, context, "ysm.main_hand();")), "project main-hand extension returns raw ID String");
        require("minecraft:dirt".equals(eval(engine, context, "ysm.off_hand();")), "project off-hand extension reads separate slot");
        require(Float.valueOf(1f).equals(eval(engine, context, "ysm.main_hand('dirt','stone');")),
                "project item comparison extension returns raw Float and accepts any matching argument");
        require(Float.valueOf(0f).equals(eval(engine, context, "ysm.off_hand('stone');")), "item mismatch returns Float zero");
        require("minecraft:stone".equals(eval(engine, context, "ysm.equipped_item('HeAd');")), "equipment extension accepts case-insensitive slots");
        require(Float.valueOf(1f).equals(eval(engine, context, "ysm.equipped_item('helmet','stone');")), "equipment extension accepts slot alias plus item comparison");
        require("".equals(eval(engine, context, "ysm.equipped_item('invalid');")), "invalid project slot yields empty ID");
        Avatar.MolangContext other = new Avatar.MolangContext(null);
        require(eval(engine, other, "ysm.has_mainhand;") == null && "".equals(eval(engine, other, "ysm.main_hand();")),
                "another context cannot inherit equipped items");
        entity.equipment.clear();
        require(Boolean.FALSE.equals(eval(engine, context, "ysm.has_mainhand;"))
                        && "".equals(eval(engine, context, "ysm.main_hand();")),
                "clearing the slot is visible without rebuilding bindings");
    }

    private static void testCoreActions(MolangEngine engine, Avatar.MolangContext context, FixtureEntity entity) throws Exception {
        context.entity = entity;
        entity.vehicle = null;
        entity.climbable = false;
        entity.swimming = false;
        entity.pose = Pose.STANDING;
        entity.grounded = true;
        context.ground_speed = 0f;
        action(engine, context, "idle");
        context.ground_speed = 0.051f;
        action(engine, context, "walk");
        entity.sprinting = true;
        action(engine, context, "run");
        entity.pose = Pose.CROUCHING;
        action(engine, context, "sneak");
        context.ground_speed = 0.05f;
        action(engine, context, "sneaking");
        entity.pose = Pose.SWIMMING;
        action(engine, context, "climbing");
        context.ground_speed = 0.051f;
        action(engine, context, "climb");
        entity.swimming = true;
        action(engine, context, "swim");
        entity.pose = Pose.SLEEPING;
        action(engine, context, "sleep");
        entity.spinAttack = true;
        action(engine, context, "riptide");
        entity.dead = true;
        action(engine, context, "death");
        entity.vehicle = entity;
        entity.alive = true;
        action(engine, context, "riding");
        entity.vehicle = null;
        entity.dead = entity.spinAttack = entity.swimming = entity.sprinting = false;
        entity.pose = Pose.STANDING;
        entity.grounded = false;
        entity.hurtTime = 2;
        entity.water = true;
        action(engine, context, "swim_stand");
        entity.water = false;
        action(engine, context, "attacked");
        entity.hurtTime = 0;
        context.vertical_speed = 0.4f;
        action(engine, context, "jump");
        context.vertical_speed = -0.4f;
        action(engine, context, "jump");
        require(Boolean.TRUE.equals(eval(engine, context, "ctrl.fall;")), "project fall extension is a descending jump subset");
        entity.pose = Pose.FALL_FLYING;
        entity.elytraFlying = true;
        action(engine, context, "elytra_fly");
        entity.climbable = true;
        action(engine, context, "ladder_down");
        entity.climbable = entity.elytraFlying = false;
        entity.pose = Pose.STANDING;
        entity.grounded = true;
        context.ground_speed = Float.NaN;
        action(engine, context, "idle");
    }

    private static void action(MolangEngine engine, Avatar.MolangContext context, String expected) throws Exception {
        for (String name : new String[]{"death", "riptide", "sleep", "swim", "climb", "climbing",
                "ladder_up", "ladder_stillness", "ladder_down", "elytra_fly", "fly", "swim_stand",
                "attacked", "jump", "sneak", "sneaking", "run", "walk", "idle", "riding"}) {
            require(Boolean.valueOf(name.equals(expected)).equals(eval(engine, context, "ctrl." + name + ";")),
                    "exclusive core action " + expected + ": " + name);
        }
    }

    private static Object eval(MolangEngine engine, Avatar.MolangContext context, String source) throws Exception {
        return ExpressionEvaluator.evaluator(context).eval(engine.parse(source).getFirst());
    }

    private static void rotations(ElytraAnimationState state, String axis, float oldDegrees, float degrees) throws Exception {
        for (String suffix : new String[]{"Old", ""}) {
            Field field = ElytraAnimationState.class.getDeclaredField("rot" + axis + suffix);
            field.setAccessible(true);
            field.setFloat(state, (float) Math.toRadians(suffix.isEmpty() ? degrees : oldDegrees));
        }
    }

    private static void number(double expected, Object actual, String message) {
        require(actual instanceof Double value && Math.abs(expected - value) < 0.00001d, message + ": " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void testCameraGeometryQueries(MolangEngine engine, Avatar.MolangContext context,
                                                   FixtureEntity entity) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        var client = (net.minecraft.client.Minecraft) unsafe.allocateInstance(net.minecraft.client.Minecraft.class);
        var renderer = (net.minecraft.client.renderer.GameRenderer) unsafe.allocateInstance(net.minecraft.client.renderer.GameRenderer.class);
        var camera = new net.minecraft.client.Camera();
        var cameraPosition = net.minecraft.client.Camera.class.getDeclaredMethod("setPosition", double.class, double.class, double.class);
        var cameraRotation = net.minecraft.client.Camera.class.getDeclaredMethod("setRotation", float.class, float.class);
        Field mainCamera = net.minecraft.client.renderer.GameRenderer.class.getDeclaredField("mainCamera");
        Field gameRenderer = net.minecraft.client.Minecraft.class.getDeclaredField("gameRenderer");
        Field instance = net.minecraft.client.Minecraft.class.getDeclaredField("instance");
        Field position = Entity.class.getDeclaredField("position");
        cameraPosition.setAccessible(true);
        cameraRotation.setAccessible(true);
        mainCamera.setAccessible(true);
        gameRenderer.setAccessible(true);
        instance.setAccessible(true);
        position.setAccessible(true);
        Object previousClient = instance.get(null);
        Object previousPosition = position.get(entity);
        Field fps = net.minecraft.client.Minecraft.class.getDeclaredField("fps");
        fps.setAccessible(true);
        int previousFps = fps.getInt(null);
        try {
            mainCamera.set(renderer, camera);
            gameRenderer.set(client, renderer);
            instance.set(null, client);
            fps.setInt(null, 144);
            require(Integer.valueOf(144).equals(eval(engine, context, "ysm.fps;")), "FPS preserves the native Integer type");
            fps.setInt(null, 27);
            require(Integer.valueOf(27).equals(eval(engine, context, "ysm.fps;")), "FPS changes immediately without a subject gate");
            context.entity = entity;
            position.set(entity, new Vec3(1d, 2d, 3d));
            cameraPosition.invoke(camera, 4d, 6d, 15d);
            cameraRotation.invoke(camera, 135f, -20f);
            float previousYaw = entity.getYRot();
            float previousOldYaw = entity.yRotO;
            entity.yRotO = 350f;
            entity.setYRot(10f);
            movementNumber(400f, eval(engine, context, "q.yaw_speed;"), "nonlocal yaw uses twenty times native wrapped tick rotation");
            entity.yRotO = 10f;
            entity.setYRot(350f);
            movementNumber(-400f, eval(engine, context, "q.yaw_speed;"), "nonlocal yaw preserves negative wrap direction");
            entity.setYRot(previousYaw);
            entity.yRotO = previousOldYaw;
            context.distance_from_camera = -99f;
            movementNumber(13f, eval(engine, context, "q.distance_from_camera;"), "native camera distance ignores stale context fields");
            movementNumber(-20f, eval(engine, context, "q.rotation_to_camera(0);"), "camera pitch raw Float");
            movementNumber(135f, eval(engine, context, "q.rotation_to_camera(1.9);"), "camera yaw uses reference integer truncation");
            cameraPosition.invoke(camera, 1d, 2d, 3d);
            movementNumber(0f, eval(engine, context, "q.distance_from_camera;"), "camera movement changes distance immediately");
            position.set(entity, new Vec3(4d, 6d, 15d));
            movementNumber(13f, eval(engine, context, "q.distance_from_camera;"), "subject movement changes distance immediately");
            ObjectBinding queries = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("query");
            Function rotation = (Function) queries.getProperty("rotation_to_camera");
            require(queries.getProperty("distance_from_camera") instanceof Variable, "camera distance is a scalar");
            require(!rotation.validateArgumentSize(0) && rotation.validateArgumentSize(1)
                            && !rotation.validateArgumentSize(2), "camera rotation requires exactly one argument");
            for (String invalid : List.of("q.rotation_to_camera();", "q.rotation_to_camera(0,1);")) {
                try {
                    engine.parse(invalid);
                    throw new AssertionError("invalid camera query arity accepted");
                } catch (org.figuramc.figura.molang.parser.ParseException expected) {
                    // Production parser rejects an invalid reference signature.
                }
            }
            require(eval(engine, context, "q.rotation_to_camera(-1);") == null
                            && eval(engine, context, "q.rotation_to_camera(2);") == null, "invalid camera axis returns null");
            Avatar.MolangContext other = new Avatar.MolangContext(null);
            require(eval(engine, other, "q.distance_from_camera;") == null, "camera distance requires its own subject");
            require(eval(engine, other, "q.yaw_speed;") == null, "yaw rate requires its own subject");
            movementNumber(135f, eval(engine, other, "q.rotation_to_camera(1);"), "camera rotation is not an Entity-only query");
            var item = (Entity) unsafe.allocateInstance(net.minecraft.world.entity.item.ItemEntity.class);
            position.set(item, new Vec3(-2d, -2d, 3d));
            other.entity = item;
            movementNumber(5f, eval(engine, other, "q.distance_from_camera;"), "camera distance supports nonliving subject and per-context coordinates");
            gameRenderer.set(client, null);
            require(eval(engine, context, "q.distance_from_camera;") == null
                            && eval(engine, context, "q.rotation_to_camera(0);") == null, "uninitialized renderer has no camera query value");
            instance.set(null, null);
            require(eval(engine, context, "ysm.fps;") == null, "FPS has no value without a client");
            require(eval(engine, context, "q.distance_from_camera;") == null
                            && eval(engine, context, "q.rotation_to_camera(1);") == null, "missing Minecraft is a supported null boundary");
        } finally {
            position.set(entity, previousPosition);
            context.entity = entity;
            instance.set(null, previousClient);
            fps.setInt(null, previousFps);
        }
    }

    private static void testBoatQueries(MolangEngine engine, Avatar.MolangContext context,
                                         FixtureEntity entity) throws Exception {
        List<String> names = List.of("boat_left_paddle", "boat_right_paddle", "boat_left_rowing_time",
                "boat_right_rowing_time", "boat_paddle_scale", "boat_body_offset_y", "boat_body_offset_z");
        ObjectBinding bindings = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("ysm");
        context.entity = null;
        for (String name : names) {
            require(bindings.getProperty(name) instanceof Variable && !(bindings.getProperty(name) instanceof Function),
                    "boat query is a scalar Variable: " + name);
            require(eval(engine, context, "ysm." + name + ";") == null, "boat query requires Entity: " + name);
        }
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Field passengers = Entity.class.getDeclaredField("passengers");
        Field data = Entity.class.getDeclaredField("entityData");
        Field positions = net.minecraft.world.entity.vehicle.boat.AbstractBoat.class.getDeclaredField("paddlePositions");
        var defineData = net.minecraft.world.entity.vehicle.boat.AbstractBoat.class.getDeclaredMethod("defineSynchedData",
                net.minecraft.network.syncher.SynchedEntityData.Builder.class);
        passengers.setAccessible(true);
        data.setAccessible(true);
        positions.setAccessible(true);
        defineData.setAccessible(true);
        Entity previousVehicle = entity.vehicle;
        float previousPartial = context.ysm_partial_tick;
        try {
            context.entity = entity;
            entity.vehicle = null;
            require(Boolean.FALSE.equals(eval(engine, context, "ysm.boat_left_paddle;")), "nonboat subject paddle is false");
            movementNumber(0f, eval(engine, context, "ysm.boat_left_rowing_time;"), "nonboat rowing is zero");
            movementNumber(1f, eval(engine, context, "ysm.boat_paddle_scale;"), "reference nonboat paddle scale is one");
            entity.vehicle = entity;
            require(Boolean.FALSE.equals(eval(engine, context, "ysm.boat_right_paddle;")), "unrelated vehicle is not a boat");
            for (Class<? extends net.minecraft.world.entity.vehicle.boat.AbstractBoat> type : List.of(
                    net.minecraft.world.entity.vehicle.boat.Boat.class, net.minecraft.world.entity.vehicle.boat.ChestBoat.class,
                    net.minecraft.world.entity.vehicle.boat.Raft.class, net.minecraft.world.entity.vehicle.boat.ChestRaft.class)) {
                // Native boat/data/paddle methods run with explicit CPU passenger and interpolation inputs.
                var boat = (net.minecraft.world.entity.vehicle.boat.AbstractBoat) unsafe.allocateInstance(type);
                var builder = new net.minecraft.network.syncher.SynchedEntityData.Builder(boat);
                // Entity's constructor normally defines these before the subclass hook.
                var define = net.minecraft.network.syncher.SynchedEntityData.Builder.class.getMethod("define",
                        net.minecraft.network.syncher.EntityDataAccessor.class, Object.class);
                Map<String, Object> baseData = Map.of(
                        "DATA_SHARED_FLAGS_ID", (byte) 0, "DATA_AIR_SUPPLY_ID", 300,
                        "DATA_CUSTOM_NAME_VISIBLE", false, "DATA_CUSTOM_NAME", java.util.Optional.empty(),
                        "DATA_SILENT", false, "DATA_NO_GRAVITY", false,
                        "DATA_POSE", Pose.STANDING, "DATA_TICKS_FROZEN", 0);
                for (var entry : baseData.entrySet()) {
                    Field accessor = Entity.class.getDeclaredField(entry.getKey());
                    accessor.setAccessible(true);
                    define.invoke(builder, accessor.get(null), entry.getValue());
                }
                defineData.invoke(boat, builder);
                data.set(boat, builder.build());
                passengers.set(boat, com.google.common.collect.ImmutableList.of(entity));
                positions.set(boat, new float[]{2f, 4f});
                boat.setPaddleState(true, false);
                entity.vehicle = boat;
                context.entity = entity;
                context.ysm_partial_tick = 0.25f;
                require(Boolean.TRUE.equals(eval(engine, context, "ysm.boat_left_paddle;"))
                                && Boolean.FALSE.equals(eval(engine, context, "ysm.boat_right_paddle;")),
                        "native paddle indices and raw Boolean states: " + type.getSimpleName());
                movementNumber(-1.7054757f, eval(engine, context, "ysm.boat_left_rowing_time;"),
                        "reference negative rowing time uses captured partial tick");
                movementNumber(0f, eval(engine, context, "ysm.boat_right_rowing_time;"), "inactive paddle has zero rowing time");
                context.ysm_partial_tick = 1f;
                movementNumber(-2f, eval(engine, context, "ysm.boat_left_rowing_time;"), "partial one reaches current native paddle position");
                boat.setPaddleState(false, true);
                require(Boolean.FALSE.equals(eval(engine, context, "ysm.boat_left_paddle;"))
                                && Boolean.TRUE.equals(eval(engine, context, "ysm.boat_right_paddle;")),
                        "native paddle changes are immediately visible");
                movementNumber(-4f, eval(engine, context, "ysm.boat_right_rowing_time;"), "right paddle uses its independent position");
                boolean raft = boat instanceof net.minecraft.world.entity.vehicle.boat.Raft
                        || boat instanceof net.minecraft.world.entity.vehicle.boat.ChestRaft;
                boolean chest = boat instanceof net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
                movementNumber(raft ? -6f : 0f, eval(engine, context, "ysm.boat_body_offset_y;"), "raft body offset: " + type.getSimpleName());
                movementNumber(chest ? -2.4f : 0f, eval(engine, context, "ysm.boat_body_offset_z;"), "chest body offset: " + type.getSimpleName());
                movementNumber(1f, eval(engine, context, "ysm.boat_paddle_scale;"), "reference scale remains one for each boat type");
                context.entity = boat;
                require(Boolean.TRUE.equals(eval(engine, context, "ysm.boat_right_paddle;")), "boat itself is a valid query subject");
                movementNumber(-4f, eval(engine, context, "ysm.boat_right_rowing_time;"), "boat-self rowing query uses native state");
                Avatar.MolangContext other = new Avatar.MolangContext(null);
                other.entity = entity;
                other.ysm_partial_tick = 0f;
                movementNumber(-3.6073008f, eval(engine, other, "ysm.boat_right_rowing_time;"), "another context retains its own partial tick");
                passengers.set(boat, com.google.common.collect.ImmutableList.of());
                require(Boolean.FALSE.equals(eval(engine, context, "ysm.boat_right_paddle;")), "native paddle state requires a controlling passenger");
            }
        } finally {
            entity.vehicle = previousVehicle;
            context.entity = entity;
            context.ysm_partial_tick = previousPartial;
        }
    }

    private static void testEntityIdentityQueries(MolangEngine engine, Avatar.MolangContext context,
                                                  FixtureEntity entity) throws Exception {
        ObjectBinding bindings = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("ysm");
        for (String name : List.of("entity_type", "is_player")) {
            require(bindings.getProperty(name) instanceof Variable
                            && !(bindings.getProperty(name) instanceof Function),
                    "entity identity query is a scalar: " + name);
            context.entity = null;
            require(eval(engine, context, "ysm." + name + ";") == null,
                    "entity identity missing LivingEntity: " + name);
        }
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        context.entity = (Entity) ((Unsafe) unsafeField.get(null))
                .allocateInstance(net.minecraft.world.entity.item.ItemEntity.class);
        require(eval(engine, context, "ysm.entity_type;") == null
                        && eval(engine, context, "ysm.is_player;") == null,
                "entity identity queries reject nonliving entities");
        for (String name : List.of("player_level", "has_cape", "cape_flap_amount")) {
            ObjectBinding queries = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("query");
            require(queries.getProperty(name) instanceof Variable
                            && !(queries.getProperty(name) instanceof Function),
                    "native player query is a scalar: " + name);
            require(eval(engine, context, "q." + name + ";") == null,
                    "native player query rejects nonliving entities: " + name);
            context.entity = entity;
            require(eval(engine, context, "q." + name + ";") == null,
                    "native player query rejects other living entities: " + name);
            context.entity = null;
            require(eval(engine, context, "q." + name + ";") == null,
                    "native player query rejects missing entities: " + name);
        }
        EntityType<?> originalType = entity.entityType;
        try {
            context.entity = entity;
            entity.entityType = EntityType.ARMOR_STAND;
            require("minecraft:armor_stand".equals(eval(engine, context, "ysm.entity_type;"))
                            && Boolean.FALSE.equals(eval(engine, context, "ysm.is_player;")),
                    "native registry identity and raw Boolean nonplayer result");
            entity.entityType = EntityType.ZOMBIE;
            require("minecraft:zombie".equals(eval(engine, context, "ysm.entity_type;")),
                    "entity type follows current native type without cached population");
            Avatar.MolangContext other = new Avatar.MolangContext(null);
            require(eval(engine, other, "ysm.entity_type;") == null
                            && eval(engine, other, "ysm.is_player;") == null,
                    "entity identity does not leak to another context");
        } finally {
            entity.entityType = originalType;
            context.entity = entity;
        }
    }

    private static void testWorldTagContracts(MolangEngine engine, Avatar.MolangContext context,
                                              FixtureEntity entity) throws Exception {
        List<String> functions = List.of("biome_has_all_tags", "biome_has_any_tag",
                "relative_block_has_all_tags", "relative_block_has_any_tag");
        var query = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("query");
        context.entity = null;
        for (String name : functions) {
            var function = (Function) query.getProperty(name);
            int minimum = name.startsWith("biome") ? 1 : 4;
            for (int size = 0; size <= 5; size++) {
                require(function.validateArgumentSize(size) == (size >= minimum), "world-tag signature: " + name + "/" + size);
            }
            String arguments = minimum == 1 ? "v.no_tag_entity=1" : "v.no_tag_entity=1,0,0,'fixture:solid'";
            require(eval(engine, context, "q." + name + "(" + arguments + ");") == null,
                    "world-tag entity gate: " + name);
        }
        require(context.variables.getPath("no_tag_entity") == null, "world-tag entity gate precedes all argument effects");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        FixtureLevel world = (FixtureLevel) unsafe.allocateInstance(FixtureLevel.class);
        var biomes = new MappedRegistry<net.minecraft.world.level.biome.Biome>(Registries.BIOME, Lifecycle.stable());
        var biomeKey = ResourceKey.create(Registries.BIOME, Identifier.parse("fixture:forest"));
        var biome = biomes.register(biomeKey,
                (net.minecraft.world.level.biome.Biome) unsafe.allocateInstance(net.minecraft.world.level.biome.Biome.class),
                RegistrationInfo.BUILT_IN);
        var biomeTags = biome.getClass().getDeclaredMethod("bindTags", java.util.Collection.class);
        biomeTags.setAccessible(true);
        biomeTags.invoke(biome, List.of(net.minecraft.tags.TagKey.create(Registries.BIOME, Identifier.parse("minecraft:forest")),
                net.minecraft.tags.TagKey.create(Registries.BIOME, Identifier.parse("fixture:temperate"))));
        world.biome = biome;
        world.block = Blocks.STONE.defaultBlockState();
        var blockHolder = Blocks.STONE.builtInRegistryHolder();
        var previousTags = blockHolder.tags().toList();
        var bindTags = blockHolder.getClass().getDeclaredMethod("bindTags", java.util.Collection.class);
        bindTags.setAccessible(true);
        Field level = Entity.class.getDeclaredField("level");
        Field position = Entity.class.getDeclaredField("position");
        Field blockPosition = Entity.class.getDeclaredField("blockPosition");
        level.setAccessible(true);
        position.setAccessible(true);
        blockPosition.setAccessible(true);
        Object previousLevel = level.get(entity);
        Object previousPosition = position.get(entity);
        Object previousBlockPosition = blockPosition.get(entity);
        BlockPos subject = new BlockPos(-1, 64, 2);
        try {
            bindTags.invoke(blockHolder, List.of(net.minecraft.tags.TagKey.create(Registries.BLOCK, Identifier.parse("fixture:solid")),
                    net.minecraft.tags.TagKey.create(Registries.BLOCK, Identifier.parse("minecraft:stone_like"))));
            context.entity = entity;
            level.set(entity, world);
            position.set(entity, new Vec3(-0.25d, 64.75d, 2.5d));
            blockPosition.set(entity, subject);
            testWorldIdentityQueries(engine, context, world);
            testDiagnosticBindings(engine, entity, world);
            world.actorCount = 8;
            require(Integer.valueOf(8).equals(eval(engine, context, "q.actor_count;")),
                    "actor count reads the subject client world as raw Integer");
            world.actorCount = 0;
            require(Integer.valueOf(0).equals(eval(engine, context, "q.actor_count;")),
                    "empty world actor count is not a constant one");
            require(Boolean.TRUE.equals(eval(engine, context, "q.biome_has_all_tags('forest','fixture:temperate');")),
                    "biome holder satisfies both native tags with default namespace");
            require(subject.equals(world.biomePosition), "biome query uses the subject block position");
            require(Boolean.FALSE.equals(eval(engine, context, "q.biome_has_all_tags('fixture:missing',v.after_biome_miss=1);"))
                            && context.variables.getPath("after_biome_miss") == null,
                    "biome all-tags short circuits a miss");
            require(Boolean.TRUE.equals(eval(engine, context, "q.biome_has_any_tag('fixture:missing','forest',v.after_biome_match=1);"))
                            && context.variables.getPath("after_biome_match") == null,
                    "biome any-tags short circuits a match");
            require(eval(engine, context, "q.biome_has_any_tag('bad id','forest');") == null,
                    "invalid biome ID is not skipped");
            require(Boolean.TRUE.equals(eval(engine, context,
                            "q.relative_block_has_all_tags(0.75,-0.25,-0.5,'fixture:solid','stone_like');")),
                    "relative block native tag membership uses fractional offsets");
            require(new BlockPos(0, 64, 2).equals(world.position), "reference negative/fractional coordinate rounding");
            require(Boolean.TRUE.equals(eval(engine, context,
                            "q.relative_block_has_any_tag(0,0,0,'fixture:missing','fixture:solid',v.after_block_match=1);"))
                            && context.variables.getPath("after_block_match") == null,
                    "relative block any-tags success short circuits");
            require(Boolean.FALSE.equals(eval(engine, context,
                            "q.relative_block_has_all_tags(0,0,0,'fixture:missing',v.after_block_miss=1);"))
                            && context.variables.getPath("after_block_miss") == null,
                    "relative block all-tags failure short circuits");
            require(eval(engine, context, "q.relative_block_has_any_tag(0,0,0,23,'fixture:solid');") == null,
                    "non-string block tag is rejected before later candidates");
            int reads = world.reads;
            require(eval(engine, context, "q.relative_block_has_any_tag(v.range_x=6,v.range_y=2,v.range_z=3,v.after_range=1);") == null,
                    "relative tag query enforces five-block range");
            require(world.reads == reads && context.variables.getPath("after_range") == null,
                    "out-of-range query neither reads block nor evaluates tags");
            movementNumber(2f, context.variables.getPath("range_y"), "all offsets captured before range check");
            movementNumber(3f, context.variables.getPath("range_z"), "third offset captured before range check");
            require(Boolean.TRUE.equals(eval(engine, context, "q.relative_block_has_any_tag(5,-5,5,'fixture:solid');")),
                    "relative tag range boundary is inclusive");
            require(new BlockPos(4, 59, 7).equals(world.position), "boundary lookup uses reference world coordinates");
            biomeTags.invoke(biome, List.of());
            require(Boolean.FALSE.equals(eval(engine, context, "q.biome_has_any_tag('forest');")), "biome tag state changes immediately");
            biomeTags.invoke(biome, List.of(net.minecraft.tags.TagKey.create(Registries.BIOME, Identifier.parse("minecraft:forest"))));
            Entity nonLiving = (net.minecraft.world.entity.item.ItemEntity) unsafe.allocateInstance(net.minecraft.world.entity.item.ItemEntity.class);
            level.set(nonLiving, world);
            position.set(nonLiving, new Vec3(-0.25d, 64.75d, 2.5d));
            blockPosition.set(nonLiving, subject);
            context.entity = nonLiving;
            require(Boolean.TRUE.equals(eval(engine, context, "q.biome_has_any_tag('forest');"))
                            && Boolean.TRUE.equals(eval(engine, context, "q.relative_block_has_any_tag(0,0,0,'fixture:solid');")),
                    "world tags are available to non-living Entity contexts");
            level.set(nonLiving, null);
            require(eval(engine, context, "q.actor_count;") == null, "actor count requires subject world");
            require(eval(engine, context, "ysm.weather;") == null
                            && eval(engine, context, "ysm.dimension_name;") == null,
                    "world identity queries require subject world");
            require(eval(engine, context, "q.biome_has_any_tag(v.no_tag_world=1);") == null
                            && context.variables.getPath("no_tag_world") == null,
                    "missing world rejects before evaluating tag expressions");
            Avatar.MolangContext other = new Avatar.MolangContext(null);
            require(eval(engine, other, "q.biome_has_any_tag('forest');") == null,
                    "another context cannot borrow the subject world");
            require(eval(engine, other, "q.actor_count;") == null, "actor count does not reuse another context world");
        } finally {
            bindTags.invoke(blockHolder, previousTags);
            level.set(entity, previousLevel);
            position.set(entity, previousPosition);
            blockPosition.set(entity, previousBlockPosition);
            context.entity = entity;
        }
    }

    private static void testDiagnosticBindings(MolangEngine engine, FixtureEntity entity,
                                                FixtureLevel world) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Avatar owner = (Avatar) unsafe.allocateInstance(Avatar.class);
        Avatar.MolangContext context = new Avatar.MolangContext(owner);
        context.entity = entity;
        var names = List.of("dump_mods", "dump_effects", "dump_biome");
        ObjectBinding bindings = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("ysm");
        for (String name : names) {
            require(bindings.getProperty(name) instanceof Variable && !(bindings.getProperty(name) instanceof Function),
                    "reference dump is a scalar variable: " + name);
        }
        boolean previousDebug = org.figuramc.figura.config.Configs.DEBUG_MODE.value;
        int previousReads = org.figuramc.figura.utils.fabric.PlatformUtilsImpl.loadedModReads;
        var logger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager
                .getLogger(org.figuramc.figura.FiguraMod.LOGGER.getName());
        var previousLevel = logger.getLevel();
        var messages = new java.util.ArrayList<String>();
        var appender = new org.apache.logging.log4j.core.appender.AbstractAppender(
                "ysm-native-diagnostic-bindings", null, null, true, null) {
            @Override
            public void append(org.apache.logging.log4j.core.LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        Field activeEffects = LivingEntity.class.getDeclaredField("activeEffects");
        activeEffects.setAccessible(true);
        Object previousEffects = activeEffects.get(entity);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(org.apache.logging.log4j.Level.WARN);
        try {
            org.figuramc.figura.config.Configs.DEBUG_MODE.value = false;
            world.biomePosition = null;
            for (String name : names) {
                require(eval(engine, context, "ysm." + name + ";") == null, "disabled dump returns raw null: " + name);
            }
            require(messages.isEmpty() && world.biomePosition == null
                            && org.figuramc.figura.utils.fabric.PlatformUtilsImpl.loadedModReads == previousReads,
                    "disabled diagnostics perform no logging or platform enumeration");
            org.figuramc.figura.config.Configs.DEBUG_MODE.value = true;
            require(eval(engine, context, "ysm.dump_mods;") == null, "mod enumeration returns raw null");
            require(messages.equals(List.of("Mod: display Alpha Mod  id fixture-z", "Mod: display Zulu Mod  id fixture-a")),
                    "platform mod diagnostics sort by display name and include IDs: " + messages);
            messages.clear();
            require(eval(engine, context, "ysm.dump_biome;") == null, "biome diagnostics return raw null");
            require(messages.contains("Name minecraft:worldgen/biome")
                            && entity.blockPosition().equals(world.biomePosition)
                            && messages.stream().filter(message -> message.startsWith("Tag ")).count() == 2,
                    "biome diagnostics expose reference registry strings and current native tags: " + messages);
            messages.clear();
            var speed = new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 200, 2);
            activeEffects.set(entity, new java.util.HashMap<>(Map.of(speed.getEffect(), speed)));
            require(eval(engine, context, "ysm.dump_effects;") == null && messages.size() == 1
                            && messages.getFirst().contains("name minecraft:speed  lv=3"),
                    "living diagnostics read native effects with display name, ID and one-based level: " + messages);
            messages.clear();
            var arrow = (net.minecraft.world.entity.projectile.arrow.Arrow)
                    unsafe.allocateInstance(net.minecraft.world.entity.projectile.arrow.Arrow.class);
            Items.TIPPED_ARROW.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
            ItemStack pickup = new ItemStack(Items.TIPPED_ARROW);
            pickup.set(DataComponents.POTION_CONTENTS, net.minecraft.world.item.alchemy.PotionContents.EMPTY.withEffectAdded(
                    new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100, 1)));
            Field pickupField = net.minecraft.world.entity.projectile.arrow.AbstractArrow.class.getDeclaredField("pickupItemStack");
            pickupField.setAccessible(true);
            pickupField.set(arrow, pickup);
            context.entity = arrow;
            require(eval(engine, context, "ysm.dump_effects;") == null && messages.size() == 1
                            && messages.getFirst().contains("name minecraft:poison  lv=2"),
                    "arrow diagnostics use native pickup potion components: " + messages);
            messages.clear();
            context.entity = (Entity) unsafe.allocateInstance(net.minecraft.world.entity.item.ItemEntity.class);
            require(eval(engine, context, "ysm.dump_effects;") == null && messages.isEmpty(), "unsupported effect subject emits nothing");
            context.entity = entity;
            owner.nbt = new net.minecraft.nbt.CompoundTag();
            var cipher = new net.minecraft.nbt.CompoundTag();
            cipher.putInt("crypto_version", 3);
            owner.nbt.put("ysm", cipher);
            int reads = org.figuramc.figura.utils.fabric.PlatformUtilsImpl.loadedModReads;
            for (String name : names) {
                require(eval(engine, context, "ysm." + name + ";") == null, "private diagnostic returns raw null: " + name);
            }
            require(messages.isEmpty() && org.figuramc.figura.utils.fabric.PlatformUtilsImpl.loadedModReads == reads,
                    "encrypted payload diagnostics log nothing and do not enumerate the platform");
            owner.nbt = null;
            context.entity = null;
            require(eval(engine, context, "ysm.dump_effects;") == null
                            && eval(engine, context, "ysm.dump_biome;") == null && messages.isEmpty(),
                    "entity diagnostics require a subject even with debugging enabled");
        } finally {
            activeEffects.set(entity, previousEffects);
            org.figuramc.figura.config.Configs.DEBUG_MODE.value = previousDebug;
            org.figuramc.figura.utils.fabric.PlatformUtilsImpl.loadedModReads = previousReads;
            logger.removeAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    private static void testWorldIdentityQueries(MolangEngine engine, Avatar.MolangContext context,
                                                 FixtureLevel world) throws Exception {
        ObjectBinding bindings = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("ysm");
        for (String name : List.of("weather", "dimension_name")) {
            require(bindings.getProperty(name) instanceof Variable
                            && !(bindings.getProperty(name) instanceof Function),
                    "world identity query is a scalar: " + name);
        }
        world.dimensionKey = net.minecraft.world.level.Level.OVERWORLD;
        require("minecraft:dimension".equals(eval(engine, context, "ysm.dimension_name;")),
                "dimension_name preserves reference registry string rather than ResourceKey debug text");
        world.dimensionKey = net.minecraft.world.level.Level.NETHER;
        require("minecraft:dimension".equals(eval(engine, context, "ysm.dimension_name;")),
                "reference dimension registry remains the same in the Nether");
        world.dimensionKey = ResourceKey.create(ResourceKey.createRegistryKey(Identifier.parse("fixture:worlds")),
                Identifier.parse("fixture:test"));
        require("fixture:worlds".equals(eval(engine, context, "ysm.dimension_name;")),
                "dimension query reads the current key registry");
        world.rainingAtSubject = true;
        world.raining = false;
        world.thundering = false;
        int reads = world.weatherReads;
        require(Integer.valueOf(0).equals(eval(engine, context, "ysm.weather;")),
                "clear weather raw Integer is independent of local rain-at-block predicate");
        world.rainingAtSubject = false;
        world.raining = true;
        require(Integer.valueOf(1).equals(eval(engine, context, "ysm.weather;")),
                "global rain remains true under a dry subject block");
        world.thundering = true;
        require(Integer.valueOf(2).equals(eval(engine, context, "ysm.weather;")), "thunder takes precedence over rain");
        world.raining = false;
        require(Integer.valueOf(2).equals(eval(engine, context, "ysm.weather;")), "thunder without rain still returns two");
        require(world.weatherReads == reads, "weather does not call local isRainingAt");
        Avatar.MolangContext other = new Avatar.MolangContext(null);
        require(eval(engine, other, "ysm.weather;") == null
                        && eval(engine, other, "ysm.dimension_name;") == null,
                "world identity queries cannot borrow another context world");
    }

    private static void testEquipmentFunctionContracts(MolangEngine engine, Avatar.MolangContext context,
                                                       FixtureEntity entity) throws Exception {
        List<String> functions = List.of("max_durability", "remaining_durability", "is_item_name_any",
                "equipped_item_all_tags", "equipped_item_any_tag");
        var registry = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("query");
        context.entity = null;
        for (String name : functions) {
            var function = (Function) registry.getProperty(name);
            boolean durability = name.endsWith("durability");
            for (int size = 0; size <= 4; size++) {
                require(function.validateArgumentSize(size) == (durability ? size == 1 : size >= 2),
                        "reference equipment signature: " + name + "/" + size);
            }
            String arguments = durability ? "v.no_equipment_entity=1" : "v.no_equipment_entity=1,'stone'";
            require(eval(engine, context, "q." + name + "(" + arguments + ");") == null,
                    "equipment function requires LivingEntity: " + name);
        }
        require(context.variables.getPath("no_equipment_entity") == null, "equipment entity gate precedes argument effects");
        require(eval(engine, context, "q.equipment_count;") == null && eval(engine, context, "ysm.armor_value;") == null,
                "equipment scalar entity gates");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        context.entity = (net.minecraft.world.entity.item.ItemEntity)
                ((Unsafe) unsafeField.get(null)).allocateInstance(net.minecraft.world.entity.item.ItemEntity.class);
        for (String name : functions) {
            String arguments = name.endsWith("durability") ? "'mainhand'" : "'mainhand','stone'";
            require(eval(engine, context, "q." + name + "(" + arguments + ");") == null,
                    "equipment function rejects non-living Entity: " + name);
        }
        context.entity = entity;
        entity.chest = ItemStack.EMPTY;
        entity.equipment = new java.util.EnumMap<>(EquipmentSlot.class);
        Items.STONE.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        ItemStack damaged = new ItemStack(Items.STONE);
        damaged.set(DataComponents.MAX_DAMAGE, 100);
        damaged.set(DataComponents.DAMAGE, 25);
        for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.FEET,
                EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)) {
            entity.equipment.clear();
            entity.equipment.put(slot, damaged);
            String slotName = slot.getSerializedName().toUpperCase(java.util.Locale.ENGLISH);
            require(Integer.valueOf(100).equals(eval(engine, context, "q.max_durability('" + slotName + "');")),
                    "named-slot durability returns native Integer: " + slot);
            require(Integer.valueOf(75).equals(eval(engine, context, "q.remaining_durability('" + slotName + "');")),
                    "named-slot remaining durability: " + slot);
        }
        entity.equipment.clear();
        entity.equipment.put(EquipmentSlot.MAINHAND, damaged);
        require(Integer.valueOf(100).equals(eval(engine, context, "q.max_durability(0);")),
                "existing project numeric main-hand slot remains supported");
        damaged.set(DataComponents.DAMAGE, 70);
        require(Integer.valueOf(30).equals(eval(engine, context, "q.remaining_durability('mainhand');")),
                "native damage changes are visible immediately");
        require(Integer.valueOf(0).equals(eval(engine, context, "q.max_durability('offhand');")), "empty slot durability is zero");
        require(eval(engine, context, "q.is_item_name_any('invalid',v.invalid_slot_match=1);") == null
                        && context.variables.getPath("invalid_slot_match") == null,
                "invalid slot returns null before matching arguments");
        require(Boolean.TRUE.equals(eval(engine, context,
                        "q.is_item_name_any('mainhand','dirt','stone',v.after_item_match=1);")),
                "native item IDs support default namespace and first-match short circuit");
        require(context.variables.getPath("after_item_match") == null, "item-name match does not evaluate later arguments");
        require(Boolean.FALSE.equals(eval(engine, context, "q.is_item_name_any('mainhand','minecraft:dirt');")),
                "item-name mismatch returns raw Boolean false");
        require(eval(engine, context, "q.is_item_name_any('mainhand','bad id','stone');") == null,
                "invalid item ID returns null before later candidate");
        require(eval(engine, context, "q.is_item_name_any('mainhand',23,'stone');") == null,
                "non-string item candidate is not coerced into an identifier");
        var holder = Items.STONE.builtInRegistryHolder();
        var previousTags = holder.tags().toList();
        var bindTags = holder.getClass().getDeclaredMethod("bindTags", java.util.Collection.class);
        bindTags.setAccessible(true);
        try {
            var firstTag = net.minecraft.tags.TagKey.create(Registries.ITEM, Identifier.parse("fixture:one"));
            var secondTag = net.minecraft.tags.TagKey.create(Registries.ITEM, Identifier.parse("fixture:two"));
            bindTags.invoke(holder, List.of(firstTag, secondTag));
            require(Boolean.TRUE.equals(eval(engine, context,
                            "q.equipped_item_all_tags('mainhand','fixture:one','fixture:two');")), "native holder satisfies both tags");
            require(Boolean.FALSE.equals(eval(engine, context,
                            "q.equipped_item_all_tags('mainhand','fixture:missing',v.after_all_miss=1);")),
                    "all-tags stops on the first absent tag");
            require(context.variables.getPath("after_all_miss") == null, "all-tags failure short circuits arguments");
            require(Boolean.TRUE.equals(eval(engine, context,
                            "q.equipped_item_any_tag('mainhand','fixture:missing','fixture:one',v.after_tag_match=1);")),
                    "any-tag stops at its first match");
            require(context.variables.getPath("after_tag_match") == null, "any-tag success short circuits arguments");
            require(eval(engine, context, "q.equipped_item_any_tag('mainhand','bad id','fixture:one');") == null,
                    "invalid tag stops rather than skipping to a later match");
            damaged.setCount(0);
            require(Boolean.FALSE.equals(eval(engine, context, "q.equipped_item_all_tags('mainhand',v.empty_tag_effect=1);"))
                            && context.variables.getPath("empty_tag_effect") == null,
                    "empty item returns false without evaluating tag arguments");
            damaged.setCount(1);
        } finally {
            bindTags.invoke(holder, previousTags);
        }
        entity.equipment.clear();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            entity.equipment.put(slot, new ItemStack(Items.STONE));
        }
        require(Integer.valueOf(5).equals(eval(engine, context, "q.equipment_count;")),
                "native humanoid and body armor count, while hands and saddle do not");
        entity.equipment.put(EquipmentSlot.BODY, ItemStack.EMPTY);
        require(Integer.valueOf(4).equals(eval(engine, context, "q.equipment_count;")), "body armor removal changes native count");
        entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(9d);
        require(Integer.valueOf(9).equals(eval(engine, context, "ysm.armor_value;")), "native armor attribute returns raw Integer");
        entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(3d);
        require(Integer.valueOf(3).equals(eval(engine, context, "ysm.armor_value;")), "native armor attribute changes immediately");
        entity.equipment.clear();
        require(Integer.valueOf(0).equals(eval(engine, context, "q.equipment_count;")), "equipment removal clears the count");
        Avatar.MolangContext other = new Avatar.MolangContext(null);
        require(eval(engine, other, "q.equipment_count;") == null && eval(engine, other, "ysm.armor_value;") == null,
                "equipment scalar values remain scoped to their context");
    }

    private static void testHandQueryContracts(MolangEngine engine, Avatar.MolangContext context,
                                               FixtureEntity entity) throws Exception {
        List<String> names = List.of("q.is_using_item", "q.is_eating", "q.is_swinging", "q.swing_time",
                "q.attack_time", "q.item_in_use_duration", "q.item_max_use_duration", "q.item_remaining_use_duration",
                "ysm.swinging", "ysm.swing_time", "ysm.attack_time", "ysm.hurt_time");
        var roots = Avatar.createMolangBindings(context);
        context.entity = null;
        for (String name : names) {
            int separator = name.indexOf('.');
            var namespace = (ObjectBinding) roots.getProperty(name.substring(0, separator));
            Object binding = namespace.getProperty(name.substring(separator + 1));
            require(binding instanceof Variable && !(binding instanceof Function),
                    "hand query is a scalar Variable, not a callable function: " + name);
            require(eval(engine, context, name + ";") == null, "hand query requires LivingEntity: " + name);
        }
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        context.entity = (net.minecraft.world.entity.item.ItemEntity)
                ((Unsafe) unsafeField.get(null)).allocateInstance(net.minecraft.world.entity.item.ItemEntity.class);
        for (String name : names) {
            require(eval(engine, context, name + ";") == null, "non-living Entity cannot answer hand query: " + name);
        }
        var consumable = net.minecraft.world.item.component.Consumable.builder().consumeSeconds(2f)
                .animation(net.minecraft.world.item.ItemUseAnimation.EAT).build();
        Items.APPLE.builtInRegistryHolder().bindComponents(
                DataComponentMap.builder().set(DataComponents.CONSUMABLE, consumable).build());
        entity.equipment = new java.util.EnumMap<>(EquipmentSlot.class);
        entity.equipment.put(EquipmentSlot.MAINHAND, new ItemStack(Items.APPLE));
        entity.equipment.put(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        entity.usedHand = net.minecraft.world.InteractionHand.MAIN_HAND;
        entity.using = true;
        entity.sleeping = false;
        entity.fixtureUseTicks = 7;
        entity.fixtureRemainingTicks = 33;
        entity.swinging = true;
        entity.swingingArm = net.minecraft.world.InteractionHand.MAIN_HAND;
        entity.swingTime = 4;
        entity.attackProgress = 0.6f;
        entity.hurtTime = 3;
        context.entity = entity;
        context.ysm_partial_tick = 0.25f;
        context.is_using_item = context.is_swinging = context.swing_time = context.attack_time = -99f;
        require(Boolean.TRUE.equals(eval(engine, context, "q.is_using_item;")), "using query returns raw Boolean");
        require(Boolean.TRUE.equals(eval(engine, context, "q.is_eating;")), "eating query reads native consumable animation");
        require(Boolean.TRUE.equals(eval(engine, context, "q.is_swinging;"))
                        && Boolean.TRUE.equals(eval(engine, context, "ysm.swinging;")), "swing names return raw Boolean");
        number(0.2125d, eval(engine, context, "q.swing_time;"), "query swing time is raw Double seconds");
        movementNumber(4.25f, eval(engine, context, "ysm.swing_time;"), "YSM swing time is raw Float ticks");
        movementNumber(0.6f, eval(engine, context, "q.attack_time;"), "query attack progress is native Float");
        movementNumber(0.6f, eval(engine, context, "ysm.attack_time;"), "YSM attack progress shares the native value");
        require(Integer.valueOf(3).equals(eval(engine, context, "ysm.hurt_time;")), "YSM hurt time retains raw Integer");
        number(0.35d, eval(engine, context, "q.item_in_use_duration;"), "native use ticks convert to Double seconds");
        number(2d, eval(engine, context, "q.item_max_use_duration;"), "native stack consumable defines total duration");
        number(1.65d, eval(engine, context, "q.item_remaining_use_duration;"), "native remaining ticks convert to seconds");
        YsmHandQuery.capture(context);
        movementNumber(1f, context.is_using_item, "render capture publishes use state to controller consumers");
        movementNumber(1f, eval(engine, context, "ctrl.attack;"), "project ctrl.attack consumes current captured hand state");
        entity.using = false;
        require(Boolean.FALSE.equals(eval(engine, context, "q.is_using_item;"))
                        && Boolean.TRUE.equals(eval(engine, context, "q.is_eating;")),
                "eating animation predicate does not add an unrelated native using flag requirement");
        entity.sleeping = true;
        require(Boolean.FALSE.equals(eval(engine, context, "q.is_swinging;")), "sleep suppresses native swinging without a local pulse");
        number(0d, eval(engine, context, "q.swing_time;"), "sleep suppresses native swing seconds");
        movementNumber(0f, eval(engine, context, "q.attack_time;"), "sleep suppresses native attack progress");
        entity.sleeping = false;
        entity.swinging = false;
        entity.attackProgress = 0.2f;
        require(Boolean.TRUE.equals(eval(engine, context, "q.is_swinging;")), "observer attack progress supplies native main-hand swing");
        number(0d, eval(engine, context, "q.swing_time;"), "native attack progress does not invent swing tick time");
        entity.attackProgress = Float.NaN;
        movementNumber(0f, eval(engine, context, "q.attack_time;"), "invalid native attack progress follows reference zero fallback");
        entity.attackProgress = 0f;
        entity.usedHand = null;
        number(0d, eval(engine, context, "q.item_in_use_duration;"), "no use releases use duration");
        number(0d, eval(engine, context, "q.item_max_use_duration;"), "empty native use item has zero duration");
        number(0d, eval(engine, context, "q.item_remaining_use_duration;"), "no pending interaction releases remaining duration");
        YsmHandQuery.capture(context);
        movementNumber(0f, eval(engine, context, "ctrl.attack;"), "controller attack capture clears when hand state stops");
        Avatar.MolangContext other = new Avatar.MolangContext(null);
        context.reset();
        context.is_using_item = context.is_swinging = context.swing_time = context.attack_time = 1f;
        YsmHandQuery.capture(context);
        movementNumber(0f, eval(engine, context, "ctrl.attack;"), "missing-entity capture clears controller attack on unload");
        for (String name : names) {
            require(eval(engine, context, name + ";") == null && eval(engine, other, name + ";") == null,
                    "hand values clear and remain context-local: " + name);
        }
        context.entity = entity;
        entity.hurtTime = 0;
    }

    private static void testMovementQueries(MolangEngine engine, Avatar.MolangContext context,
                                            FixtureEntity entity) throws Exception {
        context.entity = entity;
        var walk = new net.minecraft.world.entity.WalkAnimationState();
        Field walkState = LivingEntity.class.getDeclaredField("walkAnimation");
        walkState.setAccessible(true);
        walkState.set(entity, walk);
        context.ysm_partial_tick = 1f;
        context.ysm_position_delta_ticks = 0.5f;
        context.pos_delta_x = 0.3d;
        context.pos_delta_y = -0.1d;
        context.pos_delta_z = 0.4d;
        context.ysm_limb_swing_amount = 2f;
        context.ground_speed = context.vertical_speed = -999f;
        entity.setDeltaMovement(new Vec3(9d, 9d, 9d));
        context.updateYsmMovementContext(2f);
        movementNumber(20f, context.ground_speed, "render capture publishes ground speed to controller consumers");
        movementNumber(-4f, context.vertical_speed, "render capture publishes signed vertical speed to controller consumers");
        for (String query : List.of("q.ground_speed", "ysm.ground_speed2")) {
            movementNumber(20f, eval(engine, context, query + ";"), "tracker distance/time takes precedence: " + query);
        }
        movementNumber(-4f, eval(engine, context, "q.vertical_speed;"), "tracker signed vertical speed is in blocks/second");
        context.pos_delta_x = context.pos_delta_y = context.pos_delta_z = 0d;
        context.ysm_limb_swing_amount = -0.8f;
        movementNumber(0.8f, eval(engine, context, "q.ground_speed;"), "event limb amplitude precedes native walk/velocity");
        context.ysm_limb_swing_amount = Float.NaN;
        walk.setSpeed(0.6f);
        movementNumber(0.6f, eval(engine, context, "q.ground_speed;"), "native walk animation fallback");
        walk.setSpeed(0f);
        entity.setDeltaMovement(new Vec3(0.3d, -0.07d, 0.4d));
        entity.xo = entity.getX();
        entity.yo = entity.getY();
        entity.zo = entity.getZ();
        movementNumber(10f, eval(engine, context, "q.ground_speed;"), "native velocity fallback uses 20 ticks/second");
        movementNumber(0f, eval(engine, context, "q.vertical_speed;"), "small grounded gravity does not become falling speed");
        entity.setDeltaMovement(new Vec3(0d, -0.2d, 0d));
        movementNumber(-4f, eval(engine, context, "q.vertical_speed;"), "vertical velocity above gravity threshold");
        entity.setDeltaMovement(Vec3.ZERO);
        entity.xo = entity.getX() - 0.2d;
        entity.yo = entity.getY() - 0.1d;
        movementNumber(4f, eval(engine, context, "q.ground_speed;"), "native tick displacement fallback");
        movementNumber(2f, eval(engine, context, "q.vertical_speed;"), "vertical tick displacement fallback");
        context.ysm_position_delta_ticks = Float.NaN;
        context.pos_delta_x = Double.NaN;
        entity.setDeltaMovement(new Vec3(Double.NaN, 1d, 1d));
        movementNumber(4f, eval(engine, context, "ysm.ground_speed2;"), "nonfinite tracker/velocity sanitize before native tick fallback");
        entity.xo = Double.NaN;
        entity.yo = Double.NaN;
        movementNumber(0f, eval(engine, context, "q.ground_speed;"), "invalid final horizontal fallback returns finite zero");
        movementNumber(0f, eval(engine, context, "q.vertical_speed;"), "invalid final vertical fallback returns finite zero");
        Avatar.MolangContext other = new Avatar.MolangContext(null);
        context.reset();
        for (String query : List.of("q.ground_speed", "q.vertical_speed", "ysm.ground_speed2")) {
            require(eval(engine, context, query + ";") == null && eval(engine, other, query + ";") == null,
                    "movement query missing entity/reset/context isolation: " + query);
        }
        entity.xo = entity.getX();
        entity.yo = entity.getY();
        entity.zo = entity.getZ();
        entity.setDeltaMovement(Vec3.ZERO);
        context.entity = entity;
    }

    private static void movementNumber(float expected, Object actual, String message) {
        require(actual instanceof Float value && Math.abs(expected - value) < 0.00001f, message + ": " + actual);
    }

    private static void testInputQueries(MolangEngine engine, Avatar.MolangContext context,
                                         FixtureEntity entity) throws Exception {
        List<String> names = List.of("xxa", "yya", "zza", "input_vertical", "input_horizontal", "food_level");
        context.entity = null;
        for (String name : names) {
            require(eval(engine, context, "ysm." + name + ";") == null, "input/food query requires entity: " + name);
        }
        context.entity = entity;
        entity.xxa = -0.75f;
        entity.yya = 0.5f;
        entity.zza = 0.25f;
        for (var axis : Map.of("xxa", -0.75f, "yya", 0.5f, "zza", 0.25f).entrySet()) {
            require(axis.getValue().equals(eval(engine, context, "ysm." + axis.getKey() + ";")),
                    "raw Float input axis reads the native LivingEntity field: " + axis.getKey());
        }
        require(Integer.valueOf(20).equals(eval(engine, context, "ysm.food_level;")),
                "non-player LivingEntity food default is Integer 20");
        context.ysm_partial_tick = 1f;
        entity.yHeadRot = 0f;
        context.pos_delta_x = 0d;
        context.pos_delta_z = 1d;
        number(1d, eval(engine, context, "ysm.input_vertical;"), "forward movement projection");
        number(0d, eval(engine, context, "ysm.input_horizontal;"), "forward movement has no strafe projection");
        context.pos_delta_z = -1d;
        number(-1d, eval(engine, context, "ysm.input_vertical;"), "backward movement projection");
        context.pos_delta_x = 1d;
        context.pos_delta_z = 0d;
        number(0d, eval(engine, context, "ysm.input_vertical;"), "lateral movement has no forward projection");
        number(-1d, eval(engine, context, "ysm.input_horizontal;"), "lateral movement projection differs from raw xxa");
        entity.yHeadRot = 90f;
        number(-1d, eval(engine, context, "ysm.input_vertical;"), "camera rotation changes relative movement projection");
        entity.yHeadRotO = 0f;
        context.ysm_partial_tick = 0f;
        number(-1d, eval(engine, context, "ysm.input_horizontal;"), "view interpolation uses captured partial tick");
        context.pos_delta_x = 0.00001d;
        number(0d, eval(engine, context, "ysm.input_vertical;"), "subthreshold displacement is zero projection");
        number(0d, eval(engine, context, "ysm.input_horizontal;"), "subthreshold strafe projection");
        entity.xxa = 0f;
        entity.yya = -1f;
        entity.zza = 1f;
        require(Float.valueOf(0f).equals(eval(engine, context, "ysm.xxa;"))
                        && Float.valueOf(-1f).equals(eval(engine, context, "ysm.yya;"))
                        && Float.valueOf(1f).equals(eval(engine, context, "ysm.zza;")), "raw axis changes are immediate");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        context.entity = (net.minecraft.world.entity.item.ItemEntity)
                ((Unsafe) unsafeField.get(null)).allocateInstance(net.minecraft.world.entity.item.ItemEntity.class);
        for (String name : List.of("xxa", "yya", "zza", "food_level")) {
            require(eval(engine, context, "ysm." + name + ";") == null, "LivingEntity-only input/food query: " + name);
        }
        context.pos_delta_x = 0d;
        require(Double.valueOf(0d).equals(eval(engine, context, "ysm.input_vertical;")),
                "direction projection accepts a non-living Entity at rest");
        Avatar.MolangContext other = new Avatar.MolangContext(null);
        context.reset();
        for (String name : names) {
            require(eval(engine, context, "ysm." + name + ";") == null && eval(engine, other, "ysm." + name + ";") == null,
                    "input and food do not survive reset or leak to another context: " + name);
        }
        context.entity = entity;
    }

    private static void testYsmEntityQueries(MolangEngine engine, Avatar.MolangContext context,
                                           FixtureEntity entity) throws Exception {
        List<String> names = List.of("is_passenger", "is_sleep", "is_sneak", "eye_in_water", "frozen_ticks", "air_supply");
        ObjectBinding registry = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("ysm");
        context.entity = null;
        for (String name : names) {
            require(registry.getProperty(name) instanceof Variable && !(registry.getProperty(name) instanceof Function),
                    "YSM entity query is a scalar Variable: " + name);
            require(eval(engine, context, "ysm." + name + ";") == null, "YSM entity query requires an entity: " + name);
        }
        context.entity = entity;
        entity.vehicle = null;
        entity.pose = Pose.STANDING;
        entity.grounded = false;
        entity.fixtureEyeWater = false;
        entity.fixtureFrozen = 0;
        entity.fixtureAir = 300;
        context.is_riding = context.is_sleeping = context.is_sneaking = 1f;
        for (String name : List.of("is_passenger", "is_sleep", "is_sneak", "eye_in_water")) {
            require(Boolean.FALSE.equals(eval(engine, context, "ysm." + name + ";")),
                    "YSM state raw Boolean false rejects stale context cache: " + name);
        }
        require(Integer.valueOf(0).equals(eval(engine, context, "ysm.frozen_ticks;")), "zero frozen ticks is raw Integer");
        require(Integer.valueOf(300).equals(eval(engine, context, "ysm.air_supply;")), "full air is raw Integer");
        entity.vehicle = entity;
        entity.pose = Pose.SLEEPING;
        entity.sleeping = false;
        require(Boolean.TRUE.equals(eval(engine, context, "ysm.is_passenger;")), "passenger reads native vehicle state");
        require(Boolean.TRUE.equals(eval(engine, context, "ysm.is_sleep;"))
                        && Boolean.FALSE.equals(eval(engine, context, "q.is_sleeping;")),
                "YSM sleep uses Pose while query sleeping uses LivingEntity getter");
        entity.pose = Pose.STANDING;
        entity.sleeping = true;
        require(Boolean.FALSE.equals(eval(engine, context, "ysm.is_sleep;"))
                        && Boolean.TRUE.equals(eval(engine, context, "q.is_sleeping;")),
                "living sleep getter cannot replace YSM pose predicate");
        entity.pose = Pose.CROUCHING;
        require(Boolean.FALSE.equals(eval(engine, context, "ysm.is_sneak;")), "airborne crouch is not YSM sneak");
        entity.grounded = true;
        require(Boolean.TRUE.equals(eval(engine, context, "ysm.is_sneak;")), "grounded crouch is YSM sneak");
        entity.water = true;
        require(Boolean.FALSE.equals(eval(engine, context, "ysm.eye_in_water;")), "body water does not imply eyes underwater");
        entity.water = false;
        entity.fixtureEyeWater = true;
        entity.fixtureFrozen = 140;
        entity.fixtureAir = -20;
        require(Boolean.TRUE.equals(eval(engine, context, "ysm.eye_in_water;")), "eye water remains independent of body water");
        require(Integer.valueOf(140).equals(eval(engine, context, "ysm.frozen_ticks;")), "frozen ticks updates immediately");
        require(Integer.valueOf(-20).equals(eval(engine, context, "ysm.air_supply;")), "native negative air is preserved without clamp");
        Avatar.MolangContext other = new Avatar.MolangContext(null);
        context.entity = null;
        for (String name : names) {
            require(eval(engine, context, "ysm." + name + ";") == null && eval(engine, other, "ysm." + name + ";") == null,
                    "removed entity and another context do not leak YSM entity values: " + name);
        }
        context.entity = entity;
        entity.vehicle = null;
        entity.pose = Pose.STANDING;
        entity.sleeping = false;
        entity.grounded = false;
        entity.fixtureEyeWater = false;
    }

    private static void testWorldQueries(MolangEngine engine, Avatar.MolangContext context,
                                         FixtureEntity entity) throws Exception {
        List<String> queries = List.of("q.is_in_water_or_rain", "ysm.is_open_air", "ysm.block_light", "ysm.sky_light");
        context.entity = null;
        for (String query : queries) {
            require(eval(engine, context, query + ";") == null, "world query requires an entity: " + query);
        }
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        FixtureLevel world = (FixtureLevel) ((Unsafe) unsafeField.get(null)).allocateInstance(FixtureLevel.class);
        Field level = Entity.class.getDeclaredField("level");
        Field blockPosition = Entity.class.getDeclaredField("blockPosition");
        level.setAccessible(true);
        blockPosition.setAccessible(true);
        Object previousLevel = level.get(entity);
        Object previousPosition = blockPosition.get(entity);
        BlockPos location = new BlockPos(-1, 64, 2);
        try {
            context.entity = entity;
            blockPosition.set(entity, location);
            level.set(entity, world);
            entity.water = false;
            context.is_in_water_or_rain = 1f;
            require(Boolean.FALSE.equals(eval(engine, context, "q.is_in_water_or_rain;")),
                    "dry world query ignores a stale wet cache");
            require(location.equals(world.weatherPosition), "rain query reads the subject block position");
            world.rainingAtSubject = true;
            require(Boolean.TRUE.equals(eval(engine, context, "q.is_in_water_or_rain;")), "subject rain changes immediately");
            int weatherReads = world.weatherReads;
            entity.water = true;
            world.rainingAtSubject = false;
            require(Boolean.TRUE.equals(eval(engine, context, "q.is_in_water_or_rain;"))
                            && world.weatherReads == weatherReads,
                    "water short circuits the rain lookup");
            world.blockLight = 7;
            world.skyLight = 13;
            require(Integer.valueOf(7).equals(eval(engine, context, "ysm.block_light;")), "block light returns raw Integer");
            require(Integer.valueOf(13).equals(eval(engine, context, "ysm.sky_light;")), "sky light uses its independent layer");
            require(location.equals(world.lightPosition), "light query uses subject location");
            world.blockLight = 0;
            world.skyLight = 15;
            require(Integer.valueOf(0).equals(eval(engine, context, "ysm.block_light;"))
                            && Integer.valueOf(15).equals(eval(engine, context, "ysm.sky_light;")),
                    "native light boundaries update without cached values");
            world.seesSky = false;
            require(Boolean.FALSE.equals(eval(engine, context, "ysm.is_open_air;")) && world.heightReads == 0,
                    "closed sky short circuits heightmap lookup");
            world.seesSky = true;
            world.height = 65;
            require(Boolean.FALSE.equals(eval(engine, context, "ysm.is_open_air;")),
                    "visible sky below motion-blocking height is not open air");
            world.height = 64;
            require(Boolean.TRUE.equals(eval(engine, context, "ysm.is_open_air;")), "equal motion-blocking height is open air");
            world.height = 63;
            require(Boolean.TRUE.equals(eval(engine, context, "ysm.is_open_air;")), "above motion-blocking height is open air");
            require(world.heightmapType == net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING
                            && location.equals(world.heightPosition), "open-air query uses the reference heightmap and location");
            level.set(entity, null);
            require(Boolean.TRUE.equals(eval(engine, context, "q.is_in_water_or_rain;")),
                    "water short circuit does not require a world");
            entity.water = false;
            for (String query : queries) {
                require(eval(engine, context, query + ";") == null, "missing world does not invent a value: " + query);
            }
            Avatar.MolangContext other = new Avatar.MolangContext(null);
            for (String query : queries) {
                require(eval(engine, other, query + ";") == null, "world query stays scoped to its entity: " + query);
            }
        } finally {
            level.set(entity, previousLevel);
            blockPosition.set(entity, previousPosition);
            entity.water = false;
            context.entity = entity;
        }
    }

    private static void testEntityStateQueries(MolangEngine engine, Avatar.MolangContext context,
                                               FixtureEntity entity) throws Exception {
        List<String> names = List.of("is_on_ground", "is_sneaking", "is_sprinting", "is_swimming",
                "is_in_water", "is_on_fire", "is_riding", "has_rider", "is_sleeping", "is_spectator",
                "is_playing_dead");
        ObjectBinding registry = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("query");
        context.entity = null;
        for (String name : names) {
            require(registry.getProperty(name) instanceof Variable && !(registry.getProperty(name) instanceof Function),
                    "entity state query is a scalar Variable: " + name);
            require(eval(engine, context, "q." + name + ";") == null, "entity state query missing entity: " + name);
        }
        Field passengers = Entity.class.getDeclaredField("passengers");
        passengers.setAccessible(true);
        Object previousPassengers = passengers.get(entity);
        try {
            context.entity = entity;
            for (boolean active : new boolean[]{false, true, false}) {
                entity.grounded = active;
                entity.pose = active ? Pose.CROUCHING : Pose.STANDING;
                entity.sprinting = active;
                entity.swimming = active;
                entity.water = active;
                entity.fixtureFire = active;
                entity.vehicle = active ? entity : null;
                passengers.set(entity, active ? com.google.common.collect.ImmutableList.of(entity)
                        : com.google.common.collect.ImmutableList.of());
                entity.sleeping = active;
                entity.fixtureSpectator = active;
                entity.dead = active;
                // Opposite cache values must not replace the current entity state.
                context.is_on_ground = context.is_sneaking = context.is_sprinting = active ? 0f : 1f;
                context.is_swimming = context.is_in_water = context.is_on_fire = active ? 0f : 1f;
                context.is_riding = context.has_rider = context.is_sleeping = active ? 0f : 1f;
                context.is_spectator = context.is_playing_dead = active ? 0f : 1f;
                for (String name : names) {
                    require(Boolean.valueOf(active).equals(eval(engine, context, "q." + name + ";")),
                            "state query returns live raw Boolean: " + name + "/" + active);
                }
            }
            entity.pose = Pose.CROUCHING;
            entity.grounded = false;
            require(Boolean.FALSE.equals(eval(engine, context, "q.is_sneaking;")), "airborne crouching is not reference sneak");
            entity.pose = Pose.STANDING;
            entity.grounded = true;
            require(Boolean.FALSE.equals(eval(engine, context, "q.is_sneaking;")), "grounded standing is not reference sneak");
            entity.vehicle = entity;
            require(Boolean.TRUE.equals(eval(engine, context, "q.is_riding;"))
                            && Boolean.FALSE.equals(eval(engine, context, "q.has_rider;")),
                    "riding and carrying passengers are independent");
            entity.vehicle = null;
            passengers.set(entity, com.google.common.collect.ImmutableList.of(entity));
            require(Boolean.FALSE.equals(eval(engine, context, "q.is_riding;"))
                            && Boolean.TRUE.equals(eval(engine, context, "q.has_rider;")),
                    "native passenger list does not imply this entity is riding");
            Object numeric = eval(engine, context, "q.has_rider+0;");
            require(numeric instanceof Float value && value == 1f,
                    "raw Boolean converts to the interpreter's numeric Float in arithmetic");
            Avatar.MolangContext other = new Avatar.MolangContext(null);
            context.entity = null;
            for (String name : names) {
                require(eval(engine, other, "q." + name + ";") == null
                                && eval(engine, context, "q." + name + ";") == null,
                        "state query isolation and removed entity cleanup: " + name);
            }
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            context.entity = (net.minecraft.world.entity.item.ItemEntity)
                    ((Unsafe) unsafeField.get(null)).allocateInstance(net.minecraft.world.entity.item.ItemEntity.class);
            require(Boolean.FALSE.equals(eval(engine, context, "q.is_on_ground;")),
                    "entity-level query accepts non-living native entity");
            require(eval(engine, context, "q.is_sleeping;") == null
                            && eval(engine, context, "q.is_playing_dead;") == null,
                    "living-only state queries reject non-living entity");
        } finally {
            passengers.set(entity, previousPassengers);
            entity.vehicle = null;
            entity.pose = Pose.STANDING;
            entity.grounded = false;
            context.entity = entity;
        }
    }

    private static void testEntityMotionQueries(MolangEngine engine, Avatar.MolangContext context,
                                                FixtureEntity entity) throws Exception {
        List<String> names = List.of("walk_distance", "modified_distance_moved", "eye_target_x_rotation",
                "eye_target_y_rotation", "cardinal_facing_2d");
        ObjectBinding registry = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("query");
        context.entity = null;
        for (String name : names) {
            require(registry.getProperty(name) instanceof Variable && !(registry.getProperty(name) instanceof Function),
                    "entity motion query is a scalar Variable: " + name);
            require(eval(engine, context, "q." + name + ";") == null, "entity motion query null gate: " + name);
        }
        context.entity = entity;
        context.ysm_partial_tick = 0.25f;
        context.walk_distance = context.modified_distance_moved = -100f;
        context.eye_target_x_rot = context.eye_target_y_rot = -100f;
        context.cardinal_facing_2d = -100;
        entity.moveDist = 7.25f;
        entity.xRotO = 10f;
        entity.setXRot(30f);
        entity.yHeadRotO = 170f;
        entity.yHeadRot = 190f;
        entity.setYRot(0f);
        Map<String, Object> expected = Map.of("walk_distance", 7.25f, "modified_distance_moved", 7.25f,
                "eye_target_x_rotation", 15f, "eye_target_y_rotation", 175f, "cardinal_facing_2d", 3);
        for (var entry : expected.entrySet()) {
            Object value = eval(engine, context, "q." + entry.getKey() + ";");
            require(entry.getValue().equals(value) && value.getClass() == entry.getValue().getClass(),
                    "entity motion query raw type/native fixed vector: " + entry.getKey());
        }
        entity.moveDist = 2f;
        entity.setYRot(90f);
        context.ysm_partial_tick = 1f;
        require(Float.valueOf(2f).equals(eval(engine, context, "q.walk_distance;")), "native moveDist changes immediately");
        require(Float.valueOf(2f).equals(eval(engine, context, "q.modified_distance_moved;")), "distance names share moveDist");
        require(Float.valueOf(30f).equals(eval(engine, context, "q.eye_target_x_rotation;")), "view pitch endpoint");
        require(Float.valueOf(190f).equals(eval(engine, context, "q.eye_target_y_rotation;")), "living view yaw uses head endpoint");
        require(Integer.valueOf(4).equals(eval(engine, context, "q.cardinal_facing_2d;")), "west uses reference 3D direction ordinal");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Entity nonLiving = (net.minecraft.world.entity.item.ItemEntity)
                ((Unsafe) unsafeField.get(null)).allocateInstance(net.minecraft.world.entity.item.ItemEntity.class);
        nonLiving.moveDist = 3f;
        nonLiving.setXRot(20f);
        nonLiving.setYRot(180f);
        context.entity = nonLiving;
        require(Float.valueOf(3f).equals(eval(engine, context, "q.walk_distance;")), "non-living Entity retains motion queries");
        require(Float.valueOf(20f).equals(eval(engine, context, "q.eye_target_x_rotation;")), "non-living Entity view query");
        require(Integer.valueOf(2).equals(eval(engine, context, "q.cardinal_facing_2d;")), "non-living north direction");
        Avatar.MolangContext other = new Avatar.MolangContext(null);
        context.reset();
        for (String name : names) {
            require(eval(engine, other, "q." + name + ";") == null && eval(engine, context, "q." + name + ";") == null,
                    "entity motion values do not survive context reset or leak to another context: " + name);
        }
        context.entity = entity;
    }

    private static void testLivingQueries(MolangEngine engine, Avatar.MolangContext context,
                                          FixtureEntity entity) throws Exception {
        List<String> names = List.of("health", "max_health", "hurt_time", "body_x_rotation", "body_y_rotation");
        ObjectBinding registry = (ObjectBinding) Avatar.createMolangBindings(context).getProperty("query");
        context.entity = null;
        for (String name : names) {
            require(registry.getProperty(name) instanceof Variable && !(registry.getProperty(name) instanceof Function),
                    "living query is a scalar Variable: " + name);
            require(eval(engine, context, "q." + name + ";") == null, "living query missing entity gate: " + name);
        }
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        context.entity = (net.minecraft.world.entity.item.ItemEntity)
                ((Unsafe) unsafeField.get(null)).allocateInstance(net.minecraft.world.entity.item.ItemEntity.class);
        for (String name : names) {
            require(eval(engine, context, "q." + name + ";") == null, "living query rejects non-living Entity: " + name);
        }
        var attributes = new net.minecraft.world.entity.ai.attributes.AttributeMap(
                net.minecraft.world.entity.LivingEntity.createLivingAttributes().build());
        attributes.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40d);
        Field attributeField = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attributes");
        attributeField.setAccessible(true);
        attributeField.set(entity, attributes);
        entity.fixtureHealth = 7.5f;
        entity.hurtTime = 6;
        entity.xRotO = 10f;
        entity.setXRot(30f);
        entity.yBodyRotO = 350f;
        entity.yBodyRot = 370f;
        context.updateYsmPositionContext(entity);
        context.ysm_partial_tick = 0.25f;
        context.health = -100f;
        context.max_health = -100f;
        context.hurt_time = -100f;
        context.body_x_rot = context.body_y_rot = -100f;
        Map<String, Object> expected = Map.of("health", 7.5f, "max_health", 40f, "hurt_time", 6,
                "body_x_rotation", 15f, "body_y_rotation", -5f);
        for (var entry : expected.entrySet()) {
            Object value = eval(engine, context, "q." + entry.getKey() + ";");
            require(entry.getValue().equals(value) && value.getClass() == entry.getValue().getClass(),
                    "living query raw type and current native value: " + entry.getKey());
        }
        entity.fixtureHealth = 2f;
        attributes.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(24d);
        entity.hurtTime = 0;
        context.ysm_partial_tick = 1f;
        require(Float.valueOf(2f).equals(eval(engine, context, "q.health;")), "health changes without populateQueryContext");
        require(Float.valueOf(24f).equals(eval(engine, context, "q.max_health;")), "max health reads the native attribute map");
        require(Integer.valueOf(0).equals(eval(engine, context, "q.hurt_time;")), "hurt time retains raw Integer zero");
        require(Float.valueOf(30f).equals(eval(engine, context, "q.body_x_rotation;")), "pitch reads changed frame fraction");
        require(Float.valueOf(10f).equals(eval(engine, context, "q.body_y_rotation;")), "body yaw wraps degrees");
        Avatar.MolangContext other = new Avatar.MolangContext(null);
        for (String name : names) {
            require(eval(engine, other, "q." + name + ";") == null, "living query context isolation: " + name);
        }
        context.updateYsmPositionContext(null);
        for (String name : names) {
            require(eval(engine, context, "q." + name + ";") == null, "removed entity does not leak native values: " + name);
        }
        context.entity = entity;
    }

    private static final class FixtureEntity extends ArmorStand {
        private float fixtureHealth;
        private boolean fixtureFire;
        private boolean fixtureSpectator;
        private boolean fixtureEyeWater;
        private int fixtureFrozen;
        private int fixtureAir;
        private int fixtureUseTicks;
        private int fixtureRemainingTicks;
        private ItemStack chest;
        private Map<EquipmentSlot, ItemStack> equipment;
        private UUID id;
        private boolean sleeping;
        private Pose pose;
        private boolean climbable;
        private boolean swimming;
        private boolean dead;
        private boolean spinAttack;
        private Entity vehicle;
        private Entity passenger;
        private EntityType<?> entityType;
        private boolean alive;
        private boolean grounded;
        private boolean water;
        private boolean sprinting;
        private boolean elytraFlying;
        private boolean using;
        private net.minecraft.world.InteractionHand usedHand;
        private float attackProgress;

        private FixtureEntity() {
            super(EntityType.ARMOR_STAND, null);
        }

        @Override
        public float getHealth() {
            return fixtureHealth;
        }

        @Override
        public boolean isOnFire() {
            return fixtureFire;
        }

        @Override
        public boolean isSpectator() {
            return fixtureSpectator;
        }

        @Override
        public boolean isUnderWater() {
            return fixtureEyeWater;
        }

        @Override
        public int getTicksFrozen() {
            return fixtureFrozen;
        }

        @Override
        public int getAirSupply() {
            return fixtureAir;
        }

        @Override
        public int getTicksUsingItem() {
            return fixtureUseTicks;
        }

        @Override
        public int getUseItemRemainingTicks() {
            return fixtureRemainingTicks;
        }

        @Override
        public ItemStack getItemBySlot(EquipmentSlot slot) {
            if (equipment != null && equipment.containsKey(slot)) {
                return equipment.get(slot);
            }
            return slot == EquipmentSlot.CHEST ? chest : ItemStack.EMPTY;
        }

        @Override
        public UUID getUUID() {
            return id;
        }

        @Override
        public boolean isSleeping() {
            return sleeping;
        }

        @Override
        public Pose getPose() {
            return pose;
        }

        @Override
        public boolean onClimbable() {
            return climbable;
        }

        @Override
        public boolean isSwimming() {
            return swimming;
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
        public Entity getVehicle() {
            return vehicle;
        }

        @Override
        public Entity getFirstPassenger() {
            return passenger;
        }

        @Override
        public EntityType<?> getType() {
            return entityType == null ? EntityType.ARMOR_STAND : entityType;
        }

        @Override
        public boolean isAlive() {
            return alive;
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
            return elytraFlying;
        }

        @Override
        public boolean isUsingItem() {
            return using;
        }

        @Override
        public net.minecraft.world.InteractionHand getUsedItemHand() {
            return usedHand;
        }

        @Override
        public ItemStack getUseItem() {
            return usedHand == null ? ItemStack.EMPTY : getItemInHand(usedHand);
        }

        @Override
        public float getAttackAnim(float partialTick) {
            return attackProgress;
        }
    }

    private static final class FixtureLevel extends ClientLevel {
        private boolean raining;
        private boolean thundering;
        private ResourceKey<net.minecraft.world.level.Level> dimensionKey;

        @Override
        public boolean isRaining() {
            return raining;
        }

        @Override
        public boolean isThundering() {
            return thundering;
        }

        @Override
        public ResourceKey<net.minecraft.world.level.Level> dimension() {
            return dimensionKey;
        }

        private int actorCount;

        @Override
        public int getEntityCount() {
            return actorCount;
        }

        private Holder<net.minecraft.world.level.biome.Biome> biome;
        private BlockPos biomePosition;
        private boolean rainingAtSubject;
        private int weatherReads;
        private BlockPos weatherPosition;
        private int blockLight;
        private int skyLight;
        private BlockPos lightPosition;
        private boolean seesSky;
        private int height;
        private int heightReads;
        private BlockPos heightPosition;
        private net.minecraft.world.level.levelgen.Heightmap.Types heightmapType;
        private BlockState block;
        private BlockPos position;
        private int reads;
        private RegistryAccess registries;

        private FixtureLevel() {
            super(null, null, null, null, 0, 0, null, false, 0L, 0);
        }

        @Override
        public Holder<net.minecraft.world.level.biome.Biome> getBiome(BlockPos position) {
            biomePosition = position.immutable();
            return biome;
        }

        @Override
        public boolean isRainingAt(BlockPos position) {
            weatherPosition = position.immutable();
            weatherReads++;
            return rainingAtSubject;
        }

        @Override
        public int getBrightness(net.minecraft.world.level.LightLayer layer, BlockPos position) {
            lightPosition = position.immutable();
            return layer == net.minecraft.world.level.LightLayer.BLOCK ? blockLight : skyLight;
        }

        @Override
        public boolean canSeeSky(BlockPos position) {
            return seesSky;
        }

        @Override
        public BlockPos getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types type, BlockPos position) {
            heightPosition = position.immutable();
            heightmapType = type;
            heightReads++;
            return new BlockPos(position.getX(), height, position.getZ());
        }

        @Override
        public BlockState getBlockState(BlockPos position) {
            this.position = position.immutable();
            reads++;
            return block;
        }

        @Override
        public RegistryAccess registryAccess() {
            return registries;
        }
    }
}
