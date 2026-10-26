package top.aihmc.omnimorph.molang.runtime.binding;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.molang.runtime.AssignableVariable;
import top.aihmc.omnimorph.molang.runtime.ExecutionContext;
import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;
import top.aihmc.omnimorph.model.ysm.YsmFilterRuntime;
import top.aihmc.omnimorph.model.ysm.YsmPerlinNoise;
import top.aihmc.omnimorph.model.ysm.YsmBoneQuery;
import top.aihmc.omnimorph.model.ysm.YsmInputState;
import top.aihmc.omnimorph.model.ysm.YsmParticleRuntime;
import top.aihmc.omnimorph.model.ysm.YsmHandItemType;
import top.aihmc.omnimorph.model.ysm.YsmPlayerActionState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.entity.vehicle.boat.ChestRaft;
import net.minecraft.world.entity.vehicle.boat.Raft;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.Variable;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import org.figuramc.figura.utils.PlatformUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
        // Reference Fabric adapters expose typed defaults without a working optional-mod bridge.
        private static final Map<String, Object> OPTIONAL_DEFAULTS = Map.ofEntries(
                Map.entry("im_delta", 0L),
                Map.entry("im_pitch", 0f),
                Map.entry("im_time", 0L),
                Map.entry("bcombat_attack_animation", ""),
                Map.entry("carryon_type", ""),
                Map.entry("carryon_is_princess", false),
                Map.entry("parcool_state", ""),
                Map.entry("slashblade_animation", ""),
                Map.entry("has_sophisticated_backpack", false),
                Map.entry("tac_hold_gun", false),
                Map.entry("tac_gun_type", ""),
                Map.entry("tac_gun_id", ""),
                Map.entry("tac_is_fire", false),
                Map.entry("tac_is_aim", false),
                Map.entry("tac_is_reload", false),
                Map.entry("tac_is_melee", false),
                Map.entry("tac_is_draw", false),
                Map.entry("tac_fire_mode", "")
        );
        private final Map<String, Object> states = new HashMap<>();

        public CtrlBinding() {
            for (YsmPlayerActionState.State state : YsmPlayerActionState.State.values()) {
                if (state != YsmPlayerActionState.State.NONE && state != YsmPlayerActionState.State.RIDE) {
                    states.put(state.name().toLowerCase(java.util.Locale.ROOT), actionState(state));
                }
            }
            states.put("fall", (Variable) ctx -> ctx.entity() instanceof Avatar.MolangContext context
                    && context.entity instanceof LivingEntity living
                    ? resolveActionState(context, living) == YsmPlayerActionState.State.JUMP && context.vertical_speed < -0.05f : null);
            states.put("ride", new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
                    return ride(context, arguments);
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return size == 2 || size == 3;
                }
            });
            states.put("riding", actionState(YsmPlayerActionState.State.RIDE));
            states.put("hold", equipmentFunction(HandMode.HOLD));
            states.put("use", equipmentFunction(HandMode.USE));
            states.put("swing", equipmentFunction(HandMode.SWING));
            states.put("armor", equipmentFunction(HandMode.ARMOR));
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
            states.put("set_beginning_transition_length", transitionLengthFunction());
            states.put("set_animation", setAnimationFunction());
            states.put("reset", resetFunction());
            states.put("indicate_reload", reloadFunction());
            OPTIONAL_DEFAULTS.forEach((name, value) -> states.put(name, (Variable) execution -> {
                if (!(execution.entity() instanceof Avatar.MolangContext context)
                        || !(context.entity instanceof LivingEntity)) {
                    return null;
                }
                if (name.equals("bcombat_attack_animation") && !(context.entity instanceof AbstractClientPlayer)) {
                    return null;
                }
                return value;
            }));
        }

        public static String optionalBindingUnavailableReason(String name) {
            if (name == null || !OPTIONAL_DEFAULTS.containsKey(name.toLowerCase(java.util.Locale.ROOT))) {
                return null;
            }
            return "CAPABILITY_UNAVAILABLE: optional-mod bridge is not implemented; reference Fabric typed default only";
        }

        private static Avatar.MolangContext functionControllerContext(ExecutionContext<?> execution) {
            if (!(execution.entity() instanceof Avatar.MolangContext context) || context.owner == null
                    || context.owner.getYsmRuntime() == null
                    || !(context.controller.getPath("controller") instanceof String name) || name.isBlank()) {
                return null;
            }
            return context;
        }

        private static Function reloadFunction() {
            return new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> execution, ArgumentCollection arguments) {
                    Avatar.MolangContext context = functionControllerContext(execution);
                    if (context != null) {
                        context.owner.getYsmRuntime().animations().requestControllerFunctionReload(
                                (String) context.controller.getPath("controller"));
                    }
                    return null;
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return size == 0;
                }
            };
        }

        private static Function transitionLengthFunction() {
            return new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> execution, ArgumentCollection arguments) {
                    Avatar.MolangContext context = functionControllerContext(execution);
                    if (context == null) {
                        return null;
                    }
                    float seconds = arguments.getAsFloat(execution, 0);
                    if (Float.isFinite(seconds) && seconds >= 0f) {
                        context.owner.getYsmRuntime().animations().setControllerFunctionTransitionLength(controllerName(context), seconds);
                    }
                    return null;
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return size == 1;
                }
            };
        }

        private static Function setAnimationFunction() {
            return new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> execution, ArgumentCollection arguments) {
                    Avatar.MolangContext context = functionControllerContext(execution);
                    if (context == null) {
                        return null;
                    }
                    String animation = arguments.getAsString(execution, 0);
                    if (animation == null || animation.isEmpty()) {
                        return null;
                    }
                    var player = context.owner.getYsmRuntime().animations();
                    String controller = controllerName(context);
                    YsmAnimationClip.LoopMode fallback = player.loopModeFor(controller, animation, YsmAnimationClip.LoopMode.ONCE);
                    YsmAnimationClip.LoopMode mode = arguments.size() == 2
                            ? loopModeFromCtrlValue(arguments.getAsInt(execution, 1), fallback) : fallback;
                    player.playControllerFunctionAnimation(controller, animation, mode, 1f,
                            player.controllerFunctionTransitionLength(controller));
                    return null;
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return size == 1 || size == 2;
                }
            };
        }

        private static Function resetFunction() {
            return new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> execution, ArgumentCollection arguments) {
                    Avatar.MolangContext context = functionControllerContext(execution);
                    if (context != null) {
                        var player = context.owner.getYsmRuntime().animations();
                        String controller = controllerName(context);
                        player.clearControllerFunctionAnimation(controller, player.controllerFunctionTransitionLength(controller));
                    }
                    return null;
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return size == 0;
                }
            };
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

        private static Variable actionState(YsmPlayerActionState.State expected) {
            return execution -> {
                if (!(execution.entity() instanceof Avatar.MolangContext context)
                        || !(context.entity instanceof LivingEntity living)) {
                    return null;
                }
                return resolveActionState(context, living) == expected;
            };
        }

        private static YsmPlayerActionState.State resolveActionState(Avatar.MolangContext context, LivingEntity living) {
            if (context.owner != null && YsmParticleRuntime.isPreview(context.owner.renderMode)) {
                return YsmPlayerActionState.State.NONE;
            }
            return YsmPlayerActionState.resolve(YsmPlayerActionState.snapshot(
                    living, context.ground_speed, context.vertical_speed));
        }

        private static boolean playingExtraAnimation(ExecutionContext<?> ctx) {
            if (!(ctx.entity() instanceof Avatar.MolangContext context) || context.owner == null || context.owner.getYsmRuntime() == null)
                return false;
            for (var action : context.owner.getYsmRuntime().actions().all()) {
                if (context.owner.getYsmRuntime().actions().isActive(action.getId()))
                    return true;
            }
            return false;
        }

        private static Object controllerValue(ExecutionContext<?> ctx, String key) {
            if (!(ctx.entity() instanceof Avatar.MolangContext context))
                return 0f;
            Object value = context.controller.getPath(key);
            return value == null ? 0f : value;
        }

        private static Function equipmentFunction(HandMode mode) {
            return new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
                    return mode == HandMode.ARMOR ? armor(context, arguments) : handItem(context, arguments, mode);
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return size == 2 || size == 3;
                }
            };
        }

        private static Object handItem(ExecutionContext<?> ctx, Function.ArgumentCollection args, HandMode mode) {
            Avatar.MolangContext context = ctx.entity() instanceof Avatar.MolangContext molangContext ? molangContext : null;
            if (context == null || !(context.entity instanceof LivingEntity living) || args.size() < 2) {
                return null;
            }
            EquipmentSlot slot = parseSlot(args.getAsString(ctx, 0));
            if (slot == null || isArmorSlot(slot)) {
                return 0;
            }
            InteractionHand hand = slot == EquipmentSlot.OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            Minecraft client = Minecraft.getInstance();
            boolean localOwner = context.owner != null && context.owner.isHost && client != null
                    && YsmInputState.ownsInput(true, living, client.player)
                    && !YsmParticleRuntime.isPreview(context.owner.renderMode);
            var actions = YsmInputState.device().hands();
            if (mode == HandMode.SWING && !actions.swinging(living, hand, localOwner)) {
                return 0;
            }
            if (mode == HandMode.USE && !actions.using(living, hand, localOwner)) {
                return 0;
            }
            return itemMatches(living.getItemBySlot(slot), args.getAsString(ctx, 1)) ? 1 : 0;
        }

        private static Object armor(ExecutionContext<?> ctx, Function.ArgumentCollection args) {
            Avatar.MolangContext context = ctx.entity() instanceof Avatar.MolangContext molangContext ? molangContext : null;
            if (context == null || !(context.entity instanceof LivingEntity living) || args.size() < 2) {
                return null;
            }
            EquipmentSlot slot = parseSlot(args.getAsString(ctx, 0));
            if (slot == null || !isArmorSlot(slot)) {
                return null;
            }
            String expected = args.getAsString(ctx, 1);
            if (expected != null && expected.startsWith(":")) {
                return 0;
            }
            return itemMatches(living.getItemBySlot(slot), expected) ? 1 : 0;
        }

        private static Object ride(ExecutionContext<?> ctx, Function.ArgumentCollection args) {
            Avatar.MolangContext context = ctx.entity() instanceof Avatar.MolangContext molangContext ? molangContext : null;
            if (context == null || !(context.entity instanceof LivingEntity living) || args.size() < 2) {
                return null;
            }
            String type = args.getAsString(ctx, 0);
            String expected = args.getAsString(ctx, 1);
            if (expected == null || expected.isBlank()) {
                return 0;
            }
            Entity target;
            if ("vehicle".equals(type)) {
                target = living.getVehicle();
            } else if ("passenger".equals(type)) {
                target = living.getFirstPassenger();
            } else {
                return 0;
            }
            if (target == null || !target.isAlive()) {
                return 0;
            }
            return entityMatches(target.getType(), expected) ? 1 : 0;
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
                String actual = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                return actual.equals(expected.substring(1));
            }
            if (expected.startsWith("#")) {
                Identifier id = identifier(expected.substring(1));
                return id != null && !stack.isEmpty() && stack.is(TagKey.create(Registries.ITEM, id));
            }
            if (expected.startsWith(":")) {
                return YsmHandItemType.matches(stack, expected.substring(1));
            }
            return false;
        }

        private static boolean entityMatches(EntityType<?> type, String expected) {
            if (type == null || expected == null || expected.isBlank())
                return false;
            if (expected.startsWith("$")) {
                Identifier id = identifier(expected.substring(1));
                return id != null && id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
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

        private static YsmAnimationClip.LoopMode loopModeFromCtrlValue(int value, YsmAnimationClip.LoopMode fallback) {
            return switch (value) {
                case 10 -> YsmAnimationClip.LoopMode.LOOP;
                case 11 -> YsmAnimationClip.LoopMode.ONCE;
                case 12 -> YsmAnimationClip.LoopMode.HOLD_ON_LAST_FRAME;
                default -> fallback;
            };
        }

        private interface ContextPredicate {
            boolean test(Avatar.MolangContext context);
        }

        private enum HandMode {
            HOLD,
            USE,
            SWING,
            ARMOR
        }
    }

    public static class YsmBinding implements ObjectBinding {
        private static final Set<String> UNAVAILABLE_CURIOS = Set.of(
                "has_any_curios", "has_any_curios_with_any_tag", "has_any_curios_with_all_tags");
        private final Map<String, Object> values = new HashMap<>();

        public static String optionalBindingUnavailableReason(String name) {
            return name != null && UNAVAILABLE_CURIOS.contains(name.toLowerCase(java.util.Locale.ROOT))
                    ? "CAPABILITY_UNAVAILABLE: Curios bridge is not implemented; reference Fabric no-op only"
                    : null;
        }

        public YsmBinding() {
            values.put("rendering_in_inventory", (Variable) ctx -> {
                Avatar.MolangContext context = context(ctx);
                return context.entity == null ? null : context.isYsmModelPreview();
            });
            values.put("rendering_in_paperdoll", (Variable) ctx -> {
                Avatar.MolangContext context = context(ctx);
                return context.entity == null ? null : context.isYsmGuiPreview();
            });
            values.put("is_first_person", (Variable) ctx -> context(ctx).is_first_person);
            values.put("first_person", (Variable) ctx -> context(ctx).is_first_person);
            values.put("weather", (Variable) YsmBinding::weather);
            values.put("dimension_name", (Variable) YsmBinding::dimensionName);
            values.put("fps", (Variable) ctx -> {
                Minecraft client = Minecraft.getInstance();
                return client == null ? null : client.getFps();
            });
            values.put("time_delta", (Variable) ctx -> context(ctx).delta_time);
            values.put("head_yaw", (Variable) ctx -> context(ctx).ysm_head_yaw);
            values.put("head_pitch", (Variable) ctx -> context(ctx).ysm_head_pitch);
            values.put("ground_speed2", (Variable) YsmBinding::groundSpeed2);
            values.put("is_open_air", (Variable) YsmBinding::isOpenAir);
            values.put("block_light", (Variable) YsmBinding::blockLight);
            values.put("sky_light", (Variable) YsmBinding::skyLight);
            values.put("is_passenger", entityVariable(Entity::isPassenger));
            values.put("is_sleep", entityVariable(entity -> entity.getPose() == Pose.SLEEPING));
            values.put("is_sneak", entityVariable(entity -> entity.onGround() && entity.getPose() == Pose.CROUCHING));
            values.put("eye_in_water", entityVariable(Entity::isUnderWater));
            values.put("frozen_ticks", entityVariable(Entity::getTicksFrozen));
            values.put("air_supply", entityVariable(Entity::getAirSupply));
            values.put("input_vertical", (Variable) ctx -> directionalInput(ctx, false));
            values.put("input_horizontal", (Variable) ctx -> directionalInput(ctx, true));
            values.put("xxa", (Variable) ctx -> input(ctx, "xxa"));
            values.put("yya", (Variable) ctx -> input(ctx, "yya"));
            values.put("zza", (Variable) ctx -> input(ctx, "zza"));
            values.put("keyboard", inputFunction(false));
            values.put("mouse", inputFunction(true));
            values.put("sync", new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> ctx, ArgumentCollection args) {
                    Avatar.MolangContext context = context(ctx);
                    if (context.owner == null || context.owner.getYsmRuntime() == null) {
                        return null;
                    }
                    return context.owner.getYsmRuntime().functions().recordSync(ctx, args);
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return size <= 16;
                }
            });
            values.put("defer", (Function) (ctx, args) -> {
                Avatar.MolangContext context = context(ctx);
                if (context.owner == null || context.owner.getYsmRuntime() == null) {
                    return null;
                }
                return context.owner.getYsmRuntime().functions().captureDefer(ctx, args);
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
            values.put("play_sound", soundFunction("play_sound", 2, 5));
            values.put("stop_sound", soundFunction("stop_sound", 1, 2));
            values.put("stop_all_sounds", soundFunction("stop_all_sounds", 0, 1));
            values.put("first_order", filterFunction(false));
            values.put("second_order", filterFunction(true));
            values.put("food_level", (Variable) YsmBinding::foodLevel);
            values.put("is_local_player", (Variable) YsmBinding::isLocalPlayer);
            values.put("local_player", (Variable) YsmBinding::isLocalPlayer);
            values.put("person_view", (Variable) ctx -> context(ctx).entity == null ? null : context(ctx).ysm_camera_type);
            values.put("first_person_mod_hide", (Variable) ctx -> context(ctx).entity instanceof Player
                    ? context(ctx).ysm_camera_type == CameraType.FIRST_PERSON.ordinal() : null);
            values.put("main_hand", (Function) (ctx, args) -> itemQuery(mainHand(context(ctx)), ctx, args, 0));
            values.put("off_hand", (Function) (ctx, args) -> itemQuery(offHand(context(ctx)), ctx, args, 0));
            values.put("equipped_item", (Function) (ctx, args) -> itemQuery(equippedItem(context(ctx), args.size() > 0 ? args.getAsString(ctx, 0) : ""), ctx, args, 1));
            values.put("has_mainhand", (Variable) ctx -> equipmentPresent(ctx, EquipmentSlot.MAINHAND));
            values.put("has_offhand", (Variable) ctx -> equipmentPresent(ctx, EquipmentSlot.OFFHAND));
            values.put("has_helmet", (Variable) ctx -> equipmentPresent(ctx, EquipmentSlot.HEAD));
            values.put("has_chest_plate", (Variable) ctx -> equipmentPresent(ctx, EquipmentSlot.CHEST));
            values.put("has_leggings", (Variable) ctx -> equipmentPresent(ctx, EquipmentSlot.LEGS));
            values.put("has_boots", (Variable) ctx -> equipmentPresent(ctx, EquipmentSlot.FEET));
            for (String name : top.aihmc.omnimorph.model.ysm.YsmWeaponQuery.NAMES) {
                values.put(name, livingVariable((context, entity) ->
                        top.aihmc.omnimorph.model.ysm.YsmWeaponQuery.evaluate(name, context, entity)));
            }
            values.put("armor_value", livingVariable((context, entity) -> entity.getArmorValue()));
            values.put("hurt_time", livingVariable((context, entity) -> entity.hurtTime));
            values.put("swinging", livingVariable((context, entity) ->
                    top.aihmc.omnimorph.model.ysm.YsmHandQuery.swinging(context)));
            values.put("swing_time", livingVariable((context, entity) ->
                    top.aihmc.omnimorph.model.ysm.YsmHandQuery.swingTicks(context)));
            values.put("attack_time", livingVariable((context, entity) ->
                    top.aihmc.omnimorph.model.ysm.YsmHandQuery.attackProgress(context)));
            values.put("entity_type", (Variable) YsmBinding::entityType);
            values.put("elytra_rot_x", (Variable) ctx -> elytraRotation(ctx, 0));
            values.put("elytra_rot_y", (Variable) ctx -> elytraRotation(ctx, 1));
            values.put("elytra_rot_z", (Variable) ctx -> elytraRotation(ctx, 2));
            values.put("has_elytra", (Variable) YsmBinding::hasElytra);
            values.put("is_close_eyes", (Variable) ctx -> {
                Avatar.MolangContext context = context(ctx);
                if (!(context.entity instanceof LivingEntity living)) {
                    return null;
                }
                float phase = (context.ysm_render_ticks
                        + Math.abs(living.getUUID().getLeastSignificantBits()) % 10) % 90f;
                return living.isSleeping() || phase > 85f && phase < 90f;
            });
            values.put("texture_name", (Variable) ctx -> {
                Avatar.MolangContext context = context(ctx);
                if (!(context.entity instanceof Player) || context.owner == null
                        || context.owner.getYsmRuntime() == null) {
                    return null;
                }
                return context.owner.getYsmRuntime().textureId();
            });
            values.put("mod_version", new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
                    String modId = arguments.getAsString(context, 0);
                    return modId != null && PlatformUtils.isModLoaded(modId)
                            ? PlatformUtils.getModVersion(modId) : null;
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return size == 1;
                }
            });
            values.put("relative_block_name", relativeBlockFunction(false));
            values.put("relative_block_name_any", relativeBlockFunction(true));
            values.put("equipped_enchantment_level", equippedEnchantmentFunction());
            values.put("in_shield_block_cooldown", (Variable) ctx -> {
                Avatar.MolangContext context = context(ctx);
                if (!(context.entity instanceof Player player)) {
                    return null;
                }
                // The reference's legacy name now exposes the player frame state's isBlocking value.
                return context.owner != null && context.owner.getYsmRuntime() != null && player.isBlocking();
            });
            values.put("boat_left_paddle", entityVariable(entity -> boat(entity) != null
                    && boat(entity).getPaddleState(AbstractBoat.PADDLE_LEFT)));
            values.put("boat_right_paddle", entityVariable(entity -> boat(entity) != null
                    && boat(entity).getPaddleState(AbstractBoat.PADDLE_RIGHT)));
            values.put("boat_left_rowing_time", (Variable) ctx -> boatRowingTime(ctx, AbstractBoat.PADDLE_LEFT));
            values.put("boat_right_rowing_time", (Variable) ctx -> boatRowingTime(ctx, AbstractBoat.PADDLE_RIGHT));
            values.put("boat_paddle_scale", entityVariable(entity -> 1f));
            values.put("boat_body_offset_y", entityVariable(entity -> {
                AbstractBoat boat = boat(entity);
                return boat instanceof Raft || boat instanceof ChestRaft
                        ? -((0.8888889f - 1f / 3f) * 0.5625f * 16f + 1f) : 0f;
            }));
            values.put("boat_body_offset_z", entityVariable(entity -> boat(entity) instanceof AbstractChestBoat
                    ? -0.15f * 16f : 0f));
            values.put("shoot_item_id", (Variable) YsmBinding::shootItemId);
            values.put("is_player", (Variable) ctx -> context(ctx).entity instanceof LivingEntity
                    ? "player".equals(entityType(ctx)) : null);
            values.put("dump_mods", (Variable) YsmBinding::dumpMods);
            values.put("dump_effects", (Variable) YsmBinding::dumpEffects);
            values.put("dump_biome", (Variable) YsmBinding::dumpBiome);
            // Preserve the reference Fabric NOOP contract, without claiming a working Curios bridge.
            for (String name : UNAVAILABLE_CURIOS) {
                values.put(name, (Function) (ctx, args) -> null);
            }
            values.put("particle", particleFunction(false));
            values.put("abs_particle", particleFunction(true));
            values.put("perlin_noise", new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> ctx, ArgumentCollection args) {
                    if (!(ctx.entity() instanceof Avatar.MolangContext context) || context.entity == null) {
                        return null;
                    }
                    if (args.size() < 2) {
                        return 0f;
                    }
                    int seed = args.getAsInt(ctx, 0);
                    float x = args.getAsFloat(ctx, 1);
                    float y = args.size() > 2 ? args.getAsFloat(ctx, 2) : 0f;
                    float z = args.size() > 3 ? args.getAsFloat(ctx, 3) : 0f;
                    return YsmPerlinNoise.sample(seed, x, y, z);
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return size >= 2;
                }
            });
            values.put("bone_param", new UnavailableBinding("ysm.bone_param"));
            values.put("bone_rot", boneFunction(YsmBoneQuery.Channel.ROTATION));
            values.put("bone_pos", boneFunction(YsmBoneQuery.Channel.POSITION));
            values.put("bone_scale", boneFunction(YsmBoneQuery.Channel.SCALE));
            values.put("bone_pivot_abs", boneFunction(YsmBoneQuery.Channel.PIVOT_ABS));
        }

        @Override
        public Object getProperty(String name) {
            Object value = values.get(name.toLowerCase());
            return value != null ? value : new UnavailableBinding("ysm." + name, "unknown binding");
        }

        private static Avatar.MolangContext context(ExecutionContext<?> ctx) {
            return ctx.entity() instanceof Avatar.MolangContext context ? context : EMPTY_CONTEXT;
        }

        private static Variable entityVariable(java.util.function.Function<Entity, Object> getter) {
            return execution -> {
                Entity entity = context(execution).entity;
                return entity == null ? null : getter.apply(entity);
            };
        }

        private static Variable livingVariable(
                java.util.function.BiFunction<Avatar.MolangContext, LivingEntity, Object> getter) {
            return execution -> {
                Avatar.MolangContext context = context(execution);
                return context.entity instanceof LivingEntity living ? getter.apply(context, living) : null;
            };
        }

        private static Function soundFunction(String operation, int minimum, int maximum) {
            return new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> execution, ArgumentCollection arguments) {
                    Avatar.MolangContext context = context(execution);
                    if (context.entity == null) {
                        return null;
                    }
                    if (context.owner == null || context.owner.getYsmRuntime() == null) {
                        return false;
                    }
                    return context.owner.getYsmRuntime().sounds().execute(context, execution, arguments, operation);
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return size >= minimum && size <= maximum;
                }
            };
        }

        private static Function particleFunction(boolean absolute) {
            return new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> ctx, ArgumentCollection args) {
                    Avatar.MolangContext context = context(ctx);
                    if (context.owner == null || context.owner.getYsmRuntime() == null) {
                        return null;
                    }
                    return context.owner.getYsmRuntime().particles().execute(context, ctx, args, absolute);
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return size >= 1;
                }
            };
        }

        private static Function inputFunction(boolean mouse) {
            return new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> ctx, ArgumentCollection args) {
                    Avatar.MolangContext context = context(ctx);
                    if (context.owner == null || !context.owner.isHost || context.entity == null) {
                        return false;
                    }
                    Minecraft client = Minecraft.getInstance();
                    boolean owner = client != null
                            && YsmInputState.ownsInput(context.owner.isHost, context.entity, client.player);
                    YsmInputState input = YsmInputState.device();
                    input.updateReadiness(client);
                    return mouse ? input.mouse(owner, ctx, args) : input.keyboard(owner, ctx, args);
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return mouse ? size == 1 : size >= 1;
                }
            };
        }

        private static Function boneFunction(YsmBoneQuery.Channel channel) {
            return new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> ctx, ArgumentCollection args) {
                    if (args.size() != 1) {
                        return null;
                    }
                    String name = args.getAsString(ctx, 0);
                    Avatar.MolangContext context = context(ctx);
                    if (name == null || name.isEmpty() || context.owner == null
                            || context.owner.getYsmRuntime() == null) {
                        return null;
                    }
                    var runtime = context.owner.getYsmRuntime();
                    boolean firstPerson = context.is_first_person != 0f;
                    var parts = runtime.getAnimationParts(name, firstPerson);
                    if (!parts.isEmpty() && channel == YsmBoneQuery.Channel.PIVOT_ABS) {
                        runtime.boneQueries().trackPivot(parts.getFirst(), firstPerson);
                    }
                    return parts.isEmpty() ? null : new YsmBoneQuery(parts.getFirst(), channel, runtime.boneQueries(), firstPerson);
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return size == 1;
                }
            };
        }

        private static Function filterFunction(boolean secondOrder) {
            return new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> ctx, ArgumentCollection args) {
                    if (!(ctx.entity() instanceof Avatar.MolangContext context) || context.entity == null) {
                        return null;
                    }
                    if (args.size() < 2) {
                        return 0;
                    }
                    String name = args.getAsString(ctx, 0);
                    if (name == null || name.isEmpty()) {
                        return 0;
                    }
                    YsmFilterRuntime.Domain domain = context.ysmFilterDomain();
                    float input = args.getAsFloat(ctx, 1);
                    float first = args.size() >= 3 ? args.getAsFloat(ctx, 2) : 1f;
                    if (!secondOrder) {
                        return context.filters.firstOrder(domain, name, input, first);
                    }
                    float damping = args.size() >= 4 ? args.getAsFloat(ctx, 3) : 1f;
                    float response = args.size() >= 5 ? args.getAsFloat(ctx, 4) : 1f;
                    return context.filters.secondOrder(domain, name, input, first, damping, response);
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return size >= 2;
                }
            };
        }

        private static Object weather(ExecutionContext<?> ctx) {
            Avatar.MolangContext context = context(ctx);
            Entity entity = context.entity;
            if (entity == null || entity.level() == null)
                return null;
            if (entity.level().isThundering())
                return 2;
            return entity.level().isRaining() ? 1 : 0;
        }

        private static String dimensionName(ExecutionContext<?> ctx) {
            Entity entity = context(ctx).entity;
            // The reference exposes the dimension key's registry, rather than its value identifier.
            return entity == null || entity.level() == null ? null : entity.level().dimension().registry().toString();
        }

        private static Object groundSpeed2(ExecutionContext<?> ctx) {
            Avatar.MolangContext context = context(ctx);
            return context.entity == null ? null
                    : top.aihmc.omnimorph.model.ysm.YsmMovementQuery.groundSpeed(context);
        }

        private static Object isOpenAir(ExecutionContext<?> ctx) {
            Avatar.MolangContext context = context(ctx);
            Entity entity = context.entity;
            if (entity == null || entity.level() == null) {
                return null;
            }
            BlockPos pos = entity.blockPosition();
            return entity.level().canSeeSky(pos)
                    && entity.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos)
                            .getY() <= pos.getY();
        }

        private static Object blockLight(ExecutionContext<?> ctx) {
            Entity entity = context(ctx).entity;
            if (entity == null || entity.level() == null) {
                return null;
            }
            return entity.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, entity.blockPosition());
        }

        private static Object skyLight(ExecutionContext<?> ctx) {
            Entity entity = context(ctx).entity;
            if (entity == null || entity.level() == null) {
                return null;
            }
            return entity.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, entity.blockPosition());
        }

        private static Object foodLevel(ExecutionContext<?> ctx) {
            Entity entity = context(ctx).entity;
            if (!(entity instanceof LivingEntity)) {
                return null;
            }
            return entity instanceof Player player ? player.getFoodData().getFoodLevel() : 20;
        }

        private static Object input(ExecutionContext<?> ctx, String field) {
            Entity entity = context(ctx).entity;
            if (!(entity instanceof LivingEntity living)) {
                return null;
            }
            return switch (field) {
                case "xxa" -> living.xxa;
                case "yya" -> living.yya;
                case "zza" -> living.zza;
                default -> throw new IllegalArgumentException("Unknown living input axis: " + field);
            };
        }

        private static Object directionalInput(ExecutionContext<?> execution, boolean horizontal) {
            Avatar.MolangContext context = context(execution);
            if (context.entity == null) {
                return null;
            }
            double x = context.pos_delta_x;
            double z = context.pos_delta_z;
            if (Math.sqrt(x * x + z * z) < 1.0E-4d) {
                return 0d;
            }
            float movementAngle = (float) net.minecraft.util.Mth.atan2(z, x) * net.minecraft.util.Mth.RAD_TO_DEG;
            float viewAngle = context.entity.getViewYRot(context.ysm_partial_tick);
            float relativeAngle = net.minecraft.util.Mth.wrapDegrees(movementAngle
                    - (90f - net.minecraft.util.Mth.wrapDegrees(-viewAngle)));
            float radians = relativeAngle * net.minecraft.util.Mth.DEG_TO_RAD;
            return (double) (horizontal ? net.minecraft.util.Mth.sin(radians) : net.minecraft.util.Mth.cos(radians));
        }

        private static float isLocalPlayer(ExecutionContext<?> ctx) {
            Avatar.MolangContext context = context(ctx);
            Minecraft minecraft = Minecraft.getInstance();
            return context.entity != null && minecraft != null && minecraft.player != null
                    && context.entity.getUUID().equals(minecraft.player.getUUID()) ? 1f : 0f;
        }

        private static Object elytraRotation(ExecutionContext<?> evaluation, int axis) {
            Avatar.MolangContext context = context(evaluation);
            if (!(context.entity instanceof LivingEntity living)) {
                return null;
            }
            float radians = switch (axis) {
                case 0 -> living.elytraAnimationState.getRotX(context.ysm_partial_tick);
                case 1 -> living.elytraAnimationState.getRotY(context.ysm_partial_tick);
                default -> living.elytraAnimationState.getRotZ(context.ysm_partial_tick);
            };
            return Math.toDegrees(radians);
        }

        private static Object hasElytra(ExecutionContext<?> evaluation) {
            if (!(context(evaluation).entity instanceof LivingEntity living)) {
                return null;
            }
            ItemStack chest = living.getItemBySlot(EquipmentSlot.CHEST);
            return !chest.isEmpty() && chest.has(DataComponents.GLIDER);
        }

        private static Function relativeBlockFunction(boolean any) {
            return new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> execution, ArgumentCollection arguments) {
                    Entity entity = context(execution).entity;
                    if (entity == null || entity.level() == null) {
                        return null;
                    }
                    double x = arguments.getAsDouble(execution, 0);
                    double y = arguments.getAsDouble(execution, 1);
                    double z = arguments.getAsDouble(execution, 2);
                    if (Math.abs(x) > 5d || Math.abs(y) > 5d || Math.abs(z) > 5d) {
                        return null;
                    }
                    BlockPos position = new BlockPos((int) Math.round(entity.getX() + x - 0.5d),
                            (int) Math.round(entity.getY() + y - 0.5d),
                            (int) Math.round(entity.getZ() + z - 0.5d));
                    Identifier actual = BuiltInRegistries.BLOCK.getKey(entity.level().getBlockState(position).getBlock());
                    if (actual == null) {
                        return null;
                    }
                    if (!any) {
                        return actual.toString();
                    }
                    for (int index = 3; index < arguments.size(); index++) {
                        String name = arguments.getAsString(execution, index);
                        Identifier expected = name == null ? null : Identifier.tryParse(name);
                        if (actual.equals(expected)) {
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return any ? size > 3 : size == 3;
                }
            };
        }

        private static Function equippedEnchantmentFunction() {
            return new Function() {
                @Override
                public Object evaluate(ExecutionContext<?> execution, ArgumentCollection arguments) {
                    if (!(context(execution).entity instanceof LivingEntity living)) {
                        return null;
                    }
                    String name = arguments.getAsString(execution, 0);
                    EquipmentSlot slot = name == null ? null : switch (name.toLowerCase(java.util.Locale.ROOT)) {
                        case "chest" -> EquipmentSlot.CHEST;
                        case "feet" -> EquipmentSlot.FEET;
                        case "head" -> EquipmentSlot.HEAD;
                        case "legs" -> EquipmentSlot.LEGS;
                        case "mainhand" -> EquipmentSlot.MAINHAND;
                        case "offhand" -> EquipmentSlot.OFFHAND;
                        default -> null;
                    };
                    if (slot == null) {
                        return null;
                    }
                    ItemStack stack = living.getItemBySlot(slot);
                    if (stack.isEmpty()) {
                        return 0;
                    }
                    int level = 0;
                    var registry = living.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                    for (int index = 1; index < arguments.size(); index++) {
                        String enchantment = arguments.getAsString(execution, index);
                        Identifier id = enchantment == null ? null : Identifier.tryParse(enchantment);
                        if (id != null) {
                            var holder = registry.get(id).orElse(null);
                            if (holder != null) {
                                level += EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
                            }
                        }
                    }
                    return level;
                }

                @Override
                public boolean validateArgumentSize(int size) {
                    return size >= 2;
                }
            };
        }

        private static Object boatRowingTime(ExecutionContext<?> ctx, int paddle) {
            Avatar.MolangContext context = context(ctx);
            if (context.entity == null) {
                return null;
            }
            AbstractBoat boat = boat(context.entity);
            return boat == null ? 0f : -boat.getRowingTime(paddle, context.ysm_partial_tick);
        }

        private static AbstractBoat boat(Entity entity) {
            if (entity instanceof AbstractBoat boat) {
                return boat;
            }
            return entity.getVehicle() instanceof AbstractBoat boat ? boat : null;
        }

        private static Object dumpMods(ExecutionContext<?> execution) {
            if (QueryVariables.canEmitDebugOutput(execution)) {
                PlatformUtils.getLoadedMods().entrySet().stream().sorted(Map.Entry.comparingByValue())
                        .forEach(mod -> org.figuramc.figura.FiguraMod.LOGGER.warn(
                                "Mod: display {}  id {}", mod.getValue(), mod.getKey()));
            }
            return null;
        }

        private static Object dumpEffects(ExecutionContext<?> execution) {
            Entity entity = context(execution).entity;
            if (entity == null || !QueryVariables.canEmitDebugOutput(execution)) {
                return null;
            }
            java.util.List<net.minecraft.world.effect.MobEffectInstance> effects = new java.util.ArrayList<>();
            if (entity instanceof LivingEntity living) {
                effects.addAll(living.getActiveEffects());
            } else if (entity instanceof net.minecraft.world.entity.projectile.arrow.Arrow arrow) {
                ItemStack pickup = arrow.getPickupItemStackOrigin();
                var contents = pickup == null ? null : pickup.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                if (contents != null) {
                    contents.forEachEffect(effects::add, 1f);
                }
            }
            for (var effect : effects) {
                org.figuramc.figura.FiguraMod.LOGGER.warn("Effect: display {}  name {}  lv={}",
                        effect.getEffect().value().getDisplayName().getString(99),
                        BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()), effect.getAmplifier() + 1);
            }
            return null;
        }

        private static Object dumpBiome(ExecutionContext<?> execution) {
            Entity entity = context(execution).entity;
            if (entity == null || !QueryVariables.canEmitDebugOutput(execution) || entity.level() == null) {
                return null;
            }
            var biome = entity.level().getBiome(entity.blockPosition());
            // These registry strings intentionally match the reference diagnostics, rather than biome/tag IDs.
            biome.unwrapKey().ifPresent(key -> org.figuramc.figura.FiguraMod.LOGGER.warn("Name {}", key.registry()));
            biome.tags().forEach(tag -> org.figuramc.figura.FiguraMod.LOGGER.warn("Tag {}", tag.registry()));
            return null;
        }

        private static String shootItemId(ExecutionContext<?> ctx) {
            if (!(context(ctx).entity instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow)) {
                return null;
            }
            // Use the native firing stack when present. Remote display overrides belong to M7.
            ItemStack weapon = arrow.getWeaponItem();
            return weapon == null ? "" : itemId(weapon);
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
            if (!(entity instanceof LivingEntity)) {
                return null;
            }
            if (entity instanceof Player) {
                return "player";
            }
            Identifier type = entity.getType() == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (type == null) {
                return "";
            }
            return type.toString().equals("touhou_little_maid:maid") ? "maid" : type.toString();
        }

        private static Object equipmentPresent(ExecutionContext<?> execution, EquipmentSlot slot) {
            if (!(execution.entity() instanceof Avatar.MolangContext context)
                    || !(context.entity instanceof LivingEntity living)) {
                return null;
            }
            return !living.getItemBySlot(slot).isEmpty();
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

    private static final class UnavailableBinding implements Variable, Function {
        private final String name;
        private final String reason;

        private UnavailableBinding(String name) {
            this(name, "reference contract unavailable");
        }

        private UnavailableBinding(String name, String reason) {
            this.name = name;
            this.reason = reason;
        }

        @Override
        public Object evaluate(ExecutionContext<?> context) {
            Avatar.MolangContext modelContext = context.entity() instanceof Avatar.MolangContext value ? value : null;
            if (modelContext != null && modelContext.owner != null
                    && modelContext.owner.getYsmRuntime() != null
                    && modelContext.owner.getYsmRuntime().encryptedSource()) {
                throw new UnsupportedOperationException("Encrypted YSM operation failed: runtime/unsupported-binding");
            }
            String location = modelContext != null && modelContext.owner != null
                    && modelContext.owner.getYsmRuntime() != null
                    ? " " + modelContext.owner.getYsmRuntime().functions().bindingDiagnosticContext() : "";
            throw new UnsupportedOperationException("Unsupported YSM binding: " + name
                    + " (" + reason + ")" + location);
        }

        @Override
        public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
            return evaluate(context);
        }
    }

    private static class FallbackBinding implements Variable, Function {
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
            // Bare references are parsed as zero-argument calls because this binding is also a Function.
            return arguments.size() == 0 ? evaluate(context) : 0f;
        }
    }
}
