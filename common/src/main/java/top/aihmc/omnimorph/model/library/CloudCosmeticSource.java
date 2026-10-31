package top.aihmc.omnimorph.model.library;

import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.model.outfit.CosmeticMetadata;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** Authorized accessory input for the existing private composition preparation path. */
public final class CloudCosmeticSource {
    private static final Logger LOGGER = LoggerFactory.getLogger("OmniMorph/Outfit");
    public record Descriptor(OutfitConfig.ResourceRef reference, CosmeticMetadata metadata, String name) {}

    public static final class Prepared {
        private final Descriptor descriptor;
        private final CompoundTag data;
        private final LibrarySession session;

        private Prepared(Descriptor descriptor, CompoundTag data, LibrarySession session) {
            this.descriptor = descriptor;
            this.data = data;
            this.session = session;
        }

        public Descriptor descriptor() {
            return descriptor;
        }

        /** Called on the model worker; neither cache bytes nor another instance's NBT can be mutated. */
        public CompoundTag nbt() {
            session.requireCurrent();
            return data.copy();
        }
    }

    public record Operation(LibraryTransferService.Operation transfer, CompletableFuture<Prepared> future) implements AutoCloseable {
        @Override
        public void close() {
            future.cancel(false);
            if (transfer != null) {
                transfer.close();
            }
        }
    }

    private final LibraryClient client;
    private final LibraryTransferService transfers;
    private final Executor publisher;

    public CloudCosmeticSource(LibraryClient client, LibraryTransferService transfers, Executor publisher) {
        this.client = client;
        this.transfers = transfers;
        this.publisher = publisher;
    }

    public Operation select(LibraryModels.Model selected) {
        if (!selected.resourceKind().equals("ACCESSORY")) {
            LOGGER.debug("cloud cosmetic selection {} kind={} name={}", selected.modelId(),
                    selected.resourceKind(), selected.displayName());
            throw failure("COSMETIC_RESOURCE_KIND_REQUIRED");
        }
        return fetch(selected.modelId(), selected.revision(), null);
    }

    /** Reuse a previously acquired source for private editing; fresh admission belongs to Apply. */
    public Operation selectCached(LibraryModels.Model selected) {
        var future = AvatarLoadExecutor.submit(() -> {
            try {
                return prepare(transfers.cachedPreview(selected), null);
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        });
        return new Operation(null, future.thenApplyAsync(value -> value, publisher));
    }

    public Operation read(OutfitConfig.ResourceRef reference) {
        if (reference.source() != OutfitConfig.Source.CLOUD || !reference.origin().equals(client.origin())) {
            throw failure("COSMETIC_RESOURCE_SCOPE_MISMATCH");
        }
        return fetch(reference.resourceId(), reference.revision(), reference);
    }

    private Operation fetch(java.util.UUID id, long revision, OutfitConfig.ResourceRef expected) {
        var transfer = transfers.preview(id, revision);
        var result = new CompletableFuture<Prepared>();
        transfer.request().future().whenComplete((value, error) -> {
            if (result.isCancelled()) {
                return;
            }
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            AvatarLoadExecutor.submit(() -> {
                if (result.isCancelled()) {
                    throw new CancellationException();
                }
                client.session().requireCurrent();
                return prepare(value, expected);
            }).whenComplete((prepared, invalid) -> publisher.execute(() -> {
                if (result.isCancelled()) {
                    return;
                }
                if (!client.current()) {
                    result.cancel(false);
                } else if (invalid == null) {
                    result.complete(prepared);
                } else {
                    Throwable cause = invalid;
                    while (cause instanceof CompletionException && cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    if (cause instanceof CancellationException) {
                        result.cancel(false);
                    } else {
                        result.completeExceptionally(cause instanceof LibraryClient.Failure ? cause
                                : failure("COSMETIC_METADATA_MISMATCH"));
                    }
                }
            }));
        });
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) {
                transfer.close();
            }
        });
        return new Operation(transfer, result);
    }

    private Prepared prepare(LibraryTransferService.Package value, OutfitConfig.ResourceRef expected) {
        var model = value.model();
        var header = value.nbt().getCompoundOrEmpty("metadata");
        String headerKind = header.getStringOr("resource_kind", "");
        if (!model.resourceKind().equals("ACCESSORY") || !headerKind.equals("ACCESSORY")
                || !value.purpose().equals("preview")) {
            LOGGER.debug("cloud cosmetic package {} model_kind={} header_kind='{}' purpose={}",
                    model.modelId(), model.resourceKind(), headerKind, value.purpose());
            throw failure("COSMETIC_RESOURCE_KIND_REQUIRED");
        }
        var reference = new OutfitConfig.ResourceRef(OutfitConfig.Source.CLOUD, null, client.origin(), model.modelId(),
                model.revision(), value.rawHash(), OutfitConfig.Format.valueOf(model.modelType()));
        if (expected != null && !reference.equals(expected)) {
            throw failure("COSMETIC_RESOURCE_REFERENCE_MISMATCH");
        }
        CosmeticMetadata metadata;
        try {
            metadata = CosmeticMetadata.read(header.getStringOr("cosmetic_json", ""));
        } catch (RuntimeException invalid) {
            LOGGER.debug("cloud cosmetic package {} has no readable cosmetic declaration", model.modelId());
            throw failure("COSMETIC_METADATA_MISMATCH");
        }
        if (model.accessoryMetadata() != null) {
            var declaration = CosmeticMetadata.read(model.accessoryMetadata().toString());
            if (!sameDeclaration(metadata, declaration)) {
                throw failure("COSMETIC_METADATA_MISMATCH");
            }
        } else {
            // Older or private catalogue entries can omit the declared accessory metadata; the signed package
            // header still carries the client-extracted declaration this preview has to use.
            LOGGER.debug("cloud cosmetic {} has no catalogue accessory metadata; using the package declaration",
                    model.modelId());
        }
        return new Prepared(new Descriptor(reference, metadata, model.displayName()), value.nbt(), client.session());
    }

    private static boolean sameDeclaration(CosmeticMetadata source, CosmeticMetadata declared) {
        return source.category().equals(declared.category()) && source.cosmeticAnchor().equals(declared.cosmeticAnchor())
                && source.defaultTransform().equals(declared.defaultTransform()) && source.supportedBases().equals(declared.supportedBases())
                && source.hasBones() == declared.hasBones() && source.displayBones() == declared.displayBones()
                && source.editBones() == declared.editBones() && source.skeletonRevision().equals(declared.skeletonRevision())
                && source.skeletonHash().equals(declared.skeletonHash()) && source.exportedBones().equals(declared.exportedBones())
                && source.editableBones().equals(declared.editableBones());
    }

    public Batch readAll(Collection<OutfitConfig.ResourceRef> references) {
        var unique = references.stream().distinct().toList();
        if (unique.size() > OutfitConfig.MAX_INSTANCES) {
            throw failure("COSMETIC_RESOURCE_LIMIT");
        }
        return new Batch(unique);
    }

    /** One transfer at a time, with one cancellation handle for the complete draft generation. */
    public final class Batch implements AutoCloseable {
        private final List<OutfitConfig.ResourceRef> references;
        private final Map<OutfitConfig.ResourceRef, Prepared> values = new LinkedHashMap<>();
        private final CompletableFuture<Map<OutfitConfig.ResourceRef, Prepared>> future = new CompletableFuture<>();
        private Operation active;

        private Batch(List<OutfitConfig.ResourceRef> references) {
            this.references = references;
            future.whenComplete((value, error) -> {
                if (future.isCancelled() && active != null) {
                    active.close();
                }
            });
            next();
        }

        public CompletableFuture<Map<OutfitConfig.ResourceRef, Prepared>> future() {
            return future;
        }

        private void next() {
            if (future.isDone()) {
                return;
            }
            if (values.size() == references.size()) {
                future.complete(Map.copyOf(values));
                return;
            }
            var reference = references.get(values.size());
            try {
                active = read(reference);
            } catch (RuntimeException invalid) {
                future.completeExceptionally(invalid);
                return;
            }
            active.future().whenComplete((value, error) -> {
                if (future.isDone()) {
                    return;
                }
                if (error != null) {
                    future.completeExceptionally(error);
                } else {
                    values.put(reference, value);
                    next();
                }
            });
        }

        @Override
        public void close() {
            future.cancel(false);
            if (active != null) {
                active.close();
            }
        }
    }

    private static CompletionException failure(String code) {
        return new CompletionException(new LibraryClient.Failure(0, code));
    }
}
