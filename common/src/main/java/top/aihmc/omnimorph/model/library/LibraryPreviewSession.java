package top.aihmc.omnimorph.model.library;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import org.figuramc.figura.avatar.AvatarManager;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.mixin.gui.GuiGraphicsAccessor;
import top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext;
import top.aihmc.omnimorph.model.outfit.OutfitComposition;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.outfit.OutfitPreparation;
import top.aihmc.omnimorph.model.outfit.SkeletonIndex;
import org.figuramc.figura.permissions.PermissionPack;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Current-player Figura preview with opt-in detached management rendering and cancellable preparation. */
public final class LibraryPreviewSession implements AutoCloseable {
    public enum Stage {
        IDLE, LOADING, READY, FAILED, CLOSED
    }

    public record Status(Stage stage, String errorCode) {}

    public record ConfigurationPreview(OutfitConfig configuration, OutfitPresetResources.Prepared resources) {}

    private record PreparedModel(Avatar avatar, boolean currentPlayer) {}

    private final LibraryClient client;
    private final ClientLevel world;
    private final UUID owner = UUID.randomUUID();
    private LivingEntity entity;
    private LivingEntity detachedEntity;
    private final boolean detachedMode;
    private boolean currentPlayerFigura;
    private final AtomicLong generations = new AtomicLong();
    private LibraryTransferService.Operation transfer;
    private LibraryClient.Request<CompoundTag> resource;
    private LibraryClient.Request<LibraryWardrobePreviewService.Configuration> configurationRequest;
    private LibraryPreviewBinding binding;
    private Avatar avatar;
    private volatile boolean closed;
    private boolean expanded;
    private boolean independentFigura;
    private final boolean worldParticles;
    private UUID previewPlayer;
    private LivingEntity livePlayer;
    private Runnable preparePlayerFallback;
    private boolean playerFallbackStarted;
    private java.util.function.Supplier<CompoundTag> playerDraft;
    private CompoundTag playerDraftNbt;
    private Status status = new Status(Stage.IDLE, "");
    private PreviewHierarchy hierarchy = PreviewHierarchy.EMPTY;
    private OutfitHierarchy outfitHierarchy = OutfitHierarchy.EMPTY;
    private OutfitComposition previewComposition;
    private long hierarchyRevision;
    private final SkeletonIndex.Cache outfitIndexes = new SkeletonIndex.Cache();
    private final AtomicLong compositionGenerations = new AtomicLong();
    private CompletableFuture<Boolean> pendingComposition;
    private OutfitPresetResources configurationResources;
    private ConfigurationPreview configurationPreview;
    private java.util.List<OutfitPresetResources.Issue> configurationIssues = java.util.List.of();

    public java.util.List<OutfitPresetResources.Issue> configurationIssues() {
        return configurationIssues;
    }

    public ConfigurationPreview configurationPreview() {
        return !closed && status.stage() == Stage.READY && configurationPreview != null
                && configurationPreview.resources().ready() ? configurationPreview : null;
    }

    /** Local wardrobe previews have no backend session dependency. */
    public LibraryPreviewSession() {
        this(null);
    }

    public LibraryPreviewSession(LibraryClient client) {
        this(client, false);
    }

    /** Detached mode is reserved for management rendering and native regression fixtures. */
    public LibraryPreviewSession(LibraryClient client, boolean detachedMode) {
        this(client, detachedMode, false);
    }

    /** World previews use the normal particle engine instead of a GUI particle scope. */
    public LibraryPreviewSession(LibraryClient client, boolean detachedMode, boolean worldParticles) {
        this.client = client;
        this.detachedMode = detachedMode;
        this.worldParticles = worldParticles;
        world = Minecraft.getInstance().level;
        if (detachedMode && world != null) {
            detachedEntity = new LibraryPreviewPlayer(world, owner, () -> Minecraft.getInstance().player);
        }
        entity = detachedMode ? detachedEntity : Minecraft.getInstance().player;
    }

    public Status status() {
        return status;
    }

    public PreviewHierarchy hierarchy() {
        return hierarchy;
    }

    public OutfitHierarchy outfitHierarchy() {
        return outfitHierarchy;
    }

    public OutfitComposition outfitComposition() {
        return closed ? null : previewComposition;
    }

    public Avatar outfitBase() {
        return closed ? null : avatar;
    }

    public long hierarchyRevision() {
        return hierarchyRevision;
    }

    /** Explicit refresh also supports scripts that changed the part structure after initial loading. */
    public void refreshHierarchy() {
        if (!closed) {
            if (previewComposition != null) {
                outfitHierarchy = OutfitHierarchy.capture(previewComposition.editorSnapshot(avatar),
                        new OutfitHierarchy.Labels(treeLabel("base"), treeLabel("accessories"),
                                treeLabel("standards"), treeLabel("virtuals"), treeLabel("no_bones"),
                                treeLabel("hidden_bones"), treeLabel("disabled")));
                hierarchy = outfitHierarchy.hierarchy();
            } else {
                outfitHierarchy = OutfitHierarchy.EMPTY;
                hierarchy = avatar == null ? PreviewHierarchy.EMPTY : PreviewHierarchy.fromAvatar(avatar);
            }
            hierarchyRevision++;
        }
    }

    private static String treeLabel(String key) {
        return org.figuramc.figura.utils.FiguraText.of("gui.wardrobe.tree_" + key).getString();
    }

    public void setIndependentFigura(boolean enabled) {
        independentFigura = enabled;
    }

    /** Capture skin and standing pose data without calling an entity renderer. */
    public boolean submitIndependentFigura(GuiGraphicsExtractor gui,
                                           int drawX, int drawY, float scale, float pitch, float yaw,
                                           float delta, int x0, int y0, int x1, int y1) {
        if (!independentFigura || avatar == null || avatar.isYsmNative() || entity() == null) {
            return false;
        }
        var player = (AbstractClientPlayer) entity;
        var pose = new AvatarRenderState();
        pose.ageInTicks = entity.tickCount + delta;
        pose.skin = player.getSkin();
        pose.showHat = player.isModelPartShown(PlayerModelPart.HAT);
        pose.showJacket = player.isModelPartShown(PlayerModelPart.JACKET);
        pose.showLeftSleeve = player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        pose.showRightSleeve = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        pose.showLeftPants = player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        pose.showRightPants = player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        LibraryPreviewBinding.capture(entity, pose, delta);
        var outfit = LibraryPreviewBinding.composition(pose);
        if (outfit != null) {
            outfit.prepareFiguraBase(avatar);
        }
        var accessor = (GuiGraphicsAccessor) gui;
        var scissor = accessor.figura$getScissorStack().peek();
        var bounds = PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor);
        float offsetX = (drawX - (x0 + x1) / 2f) / scale;
        float offsetY = (drawY - (y0 + y1) / 2f) / scale;
        accessor.figura$getRenderState().addPicturesInPictureState(new LibraryFiguraPreviewState(
                avatar, pose, delta, pitch, yaw, offsetX, offsetY,
                x0, y0, x1, y1, scale, scissor, bounds));
        gui.nextStratum();
        return true;
    }

    /** Evaluate the model's real initialization result; never infer Lua behavior from source text. */
    public boolean showsVanillaPlayer() {
        return status.stage() == Stage.READY && binding != null
                && (avatar == null || (!avatar.scriptError && !avatar.isYsmNative()
                && (avatar.luaRuntime == null || avatar.luaRuntime.vanilla_model.PLAYER.checkVisible())));
    }

    /** Explicit vanilla base: the binding owns no placeholder Avatar or Lua runtime. */
    public boolean selectVanilla() {
        resetPlayerPreview();
        long generation = begin();
        return bindVanilla(generation, detachedMode || independentFigura);
    }

    private boolean bindVanilla(long generation, boolean isolated) {
        if (isolated && detachedEntity == null && world != null) {
            detachedEntity = new LibraryPreviewPlayer(world, owner, () -> Minecraft.getInstance().player);
        }
        entity = isolated ? detachedEntity : Minecraft.getInstance().player;
        if (!current(generation) || entity == null) {
            status = new Status(Stage.FAILED, "NO_PREVIEW_WORLD");
            return false;
        }
        currentPlayerFigura = !isolated;
        binding = new LibraryPreviewBinding(entity, null, isolated, worldParticles);
        status = new Status(Stage.READY, "");
        return true;
    }

    public LivingEntity entity() {
        if (livePlayer != null) {
            return current(generations.get()) && resolveLivePlayer(previewPlayer) == livePlayer ? livePlayer : null;
        }
        return !closed && binding != null && current(generations.get()) ? entity : null;
    }

    public void withPlayerContext(Runnable action) {
        if (livePlayer != null && entity() != null) {
            action.run();
        } else if (binding != null) {
            binding.withPlayerContext(action);
        }
    }

    public void selectedBone(Object part) {
        if (binding != null) {
            binding.selectedBone(part);
        }
    }

    /** Read-only render state; this does not publish equipment or register synchronization. */
    public org.figuramc.figura.avatar.UserData.OutfitState outfitState() {
        return closed || previewComposition == null || entity() == null ? null
                : new org.figuramc.figura.avatar.UserData.OutfitState(
                        previewComposition.configuration(), avatar, previewComposition);
    }

    public OutfitComposition.MountTarget previewMountTarget(UUID instance) {
        return previewComposition == null ? null : previewComposition.previewMountTarget(instance);
    }

    /** Borrow the actual online entity; only the fallback model belongs to this session. */
    public void selectPlayerResource(UUID player,
                                     java.util.function.Supplier<LibraryClient.Request<CompoundTag>> fallback) {
        selectPlayer(player, () -> startResource(fallback.get()));
    }

    public void selectPlayerDraft(UUID player, java.util.function.Supplier<CompoundTag> fallback) {
        selectPlayer(player, () -> {
            playerDraftNbt = fallback.get();
            CompoundTag data = playerDraftNbt;
            startDraft(() -> data == null ? new CompoundTag() : data);
        });
        playerDraft = fallback;
    }

    private void selectPlayer(UUID player, Runnable fallback) {
        clear();
        previewPlayer = java.util.Objects.requireNonNull(player);
        preparePlayerFallback = fallback;
        independentFigura = true;
        refreshPlayerPreview();
    }

    static LivingEntity resolveLivePlayer(UUID player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player == null || minecraft.level == null || minecraft.getConnection() == null
                || minecraft.getConnection().getPlayerInfo(player) == null) {
            return null;
        }
        LivingEntity entity = minecraft.level.getPlayerByUUID(player);
        return entity == null || entity.isRemoved() ? null : entity;
    }

    private void refreshPlayerPreview() {
        if (closed || previewPlayer == null || !current(generations.get())) {
            return;
        }
        LivingEntity resolved = resolveLivePlayer(previewPlayer);
        if (resolved != null) {
            if (livePlayer != resolved) {
                begin();
                livePlayer = resolved;
                playerFallbackStarted = false;
                status = new Status(Stage.READY, "");
            }
        } else if (!playerFallbackStarted) {
            livePlayer = null;
            playerFallbackStarted = true;
            preparePlayerFallback.run();
        } else if (playerDraft != null && playerDraft.get() != playerDraftNbt) {
            preparePlayerFallback.run();
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = !closed && expanded;
    }

    public CompletableFuture<Boolean> select(UUID model, LibraryTransferService transfers) {
        resetPlayerPreview();
        long generation = begin();
        var result = new CompletableFuture<Boolean>();
        transfer = transfers.preview(model);
        transfer.request().future().whenComplete((data, failure) -> {
            if (!current(generation)) {
                result.complete(false);
            } else if (failure != null) {
                status = new Status(Stage.FAILED, errorCode(failure));
                result.complete(false);
            } else {
                if (data.configuration() != null) {
                    prepareConfiguration(data.configuration(), data.outfitLimits(), data.model().modelId(),
                            transfers, generation, result);
                } else {
                    prepare(data.nbt(), generation, result);
                }
            }
        });
        cancelWith(result, generation);
        return result;
    }

    public CompletableFuture<Boolean> selectConfigurationResource(
            LibraryClient.Request<LibraryWardrobePreviewService.Configuration> request, LibraryTransferService transfers) {
        resetPlayerPreview();
        long generation = begin();
        configurationRequest = request;
        var result = new CompletableFuture<Boolean>();
        request.future().whenComplete((data, failure) -> {
            if (!current(generation)) {
                result.complete(false);
            } else if (failure != null) {
                status = new Status(Stage.FAILED, errorCode(failure));
                result.complete(false);
            } else {
                prepareConfiguration(data.configuration(), data.limits(), data.resource(), transfers, generation, result);
            }
        });
        cancelWith(result, generation);
        return result;
    }

    private void prepareConfiguration(OutfitConfig configuration, OutfitLimits limits, UUID identity,
                                      LibraryTransferService transfers,
                                      long generation, CompletableFuture<Boolean> result) {
        if (client == null || limits == null) {
            status = new Status(Stage.FAILED, "OUTFIT_UNSUPPORTED");
            result.complete(false);
            return;
        }
        UUID account = client.session().player();
        var dependencies = new OutfitPresetResources(configuration, limits, account,
                org.figuramc.figura.FiguraMod.getFiguraDirectory(), client, transfers,
                Minecraft.getInstance()::execute, () -> current(generation) && !result.isCancelled());
        configurationResources = dependencies;
        dependencies.future().whenComplete((resources, failure) -> {
            if (!current(generation) || result.isCancelled()) {
                result.complete(false);
                return;
            }
            if (failure != null || !resources.ready()) {
                configurationIssues = resources == null ? java.util.List.of() : resources.issues();
                status = new Status(Stage.FAILED, failure == null ? "OUTFIT_PRESET_DEPENDENCY_UNAVAILABLE"
                        : errorCode(failure));
                result.complete(false);
                return;
            }
            var base = new CompletableFuture<Boolean>();
            if (configuration.base() == null) {
                base.complete(bindVanilla(generation, true));
            } else {
                prepare(() -> {
                    try {
                        return resources.base();
                    } catch (java.io.IOException unavailable) {
                        throw new java.util.concurrent.CompletionException(unavailable);
                    }
                }, generation, base, true);
            }
            base.whenComplete((ready, problem) -> {
                if (!current(generation) || result.isCancelled() || problem != null || !Boolean.TRUE.equals(ready)) {
                    result.complete(false);
                    return;
                }
                status = new Status(Stage.LOADING, "");
                var permissions = avatar == null ? new PermissionPack.PlayerPermissionPack(
                        new PermissionPack.CategoryPermissionPack(
                                org.figuramc.figura.permissions.Permissions.Category.DEFAULT), owner.toString())
                        : avatar.permissions;
                prepareComposition(configuration, limits, account, identity,
                        permissions, resources::read)
                        .whenComplete((complete, error) -> {
                            if (current(generation) && !result.isCancelled()) {
                                boolean success = error == null && Boolean.TRUE.equals(complete);
                                status = new Status(success ? Stage.READY : Stage.FAILED,
                                        success ? "" : "MODEL_PREPARATION_FAILED");
                                configurationPreview = success ? new ConfigurationPreview(configuration, resources) : null;
                                result.complete(success);
                            } else {
                                result.complete(false);
                            }
                        });
            });
        });
    }

    /** Upload drafts use their independently prepared NBT, never a wardrobe publication entry point. */
    public CompletableFuture<Boolean> selectDraft(CompoundTag nbt) {
        return selectDraft(() -> nbt);
    }

    /** Source-bound administrator reads share the same cancellable preparation lifecycle. */
    public CompletableFuture<Boolean> selectResource(LibraryClient.Request<CompoundTag> request) {
        resetPlayerPreview();
        return startResource(request);
    }

    private CompletableFuture<Boolean> startResource(LibraryClient.Request<CompoundTag> request) {
        long generation = begin();
        resource = request;
        var result = new CompletableFuture<Boolean>();
        request.future().whenComplete((nbt, failure) -> {
            if (!current(generation)) {
                result.complete(false);
            } else if (failure != null) {
                status = new Status(Stage.FAILED, errorCode(failure));
                result.complete(false);
            } else {
                prepare(nbt, generation, result);
            }
        });
        cancelWith(result, generation);
        return result;
    }

    public CompletableFuture<Boolean> selectDraft(java.util.function.Supplier<CompoundTag> nbt) {
        resetPlayerPreview();
        return startDraft(nbt);
    }

    private CompletableFuture<Boolean> startDraft(java.util.function.Supplier<CompoundTag> nbt) {
        long generation = begin();
        var result = new CompletableFuture<Boolean>();
        prepare(nbt, generation, result);
        cancelWith(result, generation);
        return result;
    }

    private long begin() {
        if (closed) {
            throw new IllegalStateException("PREVIEW_CLOSED");
        }
        long generation = generations.incrementAndGet();
        cancelCompositionPreparation();
        if (resource != null) {
            resource.cancel();
            resource = null;
        }
        if (transfer != null) {
            transfer.close();
            transfer = null;
        }
        retireModel();
        status = new Status(Stage.LOADING, "");
        return generation;
    }

    private void prepare(CompoundTag nbt, long generation, CompletableFuture<Boolean> result) {
        prepare(() -> nbt, generation, result);
    }

    private void prepare(java.util.function.Supplier<CompoundTag> nbt, long generation,
                         CompletableFuture<Boolean> result) {
        prepare(nbt, generation, result, detachedMode || independentFigura);
    }

    private void prepare(java.util.function.Supplier<CompoundTag> nbt, long generation,
                         CompletableFuture<Boolean> result, boolean isolated) {
        if (!current(generation) || entity == null) {
            if (current(generation)) {
                status = new Status(Stage.FAILED, "NO_PREVIEW_WORLD");
            }
            result.complete(false);
            return;
        }
        LivingEntity player = Minecraft.getInstance().player;
        AvatarLoadExecutor.submit(() -> {
            if (!current(generation) || result.isCancelled()) {
                return (PreparedModel) null;
            }
            CompoundTag data = nbt.get();
            boolean figura = !data.getCompoundOrEmpty("metadata").getStringOr("format", "").equals("ysm-native");
            boolean usePlayer = !isolated && figura;
            if (usePlayer && player == null) {
                return null;
            }
            Avatar prepared = usePlayer ? Avatar.createPlayerModelPreview(player.getUUID())
                    : Avatar.createParticlePreview(owner, "LibraryPreview");
            prepared.load(data, null, () -> !current(generation) || result.isCancelled());
            return new PreparedModel(prepared, usePlayer);
        }).whenComplete((loaded, failure) -> Minecraft.getInstance().execute(() -> {
            Avatar prepared = loaded == null ? null : loaded.avatar();
            boolean stalePlayer = loaded != null && loaded.currentPlayer() && Minecraft.getInstance().player != player;
            if (failure != null || prepared == null || !prepared.loaded || prepared.nbt == null
                    || stalePlayer || !current(generation) || result.isCancelled()) {
                if (prepared != null) {
                    prepared.clean();
                }
                if (current(generation) && !result.isCancelled()) {
                    status = new Status(Stage.FAILED, "MODEL_PREPARATION_FAILED");
                }
                result.complete(false);
                return;
            }
            avatar = prepared;
            currentPlayerFigura = loaded.currentPlayer();
            if (!currentPlayerFigura && detachedEntity == null && world != null) {
                detachedEntity = new LibraryPreviewPlayer(world, owner, () -> Minecraft.getInstance().player);
            }
            if (currentPlayerFigura) {
                entity = player;
            } else if (detachedEntity != null) {
                entity = detachedEntity;
            }
            binding = new LibraryPreviewBinding(entity, prepared, !currentPlayerFigura, worldParticles);
            binding.withPlayerContext(() -> prepared.initializePreviewPlayer(entity));
            refreshHierarchy();
            status = new Status(Stage.READY, "");
            result.complete(true);
        }));
    }

    private void cancelWith(CompletableFuture<Boolean> result, long generation) {
        result.whenComplete((value, failure) -> {
            if (result.isCancelled()) {
                Minecraft.getInstance().execute(() -> {
                    if (current(generation)) {
                        clear();
                    }
                });
            }
        });
    }

    private boolean current(long generation) {
        return !closed && generations.get() == generation && (client == null || client.current())
                && Minecraft.getInstance().level == world
                && (!currentPlayerFigura || Minecraft.getInstance().player == entity);
    }

    public void tick() {
        refreshPlayerPreview();
        if (!current(generations.get())) {
            close();
        } else if (livePlayer != null) {
            // The original AvatarManager owns ticking and the borrowed avatar's lifetime.
            return;
        } else if (binding != null && entity != null && !AvatarManager.panic) {
            if (!currentPlayerFigura) {
                entity.tickCount++;
            }
            binding.withPlayerContext(binding::tick);
        }
    }

    /** Install a prepared draft generation; existing deferred frames retain the previous generation. */
    public void setComposition(OutfitComposition composition) {
        if (binding == null) {
            throw new IllegalStateException("PREVIEW_NOT_READY");
        }
        cancelCompositionPreparation();
        binding.setComposition(composition);
        previewComposition = composition;
        refreshHierarchy();
    }

    /** Keep the current preview visible until every instance in the newest draft is prepared. */
    public CompletableFuture<Boolean> prepareComposition(OutfitConfig configuration, OutfitLimits limits,
                                                          UUID account, UUID outfit, PermissionPack.PlayerPermissionPack permissions,
                                                          OutfitPreparation.Resources resources) {
        if (closed || binding == null || livePlayer != null) {
            throw new IllegalStateException("PREVIEW_NOT_READY");
        }
        if ((configuration.base() == null) != (avatar == null)
                || (avatar != null && (configuration.base().format() == OutfitConfig.Format.FIGURA) == avatar.isYsmNative())) {
            throw new IllegalArgumentException("PREVIEW_BASE_MISMATCH");
        }
        cancelCompositionPreparation();
        if (avatar != null && permissions != null && avatar.permissions != permissions) {
            // An isolated base keeps detached defaults, but its composed faces belong to the wearer, as when equipped.
            avatar.permissions.insert(org.figuramc.figura.permissions.Permissions.COMPLEXITY,
                    permissions.get(org.figuramc.figura.permissions.Permissions.COMPLEXITY),
                    org.figuramc.figura.FiguraMod.MOD_ID);
        }
        long generation = generations.get();
        long draft = compositionGenerations.get();
        LibraryPreviewBinding target = binding;
        LivingEntity wearer = entity;
        var result = new CompletableFuture<Boolean>();
        pendingComposition = result;
        java.util.function.BooleanSupplier cancelled = () -> result.isCancelled() || !current(generation)
                || compositionGenerations.get() != draft;
        AvatarLoadExecutor.submit(() -> {
            try {
                return OutfitPreparation.prepare(configuration, limits, account, outfit,
                        wearer, permissions, outfitIndexes, resources, cancelled,
                        CosmeticInstanceContext.Scope.DRAFT, null,
                        resource -> resource.source() == OutfitConfig.Source.LOCAL,
                        resource -> resource.source() == OutfitConfig.Source.LOCAL, worldParticles);
            } catch (Exception failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        })
                .whenComplete((prepared, failure) -> Minecraft.getInstance().execute(() -> {
                    if (cancelled.getAsBoolean() || target != binding || failure != null) {
                        if (prepared != null) {
                            prepared.close();
                        }
                        if (pendingComposition == result) {
                            pendingComposition = null;
                        }
                        if (failure != null && !cancelled.getAsBoolean()) {
                            result.completeExceptionally(failure);
                        } else {
                            result.complete(false);
                        }
                        return;
                    }
                    target.setComposition(prepared);
                    previewComposition = prepared;
                    refreshHierarchy();
                    pendingComposition = null;
                    result.complete(true);
                }));
        return result;
    }

    private void cancelCompositionPreparation() {
        compositionGenerations.incrementAndGet();
        if (pendingComposition != null) {
            pendingComposition.cancel(false);
            pendingComposition = null;
        }
    }

    public void beforeExtract(float delta) {
        refreshPlayerPreview();
        if (current(generations.get()) && avatar != null && !AvatarManager.panic) {
            binding.withPlayerContext(() -> binding.withBaseBudget(() -> {
                avatar.render(delta);
                avatar.clearAnimations();
                avatar.applyAnimations();
            }));
        }
    }

    public void clear() {
        if (closed) {
            return;
        }
        resetPlayerPreview();
        begin();
        status = new Status(Stage.IDLE, "");
    }

    private void retireModel() {
        if (configurationRequest != null) {
            configurationRequest.cancel();
            configurationRequest = null;
        }
        configurationPreview = null;
        if (configurationResources != null) {
            configurationResources.close();
            configurationResources = null;
        }
        configurationIssues = java.util.List.of();
        previewComposition = null;
        outfitHierarchy = OutfitHierarchy.EMPTY;
        hierarchy = PreviewHierarchy.EMPTY;
        hierarchyRevision++;
        if (binding != null) {
            binding.close();
            binding = null;
        }
        avatar = null;
        currentPlayerFigura = false;
    }

    private void resetPlayerPreview() {
        previewPlayer = null;
        livePlayer = null;
        preparePlayerFallback = null;
        playerFallbackStarted = false;
        playerDraft = null;
        playerDraftNbt = null;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        cancelCompositionPreparation();
        resetPlayerPreview();
        expanded = false;
        generations.incrementAndGet();
        if (resource != null) {
            resource.cancel();
            resource = null;
        }
        if (transfer != null) {
            transfer.close();
            transfer = null;
        }
        retireModel();
        status = new Status(Stage.CLOSED, "");
    }

    private static String errorCode(Throwable failure) {
        return failure instanceof LibraryClient.Failure library ? library.code : "PREVIEW_FAILED";
    }
}
