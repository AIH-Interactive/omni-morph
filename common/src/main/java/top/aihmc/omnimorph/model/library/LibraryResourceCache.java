package top.aihmc.omnimorph.model.library;

import com.google.gson.Gson;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.model.cache.AvatarCachePaths;
import top.aihmc.omnimorph.model.cache.ModelCacheCodec;
import top.aihmc.omnimorph.model.cache.ModelCacheLayout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Worker-only formal resources. Callers must first obtain fresh detail permission. */
public final class LibraryResourceCache {
    private record Index(long revision, long policyRevision, String hash, long length) {}
    private record StagedIndex(Index resource, UUID transfer, UUID completion, long expiresAt) {}
    public record Pending(byte[] bytes, String hash, UUID transfer, UUID completion, long expiresAt) {}
    private static final Gson JSON = new Gson();
    private static final Object PUBLICATION = new Object();
    private final Path directory;
    private final Path cacheRoot;
    private final LibrarySession session;
    private final ModelCacheCodec codec;

    public LibraryResourceCache(Path root, LibrarySession session) {
        this.session = session;
        cacheRoot = root.toAbsolutePath().normalize();
        directory = cacheRoot.resolve(ModelCacheLayout.AVATAR_DIRECTORY);
        codec = new ModelCacheCodec(root);
    }

    private Path path(LibraryModels.Model model, String suffix) throws IOException {
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("UNSAFE_RESOURCE_CACHE");
        }
        AvatarCachePaths.prepareFlat(cacheRoot, "wardrobe-resources", file -> {
            String name = file.getFileName().toString();
            if (!name.matches("[a-f0-9]{64}(\\.[a-f0-9-]{36}\\.pending)?\\.(oma|omc|json)")) {
                return null;
            }
            if (name.endsWith(".omc")) {
                name = name.substring(0, name.length() - 4) + ModelCacheLayout.AVATAR_EXTENSION;
            }
            return "resource-" + name;
        });
        String identity = session.instance() + "\n" + session.player() + "\n" + model.modelId();
        return directory.resolve("resource-" + CloudHttp.sha256(identity.getBytes(StandardCharsets.UTF_8)) + suffix);
    }

    public byte[] read(LibraryModels.Model model) throws IOException {
        session.requireCurrent();
        Path indexFile = path(model, ".json");
        Path data = path(model, ModelCacheLayout.AVATAR_EXTENSION);
        if (!Files.isRegularFile(indexFile) || !Files.isRegularFile(data)) {
            return null;
        }
        try {
            if (Files.isSymbolicLink(indexFile) || Files.size(indexFile) > 4096) {
                return null;
            }
            Index index = JSON.fromJson(Files.readString(indexFile), Index.class);
            if (index == null || index.revision() != model.revision()
                    || index.policyRevision() != model.policyRevision() || index.length() != model.byteLength()) {
                return null;
            }
            byte[] bytes = codec.read(data, CloudHttp.FILE_BYTES);
            if (bytes.length != index.length() || !CloudHttp.sha256(bytes).equals(index.hash())) {
                return null;
            }
            session.requireCurrent();
            return bytes;
        } catch (IOException | RuntimeException invalid) {
            session.requireCurrent();
            return null;
        }
    }

    /** Descriptive UI status only; actual reads still authenticate and hash-check the package. */
    public boolean present(LibraryModels.Model model) {
        try {
            Path index = path(model, ".json");
            Path data = path(model, ModelCacheLayout.AVATAR_EXTENSION);
            if (!Files.isRegularFile(index) || Files.isSymbolicLink(index) || Files.size(index) > 4096
                    || !Files.isRegularFile(data) || Files.isSymbolicLink(data)) {
                return false;
            }
            Index value = JSON.fromJson(Files.readString(index), Index.class);
            return value != null && value.revision() == model.revision()
                    && value.policyRevision() == model.policyRevision();
        } catch (IOException | RuntimeException invalid) {
            return false;
        }
    }

    /** Receipt IDs are kept with the verified encrypted input so lost responses never require another body. */
    public Pending stage(LibraryModels.Model model, Path encrypted, byte[] bytes, String hash,
                         LibraryModels.Transfer transfer) throws IOException {
        UUID completion = UUID.randomUUID();
        Path data = pendingPath(model, transfer.transferId(), ModelCacheLayout.AVATAR_EXTENSION);
        Path index = pendingPath(model, transfer.transferId(), ".json");
        Path partial = Files.createTempFile(directory, "receipt-", ".part");
        boolean published = false;
        try {
            Files.writeString(partial, JSON.toJson(new StagedIndex(
                    new Index(model.revision(), model.policyRevision(), hash, bytes.length),
                    transfer.transferId(), completion, transfer.expiresAt())));
            session.requireCurrent();
            Files.move(encrypted, data, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            Files.move(partial, index, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            published = true;
            return new Pending(bytes, hash, transfer.transferId(), completion, transfer.expiresAt());
        } finally {
            Files.deleteIfExists(partial);
            if (!published) {
                Files.deleteIfExists(data);
            }
        }
    }

    public Pending pending(LibraryModels.Model model) throws IOException {
        String prefix = path(model, "").getFileName().toString() + ".";
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(candidate -> candidate.getFileName().toString().startsWith(prefix)
                    && candidate.getFileName().toString().endsWith(".pending.json")).toList()) {
                UUID transfer;
                try {
                    String name = file.getFileName().toString();
                    transfer = UUID.fromString(name.substring(prefix.length(), name.length() - ".pending.json".length()));
                } catch (IllegalArgumentException malformedName) {
                    continue;
                }
                Path data = pendingPath(model, transfer, ModelCacheLayout.AVATAR_EXTENSION);
                try {
                    if (Files.isSymbolicLink(file) || Files.size(file) > 4096) {
                        throw new IOException("INVALID_PENDING_CACHE");
                    }
                    StagedIndex stored = JSON.fromJson(Files.readString(file), StagedIndex.class);
                    if (stored == null || stored.resource() == null || stored.completion() == null
                            || !transfer.equals(stored.transfer()) || stored.expiresAt() <= java.time.Instant.now().getEpochSecond()
                            || stored.resource().revision() != model.revision()
                            || stored.resource().policyRevision() != model.policyRevision()
                            || stored.resource().length() != model.byteLength()) {
                        throw new IOException("STALE_PENDING_CACHE");
                    }
                    byte[] bytes = codec.read(data, CloudHttp.FILE_BYTES);
                    if (bytes.length != model.byteLength() || !CloudHttp.sha256(bytes).equals(stored.resource().hash())) {
                        throw new IOException("INVALID_PENDING_CACHE");
                    }
                    session.requireCurrent();
                    return new Pending(bytes, stored.resource().hash(), transfer, stored.completion(), stored.expiresAt());
                } catch (IOException | com.google.gson.JsonParseException invalid) {
                    Files.deleteIfExists(file);
                    Files.deleteIfExists(data);
                }
            }
        }
        return null;
    }

    private Path pendingPath(LibraryModels.Model model, UUID transfer, String suffix) throws IOException {
        return path(model, "." + transfer + ".pending" + suffix);
    }

    public void publish(LibraryModels.Model model, Pending pending) throws IOException {
        synchronized (PUBLICATION) {
            Path data = pendingPath(model, pending.transfer(), ModelCacheLayout.AVATAR_EXTENSION);
            if (Files.exists(data)) {
                publish(model, data, pending.hash());
            } else {
                byte[] existing = read(model);
                if (existing == null || !CloudHttp.sha256(existing).equals(pending.hash())) {
                    throw new IOException("PENDING_CACHE_MISSING");
                }
            }
            Files.deleteIfExists(pendingPath(model, pending.transfer(), ".json"));
        }
    }

    /** Publishes data before its index; interrupted commits are cache misses, never partial models. */
    private void publish(LibraryModels.Model model, Path encrypted, String hash) throws IOException {
        Path target = path(model, ModelCacheLayout.AVATAR_EXTENSION);
        Path index = path(model, ".json");
        Path partial = Files.createTempFile(directory, "index-", ".part");
        try {
            Files.writeString(partial, JSON.toJson(new Index(model.revision(), model.policyRevision(), hash,
                    model.byteLength())));
            session.requireCurrent();
            Files.move(encrypted, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            Files.move(partial, index, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(partial);
        }
    }
}
