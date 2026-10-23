package top.aihmc.omnimorph.model.outfit;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Device-local accessory inventory. Scanning is worker-only; UI reads immutable snapshots. */
public final class LocalCosmeticInventory implements AutoCloseable {
    public record Entry(Path path, UUID localId, LocalCosmeticSource.Description source, String error) {
        public OutfitConfig.ResourceRef reference() {
            if (source == null) {
                throw new IllegalStateException(error);
            }
            return new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, localId, null, null,
                    null, source.contentHash(), source.format());
        }
    }

    private record Ids(Map<String, UUID> resources) {}

    private static final Gson JSON = new Gson();
    private final Path root;
    private final Path idsFile;
    private final WatchService watcher;
    private final Set<Path> watched = new HashSet<>();
    private final Map<String, UUID> ids = new HashMap<>();
    private volatile List<Entry> entries = List.of();
    private boolean closed;

    public LocalCosmeticInventory(Path dataRoot) throws IOException {
        Path data = dataRoot.toAbsolutePath().normalize();
        root = data.resolve(CosmeticMetadata.DIRECTORY);
        idsFile = data.resolve("cosmetic-resource-ids.json");
        refreshIds();
        watcher = data.getFileSystem().newWatchService();
    }

    /** Another worker-owned inventory may have assigned IDs while this view was retained by a child screen. */
    private void refreshIds() throws IOException {
        if (Files.isRegularFile(idsFile) && !Files.isSymbolicLink(idsFile)) {
            if (Files.size(idsFile) > 1024 * 1024) {
                throw new IOException("COSMETIC_ID_INDEX_LIMIT");
            }
            try {
                Ids stored = JSON.fromJson(Files.readString(idsFile), Ids.class);
                if (stored == null || stored.resources() == null || stored.resources().size() > 4096) {
                    throw new IOException("INVALID_COSMETIC_ID_INDEX");
                }
                for (var entry : stored.resources().entrySet()) {
                    Path path = root.resolve(entry.getKey()).normalize();
                    if (!path.startsWith(root) || path.equals(root) || entry.getValue() == null) {
                        throw new IOException("INVALID_COSMETIC_ID_INDEX");
                    }
                    ids.put(entry.getKey(), entry.getValue());
                }
            } catch (RuntimeException invalid) {
                throw new IOException("INVALID_COSMETIC_ID_INDEX", invalid);
            }
        }
    }

    public Path directory() {
        return root;
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Watch notifications only invalidate the inventory; they never reload a runtime on the UI thread. */
    public synchronized boolean changed() {
        boolean changed = false;
        WatchKey key;
        while (!closed && (key = watcher.poll()) != null) {
            if (!key.pollEvents().isEmpty()) {
                changed = true;
            }
            if (!key.reset()) {
                watched.remove((Path) key.watchable());
                changed = true;
            }
        }
        return changed;
    }

    public synchronized List<Entry> scan(BooleanSupplier cancelled) throws IOException {
        if (closed || Files.isSymbolicLink(root) || Files.isSymbolicLink(idsFile)) {
            throw new IOException("UNAVAILABLE_COSMETIC_DIRECTORY");
        }
        refreshIds();
        Files.createDirectories(root);
        var found = new ArrayList<Entry>();
        var packages = new HashSet<Path>();
        Files.walkFileTree(root, Set.of(), OutfitConfig.MAX_DEPTH, new SimpleFileVisitor<>() {
            private int count;

            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                check();
                if (watched.add(directory)) {
                    directory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                }
                if (!directory.equals(root) && packages.stream().noneMatch(directory::startsWith)
                        && Files.isRegularFile(directory.resolve(CosmeticMetadata.FILE))) {
                    packages.add(directory);
                    add(directory);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                check();
                if (attributes.isRegularFile() && packages.stream().noneMatch(file::startsWith)
                        && file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                    try (var source = top.aihmc.omnimorph.avatar.ysm.YsmPackage.open(file)) {
                        if (source.exists(CosmeticMetadata.FILE)) {
                            add(file);
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            private void check() throws IOException {
                top.aihmc.omnimorph.avatar.ysm.YsmResourceLimits.checkCancelled(cancelled);
                if (++count > 16384) {
                    throw new IOException("COSMETIC_INVENTORY_LIMIT");
                }
            }

            private void add(Path path) throws IOException {
                String key = root.relativize(path).toString().replace('\\', '/');
                if (!ids.containsKey(key) && ids.size() >= 4096) {
                    throw new IOException("COSMETIC_ID_INDEX_LIMIT");
                }
                UUID id = ids.computeIfAbsent(key, ignored -> UUID.randomUUID());
                try {
                    found.add(new Entry(path, id, LocalCosmeticSource.describe(path, cancelled), ""));
                } catch (java.io.InterruptedIOException cancelledRead) {
                    throw cancelledRead;
                } catch (IOException invalid) {
                    found.add(new Entry(path, id, null, "INVALID_COSMETIC_SOURCE"));
                }
            }
        });
        found.sort(java.util.Comparator.comparing(entry -> root.relativize(entry.path()).toString()));
        Path temporary = Files.createTempFile(idsFile.getParent(), "cosmetic-ids-", ".part");
        try {
            Files.writeString(temporary, JSON.toJson(new Ids(Map.copyOf(ids))));
            Files.move(temporary, idsFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        entries = List.copyOf(found);
        return entries;
    }

    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
            watcher.close();
            watched.clear();
            entries = List.of();
        }
    }
}
