package top.aihmc.omm;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Executes the supplied MOD's parser and evaluator without starting its game entrypoint. */
public final class JarEntryProbe {
    private JarEntryProbe() {
    }

    public static void main(String[] args) throws Exception {
        String backend = args[0];
        Path model = Path.of(args[1]);
        String prefix = backend.equals("spm")
            ? "com.micaftic.morpher" : "com.elfmcys.yesstevemodel";
        Object geometry;
        Object animations;
        if (backend.equals("spm")) {
            Class<?> parser = Class.forName(prefix + ".resource.YsmGeometryParsing");
            reportSource(parser);
            geometry = parser.getMethod("parseGeometry", byte[].class, int.class)
                .invoke(null, Files.readAllBytes(model.resolve("main.json")), 0);
            animations = Class.forName(prefix + ".resource.YsmAnimationParsing")
                .getMethod("parseAnimationFile", byte[].class)
                .invoke(null, Files.readAllBytes(model.resolve("animation.json")));
        } else {
            Class<?> parser = Class.forName(prefix + ".resource.YSMFolderDeserializer");
            reportSource(parser);
            try (AutoCloseable reader = (AutoCloseable) parser.getConstructor(Path.class).newInstance(model)) {
                var parseGeometry = parser.getDeclaredMethod("parseGeometry", byte[].class, int.class);
                var parseAnimations = parser.getDeclaredMethod("parseAnimations", byte[].class);
                parseGeometry.setAccessible(true);
                parseAnimations.setAccessible(true);
                geometry = parseGeometry.invoke(reader, Files.readAllBytes(model.resolve("main.json")), 0);
                animations = parseAnimations.invoke(reader, Files.readAllBytes(model.resolve("animation.json")));
            }
        }
        int bones = ((List<?>) geometry.getClass().getField("bones").get(geometry)).size();
        int clips = ((Map<?, ?>) animations.getClass().getField("animations").get(animations)).size();
        if (bones != 2 || clips != 1) {
            throw new AssertionError("Unexpected fixture parse: bones=" + bones + ", clips=" + clips);
        }
        System.out.println("PARSE PASS bones=" + bones + " clips=" + clips);

        Class<?> binding = Class.forName(prefix + ".molang.runtime.binding.ObjectBinding");
        Object query = binding(binding, Map.of("shift", 2.0));
        Object root = binding(binding, Map.of("query", query, "q", query));
        Class<?> engineType = Class.forName(prefix + ".molang.MolangEngine");
        Object engine = engineType.getMethod("fromCustomBinding", binding).invoke(null, root);
        Object expressions = engineType.getMethod("parse", String.class).invoke(engine, "q.shift * 3 + 1");
        Class<?> evaluatorType = Class.forName(prefix + ".molang.runtime.ExpressionEvaluator");
        Object evaluator = evaluatorType.getMethod("evaluator").invoke(null);
        Object value = evaluatorType.getMethod("evalAll", Iterable.class, boolean.class)
            .invoke(evaluator, expressions, false);
        double actual = ((Number) value).doubleValue();
        if (!Double.isFinite(actual) || Math.abs(actual - 7) > 1e-6) {
            throw new AssertionError("Molang result: " + actual);
        }
        reportSource(engineType);
        System.out.println("MOLANG PASS configured q.shift=2 result=" + actual);

        var queries = new java.util.HashMap<String, Double>();
        queries.put("shift", 2.0);
        queries.put("anim_time", 0.0);
        var animation = new ReferenceJarAnimation(prefix, queries);
        Object clip = ((Map<?, ?>) animations.getClass().getField("animations").get(animations)).get("compare");
        List<?> boneAnimations = (List<?>) clip.getClass().getField("boneAnimations").get(clip);
        Object rootBone = boneAnimations.getFirst();
        List<?> positions = animation.channel((List<?>) rootBone.getClass().getField("position").get(rootBone), false);
        Object position = animation.sample(positions, 1.0f);
        float x = position.getClass().getField("x").getFloat(position);
        if (!Float.isFinite(x) || Math.abs(x - 2) > 1e-6) {
            throw new AssertionError("Sampled root position: " + position);
        }
        System.out.println("ANIMATION PASS original keyframe processor/evaluate root=" + position);
        if (args.length > 2) {
            bake(prefix, backend, geometry, args[2].equals("gpu") ? model.resolve("texture.png") : null);
        }
    }

    private static void bake(String prefix, String backend, Object geometry, Path gpuTexture) throws Exception {
        Class<?> mapper = Class.forName(prefix + (backend.equals("spm")
            ? ".resource.bundle.GeometryBaker" : ".resource.YSMClientMapper"));
        Class<?> description = Class.forName(prefix + ".resource.models.GeometryDescription");
        Class<?> model = Class.forName("com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel");
        Class<?> scanner = Class.forName(prefix + (backend.equals("spm")
            ? ".resource.bundle.TextureAlphaAnalyzer" : ".resource.YSMClientMapper$TranslucencyScanner"));
        var lookup = java.lang.invoke.MethodHandles.privateLookupIn(mapper, java.lang.invoke.MethodHandles.lookup());
        try {
            Object context = lookup.findStatic(mapper, "buildContext",
                java.lang.invoke.MethodType.methodType(description, geometry.getClass())).invoke(geometry);
            Object baked = lookup.findStatic(mapper, "buildMesh",
                java.lang.invoke.MethodType.methodType(model, geometry.getClass(), description, int.class, scanner, boolean.class))
                .invoke(geometry, context, 0, null, false);
            System.out.println("BAKE PASS " + baked);
            {
                var vertices = new ReferenceVertexOutput(backend).render(baked, false);
                System.out.println("JAVA RENDER PASS original renderer vertices=" + vertices.size());
                String jarPath = Path.of(model.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
                JarNativeProbe.main(new String[]{backend, jarPath});
                model.getMethod("buildNativeCache").invoke(baked);
                var nativeVertices = new ReferenceVertexOutput(backend).render(baked, true);
                if (nativeVertices.size() != vertices.size()) {
                    throw new AssertionError("Java/SIMD vertex counts differ");
                }
                float maximumError = 0;
                for (int vertex = 0; vertex < vertices.size(); vertex++) {
                    for (int axis = 0; axis < 3; axis++) {
                        maximumError = Math.max(maximumError,
                            Math.abs(vertices.get(vertex)[axis] - nativeVertices.get(vertex)[axis]));
                    }
                }
                if (!Float.isFinite(maximumError) || maximumError > 1e-5f) {
                    throw new AssertionError("Java/SIMD position error: " + maximumError);
                }
                model.getMethod("freeNativeCache").invoke(baked);
                System.out.println("SIMD RENDER PASS original renderer vertices=" + nativeVertices.size()
                    + " maximumPositionError=" + maximumError);
                if (gpuTexture != null) {
                    ReferenceGpuWindow.run(backend, baked, gpuTexture);
                }
            }
        } catch (Throwable failure) {
            throw new RuntimeException("Original JAR geometry baking failed", failure);
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

    private static void reportSource(Class<?> type) {
        var source = type.getProtectionDomain().getCodeSource().getLocation();
        if (!source.getPath().endsWith(".jar")) {
            throw new AssertionError("Expected original JAR class: " + type.getName() + " from " + source);
        }
        System.out.println("SOURCE " + type.getName() + " " + source);
    }
}
