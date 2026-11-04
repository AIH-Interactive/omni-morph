package top.aihmc.omnimorph.model.rendering.nodeRenderer;

import net.minecraft.client.renderer.SubmitNodeStorage;

/** Native collector identity, nesting, failure restoration and thread isolation; no game or GPU. */
public final class FeaturePreparationScopeSmokeTest {
    public static void main(String[] args) throws InterruptedException {
        var world = new SubmitNodeStorage();
        var preview = new SubmitNodeStorage();
        assertNoCollector();
        try (var worldScope = new FeaturePreparationScope(world)) {
            check(FeaturePreparationScope.current() == world, "world tasks lost their native collector");
            try {
                try (var previewScope = new FeaturePreparationScope(preview)) {
                    check(FeaturePreparationScope.current() == preview, "PIP tasks escaped into the world collector");
                    FeaturePreparationScope.current().order(7);
                    throw new ExpectedFailure();
                }
            } catch (ExpectedFailure expected) {
                check(FeaturePreparationScope.current() == world, "failed PIP did not restore the world collector");
            }
            check(preview.getSubmitsPerOrder().containsKey(7), "PIP submission was lost");
            check(world.getSubmitsPerOrder().isEmpty(), "PIP modified the world collection");
            var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            var otherThread = new Thread(() -> {
                try {
                    assertNoCollector();
                    try (var scope = new FeaturePreparationScope(preview)) {
                        check(FeaturePreparationScope.current() == preview, "other thread collector missing");
                    }
                    assertNoCollector();
                } catch (Throwable problem) {
                    failure.set(problem);
                }
            });
            otherThread.start();
            otherThread.join();
            if (failure.get() != null) {
                throw new AssertionError("collector scope crossed threads", failure.get());
            }
            check(FeaturePreparationScope.current() == world, "other thread replaced the current collector");
        }
        assertNoCollector();
        System.out.println("Native preparation collector PASS: PIP isolation, nested failure, no stale context, thread isolation");
    }

    private static void assertNoCollector() {
        try {
            FeaturePreparationScope.current();
            throw new AssertionError("collector available outside native preparation");
        } catch (NullPointerException expected) {
            check(expected.getMessage().contains("native feature preparation"), "missing collector has no diagnostic");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class ExpectedFailure extends RuntimeException {
    }
}
