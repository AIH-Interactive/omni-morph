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
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.mixin.gui.GuiGraphicsAccessor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Current-player Figura preview with opt-in detached management rendering and cancellable preparation. */
public final class LibraryPreviewSession implements AutoCloseable {
    public enum Stage {
        IDLE, LOADING, READY, FAILED, CLOSED
    }

    public record Status(Stage stage, String errorCode) {}

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
    private LibraryPreviewBinding binding;
    private Avatar avatar;
    private volatile boolean closed;
    private boolean expanded;
    private boolean independentFigura;
    private Status status = new Status(Stage.IDLE, "");

    /** Local wardrobe previews have no backend session dependency. */
    public LibraryPreviewSession() {
        this(null);
    }

    public LibraryPreviewSession(LibraryClient client) {
        this(client, false);
    }

    /** Detached mode is reserved for management rendering and native regression fixtures. */
    public LibraryPreviewSession(LibraryClient client, boolean detachedMode) {
        this.client = client;
        this.detachedMode = detachedMode;
        world = Minecraft.getInstance().level;
        if (detachedMode && world != null) {
            detachedEntity = new LibraryPreviewPlayer(world, owner, () -> Minecraft.getInstance().player);
        }
        entity = detachedMode ? detachedEntity : Minecraft.getInstance().player;
    }

    public Status status() {
        return status;
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
        LibraryPreviewBinding.capture(entity, pose);
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
        return status.stage() == Stage.READY && avatar != null && !avatar.scriptError && !avatar.isYsmNative()
                && (avatar.luaRuntime == null || avatar.luaRuntime.vanilla_model.PLAYER.checkVisible());
    }

    public LivingEntity entity() {
        return !closed && binding != null && current(generations.get()) ? entity : null;
    }

    public void withPlayerContext(Runnable action) {
        if (binding != null) {
            binding.withPlayerContext(action);
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = !closed && expanded;
    }

    public CompletableFuture<Boolean> select(UUID model, LibraryTransferService transfers) {
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
                prepare(data.nbt(), generation, result);
            }
        });
        cancelWith(result, generation);
        return result;
    }

    /** Upload drafts use their independently prepared NBT, never a wardrobe publication entry point. */
    public CompletableFuture<Boolean> selectDraft(CompoundTag nbt) {
        return selectDraft(() -> nbt);
    }

    /** Source-bound administrator reads share the same cancellable preparation lifecycle. */
    public CompletableFuture<Boolean> selectResource(LibraryClient.Request<CompoundTag> request) {
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
        if (!current(generation) || entity == null) {
            if (current(generation)) {
                status = new Status(Stage.FAILED, "NO_PREVIEW_WORLD");
            }
            result.complete(false);
            return;
        }
        boolean isolated = detachedMode || independentFigura;
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
            Avatar prepared = usePlayer ? new Avatar(player.getUUID())
                    : Avatar.createLibraryPreview(owner, "LibraryPreview");
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
            binding = new LibraryPreviewBinding(entity, prepared, !currentPlayerFigura);
            binding.withPlayerContext(() -> prepared.initializePreviewPlayer(entity));
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
        if (!current(generations.get())) {
            close();
        } else if (avatar != null && entity != null && !AvatarManager.panic) {
            if (!currentPlayerFigura) {
                entity.tickCount++;
            }
            binding.withPlayerContext(avatar::tick);
        }
    }

    public void beforeExtract(float delta) {
        if (current(generations.get()) && avatar != null && !AvatarManager.panic) {
            binding.withPlayerContext(() -> {
                avatar.render(delta);
                avatar.clearAnimations();
                avatar.applyAnimations();
            });
        }
    }

    public void clear() {
        if (closed) {
            return;
        }
        begin();
        status = new Status(Stage.IDLE, "");
    }

    private void retireModel() {
        if (binding != null) {
            binding.close();
            binding = null;
        }
        avatar = null;
        currentPlayerFigura = false;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
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
