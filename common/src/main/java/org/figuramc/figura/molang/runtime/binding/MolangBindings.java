package org.figuramc.figura.molang.runtime.binding;

import org.figuramc.figura.molang.runtime.AssignableVariable;
import org.figuramc.figura.molang.runtime.ExecutionContext;
import org.figuramc.figura.molang.runtime.Function;

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
        bindings.put("query", new QueryBinding());

        // Register q.* as alias for query.*
        bindings.put("q", new QueryBinding());
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
}
