package top.aihmc.omnimorph.model.library;

import net.minecraft.client.Minecraft;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.avatar.AvatarLoadProgress;
import top.aihmc.omnimorph.avatar.local.CacheAvatarLoader;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/** Formal use: fresh download, worker preparation, source-bound CAS, identity recheck, client publication. */
public final class LibraryEquipmentService implements AutoCloseable {
    private static final String ROOT = "/api/library/v1/";
    private final LibraryClient client;
    private final LibraryTransferService transfers;
    private Operation active;

    public LibraryEquipmentService(LibraryClient client, LibraryTransferService transfers) {
        this.client = client;
        this.transfers = transfers;
    }

    public Operation enable(UUID model) {
        return enable(model, false);
    }

    public Operation enable(UUID model, boolean showPrivateEquipment) {
        if (active != null) {
            active.close();
        }
        var operation = new Operation(model, showPrivateEquipment);
        active = operation;
        operation.start();
        return operation;
    }

    @Override
    public void close() {
        if (active != null) {
            active.close();
            active = null;
        }
    }

    /** Lifecycle methods run on the client thread. Retry retains the exact equip operation and CAS input. */
    public final class Operation implements AutoCloseable {
        private final UUID model;
        private final boolean showPrivateEquipment;
        private final UUID operationId = UUID.randomUUID();
        private final AvatarLoadProgress progress = new AvatarLoadProgress();
        private volatile long attempt;
        private volatile boolean closed;
        private volatile boolean commitAttempted;
        private long expectedRevision;
        private long progressGeneration;
        private NetworkStuff.LibrarySelection selection;
        private LibraryClient.Request<?> pending;
        private LibraryTransferService.Operation download;
        private LibraryTransferService.Package data;
        private Avatar candidate;
        private CompletableFuture<LibraryModels.Equipment> future;

        private Operation(UUID model, boolean showPrivateEquipment) {
            this.model = model;
            this.showPrivateEquipment = showPrivateEquipment;
        }

        public CompletableFuture<LibraryModels.Equipment> future() {
            return future;
        }

        public AvatarLoadProgress.Snapshot progress() {
            return download != null && !download.request().future().isDone()
                    ? download.progress().snapshot() : progress.snapshot();
        }

        public UUID operationId() {
            return operationId;
        }

        public CompletableFuture<LibraryModels.Equipment> retry() {
            if (closed || !future.isCompletedExceptionally()) {
                throw new IllegalStateException("LIBRARY_RETRY_UNAVAILABLE");
            }
            start();
            return future;
        }

        private void start() {
            long generation = ++attempt;
            future = new CompletableFuture<>();
            progressGeneration = progress.begin(AvatarLoadProgress.Stage.PROFILE);
            try {
                if (!client.current() || !client.session().player().equals(FiguraMod.getLocalPlayerUUID())) {
                    throw new CancellationException("LIBRARY_RETIRED");
                }
                selection = NetworkStuff.beginLibrarySelection();
                var context = client.sessionInfo();
                pending = context;
                context.future().whenComplete((info, failure) -> {
                    if (failure != null) {
                        fail(generation, failure);
                    } else if (!current(generation)) {
                        fail(generation, new CancellationException("LIBRARY_RETIRED"));
                    } else if (!info.uuid().equals(client.session().player())) {
                        fail(generation, new LibraryClient.Failure(0, "LIBRARY_IDENTITY_MISMATCH"));
                    } else if (commitAttempted) {
                        // A previous response may have been lost. Recheck current authority,
                        // then replay the original equip operation without replacing its CAS input.
                        recheckAndPrepare(generation);
                    } else {
                        expectedRevision = info.equipmentRevision();
                        downloadAndPrepare(generation);
                    }
                });
            } catch (Exception failure) {
                fail(generation, failure);
            }
            CompletableFuture<LibraryModels.Equipment> completion = future;
            completion.whenComplete((value, failure) -> {
                if (completion.isCancelled()) {
                    Minecraft.getInstance().execute(() -> {
                        if (generation == attempt) {
                            close();
                        }
                    });
                }
            });
        }

        private void downloadAndPrepare(long generation) {
            download = transfers.download(model);
            download.request().future().whenComplete((received, failure) -> {
                if (failure != null) {
                    fail(generation, failure);
                } else if (current(generation)) {
                    data = received;
                    prepare(generation);
                } else {
                    fail(generation, new CancellationException("LIBRARY_RETIRED"));
                }
            });
        }

        private void recheckAndPrepare(long generation) {
            var detail = client.detail(model);
            pending = detail;
            detail.future().whenComplete((fresh, failure) -> {
                if (failure != null) {
                    fail(generation, failure);
                } else if (!current(generation)) {
                    fail(generation, new CancellationException("LIBRARY_RETIRED"));
                } else if (data == null || fresh.revision() != data.model().revision() || !fresh.allows("equip")) {
                    fail(generation, new LibraryClient.Failure(409, "EQUIPMENT_CHANGED"));
                } else {
                    data = new LibraryTransferService.Package(fresh, data.nbt(), data.rawHash(), "download");
                    prepare(generation);
                }
            });
        }

        private void prepare(long generation) {
            progress.stage(progressGeneration, AvatarLoadProgress.Stage.PREPARING);
            Avatar prepared = new Avatar(client.session().player());
            var source = data;
            var nbt = source.nbt();
            AvatarLoadExecutor.submit(() -> {
                if (!current(generation)) {
                    return false;
                }
                prepared.load(nbt, null, () -> !current(generation));
                if (prepared.loaded && prepared.nbt != null) {
                    var resource = source.model();
                    prepared.bindOutfitSource(new OutfitConfig.ResourceRef(
                            OutfitConfig.Source.CLOUD, null, client.origin(),
                            resource.modelId(), resource.revision(), source.rawHash(),
                            OutfitConfig.Format.valueOf(resource.modelType())));
                }
                return prepared.loaded && prepared.nbt != null;
            }).whenComplete((loaded, failure) -> Minecraft.getInstance().execute(() -> {
                if (failure != null || !Boolean.TRUE.equals(loaded) || !current(generation)) {
                    prepared.clean();
                    fail(generation, failure != null ? failure : new LibraryClient.Failure(0, "MODEL_PREPARATION_FAILED"));
                } else {
                    candidate = prepared;
                    commit(generation);
                }
            }));
        }

        private void commit(long generation) {
            progress.stage(progressGeneration, AvatarLoadProgress.Stage.EQUIPPING);
            var source = data;
            var guard = selection;
            Map<String, Object> input = Map.of("operation_id", operationId.toString(),
                    "model_revision", source.model().revision(), "expected_equipment_revision", expectedRevision,
                    "show_private_equipment", showPrivateEquipment);
            var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
                requireCurrent(generation);
                commitAttempted = true;
                LibraryModels.Equipment equipped = exchange.json("POST", ROOT + "models/" + model + "/equip",
                        input, LibraryModels.Equipment.class);
                String alias = "lib_" + model.toString().replace("-", "") + "_" + source.model().revision();
                if (!equipped.modelId().equals(model) || equipped.modelRevision() != source.model().revision()
                        || !equipped.owner().equals(client.session().player()) || !alias.equals(equipped.assetId())
                        || equipped.assetRevision() != 1 || equipped.equipmentRevision() != expectedRevision + 1
                        || equipped.showPrivateEquipment() != showPrivateEquipment) {
                    throw new LibraryClient.Failure(409, "EQUIPMENT_CHANGED");
                }
                requireCurrent(generation);
                String cacheKey = NetworkStuff.libraryEquipmentCacheKey(guard, equipped);
                LibraryModels.SessionInfo after = exchange.json("GET", ROOT + "session", null, LibraryModels.SessionInfo.class);
                if (!after.uuid().equals(equipped.owner()) || after.equipmentRevision() != equipped.equipmentRevision()) {
                    throw new LibraryClient.Failure(409, "EQUIPMENT_CHANGED");
                }
                requireCurrent(generation);
                CacheAvatarLoader.saveLibrary(cacheKey, source.nbt(), new CacheAvatarLoader.LibrarySource(
                        client.session().instance(), equipped.owner(), model, equipped.modelRevision(),
                        source.model().policyRevision(), equipped.assetId(), equipped.assetRevision(),
                        source.model().visibility(), source.model().requiresAuthorization(), showPrivateEquipment));
                requireCurrent(generation);
                return equipped;
            });
            pending = request;
            request.future().whenComplete((equipped, failure) -> {
                if (failure != null) {
                    fail(generation, failure);
                } else if (!current(generation)) {
                    fail(generation, new CancellationException("LIBRARY_RETIRED"));
                } else {
                    Avatar prepared = candidate;
                    candidate = null;
                    progress.stage(progressGeneration, AvatarLoadProgress.Stage.APPLYING);
                    NetworkStuff.publishLibraryEquipment(guard, prepared).whenComplete((published, error) -> {
                        if (error != null || !Boolean.TRUE.equals(published)) {
                            fail(generation, error != null ? error : new CancellationException("LIBRARY_RETIRED"));
                        } else {
                            data = null;
                            progress.finish(progressGeneration);
                            future.complete(equipped);
                        }
                    });
                }
            });
        }

        private boolean current(long generation) {
            return !closed && attempt == generation && client.current() && NetworkStuff.librarySelectionCurrent(selection);
        }

        private void requireCurrent(long generation) {
            if (!current(generation)) {
                throw new CancellationException("LIBRARY_RETIRED");
            }
        }

        private void fail(long generation, Throwable failure) {
            if (generation != attempt || closed) {
                return;
            }
            if (candidate != null) {
                candidate.clean();
                candidate = null;
            }
            NetworkStuff.cancelLibrarySelection(selection);
            progress.finish(progressGeneration);
            future.completeExceptionally(failure);
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
            if (download != null) {
                download.close();
            }
            if (candidate != null) {
                candidate.clean();
                candidate = null;
            }
            data = null;
            NetworkStuff.cancelLibrarySelection(selection);
            progress.finish(progressGeneration);
            future.cancel(false);
        }
    }
}
