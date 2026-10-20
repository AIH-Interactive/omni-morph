package top.aihmc.omnimorph.model.library;

import com.google.gson.Gson;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.model.cache.ModelCacheCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Worker-only formal resources. Callers must first obtain fresh detail permission. */
public final class LibraryResourceCache {
    private record Index(long revision, long policyRevision, String hash, long length) {}
    private static final Gson JSON = new Gson();
    private final Path directory;
    private final LibrarySession session;
    private final ModelCacheCodec codec;

    public LibraryResourceCache(Path root, LibrarySession session) {
        this.session = session;
        directory = root.toAbsolutePath().normalize().resolve("wardrobe-resources");
        codec = new ModelCacheCodec(root);
    }

    private Path path(LibraryModels.Model model, String suffix) throws IOException {
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("UNSAFE_RESOURCE_CACHE");
        }
        Files.createDirectories(directory);
        String identity = session.instance() + "\n" + session.player() + "\n" + model.modelId();
        return directory.resolve(CloudHttp.sha256(identity.getBytes(StandardCharsets.UTF_8)) + suffix);
    }

    public byte[] read(LibraryModels.Model model) throws IOException {
        session.requireCurrent();
        Path indexFile = path(model, ".json");
        Path data = path(model, ".omc");
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
            Path data = path(model, ".omc");
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

    /** Publishes data before its index; interrupted commits are cache misses, never partial models. */
    public void publish(LibraryModels.Model model, Path encrypted, String hash) throws IOException {
        Path target = path(model, ".omc");
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
