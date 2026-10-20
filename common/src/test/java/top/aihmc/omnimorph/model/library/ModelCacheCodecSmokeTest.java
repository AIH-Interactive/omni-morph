package top.aihmc.omnimorph.model.library;

import top.aihmc.omnimorph.model.cache.ModelCacheCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

public final class ModelCacheCodecSmokeTest {
    public static void main(String[] args) throws Exception {
        var root = Files.createTempDirectory("model-cache-codec-");
        var file = root.resolve("resource.omc");
        try {
            var codec = new ModelCacheCodec(root);
            byte[] plain = "PK-model-texture-script-中文".repeat(100).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            codec.write(file, plain);
            byte[] first = Files.readAllBytes(file);
            require(Arrays.equals(plain, codec.read(file, plain.length)), "roundtrip");
            require(!Arrays.equals(plain, first), "no plaintext package");
            codec.write(file, plain);
            require(!Arrays.equals(first, Files.readAllBytes(file)), "random nonce");
            reject(() -> codec.read(file, 10), "decoded budget");
            byte[] damaged = Files.readAllBytes(file);
            damaged[damaged.length - 1] ^= 1;
            Files.write(file, damaged);
            reject(() -> codec.read(file, plain.length), "authentication");
            Files.write(file, Arrays.copyOf(first, 20));
            reject(() -> codec.read(file, plain.length), "truncation");
            Files.write(file, first);
            Files.delete(root.resolve("model-cache.key"));
            reject(() -> codec.read(file, plain.length), "lost key invalidates old cache");
            codec.write(file, plain);
            require(Arrays.equals(plain, codec.read(file, plain.length)), "re-download with replacement key");
            System.out.println("PASS model cache codec: roundtrip, nonce, authentication, budgets, truncation and key recovery");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(root.resolve("model-cache.key"));
            Files.delete(root);
        }
    }

    private interface Read {
        void run() throws IOException;
    }

    private static void reject(Read read, String message) throws IOException {
        try {
            read.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
