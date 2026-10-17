package top.aihmc.omnimorph.render.acceleration;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Standalone JDK/Gson JNI tests shared by the four native runners and the same final JAR. */
public final class NativePlatformSmokeTest {
    public static void main(String[] args) throws Exception {
        require(!NativeLibraryLoader.isLoaded(), "default path does not load native code");
        if (args.length > 0 && args[0].equals("--fallback")) {
            String architecture = System.getProperty("os.arch");
            try {
                System.setProperty("os.arch", "riscv64");
                expectFailure(FallbackReason.UNSUPPORTED_PLATFORM, NativePlatformSmokeTest.class.getClassLoader());
            } finally {
                System.setProperty("os.arch", architecture);
            }
            expectFailure(FallbackReason.MISSING_LIBRARY, new ClassLoader(null) {});
            require(!NativeLibraryLoader.isLoaded(), "failed probes leave native inactive");
            System.out.println("PASS native disabled/unsupported/missing resource contracts");
            return;
        }
        // macOS exposes its temporary directory through /var -> /private/var.
        // Resolve the fresh, test-owned root before exercising the loader's no-symlink cache policy.
        var cache = Files.createTempDirectory("omnimorph-platform-jni-").toRealPath();
        if (args.length > 0 && args[0].equals("--deny")) {
            try {
                NativeLibraryLoader.load(NativePlatformSmokeTest.class.getClassLoader(), cache);
                throw new AssertionError("restricted native access was accepted");
            } catch (IllegalCallerException expected) {
                require(!NativeLibraryLoader.isLoaded(), "denied load never becomes ready");
            }
            System.out.println("PASS JDK native access denied");
            return;
        }
        var loaded = NativeLibraryLoader.load(NativePlatformSmokeTest.class.getClassLoader(), cache);
        require((loaded.capabilities() & (2 | 4 | 64)) != 0, "runner executes a real SIMD kernel");
        require(NativeBindings.handshake(999) == -1, "wrong ABI rejects");
        List<Integer> kernels = new ArrayList<>(List.of(0, -1));
        for (int kernel = 1; kernel <= 3; kernel++) {
            int capability = kernel == 3 ? 64 : 1 << kernel;
            if ((loaded.capabilities() & capability) != 0) {
                kernels.add(kernel);
            }
        }
        for (int count : new int[]{0, 1, 2, 3, 16, 257, 4096}) {
            for (int flags = 0; flags <= 3; flags++) {
                numerical(count, flags, kernels);
            }
        }
        ByteBuffer input = direct(72);
        ByteBuffer matrices = direct(160);
        ByteBuffer output = direct(72);
        for (int index = 0; index < output.capacity(); index++) {
            output.put(index, (byte) 0x55);
        }
        require(NativeBindings.transform(input, 0, 36, matrices, 0, output, 0, 36, 65537, -1, 0) != 0,
                "oversized batch rejects before reading");
        require(NativeBindings.transform(input, 0, 36, matrices, 0, output.asReadOnlyBuffer(), 0, 36, 1, -1, 0) != 0,
                "read-only output rejects");
        require(NativeBindings.transform(input, 0, 36, matrices, 0, input, 0, 36, 1, -1, 0) != 0,
                "aliased output rejects");
        input.putFloat(0, Float.NaN);
        require(NativeBindings.transform(input, 0, 36, matrices, 0, output, 0, 36, 1, -1, 0) != 0,
                "nonfinite input rejects");
        for (int index = 0; index < output.capacity(); index++) {
            require(output.get(index) == (byte) 0x55, "rejected JNI requests cannot publish partial output");
        }
        System.out.println("PASS actual JAR extraction/hash/ABI/JNI Java differential: platform=" + loaded.platform()
                + " capabilities=" + loaded.capabilities() + " kernels=" + kernels);
    }

    private static void expectFailure(FallbackReason reason, ClassLoader resources) throws Exception {
        try {
            NativeLibraryLoader.load(resources, Files.createTempDirectory("omnimorph-platform-fallback-"));
            throw new AssertionError("expected loader failure " + reason);
        } catch (NativeLibraryLoader.LoadFailure failure) {
            require(failure.reason() == reason, "typed platform failure " + reason);
        }
    }

    private static void numerical(int count, int flags, List<Integer> kernels) {
        int stride = 41;
        ByteBuffer input = direct(1 + Math.max(1, count) * stride);
        ByteBuffer matrices = direct(161);
        var random = new Random(0x6A2B + count);
        float[] matrix = new float[40];
        for (int index = 0; index < matrix.length; index++) {
            matrix[index] = random.nextFloat() * 4 - 2;
            matrices.putFloat(1 + index * 4, matrix[index]);
        }
        float[][] expected = new float[count][9];
        for (int vertex = 0; vertex < count; vertex++) {
            float[] values = new float[9];
            for (int component = 0; component < 9; component++) {
                values[component] = random.nextFloat() * 10 - 5;
                input.putFloat(1 + vertex * stride + component * 4, values[component]);
            }
            for (int row = 0; row < 4; row++) {
                expected[vertex][row] = matrix[row] * values[0] + matrix[4 + row] * values[1]
                        + matrix[8 + row] * values[2] + matrix[12 + row] * values[3];
            }
            for (int row = 0; row < 3; row++) {
                expected[vertex][4 + row] = matrix[16 + row] * values[4] + matrix[20 + row] * values[5]
                        + matrix[24 + row] * values[6];
            }
            for (int row = 0; row < 2; row++) {
                expected[vertex][7 + row] = matrix[28 + row] * values[7] + matrix[32 + row] * values[8] + matrix[36 + row];
            }
            if ((flags & 2) != 0) {
                expected[vertex][3] = 1;
            }
            if ((flags & 1) != 0) {
                float[] value = expected[vertex];
                float inverse = 1f / (float) Math.sqrt(value[4] * value[4] + value[5] * value[5] + value[6] * value[6]);
                for (int component = 4; component <= 6; component++) {
                    value[component] *= inverse;
                }
            }
        }
        for (int kernel : kernels) {
            ByteBuffer output = direct(input.capacity());
            require(NativeBindings.transform(input, 1, stride, matrices, 1, output, 1, stride, count, kernel, flags) == 0,
                    "unaligned/tail JNI execution");
            for (int vertex = 0; vertex < count; vertex++) {
                for (int component = 0; component < 9; component++) {
                    float reference = expected[vertex][component];
                    float actual = output.getFloat(1 + vertex * stride + component * 4);
                    require(Float.isFinite(actual) && Math.abs(reference - actual) <= 1e-4 + 1e-5 * Math.abs(reference),
                            "frozen Java numeric tolerance: kernel=" + kernel + " count=" + count);
                }
            }
        }
    }

    private static ByteBuffer direct(int size) {
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
