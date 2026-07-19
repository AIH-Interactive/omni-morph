package org.figuramc.figura.molang.runtime.binding;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.api.sound.SoundAPI;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.molang.runtime.AssignableVariable;
import org.figuramc.figura.molang.runtime.ExecutionContext;
import org.figuramc.figura.molang.runtime.Function;
import org.figuramc.figura.molang.runtime.Variable;
import org.figuramc.figura.model.ysm.animation.YsmAnimationClip;

import java.util.HashMap;
import java.util.Map;

/**
 * Top-level Molang bindings that resolve names like "math", "query", "v", "c", "t".
 *
 * This is the root ObjectBinding passed to the parser. It resolves the first
 * identifier in chains like "math.sin(...)" or "query.anim_time".
 *
 * Reference: YSM PrimaryBinding + StandardBindings
 */
public class MolangBindings implements ObjectBinding {

    private final Map<String, Object> bindings = new HashMap<>();

    public MolangBindings() {
        // Register math.* namespace
        MathBinding mathBinding = new MathBinding();
        bindings.put("math", mathBinding);
        bindings.put("math.", mathBinding);

        // Register built-in functions at top level
        bindings.put("loop", StandardBindings.LOOP_FUNC);
        bindings.put("for_each", StandardBindings.FOR_EACH_FUNC);

        // Register query.* namespace (initially empty, populated by Avatar)
        QueryBinding queryBinding = new QueryBinding();
        bindings.put("query", queryBinding);
        bindings.put("q", queryBinding);
        bindings.put("ctrl", new CtrlBinding());
        bindings.put("ysm", new YsmBinding());
        bindings.put("fn", new FnBinding());
        bindings.put("args", (Variable) ctx -> {
            Avatar.MolangContext context = ctx.entity() instanceof Avatar.MolangContext molangContext ? molangContext : null;
            if (context == null || context.owner == null || context.owner.getYsmRuntime() == null)
                return java.util.List.of();
            return context.owner.getYsmRuntime().functions().currentArguments();
        });
    }

    /**
     * Registers a custom binding for a specific name.
     */
    public void register(String name, Object binding) {
        bindings.put(name.toLowerCase(), binding);
    }

    /**
     * Registers a query.* variable or function.
     */
    public void registerQuery(String name, Object binding) {
        QueryBinding queryBinding = (QueryBinding) bindings.computeIfAbsent("query", k -> new QueryBinding());
        if (queryBinding != null) {
            queryBinding.register(name, binding);
        }
    }

    /**
     * Registers a v.* variable storage.
     */
    public void registerVariable(String name, AssignableVariable variable) {
        bindings.put("v", variable);
        bindings.put("variable", variable);
    }

    /**
     * Registers a c.* (controller) variable storage.
     */
    public void registerController(String name, AssignableVariable variable) {
        bindings.put("c", variable);
        bindings.put("context", variable);
    }

    /**
     * Registers a t.* (temp) variable storage.
     */
    public void registerTemp(String name, AssignableVariable variable) {
        bindings.put("t", variable);
        bindings.put("temp", variable);
    }

    @Override
    public Object getProperty(String name) {
        Object binding = bindings.get(name.toLowerCase());
        if (binding != null) return binding;

        // Check for query.*
        if (name.startsWith("q.") || name.startsWith("query.")) {
            Object queryBinding = bindings.get("query");
            if (queryBinding instanceof QueryBinding) {
                String key = name.contains(".") ? name.substring(name.indexOf('.') + 1) : name;
                return ((QueryBinding) queryBinding).getProperty(key);
            }
        }

        return null;
    }

    /**
     * ObjectBinding for the math.* namespace.
     * Maps names to Function instances or Number constants.
     */
    public static class MathBinding implements ObjectBinding {
        private final Map<String, Object> mathEntries = new HashMap<>();

        public MathBinding() {
            // Constants
            mathEntries.put("pi", (float) Math.PI);
            mathEntries.put("e", (float) Math.E);

            // Trigonometry
            mathEntries.put("sin", MathFunctions.SIN);
            mathEntries.put("cos", MathFunctions.COS);
            mathEntries.put("asin", MathFunctions.ASIN);
            mathEntries.put("acos", MathFunctions.ACOS);
            mathEntries.put("atan", MathFunctions.ATAN);
            mathEntries.put("atan2", MathFunctions.ATAN2);

            // Basic arithmetic
            mathEntries.put("abs", MathFunctions.ABS);
            mathEntries.put("ceil", MathFunctions.CEIL);
            mathEntries.put("clamp", MathFunctions.CLAMP);
            mathEntries.put("exp", MathFunctions.EXP);
            mathEntries.put("floor", MathFunctions.FLOOR);
            mathEntries.put("ln", MathFunctions.LN);
            mathEntries.put("max", MathFunctions.MAX);
            mathEntries.put("min", MathFunctions.MIN);
            mathEntries.put("mod", MathFunctions.MOD);
            mathEntries.put("pow", MathFunctions.POW);
            mathEntries.put("round", MathFunctions.ROUND);
            mathEntries.put("sqrt", MathFunctions.SQRT);
            mathEntries.put("trunc", MathFunctions.TRUNC);

            // Interpolation
            mathEntries.put("lerp", MathFunctions.LERP);
            mathEntries.put("lerprotate", MathFunctions.LERP_ROTATE);
            mathEntries.put("minangle", MathFunctions.MIN_ANGLE);
            mathEntries.put("min_angle", MathFunctions.MIN_ANGLE);

            // Hermite blend (primary + aliases)
            mathEntries.put("hermite_blend", MathFunctions.HERMIT_BLEND);
            mathEntries.put("hermitblend", MathFunctions.HERMIT_BLEND);
            mathEntries.put("hermite", MathFunctions.HERMIT_BLEND);

            // Random (primary + aliases)
            mathEntries.put("random", MathFunctions.RANDOM_FUNC);
            mathEntries.put("random_integer", MathFunctions.RANDOM_INTEGER);
            mathEntries.put("random_integer", MathFunctions.RANDOM_INTEGER);
            mathEntries.put("randominteger", MathFunctions.RANDOM_INTEGER);
            mathEntries.put("randomi", MathFunctions.RANDOM_INTEGER);

            // Die roll (primary + aliases)
            mathEntries.put("die_roll", MathFunctions.DIE_ROLL);
            mathEntries.put("dieroll", MathFunctions.DIE_ROLL);
            mathEntries.put("roll", MathFunctions.DIE_ROLL);
            mathEntries.put("die_roll_integer", MathFunctions.DIE_ROLL_INTEGER);
            mathEntries.put("dierollinteger", MathFunctions.DIE_ROLL_INTEGER);
            mathEntries.put("rolli", MathFunctions.DIE_ROLL_INTEGER);
        }

        @Override
        public Object getProperty(String name) {
            return mathEntries.get(name.toLowerCase());
        }
    }

    /**
     * ObjectBinding for the query.* namespace.
     * Stores query variables and functions.
     */
    public static class QueryBinding implements ObjectBinding {
        private final Map<String, Object> queries = new HashMap<>();

        public void register(String name, Object binding) {
            queries.put(name.toLowerCase(), binding);
        }

        @Override
        public Object getProperty(String name) {
            return queries.get(name.toLowerCase());
        }
    }

    public static class CtrlBinding implements ObjectBinding {
        private final Map<String, Object> states = new HashMap<>();

        public CtrlBinding() {
            states.put("death", (Variable) ctx -> bool(ctx, m -> m.health <= 0f));
            states.put("sleep", (Variable) ctx -> bool(ctx, m -> m.is_sleeping != 0f));
            states.put("swim", (Variable) ctx -> bool(ctx, m -> m.is_swimming != 0f));
            states.put("swim_stand", (Variable) ctx -> bool(ctx, m -> m.is_in_water != 0f && m.is_on_ground == 0f));
            states.put("jump", (Variable) ctx -> bool(ctx, m -> m.is_on_ground == 0f && m.is_in_water == 0f));
            states.put("fall", (Variable) ctx -> bool(ctx, m -> m.is_on_ground == 0f && m.is_in_water == 0f && m.vertical_speed < -0.05f));
            states.put("fly", (Variable) ctx -> bool(ctx, m -> m.is_on_ground == 0f && m.is_in_water == 0f && m.vertical_speed >= -0.05f));
            states.put("elytra_fly", (Variable) ctx -> bool(ctx, m -> m.entity instanceof LivingEntity living && living.isFallFlying()));
            states.put("sneak", (Variable) ctx -> bool(ctx, m -> m.is_sneaking != 0f && isMoving(m)));
            states.put("sneaking", (Variable) ctx -> bool(ctx, m -> m.is_sneaking != 0f));
            states.put("run", (Variable) ctx -> bool(ctx, m -> m.is_sprinting != 0f && isMoving(m)));
            states.put("walk", (Variable) ctx -> bool(ctx, m -> m.is_on_ground != 0f && m.is_sprinting == 0f && m.is_sneaking == 0f && isMoving(m)));
            states.put("idle", (Variable) ctx -> bool(ctx, m -> m.is_on_ground != 0f && !isMoving(m) && m.is_sneaking == 0f));
            states.put("attacked", (Variable) ctx -> bool(ctx, m -> m.hurt_time > 0f));
            states.put("ride", (Function) CtrlBinding::ride);
            states.put("riding", (Variable) ctx -> bool(ctx, m -> m.is_riding != 0f));
            states.put("hold", (Function) (ctx, args) -> handItem(ctx, args, HandMode.HOLD));
            states.put("use", (Function) (ctx, args) -> handItem(ctx, args, HandMode.USE));
            states.put("swing", (Function) (ctx, args) -> handItem(ctx, args, HandMode.SWING));
            states.put("armor", (Function) CtrlBinding::armor);
            states.put("attack", (Variable) ctx -> bool(ctx, m -> m.attack_time > 0f || m.is_swinging != 0f));
            states.put("playing_extra_animation", (Variable) CtrlBinding::playingExtraAnimation);
            states.put("state_continue", (Variable) ctx -> 2f);
            states.put("state_break", (Variable) ctx -> 3f);
            states.put("state_stop", (Variable) ctx -> 3f);
            states.put("state_pause", (Variable) ctx -> 4f);
            states.put("state_bypass", (Variable) ctx -> 5f);
            states.put("loop", (Variable) ctx -> 10f);
            states.put("play_once", (Variable) ctx -> 11f);
            states.put("hold_on_last_frame", (Variable) ctx -> 12f);
            states.put("set_beginning_transition_length", (Function) (ctx, args) -> {
                Avatar.MolangContext context = ctx.entity() instanceof Avatar.MolangContext molangContext ? molangContext : null;
                if (context != null && args.size() > 0)
                    context.controller.setPath("beginning_transition_length", args.getAsFloat(ctx, 0));
                return 0f;
            });
            states.put("set_animation", (Function) (ctx, args) -> {
                Avatar.MolangContext context = ctx.entity() instanceof Avatar.MolangContext molangContext ? molangContext : null;
                if (context == null || context.owner == null || context.owner.getYsmRuntime() == null || args.size() == 0)
                    return 0f;
                String animation = args.getAsString(ctx, 0);
                if (animation == null || animation.isBlank())
                    return 0f;
                Object fade = context.controller.getPath("beginning_transition_length");
                float fadeSeconds = fade instanceof Number number ? number.floatValue() : 0f;
                YsmAnimationClip.LoopMode loopMode = args.size() > 1
                        ? loopModeFromCtrlValue(args.getAsFloat(ctx, 1))
                        : context.owner.getYsmRuntime().animations().loopModeFor(animation, YsmAnimationClip.LoopMode.ONCE);
                return context.owner.getYsmRuntime().animations().playControllerFunctionAnimation(
                        controllerName(context), animation, loopMode, 1f, Math.max(0f, fadeSeconds)
                ) == null ? 0f : 1f;
            });
            states.put("reset", (Function) (ctx, args) -> {
                Avatar.MolangContext context = ctx.entity() instanceof Avatar.MolangContext molangContext ? molangContext : null;
                if (context == null || context.owner == null || context.owner.getYsmRuntime() == null)
                    return 0f;
                Object fade = context.controller.getPath("beginning_transition_length");
                float fadeSeconds = fade instanceof Number number ? number.floatValue() : 0f;
                context.owner.getYsmRuntime().animations().clearControllerFunctionAnimation(controllerName(context), Math.max(0f, fadeSeconds));
                return 1f;
            });
            states.put("indicate_reload", (Function) (ctx, args) -> 0f);
            states.put("im_delta", (Variable) ctx -> 0f);
            states.put("im_pitch", (Variable) ctx -> 0f);
            states.put("im_time", (Variable) ctx -> 0f);
        }

        @Override
        public Object getProperty(String name) {
            String key = name.toLowerCase();
            Object state = states.get(key);
            if (state != null)
                return state;
            return new FallbackBinding(ctx -> controllerValue(ctx, key));
        }

        private static float bool(ExecutionContext<?> ctx, ContextPredicate predicate) {
            if (ctx.entity() instanceof Avatar.MolangContext context && predicate.test(context))
                return 1f;
            return 0f;
        }

        private static boolean isMoving(Avatar.MolangContext context) {
            return Float.isFinite(context.ground_speed) && context.ground_speed > 0.05f;
        }

        private static float playingExtraAnimation(ExecutionContext<?> ctx) {
            if (!(ctx.entity() instanceof Avatar.MolangContext context) || context.owner == null || context.owner.getYsmRuntime() == null)
                return 0f;
            for (var action : context.owner.getYsmRuntime().actions().all()) {
                if (context.owner.getYsmRuntime().actions().isActive(action.getId()))
                    return 1f;
            }
            return 0f;
        }

        private static Object controllerValue(ExecutionContext<?> ctx, String key) {
            if (!(ctx.entity() instanceof Avatar.MolangContext context))
                return 0f;
            Object value = context.controller.getPath(key);
            return value == null ? 0f : value;
        }

        private static float handItem(ExecutionContext<?> ctx, Function.ArgumentCollection args, HandMode mode) {
            Avatar.MolangContext context = ctx.entity() instanceof Avatar.MolangContext molangContext ? molangContext : null;
            if (context == null || !(context.entity instanceof LivingEntity living) || args.size() < 2)
                return 0f;
            EquipmentSlot slot = parseSlot(args.getAsString(ctx, 0));
            if (slot == null || isArmorSlot(slot))
                return 0f;
            InteractionHand hand = slot == EquipmentSlot.OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            if (mode == HandMode.SWING && context.is_swinging == 0f)
                return 0f;
            if (mode == HandMode.USE && (context.is_using_item == 0f || living.getUsedItemHand() != hand))
                return 0f;
            return itemMatches(living.getItemBySlot(slot), args.getAsString(ctx, 1)) ? 1f : 0f;
        }

        private static float armor(ExecutionContext<?> ctx, Function.ArgumentCollection args) {
            Avatar.MolangContext context = ctx.entity() instanceof Avatar.MolangContext molangContext ? molangContext : null;
            if (context == null || !(context.entity instanceof LivingEntity living) || args.size() < 2)
                return 0f;
            EquipmentSlot slot = parseSlot(args.getAsString(ctx, 0));
            if (slot == null || !isArmorSlot(slot))
                return 0f;
            return itemMatches(living.getItemBySlot(slot), args.getAsString(ctx, 1)) ? 1f : 0f;
        }

        private static float ride(ExecutionContext<?> ctx, Function.ArgumentCollection args) {
            Avatar.MolangContext context = ctx.entity() instanceof Avatar.MolangContext molangContext ? molangContext : null;
            if (context == null || !(context.entity instanceof LivingEntity living) || args.size() < 2)
                return 0f;
            String type = args.getAsString(ctx, 0);
            Entity target = "passenger".equalsIgnoreCase(type) ? living.getFirstPassenger() : living.getVehicle();
            if (target == null || !target.isAlive())
                return 0f;
            return entityMatches(target.getType(), args.getAsString(ctx, 1)) ? 1f : 0f;
        }

        private static EquipmentSlot parseSlot(String slot) {
            if (slot == null)
                return null;
            return switch (slot.toLowerCase(java.util.Locale.US)) {
                case "mainhand", "main_hand", "main", "hand", "right", "0" -> EquipmentSlot.MAINHAND;
                case "offhand", "off_hand", "off", "left", "1" -> EquipmentSlot.OFFHAND;
                case "head", "helmet", "4" -> EquipmentSlot.HEAD;
                case "chest", "chestplate", "body", "3" -> EquipmentSlot.CHEST;
                case "legs", "leggings", "2" -> EquipmentSlot.LEGS;
                case "feet", "boots", "5" -> EquipmentSlot.FEET;
                default -> null;
            };
        }

        private static boolean isArmorSlot(EquipmentSlot slot) {
            return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
        }

        private static boolean itemMatches(ItemStack stack, String expected) {
            if (expected == null || expected.isBlank())
                return false;
            if (stack == null)
                stack = ItemStack.EMPTY;
            if ("empty".equalsIgnoreCase(expected))
                return stack.isEmpty();
            if (expected.startsWith("$")) {
                String actual = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                return actual.equals(expected.substring(1));
            }
            if (expected.startsWith("#")) {
                Identifier id = identifier(expected.substring(1));
                return id != null && !stack.isEmpty() && stack.is(TagKey.create(Registries.ITEM, id));
            }
            if (expected.startsWith(":")) {
                String type = expected.substring(1).toLowerCase(java.util.Locale.US);
                if (stack.isEmpty())
                    return false;
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(java.util.Locale.US);
                String use = stack.getUseAnimation().name().toLowerCase(java.util.Locale.US);
                return id.endsWith(":" + type) || id.contains(type) || use.equals(type);
            }
            return false;
        }

        private static boolean entityMatches(EntityType<?> type, String expected) {
            if (type == null || expected == null || expected.isBlank())
                return false;
            if (expected.startsWith("$")) {
                return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().equals(expected.substring(1));
            }
            if (expected.startsWith("#")) {
                Identifier id = identifier(expected.substring(1));
                return id != null && type.builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, id));
            }
            return false;
        }

        private static Identifier identifier(String value) {
            if (value == null || value.isBlank())
                return null;
            try {
                return Identifier.parse(value.contains(":") ? value : "minecraft:" + value);
            } catch (Exception ignored) {
                return null;
            }
        }

        private static String controllerName(Avatar.MolangContext context) {
            Object controller = context.controller.getPath("controller");
            return controller instanceof String name && !name.isBlank() ? name : "ctrl";
        }

        private static YsmAnimationClip.LoopMode loopModeFromCtrlValue(float value) {
            return switch (Math.round(value)) {
                case 10 -> YsmAnimationClip.LoopMode.LOOP;
                case 12 -> YsmAnimationClip.LoopMode.HOLD_ON_LAST_FRAME;
                default -> YsmAnimationClip.LoopMode.ONCE;
            };
        }

        private interface ContextPredicate {
            boolean test(Avatar.MolangContext context);
        }

        private enum HandMode {
            HOLD,
            USE,
            SWING
        }
    }

    public static class YsmBinding implements ObjectBinding {
        private final Map<String, Object> values = new HashMap<>();

        public YsmBinding() {
            values.put("rendering_in_inventory", (Variable) ctx -> context(ctx).rendering_in_inventory);
            values.put("rendering_in_paperdoll", (Variable) ctx -> context(ctx).rendering_in_paperdoll);
            values.put("is_first_person", (Variable) ctx -> context(ctx).is_first_person);
            values.put("first_person", (Variable) ctx -> context(ctx).is_first_person);
            values.put("weather", (Variable) YsmBinding::weather);
            values.put("dimension_name", (Variable) YsmBinding::dimensionName);
            values.put("fps", (Variable) ctx -> (float) Minecraft.getInstance().getFps());
            values.put("time_delta", (Variable) ctx -> context(ctx).delta_time);
            values.put("head_yaw", (Variable) ctx -> context(ctx).head_y_rot);
            values.put("head_pitch", (Variable) ctx -> context(ctx).head_x_rot);
            values.put("ground_speed2", (Variable) YsmBinding::groundSpeed2);
            values.put("is_open_air", (Variable) YsmBinding::isOpenAir);
            values.put("block_light", (Variable) YsmBinding::blockLight);
            values.put("sky_light", (Variable) YsmBinding::skyLight);
            values.put("is_passenger", (Variable) ctx -> context(ctx).is_riding);
            values.put("is_sleep", (Variable) ctx -> context(ctx).is_sleeping);
            values.put("is_sneak", (Variable) ctx -> context(ctx).is_sneaking);
            values.put("eye_in_water", (Variable) ctx -> context(ctx).entity != null && context(ctx).entity.isUnderWater() ? 1f : 0f);
            values.put("frozen_ticks", (Variable) ctx -> context(ctx).entity == null ? 0f : (float) context(ctx).entity.getTicksFrozen());
            values.put("air_supply", (Variable) ctx -> context(ctx).entity == null ? 0f : (float) context(ctx).entity.getAirSupply());
            values.put("input_vertical", (Variable) ctx -> input(ctx, "zza"));
            values.put("input_horizontal", (Variable) ctx -> input(ctx, "xxa"));
            values.put("xxa", (Variable) ctx -> input(ctx, "xxa"));
            values.put("yya", (Variable) ctx -> input(ctx, "yya"));
            values.put("zza", (Variable) ctx -> input(ctx, "zza"));
            values.put("keyboard", (Function) (ctx, args) -> {
                if (args.size() == 0)
                    return 0f;
                try {
                    return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), args.getAsInt(ctx, 0)) ? 1f : 0f;
                } catch (Throwable ignored) {
                    return 0f;
                }
            });
            values.put("mouse", (Function) (ctx, args) -> 0f);
            values.put("sync", (Function) (ctx, args) -> {
                Avatar.MolangContext context = context(ctx);
                if (context.owner == null || context.owner.getYsmRuntime() == null)
                    return 0f;
                return context.owner.getYsmRuntime().functions().recordSync(ctx, args);
            });
            Function eventFunction = (ctx, args) -> {
                Avatar.MolangContext context = context(ctx);
                if (context.owner == null || context.owner.getYsmRuntime() == null || args.size() == 0)
                    return 0f;
                String event = args.getAsString(ctx, 0);
                if (event == null || event.isBlank())
                    return 0f;
                return context.owner.getYsmRuntime().functions().runEvent(event, ctx, args, 1);
            };
            values.put("event", eventFunction);
            values.put("run_event", eventFunction);
            values.put("trigger_event", eventFunction);
            values.put("play_sound", (Function) (ctx, args) -> {
                Avatar.MolangContext context = context(ctx);
                if (context.owner == null || args.size() == 0)
                    return 0f;
                String sound = args.getAsString(ctx, 0);
                if (sound == null || sound.isBlank())
                    return 0f;
                try {
                    Entity entity = context.entity;
                    FiguraVec3 pos = entity == null ? FiguraVec3.of() : FiguraVec3.of(entity.getX(), entity.getY(), entity.getZ());
                    String name = args.size() > 1 ? args.getAsString(ctx, 1) : sound;
                    new SoundAPI(context.owner).playSound(name == null || name.isBlank() ? sound : name, pos, 1d, 1d, false, null, false);
                    return 1f;
                } catch (Throwable ignored) {
                    return 0f;
                }
            });
            values.put("stop_sound", (Function) (ctx, args) -> {
                Avatar.MolangContext context = context(ctx);
                if (context.owner == null)
                    return 0f;
                String sound = args.size() > 0 ? args.getAsString(ctx, 0) : null;
                try {
                    new SoundAPI(context.owner).stopSound(sound);
                } catch (Throwable ignored) {
                }
                return 0f;
            });
            values.put("stop_all_sounds", (Function) (ctx, args) -> {
                Avatar.MolangContext context = context(ctx);
                if (context.owner == null)
                    return 0f;
                try {
                    new SoundAPI(context.owner).stopSound(null);
                } catch (Throwable ignored) {
                }
                return 0f;
            });
            values.put("first_order", (Function) (ctx, args) -> args.size() > 1 ? args.getAsFloat(ctx, 1) : 0f);
            values.put("second_order", (Function) (ctx, args) -> args.size() > 1 ? args.getAsFloat(ctx, 1) : 0f);
            values.put("food_level", (Variable) YsmBinding::foodLevel);
            values.put("is_local_player", (Variable) YsmBinding::isLocalPlayer);
            values.put("local_player", (Variable) YsmBinding::isLocalPlayer);
            values.put("person_view", (Variable) ctx -> {
                Minecraft minecraft = Minecraft.getInstance();
                return minecraft.options.getCameraType().isFirstPerson() ? 0f : 1f;
            });
            values.put("main_hand", (Function) (ctx, args) -> itemQuery(mainHand(context(ctx)), ctx, args, 0));
            values.put("off_hand", (Function) (ctx, args) -> itemQuery(offHand(context(ctx)), ctx, args, 0));
            values.put("equipped_item", (Function) (ctx, args) -> itemQuery(equippedItem(context(ctx), args.size() > 0 ? args.getAsString(ctx, 0) : ""), ctx, args, 1));
            values.put("has_mainhand", (Variable) ctx -> mainHand(context(ctx)).isEmpty() ? 0f : 1f);
            values.put("has_offhand", (Variable) ctx -> offHand(context(ctx)).isEmpty() ? 0f : 1f);
            values.put("has_helmet", (Variable) ctx -> equippedItem(context(ctx), "head").isEmpty() ? 0f : 1f);
            values.put("has_chest_plate", (Variable) ctx -> equippedItem(context(ctx), "chest").isEmpty() ? 0f : 1f);
            values.put("has_leggings", (Variable) ctx -> equippedItem(context(ctx), "legs").isEmpty() ? 0f : 1f);
            values.put("has_boots", (Variable) ctx -> equippedItem(context(ctx), "feet").isEmpty() ? 0f : 1f);
            values.put("armor_value", (Variable) ctx -> context(ctx).entity instanceof LivingEntity living ? (float) living.getArmorValue() : 0f);
            values.put("hurt_time", (Variable) ctx -> context(ctx).hurt_time);
            values.put("swinging", (Variable) ctx -> context(ctx).is_swinging);
            values.put("swing_time", (Variable) ctx -> context(ctx).swing_time);
            values.put("attack_time", (Variable) ctx -> context(ctx).attack_time);
            values.put("entity_type", (Variable) YsmBinding::entityType);
            values.put("elytra_rot_z", (Variable) YsmBinding::elytraRotZ);
            values.put("boat_left_paddle", (Variable) ctx -> boatPaddle(ctx, true));
            values.put("boat_right_paddle", (Variable) ctx -> boatPaddle(ctx, false));
            values.put("boat_left_rowing_time", (Variable) ctx -> boatRowingTime(ctx, true));
            values.put("boat_right_rowing_time", (Variable) ctx -> boatRowingTime(ctx, false));
            values.put("boat_paddle_scale", (Variable) ctx -> boat(ctx) != null ? 1f : 0f);
            values.put("boat_body_offset_y", (Variable) ctx -> 0f);
            values.put("boat_body_offset_z", (Variable) ctx -> 0f);
            values.put("shoot_item_id", (Variable) YsmBinding::shootItemId);
            values.put("is_player", (Variable) ctx -> context(ctx).entity instanceof Player ? 1f : 0f);
            values.put("dump_mods", (Function) (ctx, args) -> 0f);
            values.put("dump_effects", (Function) (ctx, args) -> 0f);
            values.put("dump_biome", (Function) (ctx, args) -> 0f);
            values.put("has_any_curios", (Function) (ctx, args) -> 0f);
            values.put("has_any_curios_with_any_tag", (Function) (ctx, args) -> 0f);
            values.put("has_any_curios_with_all_tags", (Function) (ctx, args) -> 0f);
            values.put("particle", (Function) (ctx, args) -> 0f);
            values.put("abs_particle", (Function) (ctx, args) -> 0f);
            values.put("perlin_noise", (Function) (ctx, args) -> 0f);
            values.put("bone_param", (Function) (ctx, args) -> 0f);
            values.put("bone_rot", (Function) (ctx, args) -> 0f);
            values.put("bone_pos", (Function) (ctx, args) -> 0f);
            values.put("bone_scale", (Function) (ctx, args) -> 0f);
            values.put("bone_pivot_abs", (Function) (ctx, args) -> 0f);
        }

        @Override
        public Object getProperty(String name) {
            Object value = values.get(name.toLowerCase());
            return value != null ? value : FallbackBinding.ZERO;
        }

        private static Avatar.MolangContext context(ExecutionContext<?> ctx) {
            return ctx.entity() instanceof Avatar.MolangContext context ? context : EMPTY_CONTEXT;
        }

        private static float weather(ExecutionContext<?> ctx) {
            Avatar.MolangContext context = context(ctx);
            Entity entity = context.entity;
            if (entity == null || entity.level() == null)
                return 0f;
            if (entity.level().isThundering())
                return 2f;
            return entity.level().isRainingAt(entity.blockPosition()) ? 1f : 0f;
        }

        private static String dimensionName(ExecutionContext<?> ctx) {
            Entity entity = context(ctx).entity;
            return entity == null || entity.level() == null ? "" : entity.level().dimension().toString();
        }

        private static float groundSpeed2(ExecutionContext<?> ctx) {
            Avatar.MolangContext context = context(ctx);
            Entity entity = context.entity;
            if (entity != null) {
                var velocity = entity.getDeltaMovement();
                float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20f;
                if (Float.isFinite(speed) && speed > 0.001f)
                    return speed;
            }
            return context.ground_speed;
        }

        private static float isOpenAir(ExecutionContext<?> ctx) {
            Avatar.MolangContext context = context(ctx);
            Entity entity = context.entity;
            if (entity == null || entity.level() == null)
                return 0f;
            BlockPos pos = entity.blockPosition();
            return entity.level().canSeeSky(pos) ? 1f : 0f;
        }

        private static float blockLight(ExecutionContext<?> ctx) {
            Entity entity = context(ctx).entity;
            if (entity == null || entity.level() == null)
                return 0f;
            return entity.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, entity.blockPosition());
        }

        private static float skyLight(ExecutionContext<?> ctx) {
            Entity entity = context(ctx).entity;
            if (entity == null || entity.level() == null)
                return 0f;
            return entity.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, entity.blockPosition());
        }

        private static float foodLevel(ExecutionContext<?> ctx) {
            Entity entity = context(ctx).entity;
            return entity instanceof Player player ? player.getFoodData().getFoodLevel() : 20f;
        }

        private static float input(ExecutionContext<?> ctx, String field) {
            Entity entity = context(ctx).entity;
            if (entity == null)
                return 0f;
            try {
                java.lang.reflect.Field reflected = entity.getClass().getField(field);
                return reflected.getFloat(entity);
            } catch (Throwable ignored) {
                return 0f;
            }
        }

        private static float isLocalPlayer(ExecutionContext<?> ctx) {
            Avatar.MolangContext context = context(ctx);
            Minecraft minecraft = Minecraft.getInstance();
            return context.entity != null && minecraft.player != null && context.entity.getUUID().equals(minecraft.player.getUUID()) ? 1f : 0f;
        }

        private static float elytraRotZ(ExecutionContext<?> ctx) {
            Entity entity = context(ctx).entity;
            if (!(entity instanceof LivingEntity living) || !living.isFallFlying())
                return 0f;
            var velocity = living.getDeltaMovement();
            double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            if (horizontal <= 0.0001d)
                return 0f;
            return (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z));
        }

        private static float boatPaddle(ExecutionContext<?> ctx, boolean left) {
            Entity boat = boat(ctx);
            if (boat == null)
                return 0f;
            Object value = invokeBoatMethod(boat, left ? "getPaddleState" : "getPaddleState", left ? 0 : 1);
            if (value instanceof Boolean bool)
                return bool ? 1f : 0f;
            return boatRowingTime(ctx, left) > 0f ? 1f : 0f;
        }

        private static float boatRowingTime(ExecutionContext<?> ctx, boolean left) {
            Entity boat = boat(ctx);
            if (boat == null)
                return 0f;
            Object value = invokeBoatMethod(boat, "getRowingTime", left ? 0 : 1, 0f);
            return value instanceof Number number ? number.floatValue() : 0f;
        }

        private static Entity boat(ExecutionContext<?> ctx) {
            Entity entity = context(ctx).entity;
            Entity vehicle = entity == null ? null : entity.getVehicle();
            if (vehicle == null)
                return null;
            String className = vehicle.getClass().getName().toLowerCase(java.util.Locale.US);
            String entityId = vehicle.getType() == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString();
            return className.contains("boat") || entityId.contains("boat") || entityId.contains("raft") ? vehicle : null;
        }

        private static Object invokeBoatMethod(Entity boat, String name, Object... args) {
            for (java.lang.reflect.Method method : boat.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length)
                    continue;
                try {
                    return method.invoke(boat, args);
                } catch (Throwable ignored) {
                }
            }
            return null;
        }

        private static String shootItemId(ExecutionContext<?> ctx) {
            Avatar.MolangContext context = context(ctx);
            ItemStack used = ItemStack.EMPTY;
            if (context.entity instanceof LivingEntity living && living.isUsingItem())
                used = living.getUseItem();
            if (used == null || used.isEmpty()) {
                ItemStack main = mainHand(context);
                ItemStack off = offHand(context);
                String mainUse = main.isEmpty() ? "" : main.getUseAnimation().name().toLowerCase(java.util.Locale.US);
                String offUse = off.isEmpty() ? "" : off.getUseAnimation().name().toLowerCase(java.util.Locale.US);
                if ("bow".equals(mainUse) || "crossbow".equals(mainUse) || "spear".equals(mainUse))
                    used = main;
                else if ("bow".equals(offUse) || "crossbow".equals(offUse) || "spear".equals(offUse))
                    used = off;
            }
            return itemId(used);
        }

        private static Object itemQuery(ItemStack stack, ExecutionContext<?> ctx, Function.ArgumentCollection args, int compareStart) {
            String id = itemId(stack);
            if (args.size() <= compareStart)
                return id;
            for (int i = compareStart; i < args.size(); i++) {
                String expected = args.getAsString(ctx, i);
                if (expected != null && matchesItem(id, expected))
                    return 1f;
            }
            return 0f;
        }

        private static String entityType(ExecutionContext<?> ctx) {
            Entity entity = context(ctx).entity;
            if (entity == null || entity.getType() == null)
                return "";
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        }

        private static ItemStack mainHand(Avatar.MolangContext context) {
            return context.entity instanceof LivingEntity living ? living.getMainHandItem() : ItemStack.EMPTY;
        }

        private static ItemStack offHand(Avatar.MolangContext context) {
            return context.entity instanceof LivingEntity living ? living.getOffhandItem() : ItemStack.EMPTY;
        }

        private static ItemStack equippedItem(Avatar.MolangContext context, String slot) {
            if (!(context.entity instanceof LivingEntity living))
                return ItemStack.EMPTY;
            String normalized = slot == null ? "" : slot.toLowerCase(java.util.Locale.US);
            return switch (normalized) {
                case "offhand", "off_hand", "off", "1" -> living.getOffhandItem();
                case "mainhand", "main_hand", "main", "0", "" -> living.getMainHandItem();
                case "head", "helmet", "4" -> living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                case "chest", "chestplate", "3" -> living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
                case "legs", "leggings", "2" -> living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
                case "feet", "boots", "5" -> living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
                default -> ItemStack.EMPTY;
            };
        }

        private static String itemId(ItemStack stack) {
            if (stack == null || stack.isEmpty())
                return "";
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }

        private static boolean matchesItem(String actual, String expected) {
            if (actual == null || actual.isBlank() || expected == null || expected.isBlank())
                return false;
            String normalized = expected.trim().toLowerCase(java.util.Locale.US);
            String actualLower = actual.toLowerCase(java.util.Locale.US);
            return actualLower.equals(normalized) || actualLower.endsWith(":" + normalized);
        }

        private static final Avatar.MolangContext EMPTY_CONTEXT = new Avatar.MolangContext(null);
    }

    public static class FnBinding implements ObjectBinding {
        @Override
        public Object getProperty(String name) {
            return (Function) (ctx, args) -> {
                Avatar.MolangContext context = ctx.entity() instanceof Avatar.MolangContext molangContext ? molangContext : null;
                if (context == null || context.owner == null || context.owner.getYsmRuntime() == null)
                    return 0f;
                return context.owner.getYsmRuntime().functions().call(name, ctx, args);
            };
        }
    }

    private static class FallbackBinding implements Variable, Function {
        private static final FallbackBinding ZERO = new FallbackBinding(ctx -> 0f);

        private final Variable variable;

        private FallbackBinding(Variable variable) {
            this.variable = variable;
        }

        @Override
        public Object evaluate(ExecutionContext<?> context) {
            return variable == null ? 0f : variable.evaluate(context);
        }

        @Override
        public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
            return 0f;
        }
    }
}
