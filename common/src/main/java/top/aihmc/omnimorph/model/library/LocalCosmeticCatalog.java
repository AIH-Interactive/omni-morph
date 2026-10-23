package top.aihmc.omnimorph.model.library;

import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** Worker-owned inventory with immutable client-thread snapshots and bounded refresh admission. */
public final class LocalCosmeticCatalog implements AutoCloseable {
    private final Path directory;
    private final Executor clientThread;
    private volatile LocalCosmeticInventory inventory;
    private volatile boolean closed;
    private List<LocalCosmeticInventory.Entry> entries = List.of();
    private String failure = "";
    private CompletableFuture<List<LocalCosmeticInventory.Entry>> pending;
    private CompletableFuture<List<LocalCosmeticInventory.Entry>> work;

    public LocalCosmeticCatalog(Path directory, Executor clientThread) {
        this.directory = directory;
        this.clientThread = clientThread;
    }

    public List<LocalCosmeticInventory.Entry> entries() {
        return entries;
    }

    public String failure() {
        return failure;
    }

    public CompletableFuture<List<LocalCosmeticInventory.Entry>> refresh() {
        if (closed) {
            return CompletableFuture.failedFuture(new CancellationException("COSMETIC_CATALOG_CLOSED"));
        }
        if (pending != null && !pending.isDone()) {
            return pending;
        }
        var result = new CompletableFuture<List<LocalCosmeticInventory.Entry>>();
        pending = result;
        work = AvatarLoadExecutor.submit(() -> {
            if (closed) {
                throw new CancellationException("COSMETIC_CATALOG_CLOSED");
            }
            try {
                if (inventory == null) {
                    inventory = new LocalCosmeticInventory(directory);
                }
                return inventory.scan(() -> closed);
            } catch (java.io.IOException error) {
                throw new CompletionException(error);
            } finally {
                if (closed) {
                    closeInventory();
                }
            }
        });
        work.whenComplete((values, error) -> clientThread.execute(() -> {
            if (closed) {
                result.cancel(false);
            } else if (error != null) {
                failure = "COSMETIC_INVENTORY_UNAVAILABLE";
                result.completeExceptionally(error);
            } else {
                entries = List.copyOf(values);
                failure = "";
                result.complete(entries);
            }
        }));
        return result;
    }

    public void tick() {
        if (!closed && (pending == null || pending.isDone()) && inventory != null && inventory.changed()) {
            refresh();
        }
    }

    private void closeInventory() {
        var current = inventory;
        if (current != null) {
            try {
                current.close();
            } catch (java.io.IOException ignored) {
                // Retired views cannot use this watcher again.
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        entries = List.of();
        if (pending != null) {
            pending.cancel(false);
        }
        if (work != null) {
            // Do not block the UI behind a synchronized scan; the worker observes closed between resources.
            work.whenComplete((values, error) -> closeInventory());
        }
    }
}
