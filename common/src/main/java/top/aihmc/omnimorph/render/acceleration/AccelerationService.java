package top.aihmc.omnimorph.render.acceleration;

import java.util.Objects;
import java.util.function.Consumer;

/** Global routing and lifetime boundary. No engine names or platform loading here. */
public final class AccelerationService {
    public static final int MAX_BATCH_VERTICES = 65_536;
    public static final AccelerationService INSTANCE = new AccelerationService();

    public record Lease(AccelerationOwner owner, long ownerGeneration, long resources, long frame, String view) {
        public Lease {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(view);
        }
    }

    private final AccelerationBackend javaBackend = new JavaAccelerationBackend();
    private volatile AccelerationBackend preferredBackend = javaBackend;
    private volatile Consumer<Throwable> backendFailure = ignored -> {};
    private volatile long resources;
    private volatile long frame;

    public void beginFrame() {
        AccelerationBackend backend;
        synchronized (this) {
            frame++;
            backend = preferredBackend;
        }
        try {
            backend.beginFrame();
        } catch (RuntimeException | LinkageError failure) {
            backendFailed(backend, failure);
        }
    }

    public synchronized void invalidateResources() {
        resources++;
        preferredBackend.invalidateResources();
    }

    /** Switching the numerical backend invalidates captured batches before accepting new work. */
    synchronized void useBackend(AccelerationBackend backend) {
        useBackend(backend, ignored -> {});
    }

    synchronized void useBackend(AccelerationBackend backend, Consumer<Throwable> onFailure) {
        AccelerationBackend previous = preferredBackend;
        preferredBackend = backend == null ? javaBackend : Objects.requireNonNull(backend);
        backendFailure = Objects.requireNonNull(onFailure);
        resources++;
        if (previous != preferredBackend && previous != javaBackend) {
            previous.close();
        }
    }

    public boolean accelerated() {
        return preferredBackend != javaBackend;
    }

    public boolean gpuDrawEnabled() {
        return preferredBackend instanceof OpenGlAccelerationBackend;
    }

    boolean canDraw(DrawBatch batch) {
        return isCurrent(batch.lease())
                && preferredBackend.supports(AccelerationBackend.Operation.GPU_DRAW, batch);
    }

    boolean drawBatches(java.util.List<DrawBatch> batches) {
        AccelerationBackend backend = preferredBackend;
        if (batches.stream().anyMatch(batch -> !canDraw(batch))) {
            return false;
        }
        try {
            return backend.drawBatches(batches);
        } catch (RuntimeException | LinkageError failure) {
            backendFailed(backend, failure);
            // Once GL has consumed any part of a group, retrying the whole group would duplicate faces.
            return failure instanceof OpenGlAccelerationBackend.SubmittedDrawFailure;
        }
    }

    public Lease capture(AccelerationOwner owner, String view) {
        return new Lease(owner, owner.generation(), resources, frame, view);
    }

    public boolean isCurrent(Lease lease) {
        return lease.owner().isCurrent() && lease.ownerGeneration() == lease.owner().generation()
                && lease.resources() == resources && lease.frame() == frame;
    }

    /** Null means stale; the caller must not publish a stale result or retry it as Java. */
    public double[] transform(Lease lease, EngineAccelerationAdapter adapter) {
        if (!isCurrent(lease)) {
            return null;
        }
        DrawBatch batch = adapter.capture(lease);
        if (!batch.lease().equals(lease)) {
            throw new IllegalArgumentException("Adapter returned a batch from another submission");
        }
        AccelerationBackend backend = preferredBackend;
        if (!backend.supports(AccelerationBackend.Operation.VERTEX_TRANSFORM, batch)) {
            backend = javaBackend;
        }
        double[] result;
        try {
            result = backend.transform(batch);
        } catch (NativeSimdBackend.NativeBatchException failure) {
            // Numeric/budget rejection is local to this batch; its original Java semantics remain valid.
            result = javaBackend.transform(batch);
        } catch (RuntimeException | LinkageError failure) {
            if (backend == javaBackend) {
                throw failure;
            }
            backendFailed(backend, failure);
            result = javaBackend.transform(batch);
        }
        return isCurrent(lease) ? result : null;
    }

    private void backendFailed(AccelerationBackend backend, Throwable failure) {
        Consumer<Throwable> notify;
        synchronized (this) {
            if (preferredBackend != backend || backend == javaBackend) {
                return;
            }
            notify = backendFailure;
            // Keep this lease valid so an unsubmitted batch can still be emitted through Java.
            preferredBackend = javaBackend;
        }
        try {
            backend.close();
        } catch (RuntimeException | LinkageError cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        AccelerationFrameDiagnostics.fallback(FallbackReason.RUNTIME_FAILURE.name());
        notify.accept(failure);
    }
}
