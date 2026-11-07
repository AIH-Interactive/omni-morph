package top.aihmc.omm;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Adapts parsed MOD DTOs to the same MOD's keyframe processor and evaluator. */
final class ReferenceJarAnimation {
    private final String prefix;
    private final Object engine;
    private final Class<?> engineType;
    private final Object evaluator;
    private final Class<?> evaluatorType;
    private final Map<String, Double> queries;

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
        Object root = binding(bindingType, Map.of("q", query, "query", query, "math", math));
        engineType = Class.forName(prefix + ".molang.MolangEngine");
        engine = engineType.getMethod("fromCustomBinding", bindingType).invoke(null, root);
        evaluatorType = Class.forName(prefix + ".molang.runtime.ExpressionEvaluator");
        evaluator = evaluatorType.getMethod("evaluator").invoke(null);
    }

    List<?> channel(List<?> parsedFrames, boolean rotation) throws Exception {
        Class<?> rawType = Class.forName(prefix + ".geckolib3.core.keyframe.bone.RawBoneKeyFrame");
        Class<?> easingType = Class.forName(prefix + ".geckolib3.core.keyframe.bone.EasingType");
        List<Object> frames = new ArrayList<>();
        for (Object parsed : parsedFrames) {
            Class<?> parsedType = parsed.getClass();
            Object raw = rawType.getConstructor().newInstance();
            rawType.getField("startTick").setDouble(raw, parsedType.getField("timestamp").getFloat(parsed) * 20.0);
            int mode = parsedType.getField("interpolationMode").getInt(parsed);
            String easing = switch (mode) {
                case 0 -> "LINEAR";
                case 1 -> "STEP";
                case 2 -> "CATMULLROM";
                default -> throw new IllegalArgumentException("Unsupported JAR keyframe mode: " + mode);
            };
            rawType.getField("easingType").set(raw, easingType.getField(easing).get(null));
            boolean hasPre = parsedType.getField("hasPreData").getBoolean(parsed);
            rawType.getField("contiguous").setBoolean(raw, !hasPre);
            Object[] post = (Object[]) parsedType.getField("postData").get(parsed);
            Object[] pre = hasPre ? (Object[]) parsedType.getField("preData").get(parsed) : post;
            assign(raw, rawType, "pre", pre);
            assign(raw, rawType, "post", post);
            frames.add(raw);
        }
        Class<?> processor = Class.forName(prefix + ".geckolib3.core.keyframe.bone.BoneKeyFrameProcessor");
        return (List<?>) processor.getMethod("process", List.class, boolean.class).invoke(null, frames, rotation);
    }

    Object sample(List<?> frames, float seconds) throws Exception {
        queries.put("anim_time", (double) seconds);
        float tick = seconds * 20;
        Object selected = frames.getFirst();
        for (Object frame : frames) {
            if (frame.getClass().getField("startTick").getFloat(frame) <= tick) {
                selected = frame;
            }
        }
        Class<?> frameType = Class.forName(prefix + ".geckolib3.core.keyframe.bone.BoneKeyFrame");
        return frameType.getMethod("evaluate", evaluatorType, float.class).invoke(selected, evaluator, tick);
    }

    private void assign(Object target, Class<?> type, String side, Object[] values) throws Exception {
        for (int axis = 0; axis < 3; axis++) {
            String field = side + "XYZ".charAt(axis);
            if (values[axis] instanceof Number number) {
                type.getField(field).setDouble(target, number.doubleValue());
            } else {
                Object expressions = engineType.getMethod("parse", String.class).invoke(engine, values[axis].toString());
                Object value = Class.forName(prefix + ".geckolib3.core.molang.value.MolangValue")
                    .getConstructor(List.class, boolean.class).newInstance(expressions, false);
                type.getField(field + "Value").set(target, value);
            }
        }
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
