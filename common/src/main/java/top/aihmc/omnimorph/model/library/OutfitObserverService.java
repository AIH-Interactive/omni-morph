package top.aihmc.omnimorph.model.library;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import org.figuramc.figura.avatar.UserData;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.OutfitObserverSnapshot;
import top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext;
import top.aihmc.omnimorph.model.outfit.OutfitComposition;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.outfit.OutfitPreparation;
import top.aihmc.omnimorph.model.outfit.SkeletonIndex;
import org.figuramc.figura.permissions.PermissionManager;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;

/** One remote wearer's preparation lifecycle. The connection coordinator supplies its subscription identity guard. */
public final class OutfitObserverService implements AutoCloseable {
    public enum Result { PUBLISHED, HIDDEN, LEGACY }

    private final LibraryClient client;
    private final OutfitObserverSources sources;
    private Operation active;

    public OutfitObserverService(LibraryClient client, Path cache) {
        this.client = client;
        sources = new OutfitObserverSources(client, cache);
    }

    public Operation refresh(UserData user, LivingEntity wearer, BooleanSupplier subscribed) {
        if (!user.id.equals(wearer.getUUID()) || user.id.equals(client.session().player())) {
            throw new IllegalArgumentException("OUTFIT_OBSERVER_WEARER_REQUIRED");
        }
        close();
        active = new Operation(user, wearer, subscribed);
        active.start();
        return active;
    }

    @Override
    public void close() {
        if (active != null) {
            active.close();
            active = null;
        }
    }

    public final class Operation implements AutoCloseable {
        private final UserData user;
        private final LivingEntity wearer;
        private final BooleanSupplier subscribed;
        private final Object world = Minecraft.getInstance().level;
        private final long generation;
        private final CompletableFuture<Result> result = new CompletableFuture<>();
        private final Map<OutfitConfig.ResourceRef, CompoundTag> inputs = new LinkedHashMap<>();
        private volatile boolean closed;
        private LibraryClient.Request<?> pending;
        private OutfitObserverSnapshot snapshot;
        private OutfitLimits limits;
        private Candidate candidate;

        private Operation(UserData user, LivingEntity wearer, BooleanSupplier subscribed) {
            this.user = user;
            this.wearer = wearer;
            this.subscribed = subscribed;
            generation = user.ysmLoads.begin();
            result.whenComplete((value, failure) -> {
                if (result.isCancelled()) {
                    Minecraft.getInstance().execute(this::close);
                }
            });
        }

        public CompletableFuture<Result> future() {
            return result;
        }

        private boolean current() {
            return !closed && client.current() && subscribed.getAsBoolean() && user.ysmLoads.current(generation)
                    && Minecraft.getInstance().level == world && !wearer.isRemoved();
        }

        private void start() {
            await(client.capabilities(), capabilities -> {
                var configured = capabilities.outfitsV1();
                if (!capabilities.enabled() || configured == null || !configured.configuration() || !configured.apply()) {
                    legacy();
                    return;
                }
                limits = configured.limits();
                await(client.observerOutfit(user.id), observed -> {
                    var resident = user.getOutfit();
                    if (resident != null && observed.revision() < resident.configuration().revision()) {
                        throw new LibraryClient.Failure(409, "OUTFIT_STALE");
                    }
                    snapshot = observed;
                    if (snapshot.legacyFallback()) {
                        legacy();
                    } else if (snapshot.configuration() == null) {
                        user.clear();
                        result.complete(Result.HIDDEN);
                    } else {
                        read(0);
                    }
                });
            });
        }

        private void legacy() {
            if (user.getOutfit() != null) {
                user.clear();
            }
            result.complete(Result.LEGACY);
        }

        private void read(int index) {
            if (index == snapshot.resources().size()) {
                prepare();
                return;
            }
            var resource = snapshot.resources().get(index);
            await(sources.read(snapshot, resource), nbt -> {
                inputs.put(resource.reference(), nbt);
                read(index + 1);
            });
        }

        private void prepare() {
            var configuration = snapshot.configuration();
            Avatar resident = user.getMainAvatar();
            Avatar reusable = resident != null && resident.accelerationOwner().isCurrent()
                    && configuration.base() != null && configuration.base().equals(resident.outfitSource()) ? resident : null;
            AvatarLoadExecutor.submit(() -> {
                Avatar base = reusable;
                boolean ownsBase = false;
                try {
                    if (!current()) {
                        throw new CancellationException("OUTFIT_OBSERVER_RETIRED");
                    }
                    if (configuration.base() != null && base == null) {
                        base = new Avatar(user.id);
                        ownsBase = true;
                        base.load(inputs.get(configuration.base()).copy(), null, () -> !current());
                        base.bindOutfitSource(configuration.base());
                    }
                    var composition = OutfitPreparation.prepare(configuration, limits, client.session().player(),
                            UUID.randomUUID(), wearer, PermissionManager.get(user.id), new SkeletonIndex.Cache(),
                            (reference, cancelled) -> inputs.get(reference).copy(), () -> !current(),
                            CosmeticInstanceContext.Scope.OBSERVER, base,
                            this::authorizedDeclaration, this::authorizedDeclaration);
                    return new Candidate(base, composition, ownsBase);
                } catch (Exception failure) {
                    if (ownsBase) {
                        Avatar discarded = base;
                        Minecraft.getInstance().execute(discarded::clean);
                    }
                    throw new CompletionException(failure);
                }
            }).whenComplete((prepared, failure) -> Minecraft.getInstance().execute(() -> {
                if (failure != null || !current()) {
                    if (prepared != null) {
                        prepared.close();
                    }
                    fail(failure == null ? new CancellationException("OUTFIT_OBSERVER_RETIRED") : failure);
                    return;
                }
                candidate = prepared;
                await(client.capabilities(), capabilities -> {
                    requireCapabilities(capabilities);
                    await(client.observerOutfit(user.id), latest -> {
                        if (!snapshot.sameProjection(latest)) {
                            throw new LibraryClient.Failure(409, "OUTFIT_CHANGED");
                        }
                        await(client.capabilities(), confirmed -> {
                            requireCapabilities(confirmed);
                            publish(configuration);
                        });
                    });
                });
            }));
        }

        private void publish(OutfitConfig configuration) {
            Candidate ready = candidate;
            candidate = null;
            user.publishOutfit(configuration, ready.base(), ready.composition(), generation, this::current)
                    .whenComplete((published, error) -> {
                        if (error != null || !Boolean.TRUE.equals(published)) {
                            fail(error == null ? new CancellationException("OUTFIT_OBSERVER_RETIRED") : error);
                        } else {
                            inputs.clear();
                            result.complete(Result.PUBLISHED);
                        }
                    });
        }

        private boolean authorizedDeclaration(OutfitConfig.ResourceRef reference) {
            // The server has projected only render-authorized overrides/exports; this grants no editing UI rights.
            return snapshot.resources().stream().anyMatch(resource -> resource.reference().equals(reference)
                    && resource.accessoryMetadata() != null);
        }

        private void requireCapabilities(LibraryModels.Capabilities capabilities) throws LibraryClient.Failure {
            var configured = capabilities.outfitsV1();
            if (!capabilities.enabled() || configured == null || !configured.configuration() || !configured.apply()
                    || !limits.equals(configured.limits())) {
                throw new LibraryClient.Failure(409, "OUTFIT_LIMITS_CHANGED");
            }
        }

        private <T> void await(LibraryClient.Request<T> request, Step<T> success) {
            pending = request;
            request.future().whenComplete((value, failure) -> Minecraft.getInstance().execute(() -> {
                if (failure != null || !current()) {
                    fail(failure == null ? new CancellationException("OUTFIT_OBSERVER_RETIRED") : failure);
                    return;
                }
                try {
                    success.accept(value);
                } catch (Exception error) {
                    fail(error);
                }
            }));
        }

        private void fail(Throwable failure) {
            while (failure instanceof CompletionException && failure.getCause() != null) {
                failure = failure.getCause();
            }
            if (candidate != null) {
                candidate.close();
                candidate = null;
            }
            inputs.clear();
            if (current() && failure instanceof LibraryClient.Failure denied
                    && (denied.status == 403 || denied.status == 404 || denied.code.equals("OUTFIT_CHANGED"))) {
                user.clear();
            }
            result.completeExceptionally(failure);
        }

        @Override
        public void close() {
            closed = true;
            if (pending != null) {
                pending.cancel();
            }
            if (candidate != null) {
                candidate.close();
                candidate = null;
            }
            result.cancel(false);
        }
    }

    @FunctionalInterface
    private interface Step<T> {
        void accept(T value) throws Exception;
    }

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
