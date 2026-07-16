package org.figuramc.figura.molang.runtime.binding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.molang.runtime.AssignableVariable;
import org.figuramc.figura.molang.runtime.ExecutionContext;
import org.figuramc.figura.molang.runtime.Function;
import org.figuramc.figura.molang.runtime.Variable;

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
        private final Map<String, Variable> states = new HashMap<>();

        public CtrlBinding() {
            states.put("death", ctx -> bool(ctx, m -> m.health <= 0f));
            states.put("sleep", ctx -> bool(ctx, m -> m.is_sleeping != 0f));
            states.put("swim", ctx -> bool(ctx, m -> m.is_swimming != 0f));
            states.put("swim_stand", ctx -> bool(ctx, m -> m.is_in_water != 0f && m.is_on_ground == 0f));
            states.put("jump", ctx -> bool(ctx, m -> m.is_on_ground == 0f && m.is_in_water == 0f));
            states.put("sneak", ctx -> bool(ctx, m -> m.is_sneaking != 0f && m.ground_speed > 0.05f));
            states.put("sneaking", ctx -> bool(ctx, m -> m.is_sneaking != 0f));
            states.put("run", ctx -> bool(ctx, m -> m.is_sprinting != 0f && m.ground_speed > 0.05f));
            states.put("walk", ctx -> bool(ctx, m -> m.is_on_ground != 0f && m.is_sprinting == 0f && m.is_sneaking == 0f && m.ground_speed > 0.05f));
            states.put("idle", ctx -> bool(ctx, m -> m.is_on_ground != 0f && m.ground_speed <= 0.05f && m.is_sneaking == 0f));
            states.put("attacked", ctx -> bool(ctx, m -> m.hurt_time > 0f));
            states.put("ride", ctx -> bool(ctx, m -> m.is_riding != 0f));
            states.put("riding", ctx -> bool(ctx, m -> m.is_riding != 0f));
            states.put("use", ctx -> bool(ctx, m -> m.is_using_item != 0f));
            states.put("swing", ctx -> bool(ctx, m -> m.is_swinging != 0f));
            states.put("playing_extra_animation", CtrlBinding::playingExtraAnimation);
            states.put("state_continue", ctx -> 2f);
            states.put("state_stop", ctx -> 3f);
            states.put("state_pause", ctx -> 4f);
            states.put("state_bypass", ctx -> 5f);
            states.put("loop", ctx -> 10f);
            states.put("play_once", ctx -> 11f);
            states.put("hold_on_last_frame", ctx -> 12f);
        }

        @Override
        public Object getProperty(String name) {
            return states.get(name.toLowerCase());
        }

        private static float bool(ExecutionContext<?> ctx, ContextPredicate predicate) {
            if (ctx.entity() instanceof Avatar.MolangContext context && predicate.test(context))
                return 1f;
            return 0f;
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

        private interface ContextPredicate {
            boolean test(Avatar.MolangContext context);
        }
    }

    public static class YsmBinding implements ObjectBinding {
        private final Map<String, Variable> values = new HashMap<>();

        public YsmBinding() {
            values.put("rendering_in_inventory", ctx -> context(ctx).rendering_in_inventory);
            values.put("rendering_in_paperdoll", ctx -> context(ctx).rendering_in_paperdoll);
            values.put("is_first_person", ctx -> context(ctx).is_first_person);
            values.put("first_person", ctx -> context(ctx).is_first_person);
            values.put("weather", YsmBinding::weather);
            values.put("is_open_air", YsmBinding::isOpenAir);
        }

        @Override
        public Object getProperty(String name) {
            return values.get(name.toLowerCase());
        }

        private static Avatar.MolangContext context(ExecutionContext<?> ctx) {
            return ctx.entity() instanceof Avatar.MolangContext context ? context : EMPTY_CONTEXT;
        }

        private static float weather(ExecutionContext<?> ctx) {
            Avatar.MolangContext context = context(ctx);
            Entity entity = context.entity;
            if (entity == null || entity.level() == null)
                return 0f;
            return entity.level().isRainingAt(entity.blockPosition()) ? 1f : 0f;
        }

        private static float isOpenAir(ExecutionContext<?> ctx) {
            Avatar.MolangContext context = context(ctx);
            Entity entity = context.entity;
            if (entity == null || entity.level() == null)
                return 0f;
            BlockPos pos = entity.blockPosition();
            return entity.level().canSeeSky(pos) ? 1f : 0f;
        }

        private static final Avatar.MolangContext EMPTY_CONTEXT = new Avatar.MolangContext(null);
    }
}
