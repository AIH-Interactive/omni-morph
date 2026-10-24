package top.aihmc.omnimorph.model.library;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.OutfitApplyResult;
import top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitComposition;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.outfit.OutfitPreparation;
import top.aihmc.omnimorph.model.outfit.SkeletonIndex;
import top.aihmc.omnimorph.permissions.PermissionManager;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;

/** Explicit client-thread Apply lifecycle: fixed sources, native preparation, CAS, authority recheck, publication. */
public final class OutfitApplyService implements AutoCloseable {
    private final LibraryClient client;
    private final LibraryTransferService transfers;
    private final Path directory;
    private final Path cache;
    private Operation active;

    public OutfitApplyService(LibraryClient client, LibraryTransferService transfers, Path directory, Path cache) {
        this.client = client;
        this.transfers = transfers;
        this.directory = directory;
        this.cache = cache;
    }

    public Operation apply(OutfitConfig configuration, LivingEntity wearer, BooleanSupplier current) {
        close();
        active = new Operation(configuration, wearer, current, false);
        active.start();
        return active;
    }

    /** Rebuild the authenticated owner's already committed configuration without another CAS write. */
    public CompletableFuture<Boolean> restore(OutfitConfig configuration, LivingEntity wearer, BooleanSupplier current) {
        close();
        active = new Operation(configuration, wearer, current, true);
        active.start();
        return active.future().thenApply(ignored -> true);
    }

    @Override
    public void close() {
        if (active != null) {
            active.close();
            active = null;
        }
    }

    /** Retry preserves the operation and exact CAS input, including after a lost committed response. */
    public final class Operation implements AutoCloseable {
        private final UUID operationId = UUID.randomUUID();
        private final UUID outfitId = UUID.randomUUID();
        private final OutfitConfig configuration;
        private final LivingEntity wearer;
        private final Object world = Minecraft.getInstance().level;
        private final BooleanSupplier activeDraft;
        private final boolean restoring;
        private volatile boolean closed;
        private volatile long attempt;
        private CompletableFuture<OutfitApplyResult> result;
        private NetworkStuff.LibrarySelection selection;
        private AvatarManager.OutfitLoad load;
        private OutfitApplyResources sources;
        private LibraryClient.Request<?> pending;
        private Candidate candidate;
        private List<OutfitPresetResources.Issue> issues = List.of();

        private Operation(OutfitConfig configuration, LivingEntity wearer, BooleanSupplier activeDraft, boolean restoring) {
            this.configuration = OutfitCodec.decode(OutfitCodec.encode(configuration));
            this.wearer = java.util.Objects.requireNonNull(wearer);
            this.activeDraft = activeDraft;
            this.restoring = restoring;
        }

        public UUID operationId() {
            return operationId;
        }

        public CompletableFuture<OutfitApplyResult> future() {
            return result;
        }

        public List<OutfitPresetResources.Issue> issues() {
            return issues;
        }

        public CompletableFuture<OutfitApplyResult> retry() {
            if (closed || !result.isCompletedExceptionally() || result.isCancelled()) {
                throw new IllegalStateException("OUTFIT_RETRY_UNAVAILABLE");
            }
            start();
            return result;
        }

        private boolean current(long generation) {
            return !closed && attempt == generation && client.current() && activeDraft.getAsBoolean()
                    && wearer.getUUID().equals(client.session().player())
                    && wearer.getUUID().equals(FiguraMod.getLocalPlayerUUID())
                    && Minecraft.getInstance().level == world && load != null && load.current()
                    && NetworkStuff.librarySelectionCurrent(selection);
        }

        private void start() {
            long generation = ++attempt;
            result = new CompletableFuture<>();
            var completion = result;
            completion.whenComplete((value, error) -> {
                if (completion.isCancelled()) {
                    Minecraft.getInstance().execute(() -> {
                        if (generation == attempt) {
                            close();
                        }
                    });
                }
            });
            issues = List.of();
            try {
                if (!client.current() || !activeDraft.getAsBoolean()) {
                    throw new CancellationException("OUTFIT_APPLY_RETIRED");
                }
                selection = NetworkStuff.beginLibrarySelection();
                load = AvatarManager.beginOutfitLoad();
                if (!current(generation)) {
                    throw new CancellationException("OUTFIT_APPLY_RETIRED");
                }
                CompletableFuture<OutfitApplyResources.Prepared> preparation;
                if (sources == null) {
                    sources = new OutfitApplyResources(configuration, client, transfers, directory, cache,
                            Minecraft.getInstance()::execute, () -> current(attempt), !restoring);
                    preparation = sources.future();
                } else {
                    preparation = sources.retry();
                }
                preparation.whenComplete((prepared, failure) -> Minecraft.getInstance().execute(() -> {
                    if (failure != null || !current(generation)) {
                        fail(generation, failure == null ? new CancellationException("OUTFIT_APPLY_RETIRED") : failure);
                    } else {
                        issues = prepared.resources().issues();
                        if (!prepared.ready()) {
                            fail(generation, new LibraryClient.Failure(0, "OUTFIT_DEPENDENCY_UNAVAILABLE"));
                        } else {
                            prepare(generation, prepared);
                        }
                    }
                }));
            } catch (Exception failure) {
                fail(generation, failure);
            }
        }

        private void prepare(long generation, OutfitApplyResources.Prepared prepared) {
            Avatar resident = load.user().getMainAvatar();
            Avatar reusable = resident != null && configuration.base() != null
                    && configuration.base().equals(resident.outfitSource()) ? resident : null;
            AvatarLoadExecutor.submit(() -> {
                Avatar base = reusable;
                boolean ownsBase = false;
                try {
                    if (!current(generation)) {
                        throw new CancellationException("OUTFIT_APPLY_RETIRED");
                    }
                    if (configuration.base() != null && base == null) {
                        base = new Avatar(wearer.getUUID());
                        ownsBase = true;
                        base.load(prepared.resources().base(), null, () -> !current(generation));
                        base.bindOutfitSource(configuration.base());
                    }
                    var resources = prepared.resources();
                    var composition = OutfitPreparation.prepare(configuration, prepared.limits(), wearer.getUUID(),
                            outfitId, wearer, PermissionManager.get(wearer.getUUID()), new SkeletonIndex.Cache(),
                            resources::read, () -> !current(generation), CosmeticInstanceContext.Scope.OWNER, base,
                            reference -> resources.ready() && resources.metadata(reference) != null,
                            reference -> resources.ready() && resources.metadata(reference) != null);
                    return new Candidate(base, composition, ownsBase);
                } catch (Exception failure) {
                    if (ownsBase) {
                        Avatar discarded = base;
                        Minecraft.getInstance().execute(discarded::clean);
                    }
                    throw new CompletionException(failure);
                }
            }).whenComplete((assembled, failure) -> Minecraft.getInstance().execute(() -> {
                if (failure != null || !current(generation)) {
                    if (assembled != null) {
                        assembled.close();
                    }
                    fail(generation, failure == null ? new CancellationException("OUTFIT_APPLY_RETIRED") : failure);
                } else {
                    candidate = assembled;
                    await(client.capabilities(), generation, capabilities -> {
                        requireCapabilities(capabilities, prepared.limits());
                        if (restoring) {
                            restoreCurrent(generation);
                        } else {
                            commit(generation, prepared.limits());
                        }
                    });
                }
            }));
        }

        private void commit(long generation, OutfitLimits limits) {
            await(client.applyOutfit(operationId, configuration), generation, response -> {
                if (response instanceof OutfitApplyResult.Conflict) {
                    discardCandidate();
                    NetworkStuff.cancelLibrarySelection(selection);
                    result.complete(response);
                } else if (response instanceof OutfitApplyResult.Accepted accepted) {
                    if (!accepted.canActivate(operationId, configuration.revision())) {
                        throw new LibraryClient.Failure(409, "OUTFIT_CHANGED");
                    }
                    await(client.currentOutfit(), generation, current -> {
                        if (!current.equals(accepted.configuration())) {
                            throw new LibraryClient.Failure(409, "OUTFIT_CHANGED");
                        }
                        await(client.sessionInfo(), generation, identity -> {
                            if (!identity.uuid().equals(wearer.getUUID()) || identity.equipmentRevision() == null
                                    || identity.equipmentRevision() != accepted.equipmentRevision()) {
                                throw new LibraryClient.Failure(409, "OUTFIT_CHANGED");
                            }
                            await(client.capabilities(), generation, capabilities -> {
                                requireCapabilities(capabilities, limits);
                                publish(generation, accepted);
                            });
                        });
                    });
                }
            });
        }

        private void restoreCurrent(long generation) {
            await(client.currentOutfit(), generation, current -> {
                if (!current.equals(configuration)) {
                    throw new LibraryClient.Failure(409, "OUTFIT_CHANGED");
                }
                await(client.sessionInfo(), generation, identity -> {
                    if (!identity.uuid().equals(wearer.getUUID()) || identity.equipmentRevision() == null) {
                        throw new LibraryClient.Failure(409, "OUTFIT_CHANGED");
                    }
                    await(client.currentOutfit(), generation, latest -> {
                        if (!latest.equals(configuration)) {
                            throw new LibraryClient.Failure(409, "OUTFIT_CHANGED");
                        }
                        publish(generation, configuration, null);
                    });
                });
            });
        }

        private void publish(long generation, OutfitApplyResult.Accepted accepted) {
            publish(generation, accepted.configuration(), accepted);
        }

        private void publish(long generation, OutfitConfig confirmed, OutfitApplyResult receipt) {
            Candidate prepared = candidate;
            candidate = null;
            NetworkStuff.publishOutfitEquipment(selection, load, confirmed, prepared.base(),
                    prepared.composition(), () -> current(generation)).whenComplete((published, failure) -> {
                if (failure != null || !Boolean.TRUE.equals(published)) {
                    fail(generation, failure == null ? new CancellationException("OUTFIT_APPLY_RETIRED") : failure);
                } else {
                    result.complete(receipt);
                }
            });
        }

        private <T> void await(LibraryClient.Request<T> request, long generation, Step<T> success) {
            pending = request;
            request.future().whenComplete((value, failure) -> Minecraft.getInstance().execute(() -> {
                if (generation != attempt || closed) {
                    return;
                }
                if (failure != null || !current(generation)) {
                    fail(generation, failure == null ? new CancellationException("OUTFIT_APPLY_RETIRED") : failure);
                    return;
                }
                try {
                    success.accept(value);
                } catch (Exception invalid) {
                    fail(generation, invalid);
                }
            }));
        }

        private void discardCandidate() {
            if (candidate != null) {
                candidate.close();
                candidate = null;
            }
        }

        private void fail(long generation, Throwable failure) {
            if (generation != attempt || closed) {
                return;
            }
            discardCandidate();
            NetworkStuff.cancelLibrarySelection(selection);
            result.completeExceptionally(failure);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            attempt++;
            if (pending != null) {
                pending.cancel();
            }
            if (sources != null) {
                sources.close();
            }
            discardCandidate();
            NetworkStuff.cancelLibrarySelection(selection);
            if (result != null) {
                result.cancel(false);
            }
        }
    }

    @FunctionalInterface
    private interface Step<T> {
        void accept(T value) throws Exception;
    }

    private static void requireCapabilities(LibraryModels.Capabilities capabilities, OutfitLimits limits)
            throws LibraryClient.Failure {
        var outfits = capabilities.outfitsV1();
        if (!capabilities.enabled() || outfits == null || !outfits.apply() || !outfits.configuration()
                || !outfits.limits().equals(limits)) {
            throw new LibraryClient.Failure(409, "OUTFIT_LIMITS_CHANGED");
        }
    }

    /** The composition owns instances; the service only owns a newly created base until publication. */
    private record Candidate(Avatar base, OutfitComposition composition, boolean ownsBase) implements AutoCloseable {
        @Override
        public void close() {
            composition.close();
            if (ownsBase && base != null) {
                base.clean();
            }
        }
    }
}
