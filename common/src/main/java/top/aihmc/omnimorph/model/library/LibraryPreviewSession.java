package top.aihmc.omnimorph.model.library;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerSkin;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.backend2.library.LibraryClient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** One detached preview and its cancellable preparation. Public lifecycle methods run on the client thread. */
public final class LibraryPreviewSession implements AutoCloseable {
    public enum Stage {
        IDLE, LOADING, READY, FAILED, CLOSED
    }

    public record Status(Stage stage, String errorCode) {}

    private final LibraryClient client;
    private final ClientLevel world;
    private final UUID owner = UUID.randomUUID();
    private final LivingEntity entity;
    private final AtomicLong generations = new AtomicLong();
    private LibraryTransferService.Operation transfer;
    private LibraryClient.Request<CompoundTag> resource;
    private LibraryPreviewBinding binding;
    private Avatar avatar;
    private volatile boolean closed;
    private boolean expanded;
    private Status status = new Status(Stage.IDLE, "");

    /** Local wardrobe previews have no backend session dependency. */
    public LibraryPreviewSession() {
        this(null);
    }

    public LibraryPreviewSession(LibraryClient client) {
        this.client = client;
        world = Minecraft.getInstance().level;
        entity = world == null ? null : new RemotePlayer(world, new GameProfile(owner, "LibraryPreview")) {
            @Override
            public PlayerSkin getSkin() {
                var player = Minecraft.getInstance().player;
                return player == null ? super.getSkin() : player.getSkin();
            }
        };
        if (entity != null) {
            entity.setOnGround(true);
        }
    }

    public Status status() {
        return status;
    }

    /** Evaluate the model's real initialization result; never infer Lua behavior from source text. */
    public boolean showsVanillaPlayer() {
        return status.stage() == Stage.READY && avatar != null && !avatar.scriptError && !avatar.isYsmNative()
                && (avatar.luaRuntime == null || avatar.luaRuntime.vanilla_model.PLAYER.checkVisible());
    }

    public LivingEntity entity() {
        return !closed && binding != null && current(generations.get()) ? entity : null;
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

    /** A source-bound administrator wardrobe read uses the same detached rendering lifecycle. */
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
        Avatar prepared = Avatar.createLibraryPreview(owner, "LibraryPreview");
        AvatarLoadExecutor.submit(() -> {
            if (!current(generation) || result.isCancelled()) {
                return false;
            }
            prepared.load(nbt.get(), null, () -> !current(generation) || result.isCancelled());
            return prepared.loaded && prepared.nbt != null;
        }).whenComplete((loaded, failure) -> Minecraft.getInstance().execute(() -> {
            if (failure != null || !Boolean.TRUE.equals(loaded) || !current(generation) || result.isCancelled()) {
                prepared.clean();
                if (current(generation) && !result.isCancelled()) {
                    status = new Status(Stage.FAILED, "MODEL_PREPARATION_FAILED");
                }
                result.complete(false);
                return;
            }
            avatar = prepared;
            binding = new LibraryPreviewBinding(entity, prepared);
            prepared.initializeLibraryPreview(entity);
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
                && Minecraft.getInstance().level == world;
    }

    public void tick() {
        if (!current(generations.get())) {
            close();
        } else if (avatar != null && entity != null && !AvatarManager.panic) {
            entity.tickCount++;
            avatar.tick();
        }
    }

    public void beforeExtract(float delta) {
        if (current(generations.get()) && avatar != null && !AvatarManager.panic) {
            avatar.render(delta);
            avatar.clearAnimations();
            avatar.applyAnimations();
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
