package org.figuramc.figura.molang.runtime.binding;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.molang.runtime.ExecutionContext;
import org.figuramc.figura.molang.runtime.Function;
import org.figuramc.figura.molang.runtime.Variable;

/**
 * Molang query.* variable and function implementations.
 *
 * Each variable extracts a value from the {@link Avatar.MolangContext}
 * at evaluation time via the evaluation context's {@code entity()}.
 *
 * <p>The MolangContext fields are populated every frame by
 * {@link Avatar.MolangContext#populateQueryContext(net.minecraft.world.entity.Entity)}.</p>
 *
 * <p>This class covers 48+ query variables commonly used in Molang expressions,
 * matching the Bedrock Molang specification and YSM's implementation.</p>
 *
 * Reference: YSM QueryBinding
 */
public final class QueryVariables {

    private QueryVariables() {
    }

    // ===== Helper =====

    private static Avatar.MolangContext mc(ExecutionContext<?> ctx) {
        return ctx.entity() instanceof Avatar.MolangContext m ? m : null;
    }

    // ===== Time =====

    public static final Variable ANIM_TIME = ctx -> {
        var m = mc(ctx); return m != null ? m.anim_time : 0f;
    };

    public static final Variable LIFE_TIME = ctx -> {
        var m = mc(ctx); return m != null ? m.life_time : 0f;
    };

    public static final Variable DELTA_TIME = ctx -> {
        var m = mc(ctx); return m != null ? m.delta_time : 0f;
    };

    public static final Variable TIME_OF_DAY = ctx -> {
        var m = mc(ctx); return m != null ? m.time_of_day : 0f;
    };

    public static final Variable TIME_STAMP = ctx -> {
        var m = mc(ctx); return m != null ? m.time_stamp : 0f;
    };

    public static final Variable MOON_PHASE = ctx -> {
        var m = mc(ctx); return m != null ? m.moon_phase : 0f;
    };

    public static final Variable FRAME_COUNT = ctx -> {
        var m = mc(ctx); return m != null ? (float) m.frame_count : 0f;
    };

    // ===== Actor =====

    public static final Variable ACTOR_COUNT = ctx -> 1f;

    // ===== Health =====

    public static final Variable HEALTH = ctx -> {
        var m = mc(ctx); return m != null ? m.health : 20f;
    };

    public static final Variable MAX_HEALTH = ctx -> {
        var m = mc(ctx); return m != null ? m.max_health : 20f;
    };

    public static final Variable HURT_TIME = ctx -> {
        var m = mc(ctx); return m != null ? m.hurt_time : 0f;
    };

    // ===== Position =====

    /** query.position(0/1/2) — returns x/y/z coordinate as a function with axis argument */
    public static final Function POSITION_FUNC = (ctx, args) -> {
        var m = mc(ctx); if (m == null) return 0f;
        int axis = args.getAsInt(ctx, 0);
        return switch (axis) {
            case 0 -> (float) m.pos_x;
            case 1 -> (float) m.pos_y;
            case 2 -> (float) m.pos_z;
            default -> 0f;
        };
    };

    /** query.position_delta(0/1/2) — returns x/y/z position delta as a function with axis argument */
    public static final Function POSITION_DELTA_FUNC = (ctx, args) -> {
        var m = mc(ctx); if (m == null) return 0f;
        int axis = args.getAsInt(ctx, 0);
        return switch (axis) {
            case 0 -> (float) m.pos_delta_x;
            case 1 -> (float) m.pos_delta_y;
            case 2 -> (float) m.pos_delta_z;
            default -> 0f;
        };
    };

    public static final Variable POSITION_X = ctx -> {
        var m = mc(ctx); return m != null ? (float) m.pos_x : 0f;
    };

    public static final Variable POSITION_Y = ctx -> {
        var m = mc(ctx); return m != null ? (float) m.pos_y : 0f;
    };

    public static final Variable POSITION_Z = ctx -> {
        var m = mc(ctx); return m != null ? (float) m.pos_z : 0f;
    };

    public static final Variable POSITION_DELTA_X = ctx -> {
        var m = mc(ctx); return m != null ? (float) m.pos_delta_x : 0f;
    };

    public static final Variable POSITION_DELTA_Y = ctx -> {
        var m = mc(ctx); return m != null ? (float) m.pos_delta_y : 0f;
    };

    public static final Variable POSITION_DELTA_Z = ctx -> {
        var m = mc(ctx); return m != null ? (float) m.pos_delta_z : 0f;
    };

    public static final Variable DISTANCE_FROM_CAMERA = ctx -> {
        var m = mc(ctx); return m != null ? m.distance_from_camera : 0f;
    };

    /** query.rotation_to_camera(0/1) — 0 = xRot, 1 = yRot of the active camera */
    public static final Function ROTATION_TO_CAMERA_FUNC = (ctx, args) -> {
        var mc2 = Minecraft.getInstance();
        if (mc2.gameRenderer == null || mc2.gameRenderer.getMainCamera() == null) return 0f;
        var cam = mc2.gameRenderer.getMainCamera();
        int axis = args.getAsInt(ctx, 0);
        return switch (axis) {
            case 0 -> cam.xRot();
            case 1 -> cam.yRot();
            default -> 0f;
        };
    };

    // ===== Movement =====

    public static final Variable GROUND_SPEED = ctx -> {
        var m = mc(ctx); return m != null ? m.ground_speed : 0f;
    };

    public static final Variable VERTICAL_SPEED = ctx -> {
        var m = mc(ctx); return m != null ? m.vertical_speed : 0f;
    };

    public static final Variable YAW_SPEED = ctx -> {
        var m = mc(ctx); return m != null ? m.yaw_speed : 0f;
    };

    public static final Variable WALK_DISTANCE = ctx -> {
        var m = mc(ctx); return m != null ? m.walk_distance : 0f;
    };

    public static final Variable MODIFIED_DISTANCE_MOVED = ctx -> {
        var m = mc(ctx); return m != null ? m.modified_distance_moved : 0f;
    };

    // ===== Rotation =====

    public static final Variable BODY_X_ROTATION = ctx -> {
        var m = mc(ctx); return m != null ? m.body_x_rot : 0f;
    };

    public static final Variable BODY_Y_ROTATION = ctx -> {
        var m = mc(ctx); return m != null ? m.body_y_rot : 0f;
    };

    public static final Variable HEAD_X_ROTATION = ctx -> {
        var m = mc(ctx); return m != null ? m.head_x_rot : 0f;
    };

    public static final Variable HEAD_Y_ROTATION = ctx -> {
        var m = mc(ctx); return m != null ? m.head_y_rot : 0f;
    };

    public static final Variable EYE_TARGET_X_ROTATION = ctx -> {
        var m = mc(ctx); return m != null ? m.eye_target_x_rot : 0f;
    };

    public static final Variable EYE_TARGET_Y_ROTATION = ctx -> {
        var m = mc(ctx); return m != null ? m.eye_target_y_rot : 0f;
    };

    public static final Variable CARDINAL_FACING_2D = ctx -> {
        var m = mc(ctx); return m != null ? m.cardinal_facing_2d : 0f;
    };

    // ===== Boolean states =====

    public static final Variable IS_ON_GROUND = ctx -> {
        var m = mc(ctx); return m != null ? m.is_on_ground : 0f;
    };

    public static final Variable IS_JUMPING = ctx -> {
        var m = mc(ctx); return m != null ? m.is_jumping : 0f;
    };

    public static final Variable IS_SNEAKING = ctx -> {
        var m = mc(ctx); return m != null ? m.is_sneaking : 0f;
    };

    public static final Variable IS_SPRINTING = ctx -> {
        var m = mc(ctx); return m != null ? m.is_sprinting : 0f;
    };

    public static final Variable IS_SWIMMING = ctx -> {
        var m = mc(ctx); return m != null ? m.is_swimming : 0f;
    };

    public static final Variable IS_IN_WATER = ctx -> {
        var m = mc(ctx); return m != null ? m.is_in_water : 0f;
    };

    public static final Variable IS_IN_WATER_OR_RAIN = ctx -> {
        var m = mc(ctx); return m != null ? m.is_in_water_or_rain : 0f;
    };

    public static final Variable IS_ON_FIRE = ctx -> {
        var m = mc(ctx); return m != null ? m.is_on_fire : 0f;
    };

    public static final Variable IS_RIDING = ctx -> {
        var m = mc(ctx); return m != null ? m.is_riding : 0f;
    };

    public static final Variable HAS_RIDER = ctx -> {
        var m = mc(ctx); return m != null ? m.has_rider : 0f;
    };

    public static final Variable IS_SLEEPING = ctx -> {
        var m = mc(ctx); return m != null ? m.is_sleeping : 0f;
    };

    public static final Variable IS_SPECTATOR = ctx -> {
        var m = mc(ctx); return m != null ? m.is_spectator : 0f;
    };

    public static final Variable IS_FIRST_PERSON = ctx -> {
        var m = mc(ctx); return m != null ? m.is_first_person : 0f;
    };

    public static final Variable RENDERING_IN_PAPERDOLL = ctx -> {
        var m = mc(ctx); return m != null ? m.rendering_in_paperdoll : 0f;
    };

    public static final Variable RENDERING_IN_INVENTORY = ctx -> {
        var m = mc(ctx); return m != null ? m.rendering_in_inventory : 0f;
    };

    public static final Variable IS_USING_ITEM = ctx -> {
        var m = mc(ctx); return m != null ? m.is_using_item : 0f;
    };

    public static final Variable IS_SWINGING = ctx -> {
        var m = mc(ctx); return m != null ? m.is_swinging : 0f;
    };

    public static final Variable IS_EATING = ctx -> {
        var m = mc(ctx); return m != null ? m.is_eating : 0f;
    };

    public static final Variable IS_PLAYING_DEAD = ctx -> {
        var m = mc(ctx); return m != null ? m.is_playing_dead : 0f;
    };

    public static final Variable HAS_CAPE = ctx -> {
        var m = mc(ctx); return m != null ? m.has_cape : 0f;
    };

    // ===== Animation state =====

    public static final Variable ALL_ANIMATIONS_FINISHED = ctx -> {
        var m = mc(ctx); return m != null ? (m.all_animations_finished != 0f ? m.all_animations_finished : m.anim_time >= m.life_time ? 1f : 0f) : 0f;
    };

    public static final Variable ANY_ANIMATION_FINISHED = ctx -> {
        var m = mc(ctx); return m != null ? (m.any_animation_finished != 0f ? m.any_animation_finished : m.anim_time >= m.life_time ? 1f : 0f) : 0f;
    };

    public static final Variable SWING_TIME = ctx -> {
        var m = mc(ctx); return m != null ? m.swing_time : 0f;
    };

    public static final Variable ATTACK_TIME = ctx -> {
        var m = mc(ctx); return m != null ? m.attack_time : 0f;
    };

    public static final Function CONTROL_VALUE = (ctx, args) -> {
        var m = mc(ctx);
        if (m == null || m.owner == null || args.size() < 1)
            return 0f;
        return valueAsFloat(m.owner.controls.getValue(args.getAsString(ctx, 0)));
    };

    public static final Function YSM_CONTROL_VALUE = CONTROL_VALUE;
    public static final Function AVATAR_CONTROL_VALUE = CONTROL_VALUE;

    public static final Function YSM_CONTROL_BOOL = (ctx, args) -> valueAsFloat(CONTROL_VALUE.evaluate(ctx, args)) != 0f ? 1f : 0f;
    public static final Function YSM_CONTROL_NUMBER = CONTROL_VALUE;

    public static final Function YSM_CONTROL_ENUM = (ctx, args) -> {
        var m = mc(ctx);
        if (m == null || m.owner == null || args.size() < 1)
            return 0f;
        var control = m.owner.controls.get(args.getAsString(ctx, 0));
        if (control == null)
            return 0f;
        Object value = control.getValue();
        if (value == null)
            return 0f;
        int index = control.options().indexOf(value.toString());
        return index < 0 ? valueAsFloat(value) : (float) index;
    };

    public static final Function YSM_ACTION_ACTIVE = (ctx, args) -> {
        var m = mc(ctx);
        if (m == null || m.owner == null || m.owner.getYsmRuntime() == null || args.size() < 1)
            return 0f;
        return m.owner.getYsmRuntime().actions().isActive(args.getAsString(ctx, 0)) ? 1f : 0f;
    };

    public static final Function YSM_ACTION_TIME = (ctx, args) -> {
        var m = mc(ctx);
        if (m == null || m.owner == null || m.owner.getYsmRuntime() == null || args.size() < 1)
            return 0f;
        return m.owner.getYsmRuntime().actions().time(args.getAsString(ctx, 0));
    };

    // ===== Item =====

    public static final Variable ITEM_IN_USE_DURATION = ctx -> {
        var m = mc(ctx); return m != null ? m.item_in_use_duration : 0f;
    };

    public static final Variable ITEM_MAX_USE_DURATION = ctx -> {
        var m = mc(ctx); return m != null ? m.item_max_use_duration : 0f;
    };

    public static final Variable ITEM_REMAINING_USE_DURATION = ctx -> {
        var m = mc(ctx); return m != null ? m.item_remaining_use_duration : 0f;
    };

    public static final Variable EQUIPMENT_COUNT = ctx -> {
        var m = mc(ctx); return m != null ? m.equipment_count : 0f;
    };

    // ===== Durability queries =====

    /**
     * query.max_durability(slot) — returns max damage of item in the given equipment slot.
     * Slot: 0=main hand, 1=offhand, 2=feet, 3=legs, 4=chest, 5=head
     */
    public static final Function MAX_DURABILITY = (ctx, args) -> {
        if (args.size() < 1) return 0f;
        ItemStack stack = getItemInSlot(ctx, args.getAsInt(ctx, 0));
        return stack != null ? (float) stack.getMaxDamage() : 0f;
    };

    /**
     * query.remaining_durability(slot) — returns remaining durability (max - damage).
     * Slot: 0=main hand, 1=offhand, 2=feet, 3=legs, 4=chest, 5=head
     */
    public static final Function REMAINING_DURABILITY = (ctx, args) -> {
        if (args.size() < 1) return 0f;
        ItemStack stack = getItemInSlot(ctx, args.getAsInt(ctx, 0));
        return stack != null ? (float) (stack.getMaxDamage() - stack.getDamageValue()) : 0f;
    };

    // ===== Biome tag queries =====

    /**
     * query.biome_has_all_tags(tag1, tag2, ...) — 1 if the entity's current biome has ALL given tags.
     * Tags are resource location strings like "minecraft:is_ocean" or "is_ocean".
     */
    public static final Function BIOME_HAS_ALL_TAGS = (ctx, args) -> {
        var mc = mc(ctx);
        if (mc == null || mc.entity == null) return 0f;
        Level level = Minecraft.getInstance().level;
        if (level == null) return 0f;
        Biome biome = level.getBiome(mc.entity.blockPosition()).value();
        for (int i = 0; i < args.size(); i++) {
            if (!biomeHasTag(level, biome, args.getAsString(ctx, i)))
                return 0f;
        }
        return 1f;
    };

    /**
     * query.biome_has_any_tag(tag1, tag2, ...) — 1 if the entity's current biome has ANY given tag.
     */
    public static final Function BIOME_HAS_ANY_TAG = (ctx, args) -> {
        var mc = mc(ctx);
        if (mc == null || mc.entity == null) return 0f;
        Level level = Minecraft.getInstance().level;
        if (level == null) return 0f;
        Biome biome = level.getBiome(mc.entity.blockPosition()).value();
        for (int i = 0; i < args.size(); i++) {
            if (biomeHasTag(level, biome, args.getAsString(ctx, i)))
                return 1f;
        }
        return 0f;
    };

    // ===== Block tag queries =====

    /**
     * query.relative_block_has_all_tags(x, y, z, tag1, tag2, ...) — 1 if block at entity + offset has ALL tags.
     * First 3 args are offset integers, rest are tag resource location strings.
     */
    public static final Function RELATIVE_BLOCK_HAS_ALL_TAGS = (ctx, args) -> {
        var mc = mc(ctx);
        if (mc == null || mc.entity == null || args.size() < 4) return 0f;
        Level level = Minecraft.getInstance().level;
        if (level == null) return 0f;
        BlockPos pos = mc.entity.blockPosition().offset(args.getAsInt(ctx, 0), args.getAsInt(ctx, 1), args.getAsInt(ctx, 2));
        if (!level.hasChunkAt(pos)) return 0f;
        BlockState state = level.getBlockState(pos);
        for (int i = 3; i < args.size(); i++) {
            if (!blockHasTag(level, state, args.getAsString(ctx, i)))
                return 0f;
        }
        return 1f;
    };

    /**
     * query.relative_block_has_any_tag(x, y, z, tag1, tag2, ...) — 1 if block at offset has ANY tag.
     */
    public static final Function RELATIVE_BLOCK_HAS_ANY_TAG = (ctx, args) -> {
        var mc = mc(ctx);
        if (mc == null || mc.entity == null || args.size() < 4) return 0f;
        Level level = Minecraft.getInstance().level;
        if (level == null) return 0f;
        BlockPos pos = mc.entity.blockPosition().offset(args.getAsInt(ctx, 0), args.getAsInt(ctx, 1), args.getAsInt(ctx, 2));
        if (!level.hasChunkAt(pos)) return 0f;
        BlockState state = level.getBlockState(pos);
        for (int i = 3; i < args.size(); i++) {
            if (blockHasTag(level, state, args.getAsString(ctx, i)))
                return 1f;
        }
        return 0f;
    };

    // ===== Item name query =====

    /**
     * query.is_item_name_any("minecraft:stick", "minecraft:diamond", ...) — 1 if main hand item
     * matches any of the given registry names.
     */
    public static final Function IS_ITEM_NAME_ANY = (ctx, args) -> {
        var mc = mc(ctx);
        if (mc == null || mc.entity == null) return 0f;
        if (!(mc.entity instanceof Player player)) return 0f;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return 0f;
        String heldId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        for (int i = 0; i < args.size(); i++) {
            String candidate = args.getAsString(ctx, i);
            if (candidate != null && heldId.equals(candidate))
                return 1f;
        }
        return 0f;
    };

    // ===== Equipment tag queries =====

    /**
     * query.equipped_item_all_tags(slot, tag1, tag2, ...) — 1 if item in slot has ALL given tags.
     * First arg is slot index, rest are tag resource locations.
     */
    public static final Function EQUIPPED_ITEM_ALL_TAGS = (ctx, args) -> {
        if (args.size() < 2) return 0f;
        ItemStack stack = getItemInSlot(ctx, args.getAsInt(ctx, 0));
        if (stack == null || stack.isEmpty()) return 0f;
        Level level = Minecraft.getInstance().level;
        if (level == null) return 0f;
        for (int i = 1; i < args.size(); i++) {
            if (!itemHasTag(level, stack, args.getAsString(ctx, i)))
                return 0f;
        }
        return 1f;
    };

    /**
     * query.equipped_item_any_tag(slot, tag1, tag2, ...) — 1 if item in slot has ANY given tag.
     */
    public static final Function EQUIPPED_ITEM_ANY_TAG = (ctx, args) -> {
        if (args.size() < 2) return 0f;
        ItemStack stack = getItemInSlot(ctx, args.getAsInt(ctx, 0));
        if (stack == null || stack.isEmpty()) return 0f;
        Level level = Minecraft.getInstance().level;
        if (level == null) return 0f;
        for (int i = 1; i < args.size(); i++) {
            if (itemHasTag(level, stack, args.getAsString(ctx, i)))
                return 1f;
        }
        return 0f;
    };

    // ===== Misc =====

    public static final Variable CAPE_FLAP_AMOUNT = ctx -> {
        var m = mc(ctx); return m != null ? m.cape_flap_amount : 0f;
    };

    public static final Variable PLAYER_LEVEL = ctx -> {
        var m = mc(ctx); return m != null ? m.player_level : 0f;
    };

    // ===== Geometry =====

    public static final Variable GEOMETRY_IS_MODEL = ctx -> 1f;
    public static final Variable GEOMETRY_IS_BLOCK = ctx -> 0f;
    public static final Variable GEOMETRY_IS_ENTITY = ctx -> 1f;
    public static final Variable GEOMETRY_IS_FLAT = ctx -> 0f;

    // ===== Functions =====

    public static final Function DEBUG_OUTPUT_FUNC = (ctx, args) -> {
        if (args.size() > 0) {
            String msg = args.getAsString(ctx, 0);
            if (msg != null) {
                org.figuramc.figura.FiguraMod.LOGGER.debug("[Molang Debug] {}", msg);
            }
        }
        return 0f;
    };

    /**
     * Creates a query variable that reads a float field from MolangContext by getter.
     */
    public static Variable fromContextFloat(java.util.function.ToDoubleFunction<Avatar.MolangContext> getter) {
        return ctx -> {
            Avatar.MolangContext m = mc(ctx);
            return m != null ? (float) getter.applyAsDouble(m) : 0f;
        };
    }

    private static float valueAsFloat(Object value) {
        if (value instanceof Boolean bool)
            return bool ? 1f : 0f;
        if (value instanceof Number number)
            return number.floatValue();
        if (value instanceof String string) {
            try {
                return Float.parseFloat(string);
            } catch (NumberFormatException ignored) {
                return 0f;
            }
        }
        return 0f;
    }

    // ===== Helper methods =====

    /**
     * Gets the ItemStack in the given equipment slot for the context's player entity.
     * Slot: 0=main hand, 1=offhand, 2=feet, 3=legs, 4=chest, 5=head
     */
    private static ItemStack getItemInSlot(ExecutionContext<?> ctx, int slot) {
        var m = mc(ctx);
        if (m == null || !(m.entity instanceof Player player)) return null;
        return switch (slot) {
            case 0 -> player.getMainHandItem();
            case 1 -> player.getOffhandItem();
            case 2 -> player.getItemBySlot(EquipmentSlot.FEET);
            case 3 -> player.getItemBySlot(EquipmentSlot.LEGS);
            case 4 -> player.getItemBySlot(EquipmentSlot.CHEST);
            case 5 -> player.getItemBySlot(EquipmentSlot.HEAD);
            default -> ItemStack.EMPTY;
        };
    }

    /**
     * Checks if a biome has a specific tag. The tag string can be a full resource location
     * (e.g. "minecraft:is_ocean") or just a path ("is_ocean").
     */
    private static boolean biomeHasTag(Level level, Biome biome, String tagStr) {
        if (tagStr == null) return false;
        var registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        var key = registry.getResourceKey(biome);
        if (key.isEmpty()) return false;
        var holder = registry.getOrThrow(key.get());
        var tag = createTagKey(Registries.BIOME, tagStr);
        return tag != null && holder.is(tag);
    }

    /**
     * Checks if a block state has a specific tag.
     */
    private static boolean blockHasTag(Level level, BlockState state, String tagStr) {
        if (tagStr == null) return false;
        var registry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        var key = registry.getResourceKey(state.getBlock());
        if (key.isEmpty()) return false;
        var holder = registry.getOrThrow(key.get());
        var tag = createTagKey(Registries.BLOCK, tagStr);
        return tag != null && holder.is(tag);
    }

    /**
     * Checks if an item stack has a specific tag.
     */
    private static boolean itemHasTag(Level level, ItemStack stack, String tagStr) {
        if (tagStr == null || stack.isEmpty()) return false;
        var registry = level.registryAccess().lookupOrThrow(Registries.ITEM);
        var key = registry.getResourceKey(stack.getItem());
        if (key.isEmpty()) return false;
        var holder = registry.getOrThrow(key.get());
        var tag = createTagKey(Registries.ITEM, tagStr);
        return tag != null && holder.is(tag);
    }

    /**
     * Creates a TagKey from a resource location string and a registry ResourceKey.
     * Accepts both "minecraft:is_ocean" and bare "is_ocean" (auto-prepends minecraft:).
     */
    private static <T> TagKey<T> createTagKey(ResourceKey<? extends Registry<T>> registryKey, String tagStr) {
        try {
            Identifier loc;
            if (tagStr.contains(":")) {
                loc = Identifier.parse(tagStr);
            } else {
                loc = Identifier.parse("minecraft:" + tagStr);
            }
            return TagKey.create(registryKey, loc);
        } catch (Exception e) {
            return null;
        }
    }
}
