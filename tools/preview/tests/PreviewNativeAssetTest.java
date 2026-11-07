package top.aihmc.omm.preview;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Native parser output must produce the same real geometry, pixels, visibility and sampled poses as Java. */
public final class PreviewNativeAssetTest {
    private static void near(float actual, float expected, String where) {
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > 0.0003f)
            throw new AssertionError(where + ": " + actual + " != " + expected);
    }

    private static void mesh(PreviewMesh actual, PreviewMesh expected) throws Exception {
        if (actual.vertices.length != expected.vertices.length)
            throw new AssertionError("Vertex length " + actual.vertices.length + " != " + expected.vertices.length);
        if (!Arrays.equals(actual.indices, expected.indices))
            throw new AssertionError("Indices differ at " + Arrays.mismatch(actual.indices, expected.indices));
        if (!Arrays.equals(actual.owners, expected.owners))
            throw new AssertionError("Owners differ at " + Arrays.mismatch(actual.owners, expected.owners));
        if (!Arrays.equals(actual.skeleton.names, expected.skeleton.names)) {
            int index = Arrays.mismatch(actual.skeleton.names, expected.skeleton.names);
            throw new AssertionError("Bone names differ at " + index + ": " + actual.skeleton.names[index]
                + " != " + expected.skeleton.names[index]);
        }
        if (!Arrays.equals(actual.skeleton.parents, expected.skeleton.parents))
            throw new AssertionError(
                "Bone parents differ at " + Arrays.mismatch(actual.skeleton.parents, expected.skeleton.parents));
        if (!Arrays.equals(actual.skeleton.defaultVisible, expected.skeleton.defaultVisible)) {
            int index = Arrays.mismatch(actual.skeleton.defaultVisible, expected.skeleton.defaultVisible);
            throw new AssertionError("Bone visibility differs at " + index + " " + actual.skeleton.names[index] + ": "
                + actual.skeleton.defaultVisible[index] + " != " + expected.skeleton.defaultVisible[index]);
        }
        for (int index = 0; index < actual.vertices.length; index++)
            near(actual.vertices[index], expected.vertices[index], "vertex " + index);
        if (actual.textures.size() != expected.textures.size())
            throw new AssertionError("Native texture count differs");
        for (int index = 0; index < actual.textures.size(); index++) {
            var left = actual.textures.get(index);
            var right = expected.textures.get(index);
            if (left.getWidth() != right.getWidth() || left.getHeight() != right.getHeight())
                throw new AssertionError("Native texture dimensions differ");
            for (int y = 0; y < left.getHeight(); y++)
                for (int x = 0; x < left.getWidth(); x++)
                    if (left.getRGB(x, y) != right.getRGB(x, y))
                        throw new AssertionError("Native texture pixel differs at " + x + "," + y);
        }
        if (!actual.ysmResources.functions().equals(expected.ysmResources.functions())
            || !actual.ysmResources.languages().equals(expected.ysmResources.languages())
            || !actual.ysmResources.audio().keySet().equals(expected.ysmResources.audio().keySet()))
            throw new AssertionError("Native function/language/audio inventory differs");
        actual.ysmResources.audio().forEach((name, bytes) -> {
            if (!Arrays.equals(bytes, expected.ysmResources.audio().get(name)))
                throw new AssertionError("Native audio bytes differ: " + name);
        });
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        List<String> cases = args.length > 2
            ? List.of(args[2])
            : List.of("animated-ysm.json", "zip.json", "encrypted.json", "encrypted-new.json", "controlled-ysm.json",
                  "ysm-functions.json", "ysm-functions-zip.json", "ysm-declared-actions.json",
                  "ysm-controller-functions.json", "ysm-declared-transition.json");
        for (String name : cases) {
            var config = PreviewConfig.load(new String[] {"--config", root.resolve(name).toString()});
            config.settings.enabled = false;
            if (name.equals("encrypted-new.json")) {
                config.animation.enabled = true;
                config.animation.mode = "clip";
                config.animation.clip = "attacked";
            }
            config.execution.parser = "java";
            var reference = PreviewMesh.load(config);
            int[] budgets = args.length > 3 ? new int[] {Integer.parseInt(args[3])} : new int[] {1, 8, 16};
            for (int threads : budgets) {
                config.execution.parser = "native";
                config.execution.parserThreads = threads;
                config.execution.nativeLibrary = Path.of(args[1]).toAbsolutePath().toString();
                var actual = PreviewMesh.load(config);
                mesh(actual, reference);
                int expectedThreads = threads;
                if (!actual.parserInfo.implementation().equals("native")
                    || actual.parserInfo.threads() != expectedThreads || actual.parserInfo.nativeParseMs() < 0
                    || actual.parserInfo.loadMs() < actual.parserInfo.nativeParseMs())
                    throw new AssertionError("Native parse execution evidence is missing");
                if (config.animation.enabled) {
                    try (var javaAnimation = new PreviewAnimation(reference, config);
                        var nativeAnimation = new PreviewAnimation(actual, config)) {
                        var javaState = javaAnimation.newState();
                        var nativeState = nativeAnimation.newState();
                        int frame = 0;
                        for (float time : new float[] {0, 0.125f, 0.5f}) {
                            javaState.sample(frame, time);
                            nativeState.sample(frame++, time);
                            javaState.updateBones();
                            nativeState.updateBones();
                            float[] left = new float[16], right = new float[16];
                            for (int bone = 0; bone < actual.skeleton.names.length; bone++) {
                                nativeState.palette[bone].get(left);
                                javaState.palette[bone].get(right);
                                for (int value = 0; value < 16; value++)
                                    near(left[value], right[value], name + " bone " + bone);
                            }
                        }
                    }
                }
            }
            System.out.println("Native parser parity: " + name);
        }
        System.out.println("Native asset PASS: " + cases.size() + " input(s), geometry/texture/owned resources, "
            + "actual animation/controller/action poses and load/core timings");
    }
}
