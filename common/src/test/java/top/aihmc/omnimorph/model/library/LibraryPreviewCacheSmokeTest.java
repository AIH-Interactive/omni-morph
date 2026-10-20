package top.aihmc.omnimorph.model.library;

import top.aihmc.omnimorph.backend2.protocol.CloudHttp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class LibraryPreviewCacheSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("library-preview-cache-");
        try {
            Path formal = Files.createDirectories(directory.resolve("avatars")).resolve("existing.nbt");
            Files.writeString(formal, "existing wardrobe data");
            var cache = new LibraryPreviewCache(directory);
            UUID viewer = UUID.randomUUID();
            UUID model = UUID.randomUUID();
            byte[] bytes = "synthetic server distribution".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var scope = new LibraryPreviewCache.Scope("server-one", viewer, model, 1, 1);
            var key = new LibraryPreviewCache.Key(scope, CloudHttp.sha256(bytes));
            require(cache.put(key, LibraryPreviewCache.Kind.MODEL, bytes), "verified model package is cached");
            var checks = new AtomicInteger();
            try (var lease = cache.find(scope, LibraryPreviewCache.Kind.MODEL, checks::incrementAndGet).orElseThrow()) {
                require(Arrays.equals(lease.read(), bytes), "cache returns owned raw distribution bytes");
                Path file = onlyModel(cache);
                Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - Duration.ofHours(25).toMillis()));
                new LibraryPreviewCache(directory).cleanup();
                require(Files.exists(file), "another cache object cannot evict an active lease");
                require(!cache.put(key, LibraryPreviewCache.Kind.MODEL, bytes), "active data cannot be replaced");
            }
            cache.cleanup();
            require(cache.find(scope, LibraryPreviewCache.Kind.MODEL, checks::incrementAndGet).isEmpty(),
                    "expired cache data is removed after its final lease closes");
            require(checks.get() == 2, "both cache hits and misses require a live permission check");
            cache.put(key, LibraryPreviewCache.Kind.MODEL, bytes);
            try {
                cache.find(scope, LibraryPreviewCache.Kind.MODEL, () -> { throw new IOException("REVOKED"); });
                throw new AssertionError("Revoked cache access was accepted");
            } catch (IOException failure) {
                require(failure.getMessage().equals("REVOKED"), "revocation wins before disk access");
            }
            for (var other : new LibraryPreviewCache.Scope[]{
                    new LibraryPreviewCache.Scope("server-two", viewer, model, 1, 1),
                    new LibraryPreviewCache.Scope("server-one", UUID.randomUUID(), model, 1, 1),
                    new LibraryPreviewCache.Scope("server-one", viewer, model, 2, 1),
                    new LibraryPreviewCache.Scope("server-one", viewer, model, 1, 2)}) {
                require(cache.find(other, LibraryPreviewCache.Kind.MODEL, () -> {}).isEmpty(),
                        "server/player/revision/policy identities cannot share a cache entry");
            }
            Files.write(onlyModel(cache), new byte[]{0});
            require(cache.find(scope, LibraryPreviewCache.Kind.MODEL, () -> {}).isEmpty(), "corrupt data is rejected and removed");
            cache.put(key, LibraryPreviewCache.Kind.MODEL, bytes, Duration.ofHours(3));
            Path timed = onlyModel(cache);
            FileTime written = FileTime.fromMillis(System.currentTimeMillis() - Duration.ofHours(2).toMillis());
            Files.setLastModifiedTime(timed, written);
            try (var lease = new LibraryPreviewCache(directory).find(scope, LibraryPreviewCache.Kind.MODEL,
                    Duration.ofHours(3), () -> {}).orElseThrow()) {
                require(Arrays.equals(lease.read(), bytes), "a backend lifetime longer than one hour survives reopening");
                require(Files.getLastModifiedTime(timed).equals(written), "cache hits do not extend the download lifetime");
            }
            require(cache.find(scope, LibraryPreviewCache.Kind.MODEL, () -> {}).isEmpty(),
                    "the one-hour default expires old data");
            cache.put(key, LibraryPreviewCache.Kind.MODEL, bytes);
            Files.setLastModifiedTime(onlyModel(cache), FileTime.fromMillis(System.currentTimeMillis() - 2000));
            require(cache.find(scope, LibraryPreviewCache.Kind.MODEL, Duration.ofSeconds(1), () -> {}).isEmpty(),
                    "shorter backend lifetimes apply immediately");
            require(!cache.put(key, LibraryPreviewCache.Kind.MODEL, bytes, Duration.ZERO), "zero lifetime disables storage");
            Files.writeString(Files.createDirectories(cache.root().resolve("models")).resolve("interrupted.part"), "partial");
            cache.cleanup();
            require(!Files.exists(cache.root().resolve("models/interrupted.part")), "startup cleanup removes incomplete writes");
            Path legacy = cache.root().resolve("models").resolve("a".repeat(64) + "-" + "b".repeat(64) + ".bin");
            Files.writeString(legacy, "legacy plaintext preview");
            cache.cleanup();
            require(!Files.exists(legacy), "unexpired legacy plaintext previews are discarded");
            Path downloads = Files.createDirectories(directory.resolve("model-library-downloads"));
            Path raw = downloads.resolve("download-legacy.part");
            Path wrapped = downloads.resolve("download-current.part");
            Files.writeString(raw, "legacy plaintext download payload that exceeds thirty-two bytes");
            new top.aihmc.omnimorph.model.cache.ModelCacheCodec(directory).write(wrapped, bytes);
            LibraryTransferService.cleanupUnprotectedDownloads(directory);
            require(!Files.exists(raw) && Files.exists(wrapped), "legacy cleanup preserves wrapped download files");
            testThumbnailBudget(cache, viewer);
            require(Files.readString(formal).equals("existing wardrobe data"), "preview cleanup never touches formal/wardrobe storage");
            System.out.println("PASS preview cache: independent paths, live authorization, scoped identities, checksum, TTL/LRU budgets and cross-session active leases");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    private static Path onlyModel(LibraryPreviewCache cache) throws IOException {
        try (var files = Files.list(cache.root().resolve("models"))) {
            return files.filter(path -> path.toString().endsWith(".bin")).findFirst().orElseThrow();
        }
    }

    private static void testThumbnailBudget(LibraryPreviewCache cache, UUID viewer) throws Exception {
        byte[] bytes = new byte[2 * 1024 * 1024];
        for (int index = 0; index < 18; index++) {
            bytes[0] = (byte) index;
            var scope = new LibraryPreviewCache.Scope("server", viewer, new UUID(0, index), 1, 1);
            cache.put(new LibraryPreviewCache.Key(scope, CloudHttp.sha256(bytes)), LibraryPreviewCache.Kind.THUMBNAIL, bytes);
        }
        try (var files = Files.list(cache.root().resolve("thumbnails"))) {
            long total = 0;
            int count = 0;
            for (Path file : files.toList()) {
                total += Files.size(file);
                count++;
            }
            require(total <= 32L * 1024 * 1024 && count <= 16, "thumbnail LRU obeys its independent 32 MiB budget");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
