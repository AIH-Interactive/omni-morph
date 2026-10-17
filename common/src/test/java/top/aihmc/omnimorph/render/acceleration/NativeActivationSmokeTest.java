package top.aihmc.omnimorph.render.acceleration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

public final class NativeActivationSmokeTest {
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]);
        Path cache = jar.getParent().resolve("h3-native-cache");
        Map<String, byte[]> valid = new HashMap<>();
        String libraryPath = "natives/windows-x86_64/omnimorph_accel.dll";
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String name : new String[]{"natives/manifest.json", libraryPath}) {
                try (InputStream stream = zip.getInputStream(zip.getEntry(name))) {
                    valid.put(name, stream.readAllBytes());
                }
            }
        }
        reject(Map.of(), cache, FallbackReason.MISSING_LIBRARY);
        JsonObject manifest = JsonParser.parseString(new String(valid.get("natives/manifest.json"), StandardCharsets.UTF_8)
                .replaceFirst("^\\uFEFF", "")).getAsJsonObject();
        var invalid = new HashMap<>(valid);
        JsonObject badAbi = manifest.deepCopy();
        badAbi.addProperty("abi", 99);
        invalid.put("natives/manifest.json", badAbi.toString().getBytes(StandardCharsets.UTF_8));
        reject(invalid, cache, FallbackReason.ABI_MISMATCH);
        badAbi = manifest.deepCopy();
        badAbi.getAsJsonArray("libraries").get(0).getAsJsonObject().addProperty("abi", 99);
        invalid.put("natives/manifest.json", badAbi.toString().getBytes(StandardCharsets.UTF_8));
        reject(invalid, cache, FallbackReason.ABI_MISMATCH);
        invalid = new HashMap<>(valid);
        byte[] corrupt = valid.get(libraryPath).clone();
        corrupt[0] ^= 1;
        invalid.put(libraryPath, corrupt);
        reject(invalid, cache, FallbackReason.INTEGRITY_FAILURE);
        String previousArch = System.getProperty("os.arch");
        try {
            System.setProperty("os.arch", "i386");
            reject(valid, cache, FallbackReason.UNSUPPORTED_PLATFORM);
        } finally {
            System.setProperty("os.arch", previousArch);
        }
        require(!NativeLibraryLoader.isLoaded(), "all invalid resources rejected before DLL load");
        if (args[1].equals("deny")) {
            try {
                HardwareAcceleration.probe(CapabilityReport.Mode.SIMD);
                throw new AssertionError("JDK native access denial must reject activation");
            } catch (AccelerationController.ProbeFailure failure) {
                require(failure.reason == FallbackReason.NATIVE_ACCESS_DENIED, "actual JDK denial is accurately classified");
            }
            require(!NativeLibraryLoader.isLoaded(), "denied native does not become ready");
            System.out.println("PASS actual JDK 25 --illegal-native-access=deny and structured Java fallback");
        } else {
            var activation = HardwareAcceleration.probe(CapabilityReport.Mode.SIMD);
            require(NativeLibraryLoader.isLoaded() && activation.backend() instanceof NativeSimdBackend
                    && (activation.effective() == CapabilityReport.Effective.SSE2
                    || activation.effective() == CapabilityReport.Effective.AVX2
                    || activation.effective() == CapabilityReport.Effective.NEON), "actual packaged SIMD self-test before readiness");
            require(NativeBindings.handshake(1) == 1, "loaded native handshake");
            System.out.println("PASS native activation/self-test; actual effective=" + activation.effective());
        }
        System.out.println("PASS actual loader missing library, bad ABI/hash and process architecture rejection");
    }

    private static void reject(Map<String, byte[]> resources, Path cache, FallbackReason reason) throws Exception {
        ClassLoader controlled = new ClassLoader(null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                byte[] bytes = resources.get(name);
                return bytes == null ? null : new ByteArrayInputStream(bytes);
            }
        };
        try {
            NativeLibraryLoader.load(controlled, cache);
            throw new AssertionError("invalid library accepted: " + reason);
        } catch (NativeLibraryLoader.LoadFailure expected) {
            require(expected.reason() == reason, "loader rejection reason: " + reason);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
