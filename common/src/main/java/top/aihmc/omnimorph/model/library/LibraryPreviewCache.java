package top.aihmc.omnimorph.model.library;

import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.model.cache.AvatarCachePaths;
import top.aihmc.omnimorph.model.cache.ModelCacheCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Independent raw-distribution/thumbnail cache. All methods run on a worker. */
public final class LibraryPreviewCache {
    public enum Kind {
        MODEL("models", 128L * 1024 * 1024), WARDROBE("wardrobe", 128L * 1024 * 1024),
        THUMBNAIL("thumbnails", 32L * 1024 * 1024);

        private final String directory;
        private final long budget;

        Kind(String directory, long budget) {
            this.directory = directory;
            this.budget = budget;
        }
    }

    public record Scope(String instance, UUID viewer, UUID model, long revision, long policyRevision) {
        public Scope {
            if (instance == null || instance.isEmpty() || instance.length() > 2048 || viewer == null || model == null
                    || revision < 1 || policyRevision < 1) {
                throw new IllegalArgumentException("INVALID_PREVIEW_KEY");
            }
        }

        private String prefix() {
            String identity = instance + "\n" + viewer + "\n" + model + "\n" + revision + "\n" + policyRevision;
            return CloudHttp.sha256(identity.getBytes(StandardCharsets.UTF_8)) + "-";
        }
    }

    public record Key(Scope scope, String rawHash) {
        public Key {
            if (scope == null || rawHash == null || !rawHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("INVALID_PREVIEW_KEY");
            }
        }

        private String stem(Kind kind) {
            return kind.name().toLowerCase(java.util.Locale.ROOT) + "-" + scope.prefix() + rawHash;
        }
    }

    @FunctionalInterface
    public interface PermissionCheck {
        void check() throws Exception;
    }

    private static final Duration DEFAULT_LIFETIME = Duration.ofHours(1);
    private static final int MAX_ENTRIES = 4096;
    // Protect active files across screen/session cache objects sharing this independent root.
    private static final Map<Path, Integer> ACTIVE = new HashMap<>();
    private final Path root;
    private final Path cacheRoot;
    private final ModelCacheCodec codec;
    private boolean initialized;

    public LibraryPreviewCache(Path figuraCacheDirectory) {
        codec = new ModelCacheCodec(figuraCacheDirectory);
        cacheRoot = figuraCacheDirectory.toAbsolutePath().normalize();
        root = cacheRoot.resolve("avatars");
    }

    public Path root() {
        return root;
    }

    public Optional<Lease> find(Key key, Kind kind, PermissionCheck permission) throws Exception {
        return find(key, kind, DEFAULT_LIFETIME, permission);
    }

    public Optional<Lease> find(Key key, Kind kind, Duration lifetime, PermissionCheck permission) throws Exception {
        // Never turn a disk hit into an offline or stale authorization decision.
        permission.check();
        if (lifetime.isZero() || lifetime.isNegative()) {
            return Optional.empty();
        }
        initialize(lifetime);
        Path directory = directory(kind);
        Path path = directory.resolve(key.stem(kind) + ".omc");
        if (kind == Kind.THUMBNAIL) {
            path = directory.resolve(key.stem(kind) + ".png");
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                path = directory.resolve(key.stem(kind) + ".jpg");
            }
        }
        synchronized (ACTIVE) {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return Optional.empty();
            }
            if (expired(path, lifetime) || Files.size(path) > limit(kind) + 65536L) {
                deleteUnused(path);
                return Optional.empty();
            }
            ACTIVE.merge(path, 1, Integer::sum);
        }
        Lease lease = new Lease(path, key.rawHash(), limit(kind), kind == Kind.THUMBNAIL ? null : codec);
        try {
            // Verify immutable identity again; do not trust a manually replaced cache file.
            lease.read();
            return Optional.of(lease);
        } catch (IOException corrupt) {
            lease.close();
            synchronized (ACTIVE) {
                deleteUnused(path);
            }
            return Optional.empty();
        }
    }

    /** The backend detail fixes scope/revisions before a hash from a previous transfer is reused. */
    public Optional<Lease> find(Scope scope, Kind kind, PermissionCheck permission) throws Exception {
        return find(scope, kind, DEFAULT_LIFETIME, permission);
    }

    public Optional<Lease> find(Scope scope, Kind kind, Duration lifetime, PermissionCheck permission) throws Exception {
        permission.check();
        if (lifetime.isZero() || lifetime.isNegative()) {
            return Optional.empty();
        }
        initialize(lifetime);
        try (var files = Files.list(directory(kind))) {
            for (Path path : files.filter(file -> matches(file.getFileName().toString(), kind))
                    .limit(MAX_ENTRIES + 1L).toList()) {
                String name = path.getFileName().toString();
                String prefix = kind.name().toLowerCase(java.util.Locale.ROOT) + "-" + scope.prefix();
                if (name.startsWith(prefix)) {
                    String hash = name.substring(prefix.length(), prefix.length() + 64);
                    Optional<Lease> lease = find(new Key(scope, hash), kind, lifetime, () -> {});
                    if (lease.isPresent()) {
                        return lease;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Only bytes from a verified server distribution response or original encrypted payload belong here. */
    public boolean put(Key key, Kind kind, byte[] bytes) throws IOException {
        return put(key, kind, bytes, DEFAULT_LIFETIME);
    }

    public boolean put(Key key, Kind kind, byte[] bytes, Duration lifetime) throws IOException {
        if (lifetime.isZero() || lifetime.isNegative()) {
            return false;
        }
        if (bytes.length == 0 || bytes.length > limit(kind) || bytes.length > kind.budget) {
            return false;
        }
        if (!CloudHttp.sha256(bytes).equals(key.rawHash())) {
            throw new IOException("PREVIEW_HASH_MISMATCH");
        }
        initialize(lifetime);
        Path directory = directory(kind);
        Path target = directory.resolve(key.stem(kind)
                + (kind == Kind.THUMBNAIL ? AvatarCachePaths.imageExtension(bytes) : ".omc"));
        synchronized (ACTIVE) {
            if (ACTIVE.containsKey(target)) {
                return false;
            }
            Path partial = Files.createTempFile(directory, "preview-" + kind.directory + "-", ".part");
            try {
                if (kind == Kind.THUMBNAIL) {
                    Files.write(partial, bytes);
                } else {
                    codec.write(partial, bytes);
                }
                try {
                    Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    return false;
                }
            } finally {
                Files.deleteIfExists(partial);
            }
            cleanup(kind, lifetime);
        }
        return Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS);
    }

    /** Startup/periodic cleanup; leased files are excluded from expiry and size-budget eviction. */
    public void cleanup() throws IOException {
        cleanup(DEFAULT_LIFETIME);
    }

    private void cleanup(Duration lifetime) throws IOException {
        synchronized (ACTIVE) {
            for (Kind kind : Kind.values()) {
                cleanup(kind, lifetime);
            }
            initialized = true;
        }
    }

    private void initialize(Duration lifetime) throws IOException {
        synchronized (ACTIVE) {
            if (!initialized) {
                cleanup(lifetime);
            }
        }
    }

    private void cleanup(Kind kind, Duration lifetime) throws IOException {
        Path directory = directory(kind);
        record Entry(Path path, long length, long modified) {}
        var entries = new ArrayList<Entry>();
        try (var files = Files.list(directory)) {
            for (Path path : files.toList()) {
                if (Files.isSymbolicLink(path)) {
                    continue;
                }
                if (path.getFileName().toString().startsWith("preview-" + kind.directory + "-")
                        && path.getFileName().toString().endsWith(".part")) {
                    deleteUnused(path);
                } else if (matches(path.getFileName().toString(), kind)
                        && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (expired(path, lifetime) || kind != Kind.THUMBNAIL && !ModelCacheCodec.isWrapped(path)) {
                        deleteUnused(path);
                    }
                    if (Files.exists(path)) {
                        entries.add(new Entry(path, Files.size(path), Files.getLastModifiedTime(path).toMillis()));
                    }
                }
            }
        }
        entries.sort(Comparator.comparingLong(Entry::modified));
        long bytes = entries.stream().mapToLong(Entry::length).sum();
        int count = entries.size();
        for (Entry entry : entries) {
            if (bytes <= kind.budget && count <= MAX_ENTRIES) {
                break;
            }
            if (deleteUnused(entry.path())) {
                bytes -= entry.length();
                count--;
            }
        }
    }

    private Path directory(Kind kind) throws IOException {
        return AvatarCachePaths.prepareFlat(cacheRoot, "model-library-preview/" + kind.directory, file -> {
            String name = file.getFileName().toString();
            if (!name.matches("[a-f0-9]{64}-[a-f0-9]{64}\\.(bin|omc|png|jpg)")) {
                return null;
            }
            String extension = ".omc";
            if (kind == Kind.THUMBNAIL) {
                try (var input = Files.newInputStream(file)) {
                    extension = AvatarCachePaths.imageExtension(input.readNBytes(8));
                } catch (IOException invalidImage) {
                    return null;
                }
            }
            return kind.name().toLowerCase(java.util.Locale.ROOT) + "-" + name.substring(0, 129) + extension;
        });
    }

    private static boolean matches(String name, Kind kind) {
        String extension = kind == Kind.THUMBNAIL ? "(png|jpg)" : "omc";
        return name.matches(kind.name().toLowerCase(java.util.Locale.ROOT)
                + "-[a-f0-9]{64}-[a-f0-9]{64}\\." + extension);
    }

    private static int limit(Kind kind) {
        return kind == Kind.THUMBNAIL ? 2 * 1024 * 1024 : (int) CloudHttp.FILE_BYTES;
    }

    private static boolean expired(Path path, Duration lifetime) throws IOException {
        return System.currentTimeMillis() - Files.getLastModifiedTime(path).toMillis() >= lifetime.toMillis();
    }

    private static boolean deleteUnused(Path path) throws IOException {
        return !ACTIVE.containsKey(path) && Files.deleteIfExists(path);
    }

    public static final class Lease implements AutoCloseable {
        private final Path path;
        private final String hash;
        private final int limit;
        private final ModelCacheCodec codec;
        private boolean closed;

        private Lease(Path path, String hash, int limit, ModelCacheCodec codec) {
            this.path = path;
            this.hash = hash;
            this.limit = limit;
            this.codec = codec;
        }

        public synchronized byte[] read() throws IOException {
            if (closed || Files.isSymbolicLink(path)) {
                throw new IOException("PREVIEW_LEASE_CLOSED");
            }
            byte[] bytes;
            if (codec != null) {
                bytes = codec.read(path, limit);
            } else {
                try (var input = Files.newInputStream(path)) {
                    bytes = CloudHttp.readBounded(input, limit);
                }
            }
            if (!CloudHttp.sha256(bytes).equals(hash)) {
                throw new IOException("PREVIEW_HASH_MISMATCH");
            }
            return bytes;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            synchronized (ACTIVE) {
                ACTIVE.computeIfPresent(path, (ignored, references) -> references > 1 ? references - 1 : null);
            }
        }
    }
}
