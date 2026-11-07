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
        if (!Boolean.getBoolean("omm.preview.fixture")) {
            loadModel(prefix, backend, model, args.length > 2 ? args[2] : "parse");
            return;
        }
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

    private static void loadModel(String prefix, String backend, Path path, String probe) throws Exception {
        Object raw = readModel(prefix, backend, path);
        Object entity = raw.getClass().getField("mainEntity").get(raw);
        Object geometry = entity.getClass().getField("mainModel").get(entity);
        if (geometry == null) {
            throw new IllegalArgumentException("Original reader returned no main geometry: " + path);
        }
        int bones = ((List<?>) geometry.getClass().getField("bones").get(geometry)).size();
        var animations = (Map<?, ?>) entity.getClass().getField("animationFiles").get(entity);
        var textures = (Map<?, ?>) entity.getClass().getField("textures").get(entity);
        System.out.println("ORIGINAL MODEL PARSE PASS bones=" + bones + " animationFiles=" + animations.keySet()
            + " textures=" + textures.keySet());
        if (probe.equals("bake")) {
            bake(prefix, backend, geometry, null, animations);
        } else if (probe.equals("gpu") || probe.equals("window")) {
            Path textureFile = Path.of(System.getProperty("omm.preview.output"), backend, "model-texture.png");
            decodeTexture(prefix, backend, raw, textures, textureFile);
            bake(prefix, backend, geometry, textureFile, animations, probe.equals("window"));
        } else if (!probe.equals("parse")) {
            throw new UnsupportedOperationException("General reference model window binding is not connected yet");
        }
    }

    static Object readModel(String prefix, String backend, Path path) throws Exception {
        if (Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ysm")) {
            byte[] bytes = Files.readAllBytes(path);
            if (backend.equals("spm")) {
                Class<?> parser = Class.forName(prefix + ".resource.MemoryModelParser");
                reportSource(parser);
                return parser.getMethod("parseLegacy", byte[].class, String.class)
                    .invoke(null, bytes, path.getFileName().toString());
            }
            Class<?> crypt = Class.forName("rip.ysm.security.YsmCrypt");
            reportSource(crypt);
            byte[] decrypted = (byte[]) crypt.getMethod("decryptYsmFile", byte[].class).invoke(null, (Object) bytes);
            Class<?> readerType = Class.forName(prefix + ".resource.YSMBinaryDeserializer");
            try (AutoCloseable reader = (AutoCloseable) readerType.getConstructor(byte[].class).newInstance((Object) decrypted)) {
                Object raw = readerType.getMethod("deserializeKeepOpen").invoke(reader);
                readerType.getMethod("parseYSMFooter", raw.getClass()).invoke(reader, raw);
                return raw;
            }
        }
        Class<?> readerType = Class.forName(prefix + ".resource.YSMFolderDeserializer");
        reportSource(readerType);
        try (var archive = backend.equals("oysm") && Files.isRegularFile(path)
                ? java.nio.file.FileSystems.newFileSystem(path) : null;
             AutoCloseable reader = (AutoCloseable) readerType.getConstructor(Path.class)
                 .newInstance(archive == null ? path : archive.getPath("/"))) {
            return readerType.getMethod("deserialize").invoke(reader);
        }
    }

    private static void bake(String prefix, String backend, Object geometry, Path gpuTexture) throws Exception {
        bake(prefix, backend, geometry, gpuTexture, Map.of());
    }

    private static void bake(String prefix, String backend, Object geometry, Path gpuTexture,
                             Map<?, ?> animations) throws Exception {
        bake(prefix, backend, geometry, gpuTexture, animations, false);
    }

    private static void bake(String prefix, String backend, Object geometry, Path gpuTexture,
                             Map<?, ?> animations, boolean cpuWindow) throws Exception {
        Class<?> model = Class.forName("com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel");
        try {
            Object baked = bakeGeometry(prefix, backend, geometry, gpuTexture);
            System.out.println("BAKE PASS " + baked);
            boolean translucent = (boolean) model.getMethod("isTranslucentTexture", int.class).invoke(baked, 0);
            System.out.println("ORIGINAL MATERIAL translucent=" + translucent);
            if (!cpuWindow && gpuTexture != null && backend.equals("spm") && translucent) {
                // Original SPM GpuRenderPath.tryRender declines translucent textures before GPU submission.
                cpuWindow = true;
                System.out.println("BACKEND requested=gpu actual=java reason=original-spm-translucent-gpu-declined");
            }
            var config = com.google.gson.JsonParser.parseString(Files.readString(
                Path.of(System.getProperty("omm.preview.output"), "effective-config.json"))).getAsJsonObject();
            ReferenceModelAnimation animation = config.has("animation")
                ? new ReferenceModelAnimation(prefix, baked, animations, config.getAsJsonObject("animation")) : null;
            if (cpuWindow) {
                boolean nativeSimd = System.getProperty("omm.preview.mode").equals("simd");
                if (nativeSimd) {
                    String jarPath = Path.of(model.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
                    JarNativeProbe.main(new String[]{backend, jarPath});
                    model.getMethod("buildNativeCache").invoke(baked);
                }
                try {
                    SceneLayout layout = SceneLayout.read();
                    var source = new ReferenceCpuScene(backend, baked, nativeSimd, animation, layout);
                    List<double[]> vertices = source.sample(0);
                    List<int[]> indices = vertices.stream()
                        .map(batch -> ReferenceVertexOutput.quadIndices(batch.length / 9)).toList();
                    CpuPreviewWindow.run(backend + " original " + (nativeSimd ? "SIMD" : "Java"),
                        vertices, indices, java.util.Collections.nCopies(vertices.size(), Files.readAllBytes(gpuTexture)),
                        source, backend.equals("oysm"));
                } finally {
                    if (nativeSimd) {
                        model.getMethod("freeNativeCache").invoke(baked);
                    }
                }
                return;
            }
            if (gpuTexture != null) {
                String jarPath = Path.of(model.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
                JarNativeProbe.main(new String[]{backend, jarPath});
                ReferenceGpuWindow.run(backend, baked, gpuTexture, animation);
                return;
            }
            {
                float[] parameters = animation == null
                    ? (float[]) model.getMethod("getBoneTransformData").invoke(baked) : animation.sample(0);
                var vertices = new ReferenceVertexOutput(backend).render(baked, false, parameters);
                System.out.println("JAVA RENDER PASS original renderer vertices=" + vertices.size());
                String jarPath = Path.of(model.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
                JarNativeProbe.main(new String[]{backend, jarPath});
                model.getMethod("buildNativeCache").invoke(baked);
                try {
                    var nativeVertices = new ReferenceVertexOutput(backend).render(baked, true, parameters);
                    if (nativeVertices.size() != vertices.size()) {
                        throw new AssertionError("Java/SIMD vertex counts differ: java=" + vertices.size()
                            + " simd=" + nativeVertices.size());
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
                    System.out.println("SIMD RENDER PASS original renderer vertices=" + nativeVertices.size()
                        + " maximumPositionError=" + maximumError);
                    if (animation != null && config.getAsJsonObject("animation").has("compareTime")) {
                        float time = config.getAsJsonObject("animation").get("compareTime").getAsFloat();
                        var compared = new ReferenceVertexOutput(backend).render(baked, false, animation.sampleTime(time));
                        if (vertices.size() != compared.size()) {
                            System.out.println("ANIMATION OUTPUT changed visible vertex count=" + compared.size());
                        } else {
                            float change = 0;
                            for (int vertex = 0; vertex < vertices.size(); vertex++) {
                                for (int axis = 0; axis < 3; axis++) {
                                    change = Math.max(change, Math.abs(vertices.get(vertex)[axis] - compared.get(vertex)[axis]));
                                }
                            }
                            System.out.println("ANIMATION OUTPUT maximumPositionChange=" + change + " compareTime=" + time);
                        }
                    }
                } finally {
                    model.getMethod("freeNativeCache").invoke(baked);
                }
            }
        } catch (Throwable failure) {
            throw new RuntimeException("Original JAR geometry baking failed", failure);
        }
    }

    static void decodeTexture(String prefix, String backend, Object raw, Map<?, ?> textures, Path textureFile) throws Exception {
        Object properties = raw.getClass().getField("properties").get(raw);
        String defaultTexture = (String) properties.getClass().getField("defaultTexture").get(properties);
        Object selected = textures.get(defaultTexture);
        if (selected == null && !textures.isEmpty()) {
            selected = textures.values().iterator().next();
        }
        if (selected == null) {
            throw new IllegalArgumentException("Original model contains no texture");
        }
        Class<?> textureType = selected.getClass();
        Class<?> decoder = Class.forName(prefix + (backend.equals("spm")
            ? ".resource.bundle.TextureDecoder" : ".resource.YSMClientMapper"));
        byte[] png = (byte[]) decoder.getMethod("toPng", byte[].class, int.class, int.class, int.class)
            .invoke(null, textureType.getField("data").get(selected),
                textureType.getField("imageFormat").getInt(selected), textureType.getField("width").getInt(selected),
                textureType.getField("height").getInt(selected));

        Files.write(textureFile, png);
        System.out.println("ORIGINAL TEXTURE " + textureType.getField("name").get(selected));
    }

    static Object bakeGeometry(String prefix, String backend, Object geometry, Path gpuTexture) throws Exception {
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
            Object alphaScanner = null;
            if (gpuTexture != null) {
                var image = javax.imageio.ImageIO.read(gpuTexture.toFile());
                if (image == null) {
                    throw new IllegalArgumentException("Original decoded texture could not be read: " + gpuTexture);
                }
                alphaScanner = scanner.getConstructor(java.awt.image.BufferedImage[].class, int.class)
                    .newInstance(new java.awt.image.BufferedImage[]{image}, 1);
            }
            Object baked = lookup.findStatic(mapper, "buildMesh",
                java.lang.invoke.MethodType.methodType(model, geometry.getClass(), description, int.class, scanner, boolean.class))
                .invoke(geometry, context, 0, alphaScanner, false);
            return baked;
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
