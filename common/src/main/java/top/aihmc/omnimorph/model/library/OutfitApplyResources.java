package top.aihmc.omnimorph.model.library;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.OutfitPrivateUpload;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Client-thread coordinator for explicit Apply preparation, before any configuration commit or activation. */
public final class OutfitApplyResources implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("OmniMorph/Outfit");
    public record Prepared(OutfitConfig configuration, OutfitLimits limits, OutfitPresetResources.Prepared resources) {
        public boolean ready() {
            return resources.ready();
        }
    }

    private record Dependency(OutfitConfig.ResourceRef reference, OutfitConfig.ResourceKind kind) {}

    private final OutfitConfig configuration;
    private final LibraryClient client;
    private final LibraryTransferService transfers;
    private final OutfitSourceService sources;
    private final Path dataRoot;
    private final Executor publisher;
    private final BooleanSupplier active;
    private final boolean allowUpload;
    private final List<Dependency> local;
    private final Map<OutfitConfig.ResourceRef, OutfitPrivateUpload> uploads = new LinkedHashMap<>();
    private final Map<OutfitConfig.ResourceRef, OutfitPresetResources.LocalInput> inputs = new LinkedHashMap<>();
    private volatile CompletableFuture<Prepared> completion;
    private OutfitPresetResources dependencies;
    private AutoCloseable pending;
    private OutfitLimits limits;
    private volatile long generation;
    private int next;
    private volatile boolean closed;

    public OutfitApplyResources(OutfitConfig configuration, LibraryClient client, LibraryTransferService transfers,
                                Path dataRoot, Path cacheRoot, Executor publisher, BooleanSupplier active) {
        this(configuration, client, transfers, dataRoot, cacheRoot, publisher, active, true);
    }

    OutfitApplyResources(OutfitConfig configuration, LibraryClient client, LibraryTransferService transfers,
                         Path dataRoot, Path cacheRoot, Executor publisher, BooleanSupplier active, boolean allowUpload) {
        this.configuration = OutfitCodec.decode(OutfitCodec.encode(configuration));
        this.client = java.util.Objects.requireNonNull(client);
        this.transfers = transfers;
        this.sources = new OutfitSourceService(client, cacheRoot);
        this.dataRoot = dataRoot;
        this.publisher = publisher;
        this.active = active;
        this.allowUpload = allowUpload;
        var unique = new LinkedHashMap<OutfitConfig.ResourceRef, Dependency>();
        var base = this.configuration.base();
        if (base != null && this.configuration.instances().stream().anyMatch(instance -> base.equals(instance.resource()))) {
            throw new IllegalArgumentException("OUTFIT_RESOURCE_KIND_CONFLICT");
        }
        if (base != null && base.source() == OutfitConfig.Source.LOCAL) {
            unique.put(base, new Dependency(base, OutfitConfig.ResourceKind.AVATAR));
        }
        for (var instance : this.configuration.instances()) {
            if (instance.resource().source() == OutfitConfig.Source.LOCAL) {
                unique.putIfAbsent(instance.resource(), new Dependency(instance.resource(), OutfitConfig.ResourceKind.ACCESSORY));
            }
        }
        local = List.copyOf(unique.values());
        start();
    }

    public CompletableFuture<Prepared> future() {
        return completion;
    }

    /** Re-read capabilities and permissions; retain only this session's immutable upload operations. */
    public CompletableFuture<Prepared> retry() {
        if (closed || !completion.isDone()) {
            throw new IllegalStateException("OUTFIT_PREPARATION_NOT_RETRYABLE");
        }
        start();
        return completion;
    }

    private boolean current(long attempt) {
        return !closed && attempt == generation && active.getAsBoolean() && client.current();
    }

    private void start() {
        long attempt = ++generation;
        retirePending();
        if (dependencies != null) {
            dependencies.close();
            dependencies = null;
        }
        inputs.clear();
        next = 0;
        completion = new CompletableFuture<>();
        var result = completion;
        result.whenComplete((value, error) -> {
            if (result.isCancelled() && result == completion) {
                close();
            }
        });
        if (!current(attempt)) {
            result.cancel(false);
            return;
        }
        await(client.capabilities(), attempt, capabilities -> {
            var outfits = capabilities.outfitsV1();
            if (!capabilities.enabled() || outfits == null || !outfits.configuration() || !outfits.apply()) {
                result.completeExceptionally(new LibraryClient.Failure(415, "UNSUPPORTED_FORMAT"));
                return;
            }
            limits = outfits.limits();
            try {
                OutfitCodec.encode(configuration, limits);
                next(attempt);
            } catch (RuntimeException invalid) {
                result.completeExceptionally(new LibraryClient.Failure(0, "OUTFIT_PREPARATION_INVALID"));
            }
        }, result::completeExceptionally);
    }

    private void next(long attempt) {
        if (!current(attempt)) {
            completion.cancel(false);
            return;
        }
        if (next == local.size()) {
            dependencies = new OutfitPresetResources(configuration, limits, client.session().player(), dataRoot,
                    client, transfers, publisher, () -> current(attempt), Map.copyOf(inputs));
            pending = dependencies;
            dependencies.future().whenComplete((prepared, error) -> publisher.execute(() -> {
                if (!accept(attempt)) {
                    return;
                }
                if (error == null) {
                    completion.complete(new Prepared(configuration, limits, prepared));
                } else {
                    completion.completeExceptionally(error);
                }
            }));
            return;
        }
        Dependency dependency = local.get(next++);
        await(client.resolveOutfitSource(dependency.reference(), dependency.kind()), attempt, existing -> {
            if (allowUpload) {
                // Publishing repackages the local source, so a package left by an older client is replaced.
                upload(dependency, attempt, existing.orElse(null));
            } else if (existing.isPresent()) {
                read(dependency, attempt);
            } else {
                failed(dependency, attempt, new LibraryClient.Failure(404, "NOT_FOUND"));
            }
        }, error -> failed(dependency, attempt, error));
    }

    private void upload(Dependency dependency, long attempt, OutfitPrivateUpload.Binding existing) {
        OutfitPrivateUpload frozen = uploads.get(dependency.reference());
        if (frozen != null) {
            sendUnlessCurrent(dependency, attempt, frozen, existing);
            return;
        }
        var preparation = AvatarLoadExecutor.submit(() -> {
            try {
                return OutfitPrivatePreparation.prepare(UUID.randomUUID(), dependency.reference(), dependency.kind(),
                        client.session().player(), dataRoot, limits, CloudHttp.FILE_BYTES, () -> !current(attempt));
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        });
        pending = () -> preparation.cancel(false);
        preparation.whenComplete((prepared, error) -> publisher.execute(() -> {
            if (!accept(attempt)) {
                return;
            }
            pending = null;
            if (error == null) {
                uploads.put(dependency.reference(), prepared.upload());
                sendUnlessCurrent(dependency, attempt, prepared.upload(), existing);
            } else if (existing != null && localSourceMissing(error)) {
                // The device-local copy was deleted after an earlier apply; the server still holds the package.
                LOGGER.debug("local outfit source {} is missing; loading the stored cloud package",
                        dependency.reference().localId());
                read(dependency, attempt);
            } else {
                failed(dependency, attempt, error);
            }
        }));
    }

    /** Reuse the server package only when it is byte-identical to the current local packaging. */
    private void sendUnlessCurrent(Dependency dependency, long attempt, OutfitPrivateUpload frozen,
                                   OutfitPrivateUpload.Binding existing) {
        if (existing != null && frozen.rawSha256().equals(existing.rawSha256())) {
            read(dependency, attempt);
        } else {
            send(dependency, attempt, frozen);
        }
    }

    private void send(Dependency dependency, long attempt, OutfitPrivateUpload frozen) {
        await(client.uploadOutfitSource(frozen, ignored -> {}), attempt,
                binding -> read(dependency, attempt), error -> failed(dependency, attempt, error));
    }

    private void read(Dependency dependency, long attempt) {
        await(sources.read(dependency.reference(), dependency.kind()), attempt, prepared -> {
            inputs.put(dependency.reference(), new OutfitPresetResources.LocalInput(prepared.nbt(), null));
            next(attempt);
        }, error -> failed(dependency, attempt, error));
    }

    private void failed(Dependency dependency, long attempt, Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        String code = error instanceof LibraryClient.Failure failure ? failure.code
                : error instanceof IOException ? error.getMessage() : null;
        if (code == null || !code.matches("[A-Z_]{1,64}")) {
            code = "OUTFIT_PRIVATE_SOURCE_FAILED";
        }
        inputs.put(dependency.reference(), new OutfitPresetResources.LocalInput(null, code));
        next(attempt);
    }

    /** The local package is gone; only the source the server already stored can still supply this dependency. */
    private static boolean localSourceMissing(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        if (!(error instanceof IOException)) {
            return false;
        }
        String message = error.getMessage();
        return "OUTFIT_PRIVATE_SOURCE_MISSING".equals(message) || "OUTFIT_BASE_SOURCE_MISSING".equals(message);
    }

    private <T> void await(LibraryClient.Request<T> request, long attempt,
                           Consumer<T> success, Consumer<Throwable> failure) {
        pending = request;
        request.future().whenComplete((value, error) -> publisher.execute(() -> {
            if (!accept(attempt)) {
                return;
            }
            pending = null;
            try {
                if (error == null) {
                    success.accept(value);
                } else {
                    failure.accept(error);
                }
            } catch (RuntimeException invalid) {
                completion.completeExceptionally(new LibraryClient.Failure(0, "OUTFIT_PREPARATION_INVALID"));
            }
        }));
    }

    private boolean accept(long attempt) {
        if (closed || attempt != generation) {
            return false;
        }
        if (!current(attempt)) {
            completion.cancel(false);
            return false;
        }
        return true;
    }

    private void retirePending() {
        if (pending != null) {
            try {
                pending.close();
            } catch (Exception ignored) {
                // The retired handle no longer owns this preparation generation.
            }
            pending = null;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        generation++;
        retirePending();
        if (dependencies != null) {
            dependencies.close();
        }
        completion.cancel(false);
        inputs.clear();
        uploads.clear();
    }
}
