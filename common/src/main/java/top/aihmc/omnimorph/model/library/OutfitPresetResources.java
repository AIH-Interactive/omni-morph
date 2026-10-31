package top.aihmc.omnimorph.model.library;

import org.figuramc.figura.utils.NbtDefaults;

import net.minecraft.nbt.CompoundTag;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.avatar.local.LocalAvatarSource;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.model.outfit.CosmeticMetadata;
import top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory;
import top.aihmc.omnimorph.model.outfit.LocalCosmeticSource;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/** Resolves the exact dependencies of one preset. No publication, source upgrade or implicit authorization. */
public final class OutfitPresetResources implements AutoCloseable {
    public record Issue(OutfitConfig.ResourceRef resource, boolean base, List<UUID> instances, String code) {}

    private record Dependency(OutfitConfig.ResourceRef resource, boolean base) {}
    private record Resource(CompoundTag data, CosmeticMetadata metadata, LocalCosmeticInventory.Entry local) {}

    /** Formal preparation may supply already authorized fixed native input, including a precise failure per source. */
    public record LocalInput(CompoundTag data, String error) {
        public LocalInput {
            if ((data == null) == (error == null) || error != null && !error.matches("[A-Z_]{1,64}")) {
                throw new IllegalArgumentException("INVALID_OUTFIT_LOCAL_INPUT");
            }
            data = data == null ? null : data.copy();
        }

        @Override
        public CompoundTag data() {
            return data == null ? null : data.copy();
        }
    }

    public static final class Prepared {
        private final OutfitConfig configuration;
        private final Map<OutfitConfig.ResourceRef, Resource> resources;
        private final List<Issue> issues;
        private final BooleanSupplier current;

        private Prepared(OutfitConfig configuration, Map<OutfitConfig.ResourceRef, Resource> resources,
                         List<Issue> issues, BooleanSupplier current) {
            this.configuration = configuration;
            this.resources = Map.copyOf(resources);
            this.issues = List.copyOf(issues);
            this.current = current;
        }

        public List<Issue> issues() {
            return issues;
        }

        public boolean ready() {
            return current.getAsBoolean() && issues.isEmpty();
        }

        public boolean contains(OutfitConfig.ResourceRef reference) {
            requireCurrent();
            return resources.containsKey(reference);
        }

        public CompoundTag base() throws IOException {
            return configuration.base() == null ? null : read(configuration.base(), () -> false);
        }

        public CosmeticMetadata metadata(OutfitConfig.ResourceRef reference) {
            requireCurrent();
            Resource resource = resources.get(reference);
            return resource == null ? null : resource.metadata();
        }

        public Map<OutfitConfig.ResourceRef, LocalCosmeticInventory.Entry> localAccessories() {
            requireCurrent();
            var result = new LinkedHashMap<OutfitConfig.ResourceRef, LocalCosmeticInventory.Entry>();
            resources.forEach((reference, resource) -> {
                if (resource.local() != null) {
                    result.put(reference, resource.local());
                }
            });
            return Map.copyOf(result);
        }

        /** Every runtime gets its own mutable NBT. A prepared result never extends the originating session's lifetime. */
        public CompoundTag read(OutfitConfig.ResourceRef reference, BooleanSupplier cancelled) throws IOException {
            requireCurrent();
            if (cancelled.getAsBoolean()) {
                throw new CancellationException("OUTFIT_PRESET_RETIRED");
            }
            Resource resource = resources.get(reference);
            if (resource == null) {
                throw new IOException("OUTFIT_PRESET_DEPENDENCY_UNAVAILABLE");
            }
            return resource.data().copy();
        }

        private void requireCurrent() {
            if (!current.getAsBoolean()) {
                throw new CancellationException("OUTFIT_PRESET_RETIRED");
            }
        }
    }

    private final OutfitConfig configuration;
    private final UUID account;
    private final Path directory;
    private final LibraryClient client;
    private final LibraryTransferService transfers;
    private final Executor publisher;
    private final BooleanSupplier active;
    private final List<Dependency> dependencies;
    private final boolean requiresCloud;
    private final Map<OutfitConfig.ResourceRef, LocalInput> fixedLocal;
    private final Map<OutfitConfig.ResourceRef, Resource> resources = new LinkedHashMap<>();
    private final List<Issue> issues = new ArrayList<>();
    private final CompletableFuture<Prepared> future = new CompletableFuture<>();
    private Map<UUID, LocalCosmeticInventory.Entry> localAccessories;
    private AutoCloseable pending;
    private int next;
    private volatile boolean closed;

    public OutfitPresetResources(OutfitConfig configuration, OutfitLimits limits, UUID account, Path directory,
                                LibraryClient client, LibraryTransferService transfers, Executor publisher,
                                BooleanSupplier active) {
        this(configuration, limits, account, directory, client, transfers, publisher, active, null);
    }

    public OutfitPresetResources(OutfitConfig configuration, OutfitLimits limits, UUID account, Path directory,
                                LibraryClient client, LibraryTransferService transfers, Executor publisher,
                                BooleanSupplier active, Map<OutfitConfig.ResourceRef, LocalInput> fixedLocal) {
        OutfitCodec.encode(configuration, limits);
        this.configuration = configuration;
        this.account = account;
        this.directory = directory;
        this.client = client;
        this.transfers = transfers;
        this.publisher = publisher;
        this.active = active;
        this.fixedLocal = fixedLocal == null ? null : Map.copyOf(fixedLocal);
        var requested = new LinkedHashMap<OutfitConfig.ResourceRef, Dependency>();
        if (configuration.base() != null) {
            requested.put(configuration.base(), new Dependency(configuration.base(), true));
        }
        for (var instance : configuration.instances()) {
            requested.putIfAbsent(instance.resource(), new Dependency(instance.resource(), false));
        }
        dependencies = List.copyOf(requested.values());
        requiresCloud = dependencies.stream().anyMatch(dependency -> dependency.resource().source() == OutfitConfig.Source.CLOUD);
        future.whenComplete((value, failure) -> {
            if (future.isCancelled()) {
                close();
            }
        });
        next();
    }

    public CompletableFuture<Prepared> future() {
        return future;
    }

    private boolean current() {
        return !closed && active.getAsBoolean()
                && (!requiresCloud || client == null || client.current() && account.equals(client.session().player()));
    }

    /** One dependency at a time also bounds transfer handles and temporary decoded packages. */
    private void next() {
        if (future.isDone()) {
            return;
        }
        if (!current()) {
            future.cancel(false);
            return;
        }
        if (next == dependencies.size()) {
            future.complete(new Prepared(configuration, resources, issues, this::current));
            return;
        }
        Dependency dependency = dependencies.get(next++);
        var reference = dependency.resource();
        if (reference.source() == OutfitConfig.Source.LOCAL) {
            AvatarLoadExecutor.submit(() -> {
                try {
                    return readLocal(dependency);
                } catch (Exception failure) {
                    throw new CompletionException(failure);
                }
            }).whenComplete((value, failure) ->
                    publisher.execute(() -> accept(dependency, value, failure)));
            return;
        }
        if (client == null || transfers == null || !client.origin().equals(reference.origin())) {
            accept(dependency, null, new IOException("OUTFIT_PRESET_DEPENDENCY_OFFLINE"));
            return;
        }
        try {
            if (dependency.base()) {
                var transfer = transfers.preview(reference.resourceId(), reference.revision());
                pending = transfer;
                transfer.request().future().whenComplete((value, failure) -> {
                    if (failure != null) {
                        accept(dependency, null, failure);
                    } else {
                        AvatarLoadExecutor.submit(() -> {
                            try {
                                return readCloudBase(reference, value);
                            } catch (IOException invalid) {
                                throw new CompletionException(invalid);
                            }
                        }).whenComplete((prepared, invalid) ->
                                publisher.execute(() -> accept(dependency, prepared, invalid)));
                    }
                });
            } else {
                var operation = new CloudCosmeticSource(client, transfers, publisher).read(reference);
                pending = operation;
                operation.future().whenComplete((value, failure) -> {
                    Resource prepared = null;
                    try {
                        if (failure == null && current() && !future.isDone()) {
                            prepared = new Resource(value.nbt(), value.descriptor().metadata(), null);
                        }
                    } catch (RuntimeException retired) {
                        failure = retired;
                    }
                    accept(dependency, prepared, failure);
                });
            }
        } catch (RuntimeException failure) {
            accept(dependency, null, failure);
        }
    }

    private Resource readLocal(Dependency dependency) throws Exception {
        if (!current()) {
            throw new CancellationException("OUTFIT_PRESET_RETIRED");
        }
        var reference = dependency.resource();
        if (fixedLocal != null) {
            LocalInput input = fixedLocal.get(reference);
            if (input == null) {
                throw new IOException("OUTFIT_PRESET_DEPENDENCY_MISSING");
            }
            if (input.error() != null) {
                throw new IOException(input.error());
            }
            CompoundTag data = input.data();
            String kind = NbtDefaults.getStringOr(data.getCompound("metadata"), "resource_kind", "AVATAR");
            if (!kind.equals(dependency.base() ? "AVATAR" : "ACCESSORY")) {
                throw new IOException("OUTFIT_PRESET_RESOURCE_KIND_MISMATCH");
            }
            CosmeticMetadata metadata = dependency.base() ? null : CosmeticMetadata.read(
                    data.getCompound("metadata").getString("cosmetic_json"));
            return new Resource(data, metadata, null);
        }
        if (dependency.base()) {
            CompoundTag data = new LocalAvatarSource(directory).prepare(reference, account, () -> !current());
            return new Resource(data, null, null);
        }
        if (localAccessories == null) {
            try (var inventory = new LocalCosmeticInventory(directory)) {
                var found = new LinkedHashMap<UUID, LocalCosmeticInventory.Entry>();
                for (var entry : inventory.scan(() -> !current())) {
                    found.put(entry.localId(), entry);
                }
                localAccessories = Map.copyOf(found);
            }
        }
        var entry = localAccessories.get(reference.localId());
        if (entry == null) {
            throw new IOException("OUTFIT_PRESET_DEPENDENCY_MISSING");
        }
        if (entry.source() == null || !entry.reference().equals(reference)) {
            throw new IOException("OUTFIT_PRESET_DEPENDENCY_CHANGED");
        }
        var prepared = LocalCosmeticSource.prepare(entry.path(), account, reference.contentHash(), () -> !current());
        return new Resource(prepared.nbt(), entry.source().metadata(), entry);
    }

    private Resource readCloudBase(OutfitConfig.ResourceRef expected, LibraryTransferService.Package source) throws IOException {
        if (!current()) {
            throw new CancellationException("OUTFIT_PRESET_RETIRED");
        }
        var model = source.model();
        var actual = new OutfitConfig.ResourceRef(OutfitConfig.Source.CLOUD, null, client.origin(), model.modelId(),
                model.revision(), source.rawHash(), OutfitConfig.Format.valueOf(model.modelType()));
        if (!expected.equals(actual)) {
            throw new IOException("OUTFIT_PRESET_DEPENDENCY_CHANGED");
        }
        if (!source.purpose().equals("preview") || !model.resourceKind().equals("AVATAR")
                || !NbtDefaults.getStringOr(source.nbt().getCompound("metadata"), "resource_kind", "AVATAR").equals("AVATAR")) {
            throw new IOException("OUTFIT_BASE_RESOURCE_KIND_REQUIRED");
        }
        return new Resource(source.nbt().copy(), null, null);
    }

    private void accept(Dependency dependency, Resource value, Throwable failure) {
        pending = null;
        if (future.isDone()) {
            return;
        }
        if (!current()) {
            future.cancel(false);
            return;
        }
        if (failure == null && dependency.base() && configuration.instances().stream()
                .anyMatch(instance -> instance.resource().equals(dependency.resource()))) {
            failure = new IOException("COSMETIC_RESOURCE_KIND_REQUIRED");
        }
        if (failure == null) {
            resources.put(dependency.resource(), value);
        } else {
            Throwable cause = failure;
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            String code = cause instanceof LibraryClient.Failure library ? library.code
                    : cause instanceof IOException ? cause.getMessage() : "OUTFIT_PRESET_DEPENDENCY_FAILED";
            var instances = configuration.instances().stream()
                    .filter(instance -> instance.resource().equals(dependency.resource()))
                    .map(OutfitConfig.Instance::instanceId).toList();
            issues.add(new Issue(dependency.resource(), dependency.base(), instances, code));
        }
        next();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        future.cancel(false);
        if (pending != null) {
            try {
                pending.close();
            } catch (Exception ignored) {
                // Retired transfer handles cannot publish into this operation again.
            }
            pending = null;
        }
    }
}
