package org.figuramc.figura.avatar.local;

import java.util.concurrent.CompletableFuture;

/** Exercises the actual avatar discovery loader scope on an unrelated common-pool context. */
public final class LocalAvatarFetcherContextSmokeTest {
    public static void main(String[] args) {
        CompletableFuture.runAsync(() -> {
            Thread worker = Thread.currentThread();
            ClassLoader original = worker.getContextClassLoader();
            ClassLoader unrelated = new ClassLoader(null) {};
            worker.setContextClassLoader(unrelated);
            try {
                LocalAvatarFetcher.withAvatarClassLoader(() -> require(
                        worker.getContextClassLoader() == LocalAvatarFetcher.class.getClassLoader(),
                        "discovery uses the mod class loader"));
                require(worker.getContextClassLoader() == unrelated, "normal completion restores worker context");
                RuntimeException failure = new RuntimeException("expected discovery failure");
                try {
                    LocalAvatarFetcher.withAvatarClassLoader(() -> {
                        require(worker.getContextClassLoader() == LocalAvatarFetcher.class.getClassLoader(),
                                "failing discovery still uses the mod class loader");
                        throw failure;
                    });
                    throw new AssertionError("Discovery failure was swallowed");
                } catch (RuntimeException expected) {
                    require(expected == failure, "original discovery failure propagates");
                }
                require(worker.getContextClassLoader() == unrelated, "failure restores worker context");
            } finally {
                worker.setContextClassLoader(original);
            }
        }).join();
        System.out.println("PASS actual discovery loader scope on common pool, normal/failure restoration and propagation");
        System.out.println("Not covered: full ModLauncher/EventBus transformation or client avatar listing");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
