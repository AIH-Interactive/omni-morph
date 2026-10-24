package top.aihmc.omnimorph.render.acceleration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipFile;

public final class NativeAccelerationSmokeTest {
    private static volatile double timingSink;

    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]);
        validatePackagedManifest(jar);
        try (URLClassLoader resources = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, null)) {
            var loaded = NativeLibraryLoader.load(resources, jar.getParent().resolve("smoke-cache"));
            require(loaded.platform().equals("windows-x86_64"), "local Windows platform");
            require(NativeBindings.handshake(1) == 1 && NativeBindings.handshake(2) == -1, "bidirectional ABI");
            System.out.println("Loaded actual packaged JNI; capability bits=" + loaded.capabilities());
        }
        if (args[2].equals("--timing-only")) {
            timingTests(Path.of(args[1]));
            System.out.println("PASS JNI timing without -Xcheck:jni");
            return;
        }
        boundaryTests();
        differentialTests();
        NativeSimdBackend nativeBackend = new NativeSimdBackend();
        AccelerationService.INSTANCE.useBackend(new AccelerationBackend() {
            @Override
            public boolean supports(Operation operation, DrawBatch batch) {
                return nativeBackend.supports(operation, batch);
            }

            @Override
            public double[] transform(DrawBatch batch) {
                double[] actual = nativeBackend.transform(batch);
                compare(new JavaAccelerationBackend().transform(batch), actual);
                return actual;
            }
        });
        try {
            FiguraAccelerationReplay.main(new String[0]);
            long figuraVertices = nativeBackend.transformedVertices();
            require(figuraVertices > 0, "actual Figura replay must execute JNI");
            YsmMaterialNativeSmokeTest.main(new String[]{"--held-items", Path.of(args[2]).getParent().toString()});
            require(nativeBackend.transformedVertices() > figuraVertices, "actual encrypted YSM must execute JNI");
            System.out.println("Both real renderer replays consumed JNI output; vertices=" + nativeBackend.transformedVertices());
        } finally {
            AccelerationService.INSTANCE.useBackend(null);
        }
        timingTests(Path.of(args[1]));
        System.out.println("PASS packaged JNI boundaries, Java differential, Figura/YSM renderer replays and timing");
    }

    private static void validatePackagedManifest(Path jar) throws Exception {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            require(zip.size() == zip.stream().map(entry -> entry.getName()).distinct().count(), "duplicate JAR entries");
            JsonObject manifest;
            try (var stream = zip.getInputStream(zip.getEntry("natives/manifest.json"))) {
                manifest = JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .replaceFirst("^\\uFEFF", "")).getAsJsonObject();
            }
            String path = "natives/windows-x86_64/omnimorph_accel.dll";
            NativeLibraryLoader.validateManifest(manifest, "windows-x86_64", path);
            JsonObject invalid = manifest.deepCopy();
            invalid.addProperty("abi", 99);
            expectInvalidManifest(invalid, path);
            invalid = manifest.deepCopy();
            invalid.getAsJsonArray("libraries").add(invalid.getAsJsonArray("libraries").get(0).deepCopy());
            expectInvalidManifest(invalid, path);
            invalid = manifest.deepCopy();
            invalid.getAsJsonArray("libraries").get(0).getAsJsonObject().addProperty("path", "../model.dll");
            expectInvalidManifest(invalid, path);
            require(zip.getEntry("fabric.mod.json") != null && zip.getEntry("natives/NOTICE.txt") != null,
                    "mod manifest and native notice retained");
        }
    }

    private static void expectInvalidManifest(JsonObject manifest, String path) throws Exception {
        try {
            NativeLibraryLoader.validateManifest(manifest, "windows-x86_64", path);
            throw new AssertionError("invalid manifest accepted");
        } catch (IOException expected) {
            // Metadata must fail before any native load attempt.
        }
    }

    private static void boundaryTests() {
        ByteBuffer input = direct(256);
        ByteBuffer matrix = identityMatrices();
        ByteBuffer output = direct(256);
        for (int index = 0; index < output.capacity(); index++) {
            output.put(index, (byte) 0x55);
        }
        byte[] sentinel = bytes(output);
        reject(input, -1, 36, matrix, 0, output, 0, 36, 1, -1, sentinel);
        reject(input, 0, 35, matrix, 0, output, 0, 36, 1, -1, sentinel);
        reject(input, 0, 36, matrix, 0, output, 0, 36, Integer.MAX_VALUE, -1, sentinel);
        reject(input, 0, 36, matrix, 0, output, 0, 36, -1, -1, sentinel);
        reject(input, 0, 36, matrix, 1, output, 0, 36, 1, -1, sentinel);
        reject(input, 0, 36, matrix, 0, output.asReadOnlyBuffer(), 0, 36, 1, -1, sentinel);
        reject(ByteBuffer.allocate(256), 0, 36, matrix, 0, output, 0, 36, 1, -1, sentinel);
        reject(input, 0, 36, matrix, 0, output, 0, 36, 1, 99, sentinel);
        require(NativeBindings.transform(input, 0, 36, matrix, 0, input.slice(1, 100), 0, 36, 1, -1, 0) != 0,
                "sliced direct-buffer overlap must reject");
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            input.putFloat(0, invalid);
            reject(input, 0, 36, matrix, 0, output, 0, 36, 1, -1, sentinel);
        }
        input.putFloat(0, Float.MAX_VALUE);
        matrix.putFloat(0, Float.MAX_VALUE);
        reject(input, 0, 36, matrix, 0, output, 0, 36, 1, -1, sentinel);
        require(NativeBindings.transform(direct(0), 0, 36, identityMatrices(), 0, direct(0), 0, 36, 0, -1, 0) == 0,
                "empty direct buffers");
        // Arbitrary offsets and odd tails cross the actual JNI boundary with sentinel padding intact.
        input = direct(256);
        matrix = identityMatrices();
        ByteBuffer unalignedMatrix = direct(161);
        for (int index = 0; index < 160; index++) {
            unalignedMatrix.put(index + 1, matrix.get(index));
        }
        for (int vertex = 0; vertex < 3; vertex++) {
            for (int component = 0; component < 9; component++) {
                input.putFloat(1 + vertex * 41 + component * 4, component + vertex);
            }
        }
        require(NativeBindings.transform(input, 1, 41, unalignedMatrix, 1, output, 1, 41, 3, -1, 0) == 0,
                "unaligned JNI tail");
        require(output.getFloat(1) == 0 && output.getFloat(42) == 1 && output.getFloat(83) == 2
                && output.get(0) == 0x55 && output.get(37) == 0x55, "unaligned results and padding");
    }

    private static void reject(ByteBuffer input, int inputOffset, int inputStride, ByteBuffer matrix, int matrixOffset,
                               ByteBuffer output, int outputOffset, int outputStride, int count, int kernel, byte[] sentinel) {
        int status = NativeBindings.transform(input, inputOffset, inputStride, matrix, matrixOffset,
                output, outputOffset, outputStride, count, kernel, 0);
        require(status != 0 && Arrays.equals(sentinel, bytes(output)), "JNI rejection preserves output: status=" + status);
    }

    private static void differentialTests() {
        Random random = new Random(0x4f4d4e49);
        List<Integer> kernels = new ArrayList<>(List.of(0));
        if ((NativeBindings.capabilities() & 2) != 0) {
            kernels.add(1);
        }
        if ((NativeBindings.capabilities() & 4) != 0) {
            kernels.add(2);
        }
        if ((NativeBindings.capabilities() & 64) != 0) {
            kernels.add(3);
        }
        kernels.add(-1);
        for (int count : new int[]{0, 1, 3, 16, 257, 4096}) {
            double[] vertices = new double[count * 9];
            List<MeshHandle.Influences> influences = new ArrayList<>();
            for (int vertex = 0; vertex < count; vertex++) {
                for (int component = 0; component < 9; component++) {
                    vertices[vertex * 9 + component] = random.nextDouble() * 8 - 4;
                }
                vertices[vertex * 9 + 3] = 1;
                influences.add(new MeshHandle.Influences(new int[]{0, 1, 0, 1, 0}, new float[]{1, 2, 3, 4, 5}));
            }
            MeshHandle mesh = new MeshHandle(vertices, 1, influences);
            FiguraMat4 position = FiguraMat4.of().scale(-2, 0.5, 3).translate(0.25, -2, 8);
            FiguraMat3 normal = FiguraMat3.of(-0.5, 0, 0, 0, 2, 0, 0, 0, 1.0 / 3);
            FiguraMat3 uv = FiguraMat3.of(0.5, 0.2, 0, -0.2, 2, 0, 0.1, 0.75, 1);
            PoseStack vanilla = new PoseStack();
            vanilla.scale(-2, 0.5f, 3);
            vanilla.translate(0.25, -2, 8);
            PoseStack zeroScale = new PoseStack();
            zeroScale.scale(0, 0, 0);
            for (PoseSnapshot pose : new PoseSnapshot[]{new PoseSnapshot(position, normal, uv, null),
                    new PoseSnapshot(position, normal, uv, new FiguraMat4[]{FiguraMat4.of(), FiguraMat4.of().translate(1, 2, 3)}),
                    new PoseSnapshot(vanilla.last()), new PoseSnapshot(zeroScale.last()),
                    new PoseSnapshot(FiguraMat4.of().scale(0, 0, 0), FiguraMat3.of(0, 0, 0, 0, 0, 0, 0, 0, 0))}) {
                DrawBatch batch = batch(mesh, pose);
                double[] expected = new JavaAccelerationBackend().transform(batch);
                for (int kernel : kernels) {
                    NativeSimdBackend backend = new NativeSimdBackend(kernel);
                    try {
                        compare(expected, backend.transform(batch));
                    } catch (NativeSimdBackend.NativeBatchException failure) {
                        require(Arrays.stream(expected).anyMatch(value -> !Double.isFinite(value)),
                                "finite Java batch must not be rejected: " + failure.status());
                    }
                }
            }
        }
        System.out.println("PASS Java differential: rigid, five weights, separate normal/UV, negative/zero scale; kernels=" + kernels);
    }

    private static void compare(double[] expected, double[] actual) {
        require(expected.length == actual.length, "result length");
        for (int offset = 0; offset < expected.length; offset += 9) {
            for (int component = 0; component < 9; component++) {
                double reference = expected[offset + component];
                double value = actual[offset + component];
                require(Double.isFinite(value) && Math.abs(value - reference) <= 1e-4 + 1e-5 * Math.abs(reference),
                        "frozen numeric tolerance: component=" + component + " expected=" + reference + " actual=" + value);
            }
            double dot = 0;
            double expectedLength = 0;
            double actualLength = 0;
            for (int component = 4; component <= 6; component++) {
                dot += expected[offset + component] * actual[offset + component];
                expectedLength += expected[offset + component] * expected[offset + component];
                actualLength += actual[offset + component] * actual[offset + component];
            }
            if (expectedLength > 0 && actualLength > 0) {
                double angle = Math.toDegrees(Math.acos(Math.clamp(dot / Math.sqrt(expectedLength * actualLength), -1, 1)));
                require(angle <= 0.1, "frozen normal angular tolerance");
            }
        }
    }

    private static void timingTests(Path report) throws IOException {
        Files.createDirectories(report.getParent());
        StringBuilder raw = new StringBuilder("round,vertices,sample,java_ns,native_total_ns,jni_only_ns\n");
        for (int count : new int[]{16, 256, 4096}) {
            double[] vertices = new double[count * 9];
            for (int index = 0; index < vertices.length; index++) {
                vertices[index] = (index % 13) * 0.125;
            }
            DrawBatch batch = batch(new MeshHandle(vertices, 1), new PoseSnapshot(FiguraMat4.of().translate(1, 2, 3), FiguraMat3.of()));
            NativeSimdBackend backend = new NativeSimdBackend();
            JavaAccelerationBackend javaBackend = new JavaAccelerationBackend();
            ByteBuffer input = direct(count * 36);
            ByteBuffer output = direct(count * 36);
            ByteBuffer matrix = identityMatrices();
            batch.pose().writeNativeVertices(batch.mesh(), input);
            for (int round = 0; round < 3; round++) {
                for (int warmup = 0; warmup < 200; warmup++) {
                    timingSink = backend.transform(batch)[0] + javaBackend.transform(batch)[0];
                }
                long[] javaTimes = new long[300];
                long[] nativeTimes = new long[300];
                long[] jniTimes = new long[300];
                for (int sample = 0; sample < 300; sample++) {
                    long start = System.nanoTime();
                    timingSink = javaBackend.transform(batch)[0];
                    javaTimes[sample] = System.nanoTime() - start;
                    start = System.nanoTime();
                    timingSink = backend.transform(batch)[0];
                    nativeTimes[sample] = System.nanoTime() - start;
                    start = System.nanoTime();
                    int status = NativeBindings.transform(input, 0, 36, matrix, 0, output, 0, 36, count, -1, 0);
                    jniTimes[sample] = System.nanoTime() - start;
                    require(status == 0, "timing JNI status");
                    raw.append(round).append(',').append(count).append(',').append(sample).append(',')
                            .append(javaTimes[sample]).append(',').append(nativeTimes[sample]).append(',').append(jniTimes[sample]).append('\n');
                }
                Arrays.sort(javaTimes);
                Arrays.sort(nativeTimes);
                Arrays.sort(jniTimes);
                System.out.printf("round=%d vertices=%d Java p50/p95=%d/%dns native total=%d/%dns JNI=%d/%dns%n",
                        round, count, javaTimes[150], javaTimes[285], nativeTimes[150], nativeTimes[285], jniTimes[150], jniTimes[285]);
            }
        }
        Files.writeString(report, raw);
    }

    private static DrawBatch batch(MeshHandle mesh, PoseSnapshot pose) {
        return new DrawBatch(AccelerationService.INSTANCE.capture(new AccelerationOwner(), "jni-smoke"), mesh, pose);
    }

    private static ByteBuffer identityMatrices() {
        ByteBuffer matrix = direct(160);
        for (int index : new int[]{0, 5, 10, 15, 16, 21, 26, 28, 33, 38}) {
            matrix.putFloat(index * 4, 1);
        }
        return matrix;
    }

    private static ByteBuffer direct(int capacity) {
        return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
    }

    private static byte[] bytes(ByteBuffer buffer) {
        byte[] result = new byte[buffer.capacity()];
        buffer.duplicate().clear().get(result);
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
