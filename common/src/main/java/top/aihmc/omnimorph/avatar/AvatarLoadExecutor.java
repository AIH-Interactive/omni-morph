package top.aihmc.omnimorph.avatar;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Bounds expensive model preparation independently of the network and game threads. */
public final class AvatarLoadExecutor {
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(
            1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), action -> {
                Thread thread = new Thread(action, "OmniMorph model loader " + THREAD_IDS.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });

    private AvatarLoadExecutor() {
    }

    public static <T> CompletableFuture<T> submit(Supplier<T> action) {
        try {
            return CompletableFuture.supplyAsync(action, WORKERS);
        } catch (RejectedExecutionException busy) {
            return CompletableFuture.failedFuture(busy);
        }
    }
}
