package top.aihmc.omnimorph.molang.runtime.binding;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.molang.runtime.ExecutionContext;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.Variable;

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

    public static final Variable ACTOR_COUNT = ctx -> {
        Avatar.MolangContext context = mc(ctx);
        return context != null && context.entity != null
                && context.entity.level() instanceof net.minecraft.client.multiplayer.ClientLevel level
                ? level.getEntityCount() : null;
    };

    // ===== Health =====

    public static final Variable HEALTH = livingVariable((context, entity) -> entity.getHealth());
    public static final Variable MAX_HEALTH = livingVariable((context, entity) -> entity.getMaxHealth());
    public static final Variable HURT_TIME = livingVariable((context, entity) -> entity.hurtTime);

    private static Variable livingVariable(
            java.util.function.BiFunction<Avatar.MolangContext, LivingEntity, Object> getter) {
        return execution -> {
            Avatar.MolangContext context = mc(execution);
            if (context == null || !(context.entity instanceof LivingEntity entity)) {
                return null;
            }
            return getter.apply(context, entity);
        };
    }

    private static Variable entityVariable(
            java.util.function.BiFunction<Avatar.MolangContext, Entity, Object> getter) {
        return execution -> {
            Avatar.MolangContext context = mc(execution);
            if (context == null || context.entity == null) {
                return null;
            }
            return getter.apply(context, context.entity);
        };
    }

    // ===== Position =====

    /** query.position(0/1/2) — returns x/y/z coordinate as a function with axis argument */
    public static final Function POSITION_FUNC = positionFunction(false);

    /** query.position_delta(0/1/2) — returns x/y/z position delta as a function with axis argument */
    public static final Function POSITION_DELTA_FUNC = positionFunction(true);

    private static Function positionFunction(boolean delta) {
        return new Function() {
            @Override
            public Object evaluate(ExecutionContext<?> execution, ArgumentCollection arguments) {
                Avatar.MolangContext context = mc(execution);
                if (context == null || context.entity == null) {
                    return null;
                }
                int axis = arguments.getAsInt(execution, 0);
                if (delta) {
                    return switch (axis) {
                        case 0 -> context.pos_delta_x;
                        case 1 -> context.pos_delta_y;
                        case 2 -> context.pos_delta_z;
                        default -> null;
                    };
                }
                var entity = context.entity;
                float partialTick = context.ysm_partial_tick;
                return switch (axis) {
                    case 0 -> net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX());
                    case 1 -> net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY());
                    case 2 -> net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ());
                    default -> null;
                };
            }

            @Override
            public boolean validateArgumentSize(int size) {
                return size == 1;
            }
        };
    }

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

    public static final Variable DISTANCE_FROM_CAMERA = entityVariable((context, entity) -> {
        net.minecraft.client.Camera camera = activeCamera();
        return camera == null ? null : net.minecraft.util.Mth.sqrt((float) entity.distanceToSqr(camera.position()));
    });

    /** query.rotation_to_camera(0/1) — 0 = xRot, 1 = yRot of the active camera */
    public static final Function ROTATION_TO_CAMERA_FUNC = new Function() {
        @Override
        public Object evaluate(ExecutionContext<?> execution, ArgumentCollection arguments) {
            int axis = arguments.getAsInt(execution, 0);
            if (axis < 0 || axis > 1) {
                return null;
            }
            Avatar.MolangContext context = mc(execution);
            if (context != null && context.owner != null
                    && context.owner.renderMode == org.figuramc.figura.model.rendering.EntityRenderMode.PAPERDOLL) {
                return 0f;
            }
            net.minecraft.client.Camera camera = activeCamera();
            return camera == null ? null : axis == 0 ? camera.xRot() : camera.yRot();
        }

        @Override
        public boolean validateArgumentSize(int size) {
            return size == 1;
        }
    };

    private static net.minecraft.client.Camera activeCamera() {
        Minecraft client = Minecraft.getInstance();
        return client == null || client.gameRenderer == null ? null : client.gameRenderer.getMainCamera();
    }

    // ===== Movement =====

    public static final Variable GROUND_SPEED = entityVariable((context, entity) ->
            top.aihmc.omnimorph.model.ysm.YsmMovementQuery.groundSpeed(context));

    public static final Variable VERTICAL_SPEED = entityVariable((context, entity) ->
            top.aihmc.omnimorph.model.ysm.YsmMovementQuery.verticalSpeed(context));

    public static final Variable YAW_SPEED = entityVariable((context, entity) ->
            entity instanceof net.minecraft.client.player.LocalPlayer ? context.ysm_local_yaw_speed
                    : 20f * net.minecraft.util.Mth.wrapDegrees(entity.getYRot() - entity.yRotO));

    public static final Variable WALK_DISTANCE = entityVariable((context, entity) -> entity.moveDist);
    public static final Variable MODIFIED_DISTANCE_MOVED = entityVariable((context, entity) -> entity.moveDist);

    // ===== Rotation =====

    public static final Variable BODY_X_ROTATION = livingVariable((context, entity) ->
            net.minecraft.util.Mth.lerp(context.ysm_partial_tick, entity.xRotO, entity.getXRot()));

    public static final Variable BODY_Y_ROTATION = livingVariable((context, entity) ->
            net.minecraft.util.Mth.wrapDegrees(
                    net.minecraft.util.Mth.lerp(context.ysm_partial_tick, entity.yBodyRotO, entity.yBodyRot)));

    public static final Variable HEAD_X_ROTATION = ctx -> {
        var m = mc(ctx);
        if (m == null) {
            return 0f;
        }
        return m.owner != null && m.owner.isYsmNative() ? m.ysm_head_yaw : m.head_x_rot;
    };

    public static final Variable HEAD_Y_ROTATION = ctx -> {
        var m = mc(ctx);
        if (m == null) {
            return 0f;
        }
        return m.owner != null && m.owner.isYsmNative() ? m.ysm_head_pitch : m.head_y_rot;
    };

    public static final Variable EYE_TARGET_X_ROTATION = entityVariable((context, entity) ->
            entity.getViewXRot(context.ysm_partial_tick));

    public static final Variable EYE_TARGET_Y_ROTATION = entityVariable((context, entity) ->
            entity.getViewYRot(context.ysm_partial_tick));

    public static final Variable CARDINAL_FACING_2D = entityVariable((context, entity) ->
            entity.getDirection().get3DDataValue());

    // ===== Boolean states =====

    public static final Variable IS_ON_GROUND = entityVariable((context, entity) -> entity.onGround());

    public static final Variable IS_JUMPING = ctx -> {
        Avatar.MolangContext context = mc(ctx);
        if (context == null || !(context.entity instanceof Player player)) {
            return null;
        }
        return !player.getAbilities().flying && !player.isPassenger() && !player.onGround() && !player.isInWater();
    };

    public static final Variable IS_SNEAKING = entityVariable((context, entity) ->
            entity.onGround() && entity.getPose() == net.minecraft.world.entity.Pose.CROUCHING);

    public static final Variable IS_SPRINTING = entityVariable((context, entity) -> entity.isSprinting());
    public static final Variable IS_SWIMMING = entityVariable((context, entity) -> entity.isSwimming());
    public static final Variable IS_IN_WATER = entityVariable((context, entity) -> entity.isInWater());

    public static final Variable IS_IN_WATER_OR_RAIN = entityVariable((context, entity) -> {
        if (entity.isInWater()) {
            return true;
        }
        Level level = entity.level();
        return level == null ? null : level.isRainingAt(entity.blockPosition());
    });

    public static final Variable IS_ON_FIRE = entityVariable((context, entity) -> entity.isOnFire());
    public static final Variable IS_RIDING = entityVariable((context, entity) -> entity.isPassenger());
    public static final Variable HAS_RIDER = entityVariable((context, entity) -> entity.isVehicle());
    public static final Variable IS_SLEEPING = livingVariable((context, entity) -> entity.isSleeping());
    public static final Variable IS_SPECTATOR = entityVariable((context, entity) -> entity.isSpectator());

    public static final Variable IS_FIRST_PERSON = entityVariable((context, entity) ->
            context.ysm_camera_type == net.minecraft.client.CameraType.FIRST_PERSON.ordinal());

    public static final Variable RENDERING_IN_PAPERDOLL = ctx -> {
        Avatar.MolangContext context = mc(ctx);
        if (context == null) {
            return 0f;
        }
        if (context.owner != null && context.owner.getYsmRuntime() != null) {
            return context.isYsmGuiPreview() ? 1f : 0f;
        }
        return context.rendering_in_paperdoll;
    };

    public static final Variable RENDERING_IN_INVENTORY = ctx -> {
        Avatar.MolangContext context = mc(ctx);
        if (context == null) {
            return 0f;
        }
        if (context.owner != null && context.owner.getYsmRuntime() != null) {
            return context.isYsmModelPreview() ? 1f : 0f;
        }
        return context.rendering_in_inventory;
    };

    public static final Variable IS_USING_ITEM = livingVariable((context, entity) ->
            top.aihmc.omnimorph.model.ysm.YsmHandQuery.using(context));

    public static final Variable IS_SWINGING = livingVariable((context, entity) ->
            top.aihmc.omnimorph.model.ysm.YsmHandQuery.swinging(context));

    public static final Variable IS_EATING = livingVariable((context, entity) ->
            entity.getUseItem().getUseAnimation() == net.minecraft.world.item.ItemUseAnimation.EAT);

    public static final Variable IS_PLAYING_DEAD = livingVariable((context, entity) -> entity.isDeadOrDying());

    public static final Variable HAS_CAPE = ctx -> {
        Avatar.MolangContext context = mc(ctx);
        if (context == null || !(context.entity instanceof net.minecraft.client.player.AbstractClientPlayer player)) {
            return null;
        }
        return player.getSkin().cape() != null && !player.isInvisible()
                && player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.CAPE);
    };

    // ===== Animation state =====

    public static final Variable ALL_ANIMATIONS_FINISHED = ctx -> {
        Avatar.MolangContext context = mc(ctx);
        return context != null && context.all_animations_finished != 0f;
    };

    public static final Variable ANY_ANIMATION_FINISHED = ctx -> {
        Avatar.MolangContext context = mc(ctx);
        return context != null && context.any_animation_finished != 0f;
    };

    public static final Variable SWING_TIME = livingVariable((context, entity) ->
            top.aihmc.omnimorph.model.ysm.YsmHandQuery.swingTicks(context) / 20d);

    public static final Variable ATTACK_TIME = livingVariable((context, entity) ->
            top.aihmc.omnimorph.model.ysm.YsmHandQuery.attackProgress(context));

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

    public static final Variable ITEM_IN_USE_DURATION = livingVariable((context, entity) ->
            top.aihmc.omnimorph.model.ysm.YsmHandQuery.useTicks(context) / 20d);

    public static final Variable ITEM_MAX_USE_DURATION = livingVariable((context, entity) -> {
        ItemStack stack = entity.getUseItem();
        return stack.isEmpty() ? 0d : stack.getUseDuration(entity) / 20d;
    });

    public static final Variable ITEM_REMAINING_USE_DURATION = livingVariable((context, entity) ->
            top.aihmc.omnimorph.model.ysm.YsmHandQuery.remainingUseTicks(context) / 20d);

    public static final Variable EQUIPMENT_COUNT = livingVariable((context, entity) -> {
        int count = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.isArmor() && !entity.getItemBySlot(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    });

    // ===== Durability queries =====

    /**
     * query.max_durability(slot) — returns max damage of item in the given equipment slot.
     * Reference slots: mainhand/offhand/feet/legs/chest/head; legacy project indices 0..5 are retained.
     */
    public static final Function MAX_DURABILITY = equipmentFunction(EquipmentQuery.MAX_DURABILITY);

    /**
     * query.remaining_durability(slot) — returns remaining durability (max - damage).
     * Reference slots: mainhand/offhand/feet/legs/chest/head; legacy project indices 0..5 are retained.
     */
    public static final Function REMAINING_DURABILITY = equipmentFunction(EquipmentQuery.REMAINING_DURABILITY);

    // ===== Biome tag queries =====

    /**
     * query.biome_has_all_tags(tag1, tag2, ...) — whether the entity's current biome has ALL given tags.
     * Tags are resource location strings like "minecraft:is_ocean" or "is_ocean".
     */
    public static final Function BIOME_HAS_ALL_TAGS = worldTagFunction(false, true);

    /**
     * query.biome_has_any_tag(tag1, tag2, ...) — whether the entity's current biome has ANY given tag.
     */
    public static final Function BIOME_HAS_ANY_TAG = worldTagFunction(false, false);

    // ===== Block tag queries =====

    /**
     * query.relative_block_has_all_tags(x, y, z, tag1, tag2, ...) — whether the relative block has ALL tags.
     * First 3 args are numeric offsets within five blocks, rest are tag resource location strings.
     */
    public static final Function RELATIVE_BLOCK_HAS_ALL_TAGS = worldTagFunction(true, true);

    /**
     * query.relative_block_has_any_tag(x, y, z, tag1, tag2, ...) — whether the relative block has ANY tag.
     */
    public static final Function RELATIVE_BLOCK_HAS_ANY_TAG = worldTagFunction(true, false);

    private static Function worldTagFunction(boolean relativeBlock, boolean requireAll) {
        return new Function() {
            @Override
            public Object evaluate(ExecutionContext<?> execution, ArgumentCollection arguments) {
                Avatar.MolangContext context = mc(execution);
                if (context == null || context.entity == null || context.entity.level() == null
                        || !validateArgumentSize(arguments.size())) {
                    return null;
                }
                Entity entity = context.entity;
                Level level = entity.level();
                java.util.function.Predicate<Identifier> matches;
                int firstTag;
                if (relativeBlock) {
                    BlockPos position = relativeQueryPosition(entity, execution, arguments);
                    if (position == null) {
                        return null;
                    }
                    BlockState state = level.getBlockState(position);
                    matches = id -> state.is(TagKey.create(Registries.BLOCK, id));
                    firstTag = 3;
                } else {
                    var biome = level.getBiome(entity.blockPosition());
                    matches = id -> biome.is(TagKey.create(Registries.BIOME, id));
                    firstTag = 0;
                }
                for (int index = firstTag; index < arguments.size(); index++) {
                    String name = arguments.getAsString(execution, index);
                    Identifier id = name == null ? null : Identifier.tryParse(name);
                    if (id == null) {
                        return null;
                    }
                    boolean matched = matches.test(id);
                    if (requireAll && !matched) {
                        return false;
                    }
                    if (!requireAll && matched) {
                        return true;
                    }
                }
                return requireAll;
            }

            @Override
            public boolean validateArgumentSize(int size) {
                return size >= (relativeBlock ? 4 : 1);
            }
        };
    }

    private static BlockPos relativeQueryPosition(Entity entity, ExecutionContext<?> execution,
                                                   Function.ArgumentCollection arguments) {
        double x = arguments.getAsDouble(execution, 0);
        double y = arguments.getAsDouble(execution, 1);
        double z = arguments.getAsDouble(execution, 2);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 5d || Math.abs(y) > 5d || Math.abs(z) > 5d) {
            return null;
        }
        return new BlockPos((int) Math.round(entity.getX() + x - 0.5d),
                (int) Math.round(entity.getY() + y - 0.5d),
                (int) Math.round(entity.getZ() + z - 0.5d));
    }

    // ===== Item name query =====

    /**
     * query.is_item_name_any("minecraft:stick", "minecraft:diamond", ...) — 1 if main hand item
     * matches any of the given registry names.
     */
    public static final Function IS_ITEM_NAME_ANY = equipmentFunction(EquipmentQuery.NAME_ANY);

    // ===== Equipment tag queries =====

    /**
     * query.equipped_item_all_tags(slot, tag1, tag2, ...) returns a Boolean for native item tags.
     * Named slots and legacy numeric slots are accepted; remaining arguments are identifiers.
     */
    public static final Function EQUIPPED_ITEM_ALL_TAGS = equipmentFunction(EquipmentQuery.TAGS_ALL);

    /**
     * query.equipped_item_any_tag(slot, tag1, tag2, ...) returns a Boolean for native item tags.
     */
    public static final Function EQUIPPED_ITEM_ANY_TAG = equipmentFunction(EquipmentQuery.TAGS_ANY);

    private enum EquipmentQuery {
        MAX_DURABILITY, REMAINING_DURABILITY, NAME_ANY, TAGS_ALL, TAGS_ANY
    }

    private static Function equipmentFunction(EquipmentQuery query) {
        return new Function() {
            @Override
            public Object evaluate(ExecutionContext<?> execution, ArgumentCollection arguments) {
                Avatar.MolangContext context = mc(execution);
                if (context == null || !(context.entity instanceof LivingEntity entity)
                        || !validateArgumentSize(arguments.size())) {
                    return null;
                }
                EquipmentSlot slot = querySlot(arguments.getValue(execution, 0));
                if (slot == null) {
                    return null;
                }
                ItemStack stack = entity.getItemBySlot(slot);
                if (query == EquipmentQuery.MAX_DURABILITY) {
                    return stack.getMaxDamage();
                }
                if (query == EquipmentQuery.REMAINING_DURABILITY) {
                    return stack.getMaxDamage() - stack.getDamageValue();
                }
                if (stack.isEmpty()) {
                    return false;
                }
                Identifier itemId = query == EquipmentQuery.NAME_ANY ? BuiltInRegistries.ITEM.getKey(stack.getItem()) : null;
                if (query == EquipmentQuery.NAME_ANY && itemId == null) {
                    return false;
                }
                for (int index = 1; index < arguments.size(); index++) {
                    String name = arguments.getAsString(execution, index);
                    Identifier expected = name == null ? null : Identifier.tryParse(name);
                    if (expected == null) {
                        return null;
                    }
                    boolean matches = query == EquipmentQuery.NAME_ANY ? expected.equals(itemId)
                            : stack.is(TagKey.create(Registries.ITEM, expected));
                    if (query == EquipmentQuery.TAGS_ALL && !matches) {
                        return false;
                    }
                    if (query != EquipmentQuery.TAGS_ALL && matches) {
                        return true;
                    }
                }
                return query == EquipmentQuery.TAGS_ALL;
            }

            @Override
            public boolean validateArgumentSize(int size) {
                return query == EquipmentQuery.MAX_DURABILITY || query == EquipmentQuery.REMAINING_DURABILITY
                        ? size == 1 : size >= 2;
            }
        };
    }

    // ===== Misc =====

    public static final Variable CAPE_FLAP_AMOUNT = ctx -> {
        Avatar.MolangContext context = mc(ctx);
        if (context == null || !(context.entity instanceof Player player)) {
            return null;
        }
        float partial = context.ysm_partial_tick;
        // MC 26.x removed the old cloak-position fields; these zero terms match the reference port.
        float deltaX = (float) -net.minecraft.util.Mth.lerp(partial, player.xo, player.getX());
        float deltaY = (float) -net.minecraft.util.Mth.lerp(partial, player.yo, player.getY());
        float deltaZ = (float) -net.minecraft.util.Mth.lerp(partial, player.zo, player.getZ());
        float radians = player.yBodyRot * 0.017453292f;
        float vertical = net.minecraft.util.Mth.clamp(deltaY * 10f, -6f, 32f);
        float forward = net.minecraft.util.Mth.clamp((deltaX * net.minecraft.util.Mth.sin(radians)
                - deltaZ * net.minecraft.util.Mth.cos(radians)) * 100f, 0f, 150f);
        if (player.isCrouching()) {
            vertical += 25f;
        }
        return net.minecraft.util.Mth.clamp((6f + forward / 2f + vertical) / 108f, 0f, 1f);
    };

    public static final Variable PLAYER_LEVEL = ctx -> {
        Avatar.MolangContext context = mc(ctx);
        return context != null && context.entity instanceof Player player ? player.experienceLevel : null;
    };

    // ===== Geometry =====

    public static final Variable GEOMETRY_IS_MODEL = ctx -> 1f;
    public static final Variable GEOMETRY_IS_BLOCK = ctx -> 0f;
    public static final Variable GEOMETRY_IS_ENTITY = ctx -> 1f;
    public static final Variable GEOMETRY_IS_FLAT = ctx -> 0f;

    // ===== Functions =====

    static boolean canEmitDebugOutput(ExecutionContext<?> execution) {
        if (!org.figuramc.figura.FiguraMod.debugModeEnabled()) {
            return false;
        }
        Avatar.MolangContext context = mc(execution);
        Avatar owner = context == null ? null : context.owner;
        if (owner == null && execution.entity() instanceof Avatar avatar) {
            owner = avatar;
        }
        return owner == null || !((owner.getYsmRuntime() != null && owner.getYsmRuntime().encryptedSource())
                || (owner.nbt != null && top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload
                        .isEncrypted(owner.nbt.getCompoundOrEmpty("ysm"))));
    }

    public static final Function DEBUG_OUTPUT_FUNC = new Function() {
        @Override
        public Object evaluate(ExecutionContext<?> execution, ArgumentCollection arguments) {
            if (!canEmitDebugOutput(execution)) {
                return null;
            }
            StringBuilder message = new StringBuilder();
            for (int index = 0; index < arguments.size(); index++) {
                message.append(arguments.getValue(execution, index));
            }
            org.figuramc.figura.FiguraMod.LOGGER.warn("[Molang Debug] {}", message);
            return null;
        }

        @Override
        public boolean validateArgumentSize(int size) {
            return size > 0;
        }
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
    private static EquipmentSlot querySlot(Object value) {
        // Retain the existing project numeric-slot interface alongside reference named slots.
        if (value instanceof Number number) {
            return switch (number.intValue()) {
                case 0 -> EquipmentSlot.MAINHAND;
                case 1 -> EquipmentSlot.OFFHAND;
                case 2 -> EquipmentSlot.FEET;
                case 3 -> EquipmentSlot.LEGS;
                case 4 -> EquipmentSlot.CHEST;
                case 5 -> EquipmentSlot.HEAD;
                default -> null;
            };
        }
        String name = ValueConversions.asString(value);
        if (name == null) {
            return null;
        }
        return switch (name.toLowerCase(java.util.Locale.ENGLISH)) {
            case "mainhand" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            case "feet" -> EquipmentSlot.FEET;
            case "legs" -> EquipmentSlot.LEGS;
            case "chest" -> EquipmentSlot.CHEST;
            case "head" -> EquipmentSlot.HEAD;
            default -> null;
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
