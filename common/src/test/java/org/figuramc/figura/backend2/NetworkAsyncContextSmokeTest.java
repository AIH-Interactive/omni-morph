package org.figuramc.figura.backend2;

import top.aihmc.omnimorph.backend2.protocol.generated.Cloud;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Runs the real bounded network queue; no backend connection or credentials are used. */
public final class NetworkAsyncContextSmokeTest {
    public static void run() throws Exception {
        var tasksField = NetworkStuff.class.getDeclaredField("tasks");
        tasksField.setAccessible(true);
        var pendingField = NetworkStuff.class.getDeclaredField("pendingTasks");
        pendingField.setAccessible(true);
        var pending = (AtomicInteger) pendingField.get(null);
        require(pending.get() == 0, "isolated network queue");
        Object previousTasks = tasksField.get(null);
        ClassLoader foreign = new ClassLoader(null) {};
        var contexts = new java.util.concurrent.ConcurrentHashMap<Thread, ClassLoader>();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch warmupRelease = new CountDownLatch(1);
        var warmups = new java.util.ArrayList<CompletableFuture<Void>>();
        for (int index = 0; index < 2; index++) {
            warmups.add(CompletableFuture.runAsync(() -> {
                Thread thread = Thread.currentThread();
                contexts.put(thread, thread.getContextClassLoader());
                thread.setContextClassLoader(foreign);
                ready.countDown();
                try {
                    require(warmupRelease.await(5, TimeUnit.SECONDS), "warmup release");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }));
        }
        require(ready.await(5, TimeUnit.SECONDS), "both controlled common-pool workers ready");
        warmupRelease.countDown();
        CompletableFuture.allOf(warmups.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        try {
            for (boolean fail : new boolean[] {false, true, false}) {
                CountDownLatch entered = new CountDownLatch(1);
                CountDownLatch release = new CountDownLatch(1);
                AtomicReference<Thread> worker = new AtomicReference<>();
                NetworkStuff.async(() -> {
                    worker.set(Thread.currentThread());
                    entered.countDown();
                    try {
                        require(release.await(5, TimeUnit.SECONDS), "test releases queued operation");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                    require(Thread.currentThread().getContextClassLoader() == NetworkStuff.class.getClassLoader(),
                            "actual network operation must use mod loader");
                    require(Cloud.Subscribe.newBuilder().addOwners("synthetic-owner").build().getOwnersCount() == 1,
                            "cold subscribe builder construction on actual request worker");
                    if (fail) {
                        throw new IllegalStateException("expected request failure");
                    }
                });
                require(entered.await(5, TimeUnit.SECONDS), "actual network worker entered");
                CompletableFuture<?> task = (CompletableFuture<?>) tasksField.get(null);
                var restored = task.handle((value, error) -> {
                    require(Thread.currentThread() == worker.get(), "completion runs on original worker");
                    require(Thread.currentThread().getContextClassLoader() == foreign,
                            "completion restores the common-pool loader");
                    require(pending.get() == 0, "request capacity released");
                    if (fail) {
                        require(error != null && error.getCause() instanceof IllegalStateException,
                                "original request failure propagates");
                    } else {
                        require(error == null, "normal queued request succeeds: " + error);
                    }
                    return null;
                });
                release.countDown();
                restored.get(5, TimeUnit.SECONDS);
            }
        } finally {
            tasksField.set(null, previousTasks);
            contexts.forEach(Thread::setContextClassLoader);
        }
        System.out.println("PASS actual network queue mod loader, Subscribe builder, failure propagation/recovery and restoration");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
