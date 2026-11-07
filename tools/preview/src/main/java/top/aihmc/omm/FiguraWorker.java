package top.aihmc.omm;

import java.nio.file.Path;
import java.util.UUID;

/** Uses the Figura compiler embedded in the selected OmniMorph JAR. */
public final class FiguraWorker {
    private FiguraWorker() {
    }

    public static void main(String[] args) throws Exception {
        Path configDirectory = Path.of(System.getProperty("omm.preview.output"), "figura-config");
        FabricRuntimeInputs.configure(configDirectory, Path.of(System.getProperty("omm.preview.modJar")));
        Path model = Path.of(args[1]);
        Object avatar = compileModel(model);
        if (args.length > 2) {
            loadRuntime(avatar, model, args[2]);
        }
    }

    static Object compileModel(Path model) throws Exception {
        Class<?> loader = Class.forName("org.figuramc.figura.avatar.local.LocalAvatarLoader");
        System.out.println("SOURCE " + loader.getName() + " "
            + loader.getProtectionDomain().getCodeSource().getLocation());
        Object avatar = loader.getMethod("compileFiguraAvatar", Path.class, UUID.class)
            .invoke(null, model, UUID.nameUUIDFromBytes(model.toAbsolutePath().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if (avatar == null || (boolean) avatar.getClass().getMethod("isEmpty").invoke(avatar)) {
            throw new IllegalArgumentException("Original Figura compiler returned no avatar data");
        }
        System.out.println("FIGURA COMPILE PASS keys=" + avatar.getClass().getMethod("keySet").invoke(avatar)
            + " bytes=" + avatar.getClass().getMethod("sizeInBytes").invoke(avatar));
        return avatar;
    }

    private static void loadRuntime(Object data, Path model, String probe) throws Exception {
        // Initializes dependency constants/registries, without creating a client or world.
        Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
        var sceneConfiguration = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
            Path.of(System.getProperty("omm.preview.output"), "effective-config.json"))).getAsJsonObject();
        if (probe.equals("gpu") || probe.equals("window")) {
            if (probe.equals("window")) {
                com.mojang.blaze3d.systems.RenderSystem.initRenderThread();
            }
            try (FiguraScene scene = new FiguraScene(data, model, SceneLayout.read(), sceneConfiguration)) {
                Object backend = OmniMorphBatches.backend(probe.equals("window")
                    && System.getProperty("omm.preview.mode").equals("simd"));
                var initial = OmniMorphBatches.transform(scene.sample(0), backend);
                if (probe.equals("gpu")) {
                    OmniMorphGpuWindow.run(scene.sample(0), initial, scene.textureBytes(), scene::sample);
                } else {
                    CpuPreviewWindow.run("OmniMorph Figura " + System.getProperty("omm.preview.mode"), initial,
                        scene.triangleIndices(), scene.textureBytes(),
                        new OmniMorphCpuFrames(scene::sample, backend));
                }
            }
            return;
        }
        Object runtime = createRuntime(data, model);
        try (var resources = new PreviewAvatarResources(runtime)) {
            Class<?> avatarType = runtime.getClass();
            FiguraAnimationInput.report(runtime);
            var configuration = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
                Path.of(System.getProperty("omm.preview.output"), "effective-config.json"))).getAsJsonObject();
            if (configuration.has("animation")) {
                var animation = configuration.getAsJsonObject("animation");
                new FiguraAnimationInput(runtime, animation).sample(animation.get("time").getAsFloat());
            }
            try (FiguraBatches batches = new FiguraBatches(avatarType.getField("renderer").get(runtime))) {
                var javaVertices = batches.transform(false);
                var simdVertices = batches.transform(true);
                double maximumError = 0;
                long components = 0;
                if (javaVertices.size() != simdVertices.size()) {
                    throw new AssertionError("Figura backend batch counts differ");
                }
                for (int batch = 0; batch < javaVertices.size(); batch++) {
                    double[] expected = javaVertices.get(batch);
                    double[] actual = simdVertices.get(batch);
                    if (expected.length != actual.length) {
                        throw new AssertionError("Figura backend vertex counts differ");
                    }
                    for (int index = 0; index < expected.length; index++) {
                        maximumError = Math.max(maximumError, Math.abs(expected[index] - actual[index]));
                    }
                    components += expected.length;
                }
                if (maximumError > 1.0e-5) {
                    throw new AssertionError("Figura Java/SIMD maximum error: " + maximumError);
                }
                System.out.println("FIGURA POSE JAVA/SIMD PASS batches=" + javaVertices.size()
                    + " components=" + components + " maxError=" + maximumError);
                if (configuration.has("animation")
                    && configuration.getAsJsonObject("animation").has("compareTime")) {
                    var animation = configuration.getAsJsonObject("animation");
                    new FiguraAnimationInput(runtime, animation).sample(animation.get("compareTime").getAsFloat());
                    try (FiguraBatches next = new FiguraBatches(avatarType.getField("renderer").get(runtime))) {
                        var nextVertices = next.transform(false);
                        if (nextVertices.size() != javaVertices.size()) {
                            throw new AssertionError("Animation comparison requires stable batch topology");
                        }
                        double displacement = 0;
                        for (int batch = 0; batch < javaVertices.size(); batch++) {
                            double[] before = javaVertices.get(batch);
                            double[] after = nextVertices.get(batch);
                            if (before.length != after.length) {
                                throw new AssertionError("Animation changed vertex topology");
                            }
                            for (int index = 0; index < before.length; index++) {
                                if (index % 9 < 3) {
                                    displacement = Math.max(displacement, Math.abs(before[index] - after[index]));
                                }
                            }
                        }
                        if (displacement <= 1.0e-6) {
                            throw new AssertionError("Configured animation samples produced no position change");
                        }
                        System.out.println("FIGURA ANIMATION CHANGE PASS maximumPositionDelta=" + displacement);
                    }
                }
            }
        }
    }

    static Object createRuntime(Object data, Path model) throws Exception {
        Class<?> avatarType = Class.forName("org.figuramc.figura.avatar.Avatar");
        Object runtime = avatarType.getMethod("createLibraryPreview", UUID.class, String.class)
            .invoke(null, UUID.randomUUID(), model.getFileName().toString());
        try {
            avatarType.getMethod("load", data.getClass()).invoke(runtime, data);
            if (!(boolean) avatarType.getField("loaded").get(runtime)
                || avatarType.getField("nbt").get(runtime) == null
                || avatarType.getField("renderer").get(runtime) == null) {
                throw new IllegalStateException("Original Figura runtime did not finish loading");
            }
            System.out.println("FIGURA RUNTIME LOAD PASS renderer="
                + avatarType.getField("renderer").get(runtime).getClass().getName());
        } catch (Exception failure) {
            try {
                new PreviewAvatarResources(runtime).close();
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        return runtime;
    }
}
