package top.aihmc.omnimorph.render.acceleration;

import java.util.Objects;

/** Global routing and lifetime boundary. No engine names or platform loading here. */
public final class AccelerationService {
    public static final AccelerationService INSTANCE = new AccelerationService();

    public record Lease(AccelerationOwner owner, long ownerGeneration, long resources, long frame, String view) {
        public Lease {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(view);
        }
    }

    private final AccelerationBackend javaBackend = new JavaAccelerationBackend();
    private volatile AccelerationBackend preferredBackend = javaBackend;
    private volatile long resources;
    private volatile long frame;

    public synchronized void beginFrame() {
        frame++;
    }

    public synchronized void invalidateResources() {
        resources++;
    }

    /** Switching the numerical backend invalidates captured batches before accepting new work. */
    synchronized void useBackend(AccelerationBackend backend) {
        preferredBackend = backend == null ? javaBackend : Objects.requireNonNull(backend);
        resources++;
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
        }
        return isCurrent(lease) ? result : null;
    }
}
