package top.aihmc.omm;

import com.google.gson.JsonObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Configuration values at the original query boundary, scoped to each original Molang context. */
public final class ConfiguredMolangQueries {
    private static final String PREFIX = "top.aihmc.omnimorph.molang.runtime.";
    private static final Map<Object, Map<String, Double>> VALUES = new WeakHashMap<>();

    private ConfiguredMolangQueries() {}

    static void configure(Object context, JsonObject configuration) throws Exception {
        Map<String, Double> values = new HashMap<>();
        if (configuration.has("queries")) {
            Class<?> bindingType = Class.forName(PREFIX + "binding.MolangBindings");
            Object owner = context.getClass().getField("owner").get(context);
            Object binding = owner.getClass().getMethod("getAvatarBindings").invoke(owner);
            Class<?> variable = Class.forName(PREFIX + "Variable");
            for (var entry : configuration.getAsJsonObject("queries").entrySet()) {
                String name = entry.getKey().toLowerCase(java.util.Locale.ROOT);
                Object original = bindingType.getMethod("getProperty", String.class).invoke(binding, "query." + name);
                if (name.equals("anim_time") || !variable.isInstance(original)) {
                    throw new IllegalArgumentException("Query is not a configurable scalar (anim_time uses clip time): " + name);
                }
                double value = entry.getValue().getAsDouble();
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("Query must be finite: " + name);
                }
                values.put(name, value);
            }
        }
        synchronized (VALUES) {
            VALUES.put(context, Map.copyOf(values));
        }
    }

    public static Object bind(Object original, String name) throws Exception {
        String key = name.toLowerCase(java.util.Locale.ROOT);
        Class<?> variable = Class.forName(PREFIX + "Variable");
        if (!variable.isInstance(original)) {
            return original;
        }
        var entity = Class.forName(PREFIX + "ExecutionContext").getMethod("entity");
        return Proxy.newProxyInstance(variable.getClassLoader(), new Class<?>[]{variable}, (proxy, method, args) -> {
            if (method.getName().equals("evaluate")) {
                Object context = entity.invoke(args[0]);
                synchronized (VALUES) {
                    Map<String, Double> values = VALUES.get(context);
                    if (values != null && values.containsKey(key)) {
                        return values.get(key);
                    }
                }
            }
            try {
                return method.invoke(original, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        });
    }
}
