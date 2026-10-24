package top.aihmc.omnimorph.model.library;

import com.google.gson.Gson;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import org.figuramc.figura.config.Configs;
import top.aihmc.omnimorph.model.cache.ModelCacheCodec;
import top.aihmc.omnimorph.model.cache.ModelCacheLayout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Connection-owned collection with one bounded automatic transfer at a time. Client-thread lifecycle. */
public final class LibraryCollectionService implements AutoCloseable {
    private record Snapshot(List<LibraryModels.CollectionEntry> entries, Map<UUID, String> cacheStates, String failure) {}

    private final LibraryClient client;
    private final LibraryTransferService transfers;
    private final Path snapshot;
    private final ModelCacheCodec codec;
    private final LibraryResourceCache resources;
    private final Map<UUID, String> cacheStates = new LinkedHashMap<>();
    private final Map<UUID, LibraryTransferService.Operation> additions = new LinkedHashMap<>();
    private List<LibraryModels.CollectionEntry> entries = List.of();
    private LibraryClient.Request<Snapshot> refresh;
    private LibraryTransferService.Operation automatic;
    private UUID automaticModel;
    private String automaticPrevious;
    private int automaticIndex;
    private boolean closed;
    private boolean authoritative;
    private String failure = "";

    public LibraryCollectionService(LibraryClient client, Path cacheRoot) {
        this.client = client;
        transfers = new LibraryTransferService(client, cacheRoot);
        codec = new ModelCacheCodec(cacheRoot);
        resources = new LibraryResourceCache(cacheRoot, client.session());
        snapshot = metadataPath(cacheRoot, client.origin(), client.session().player());
    }

    public List<LibraryModels.CollectionEntry> entries() {
        return entries;
    }

    public String failure() {
        return failure;
    }

    public String cacheState(UUID model) {
        return cacheStates.getOrDefault(model, "not_cached");
    }

    public boolean available() {
        return !closed && authoritative;
    }

    public boolean busy(UUID model) {
        var addition = additions.get(model);
        return addition != null && !addition.request().future().isDone()
                || model.equals(automaticModel) && automatic != null && !automatic.request().future().isDone();
    }

    public CompletableFuture<List<LibraryModels.CollectionEntry>> remove(UUID model) {
        if (busy(model)) {
            return CompletableFuture.failedFuture(new LibraryClient.Failure(0, "RESOURCE_BUSY"));
        }
        return client.collectionRemove(model).future().thenCompose(ignored -> {
            // A pre-mutation page must not restore the just-removed membership.
            if (refresh != null && !refresh.future().isDone()) {
                refresh.cancel();
            }
            refresh = null;
            entries = entries.stream().filter(entry -> !entry.modelId().equals(model)).toList();
            cacheStates.remove(model);
            return refresh();
        });
    }

    public CompletableFuture<List<LibraryModels.CollectionEntry>> refresh() {
        if (refresh != null && !refresh.future().isDone()) {
            return refresh.future().thenApply(Snapshot::entries);
        }
        failure = "";
        authoritative = false;
        automaticIndex = 0;
        refresh = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            try {
                var result = new ArrayList<LibraryModels.CollectionEntry>();
                int page = 1;
                while (true) {
                    var response = exchange.json("GET", "/api/library/v1/collection?page_size=64&page=" + page,
                            null, LibraryModels.CollectionPage.class);
                    result.addAll(response.items());
                    if (!response.hasNext()) {
                        saveSnapshot(result);
                        var states = new LinkedHashMap<UUID, String>();
                        for (var entry : result) {
                            states.put(entry.modelId(), entry.model() == null ? "unavailable"
                                    : resources.present(entry.model()) ? "cached" : "not_cached");
                        }
                        return new Snapshot(List.copyOf(result), states, "");
                    }
                    exchange.requireCurrent();
                    page++;
                }
            } catch (IOException unavailable) {
                // Only metadata is retained offline. Every preview/download still rechecks authority.
                return new Snapshot(readSnapshot(), Map.of(), code(unavailable));
            }
        });
        refresh.future().whenComplete((value, error) -> {
            if (!closed && client.current()) {
                if (error == null) {
                    entries = value.entries();
                    failure = value.failure();
                    authoritative = value.failure().isEmpty();
                    cacheStates.clear();
                    cacheStates.putAll(value.cacheStates());
                } else {
                    failure = code(error);
                }
            }
        });
        return refresh.future().thenApply(Snapshot::entries);
    }

    private void saveSnapshot(List<LibraryModels.CollectionEntry> entries) throws IOException {
        Path partial = Files.createTempFile(snapshot.getParent(), "collection-", ".part");
        try {
            codec.write(partial, new Gson().toJson(entries).getBytes(StandardCharsets.UTF_8));
            client.session().requireCurrent();
            Files.move(partial, snapshot, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    private List<LibraryModels.CollectionEntry> readSnapshot() {
        return readMetadata(snapshot, codec);
    }

    /** Offline metadata is scoped to the configured origin and account, never used as authorization. */
    public static List<LibraryModels.CollectionEntry> offlineMetadata(Path root, String origin, UUID viewer) {
        return readMetadata(metadataPath(root, origin, viewer), new ModelCacheCodec(root));
    }

    private static Path metadataPath(Path root, String origin, UUID viewer) {
        String scope = origin + "\n" + viewer;
        return root.resolve("wardrobe-collection-" + CloudHttp.sha256(scope.getBytes(StandardCharsets.UTF_8)) + ModelCacheLayout.COLLECTION_EXTENSION);
    }

    private static List<LibraryModels.CollectionEntry> readMetadata(Path snapshot, ModelCacheCodec codec) {
        try {
            byte[] bytes = codec.read(snapshot, 16 * 1024 * 1024);
            var values = new Gson().fromJson(new String(bytes, StandardCharsets.UTF_8),
                    LibraryModels.CollectionEntry[].class);
            return values == null ? List.of() : List.of(values);
        } catch (IOException | RuntimeException invalid) {
            return List.of();
        }
    }

    public LibraryTransferService.Operation add(UUID model) {
        if (automatic != null && model.equals(automaticModel) && !automatic.request().future().isDone()) {
            return automatic; // Automatic transfers only target resources already in this account's collection.
        }
        var existing = additions.get(model);
        if (existing != null && !existing.request().future().isDone()) {
            return existing;
        }
        var operation = transfers.add(model);
        cacheStates.put(model, "downloading");
        additions.put(model, operation);
        operation.request().future().whenComplete((value, error) -> {
            additions.remove(model, operation);
            if (!closed && client.current()) {
                if (error == null) {
                    cacheStates.put(model, "cached");
                    refresh();
                } else {
                    cacheStates.put(model, "cache_failed");
                    failure = code(error);
                    // A lost join response or a failed local commit may still have created the member.
                    refresh().whenComplete((members, refreshError) -> {
                        if (!closed && client.current()) {
                            cacheStates.put(model, "cache_failed");
                            failure = code(error);
                        }
                    });
                }
            }
        });
        return operation;
    }

    public void tick() {
        if (closed || !client.current()) {
            close();
            return;
        }
        if (!Configs.WARDROBE_AUTO_CACHE.value) {
            if (automatic != null) {
                if (!automatic.request().future().isDone()) {
                    cacheStates.put(automaticModel, automaticPrevious);
                }
                automatic.close();
                automatic = null;
                automaticIndex = 0;
            }
            return;
        }
        if (!available() || !failure.isEmpty() || refresh == null || !refresh.future().isDone()
                || automatic != null && !automatic.request().future().isDone()) {
            return;
        }
        while (automaticIndex < entries.size()) {
            var entry = entries.get(automaticIndex++);
            var addition = additions.get(entry.modelId());
            if (addition != null && !addition.request().future().isDone()) {
                continue;
            }
            if (entry.model() != null && entry.model().allows("download")) {
                automatic = transfers.download(entry.modelId());
                automaticModel = entry.modelId();
                automaticPrevious = cacheState(entry.modelId());
                cacheStates.put(entry.modelId(), "downloading");
                automatic.request().future().whenComplete((value, error) -> {
                    if (error != null && !closed && Configs.WARDROBE_AUTO_CACHE.value) {
                        cacheStates.put(entry.modelId(), "cache_failed");
                        failure = code(error);
                    } else if (error == null && !closed) {
                        cacheStates.put(entry.modelId(), "cached");
                    }
                });
                break;
            }
        }
    }

    private static String code(Throwable error) {
        return error instanceof LibraryClient.Failure typed ? typed.code : "CACHE_UNAVAILABLE";
    }

    @Override
    public void close() {
        closed = true;
        if (refresh != null) {
            refresh.cancel();
        }
        if (automatic != null) {
            automatic.close();
        }
        for (var operation : List.copyOf(additions.values())) {
            operation.close();
        }
        additions.clear();
        entries = List.of();
    }
}
