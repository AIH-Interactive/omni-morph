package top.aihmc.omm;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Adapts parsed MOD DTOs to the same MOD's keyframe processor and evaluator. */
final class ReferenceJarAnimation {
    private final String prefix;
    private final Object parser;
    private final Object evaluator;
    private final Class<?> evaluatorType;
    private final Map<String, Double> queries;
    private final Map<List<?>, Object> timelines = new java.util.IdentityHashMap<>();

    ReferenceJarAnimation(String prefix, Map<String, Double> queries) throws Exception {
        this.prefix = prefix;
        this.queries = queries;
        Class<?> bindingType = Class.forName(prefix + ".molang.runtime.binding.ObjectBinding");
        Class<?> variableType = Class.forName(prefix + ".molang.runtime.Variable");
        Map<String, Object> variables = new HashMap<>();
        for (String name : queries.keySet()) {
            Object variable = Proxy.newProxyInstance(variableType.getClassLoader(), new Class<?>[]{variableType},
                (proxy, method, args) -> switch (method.getName()) {
                    case "evaluate" -> queries.get(name);
                    case "evaluateFloat" -> queries.get(name).floatValue();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "ConfiguredQuery[" + name + "]";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
            variables.put(name, variable);
        }
        Object query = binding(bindingType, variables);
        Object math = Class.forName(prefix + ".geckolib3.core.molang.builtin.MathBinding")
            .getField("INSTANCE").get(null);
        parser = Class.forName(prefix + ".geckolib3.core.molang.MolangParser").getConstructor(Map.class)
            .newInstance(Map.of("q", query, "query", query, "math", math));
        Object root = binding(bindingType, Map.of("q", query, "query", query, "math", math));
        Object engine = Class.forName(prefix + ".molang.MolangEngine")
            .getMethod("fromCustomBinding", bindingType).invoke(null, root);
        var engineField = parser.getClass().getDeclaredField("engine");
        engineField.setAccessible(true);
        engineField.set(parser, engine);
        evaluatorType = Class.forName(prefix + ".molang.runtime.ExpressionEvaluator");
        evaluator = evaluatorType.getMethod("evaluator").invoke(null);
    }

    List<?> channel(List<?> parsedFrames, boolean rotation) throws Exception {
        Class<?> cache = Class.forName(prefix + ".geckolib3.resource.GeckoLibCache");
        var poolField = cache.getDeclaredField("PARSER_POOL");
        poolField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var pool = (java.util.Queue<Object>) poolField.get(null);
        var saved = new ArrayList<>(pool);
        pool.clear();
        pool.add(parser);
        try {
            String mapperName = prefix.equals("com.micaftic.morpher")
                ? ".resource.bundle.AnimationMapper" : ".resource.YSMClientMapper";
            var parse = Class.forName(prefix + mapperName).getDeclaredMethod("parseKeyframes", List.class, boolean.class);
            parse.setAccessible(true);
            return (List<?>) parse.invoke(null, parsedFrames, rotation);
        } finally {
            pool.clear();
            pool.addAll(saved);
        }
    }

    Object sample(List<?> frames, float seconds) throws Exception {
        queries.put("anim_time", (double) seconds);
        float tick = seconds * 20;
        Object timeline = timelines.get(frames);
        if (timeline == null) {
            Class<?> providerType = Class.forName(prefix + ".geckolib3.util.InterpolationLookup$FrameTimeProvider");
            Object provider = Proxy.newProxyInstance(providerType.getClassLoader(), new Class<?>[]{providerType},
                (proxy, method, args) -> {
                    if (method.getName().equals("apply")) {
                        return args[0].getClass().getMethod("getEndTick").invoke(args[0]);
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
            timeline = Class.forName(prefix + ".geckolib3.util.InterpolationLookup")
                .getConstructor(List.class, float.class, providerType).newInstance(frames, 0f, provider);
            timelines.put(frames, timeline);
        }
        Object selected = timeline.getClass().getMethod("getAtTime", float.class).invoke(timeline, tick);
        Class<?> frameType = Class.forName(prefix + ".geckolib3.core.keyframe.bone.BoneKeyFrame");
        return frameType.getMethod("evaluate", evaluatorType, float.class).invoke(selected, evaluator, tick);
    }

    private static Object binding(Class<?> type, Map<String, Object> values) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getProperty" -> values.get((String) args[0]);
                case "toString" -> "ConfiguredBinding";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.toString());
            };
        });
    }
}
