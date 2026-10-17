package top.aihmc.omnimorph.model.library;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
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
    private LibraryPreviewBinding binding;
    private Avatar avatar;
    private volatile boolean closed;
    private boolean expanded;
    private Status status = new Status(Stage.IDLE, "");

    public LibraryPreviewSession(LibraryClient client) {
        this.client = client;
        world = Minecraft.getInstance().level;
        entity = world == null ? null : new RemotePlayer(world, new GameProfile(owner, "LibraryPreview"));
    }

    public Status status() {
        return status;
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
        if (transfer != null) {
            transfer.close();
            transfer = null;
        }
        retireModel();
        status = new Status(Stage.LOADING, "");
        return generation;
    }

    private void prepare(CompoundTag nbt, long generation, CompletableFuture<Boolean> result) {
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
            prepared.load(nbt, null, () -> !current(generation) || result.isCancelled());
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
            if (prepared.luaRuntime != null) {
                prepared.luaRuntime.setUser(entity);
                prepared.run("ENTITY_INIT", prepared.init.post());
            }
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
        return !closed && generations.get() == generation && client.current()
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
