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
            testLegacyMigration(directory);
            var cache = new LibraryPreviewCache(directory);
            require(cache.root().startsWith(directory.resolve("avatars")), "model previews share the avatar-only root");
            UUID viewer = UUID.randomUUID();
            UUID model = UUID.randomUUID();
            byte[] bytes = "synthetic server distribution".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var scope = new LibraryPreviewCache.Scope("server-one", viewer, model, 1, 1);
            var key = new LibraryPreviewCache.Key(scope, CloudHttp.sha256(bytes));
            require(cache.put(key, LibraryPreviewCache.Kind.MODEL, bytes), "verified model package is cached");
            Path savedPreview = onlyModel(cache);
            Path oldPreview = Files.createDirectories(directory.resolve("model-library-preview/models"))
                    .resolve(savedPreview.getFileName().toString().substring("model-".length()).replace(".oma", ".bin"));
            Files.move(savedPreview, oldPreview);
            var checks = new AtomicInteger();
            try (var lease = cache.find(scope, LibraryPreviewCache.Kind.MODEL, checks::incrementAndGet).orElseThrow()) {
                require(Arrays.equals(lease.read(), bytes), "cache returns owned raw distribution bytes");
                require(!Files.exists(oldPreview), "actual preview lookup migrates the legacy model directory");
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
            Files.writeString(Files.createDirectories(cache.root()).resolve("preview-models-interrupted.part"), "partial");
            cache.cleanup();
            require(!Files.exists(cache.root().resolve("preview-models-interrupted.part")), "startup cleanup removes incomplete writes");
            Path legacy = Files.createDirectories(directory.resolve("model-library-preview/models")).resolve("a".repeat(64) + "-" + "b".repeat(64) + ".bin");
            Files.writeString(legacy, "legacy plaintext preview");
            cache.cleanup();
            require(!Files.exists(legacy), "unexpired legacy plaintext previews are discarded");
            Path downloads = Files.createDirectories(directory.resolve("model-library-downloads"));
            Path raw = downloads.resolve("download-legacy.part");
            Path wrapped = downloads.resolve("download-current.part");
            Files.writeString(raw, "legacy plaintext download payload that exceeds thirty-two bytes");
            new top.aihmc.omnimorph.model.cache.ModelCacheCodec(directory).write(wrapped, bytes);
            LibraryTransferService.cleanupUnprotectedDownloads(directory);
            require(!Files.exists(directory.resolve("avatars/model-library-downloads").resolve(raw.getFileName()))
                            && Files.exists(directory.resolve("avatars/model-library-downloads").resolve(wrapped.getFileName())),
                    "legacy downloads relocate before cleanup, preserving wrapped files");
            testThumbnailBudget(cache, viewer, directory);
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

    private static void testLegacyMigration(Path directory) throws Exception {
        var codec = new top.aihmc.omnimorph.model.cache.ModelCacheCodec(directory);
        byte[] bytes = "retained encrypted avatar".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String resource = "c".repeat(64) + ".omc";
        Path flat = Files.createDirectories(directory.resolve("avatars"));
        String flatName = "b".repeat(64);
        codec.write(flat.resolve(flatName + ".omc"), bytes);
        Path old = Files.createDirectories(directory.resolve("wardrobe-resources")).resolve(resource);
        codec.write(old, bytes);
        Path thumbnails = Files.createDirectories(directory.resolve("model-library-preview/thumbnails"));
        String stem = "d".repeat(64) + "-" + "e".repeat(64);
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
        Files.write(thumbnails.resolve(stem + ".bin"), png);
        byte[] jpg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
        String jpgStem = "f".repeat(64) + "-" + "a".repeat(64);
        Path previous = Files.createDirectories(directory.resolve("avatars/model-library-preview/thumbnails"));
        Files.write(previous.resolve(jpgStem + ".bin"), jpg);
        Path snapshot = directory.resolve("wardrobe-collection-sample.omc");
        Files.writeString(snapshot, "account metadata");
        Path target = top.aihmc.omnimorph.model.cache.AvatarCachePaths.prepareFlat(directory, "wardrobe-resources",
                file -> "resource-" + file.getFileName().toString().replace(".omc", ".oma"));
        Path relocated = target.resolve("resource-" + resource.replace(".omc", ".oma"));
        require(!Files.exists(flat.resolve(flatName + ".omc"))
                        && Arrays.equals(codec.read(flat.resolve(flatName + ".oma"), 1024), bytes),
                "existing flat avatar .omc files migrate to .oma without changing their contents");
        require(!Files.exists(old) && Arrays.equals(codec.read(relocated, 1024), bytes),
                "relocated encrypted avatars retain their existing key");
        Files.createDirectories(old.getParent());
        Files.writeString(old, "older conflict");
        top.aihmc.omnimorph.model.cache.AvatarCachePaths.prepareFlat(directory, "wardrobe-resources",
                file -> "resource-" + file.getFileName().toString().replace(".omc", ".oma"));
        require(Files.exists(old) && Arrays.equals(codec.read(relocated, 1024), bytes),
                "migration preserves both files when the target already exists");
        new LibraryPreviewCache(directory).cleanup();
        Path pngFile = target.resolve("thumbnail-" + stem + ".png");
        Path jpgFile = target.resolve("thumbnail-" + jpgStem + ".jpg");
        require(Arrays.equals(Files.readAllBytes(pngFile), png) && Arrays.equals(Files.readAllBytes(jpgFile), jpg)
                        && !Files.exists(thumbnails.resolve(stem + ".bin"))
                        && !Files.exists(previous.resolve(jpgStem + ".bin")),
                "both legacy thumbnail layouts migrate beside avatars with signature-correct image extensions");
        require(Files.exists(snapshot) && Files.exists(directory.resolve("model-cache.key"))
                        && !Files.exists(directory.resolve("decorations")),
                "account snapshots, keys and future decoration storage remain independent");
        Files.delete(pngFile);
        Files.delete(jpgFile);
    }

    private static Path onlyModel(LibraryPreviewCache cache) throws IOException {
        try (var files = Files.list(cache.root())) {
            return files.filter(path -> path.getFileName().toString().startsWith("model-") && path.toString().endsWith(".oma")).findFirst().orElseThrow();
        }
    }

    private static void testThumbnailBudget(LibraryPreviewCache cache, UUID viewer, Path directory) throws Exception {
        byte[] bytes = new byte[2 * 1024 * 1024];
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(png, 0, bytes, 0, png.length);
        for (int index = 0; index < 18; index++) {
            bytes[8] = (byte) index;
            var scope = new LibraryPreviewCache.Scope("server", viewer, new UUID(0, index), 1, 1);
            cache.put(new LibraryPreviewCache.Key(scope, CloudHttp.sha256(bytes)), LibraryPreviewCache.Kind.THUMBNAIL, bytes);
        }
        try (var files = Files.list(cache.root())) {
            long total = 0;
            int count = 0;
            for (Path file : files.filter(path -> path.getFileName().toString().startsWith("thumbnail-")).toList()) {
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
