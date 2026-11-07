package top.aihmc.omm;

import java.nio.file.Path;

/** Worker entry point running against the supplied OmniMorph JAR. */
public final class OmniMorphWorker {
    private OmniMorphWorker() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 2 && args[2].equals("gpu-check")) {
            OmniMorphGpuRuntime.check();
            return;
        }
        try (OmniMorphModel model = OmniMorphModel.load(Path.of(args[1]))) {
            model.report();
            if (args.length > 2 && args[2].equals("gpu")) {
                runGpu(model);
            }
            if (args.length > 2 && args[2].equals("window")) {
                runWindow(model);
            }
            if (args.length > 2 && args[2].equals("bake")) {
                com.mojang.blaze3d.systems.RenderSystem.initRenderThread();
                Object runtime = model.createRuntime();
                try {
                    Object player = runtime.getClass().getMethod("animations").invoke(runtime);
                    var clips = (java.util.Map<?, ?>) player.getClass().getMethod("getClips").invoke(player);
                    System.out.println("OMP YSM RUNTIME PASS clips=" + clips.keySet());
                    var configuration = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
                        Path.of(System.getProperty("omm.preview.output"), "effective-config.json"))).getAsJsonObject();
                    if (configuration.has("animation")) {
                        verifyAnimation(model, runtime, configuration.getAsJsonObject("animation"));
                    }
                } finally {
                    PreviewAvatarResources.closeYsmRuntime(runtime);
                }
                try (var batches = new OmniMorphBatches(model.geometry())) {
                    var javaOutput = batches.transform(false);
                    var nativeOutput = batches.transform(true);
                    if (javaOutput.size() != nativeOutput.size()) {
                        throw new AssertionError("Original backend batch count mismatch");
                    }
                    double maximumError = 0;
                    long values = 0;
                    for (int batch = 0; batch < javaOutput.size(); batch++) {
                        double[] expected = javaOutput.get(batch);
                        double[] actual = nativeOutput.get(batch);
                        if (expected.length != actual.length) {
                            throw new AssertionError("Original backend vertex count mismatch");
                        }
                        values += expected.length;
                        for (int index = 0; index < expected.length; index++) {
                            maximumError = Math.max(maximumError, Math.abs(expected[index] - actual[index]));
                        }
                    }
                    if (values == 0 || maximumError > 1e-5) {
                        throw new AssertionError("Original Java/SIMD mismatch: " + maximumError);
                    }
                    System.out.println("OMP ORIGINAL JAVA/SIMD PASS batches=" + javaOutput.size()
                        + " values=" + values + " maximumError=" + maximumError);
                }
            }
        }
    }

    private static void runGpu(OmniMorphModel model) throws Exception {
        var configuration = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
            Path.of(System.getProperty("omm.preview.output"), "effective-config.json"))).getAsJsonObject();
        SceneLayout layout = SceneLayout.read();
        try (var scene = new OmniMorphScene(model, layout, configuration)) {
            var batches = scene.sample(0);
            OmniMorphGpuWindow.run(batches, OmniMorphBatches.transform(batches, false), model.texture(), scene::sample);
        }
    }

    private static void runWindow(OmniMorphModel model) throws Exception {
        com.mojang.blaze3d.systems.RenderSystem.initRenderThread();
        var configuration = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
            Path.of(System.getProperty("omm.preview.output"), "effective-config.json"))).getAsJsonObject();
        SceneLayout layout = SceneLayout.read();
        boolean nativeSimd = System.getProperty("omm.preview.mode", "java").equals("simd");
        Object backend = OmniMorphBatches.backend(nativeSimd);
        try (var scene = new OmniMorphScene(model, layout, configuration)) {
            var vertices = OmniMorphBatches.transform(scene.sample(0), backend);
            CpuPreviewWindow.run("OMP original " + (nativeSimd ? "SIMD" : "Java"), vertices,
                scene.triangleIndices(), java.util.Collections.nCopies(vertices.size(), model.texture()),
                new OmniMorphCpuFrames(scene::sample, backend));
        }
    }

    private static void verifyAnimation(OmniMorphModel model, Object runtime,
                                        com.google.gson.JsonObject configuration) throws Exception {
        YsmAnimationInput animation = new YsmAnimationInput(runtime, configuration);
        animation.sample(configuration.get("time").getAsFloat());
        try (var batches = new OmniMorphBatches(model.geometry(), runtime)) {
            var first = batches.transform(false);
            var simd = batches.transform(true);
            double maximumError = difference(first, simd, false);
            if (maximumError > 1e-5) {
                throw new AssertionError("Animated Java/SIMD mismatch: " + maximumError);
            }
            System.out.println("OMP YSM ANIMATED JAVA/SIMD PASS maximumError=" + maximumError);
            if (configuration.has("compareTime")) {
                animation.sample(configuration.get("compareTime").getAsFloat());
                batches.refresh();
                double change = difference(first, batches.transform(false), true);
                if (change <= 1e-6) {
                    throw new AssertionError("Configured YSM animation produced no position change");
                }
                System.out.println("OMP YSM ANIMATION CHANGE PASS maximumPositionChange=" + change);
            }
        }
    }

    private static double difference(java.util.List<double[]> first, java.util.List<double[]> second,
                                     boolean positionsOnly) {
        if (first.size() != second.size()) {
            throw new AssertionError("Animation batch topology changed");
        }
        double maximum = 0;
        for (int batch = 0; batch < first.size(); batch++) {
            double[] a = first.get(batch);
            double[] b = second.get(batch);
            if (a.length != b.length) {
                throw new AssertionError("Animation vertex topology changed");
            }
            for (int index = 0; index < a.length; index++) {
                if (!positionsOnly || index % 9 < 3) {
                    maximum = Math.max(maximum, Math.abs(a[index] - b[index]));
                }
            }
        }
        return maximum;
    }
}
