package top.aihmc.omnimorph.render.acceleration;

import java.util.Objects;
import java.util.List;
import java.util.function.Consumer;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.rendertype.RenderType;

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

    /** Called only at the actual material/device draw boundary. Unsupported draws retain the Java path. */
    public GpuAccelerationBackend.DrawResult tryDraw(DrawBatch batch, GpuDrawContext context) {
        if (!isCurrent(batch.lease())) {
            return GpuAccelerationBackend.DrawResult.STALE;
        }
        AccelerationBackend backend = preferredBackend;
        try {
            return backend.draw(batch, context, () -> isCurrent(batch.lease()));
        } catch (GpuAccelerationBackend.Failure failure) {
            backendFailed(backend, failure);
            return failure.issued ? GpuAccelerationBackend.DrawResult.ISSUED_BEFORE_FAILURE
                    : GpuAccelerationBackend.DrawResult.UNSUPPORTED;
        } catch (RuntimeException | LinkageError failure) {
            backendFailed(backend, failure);
            // A foreign backend cannot establish whether it submitted a partial draw. Do not duplicate it.
            return GpuAccelerationBackend.DrawResult.ISSUED_BEFORE_FAILURE;
        }
    }

    public boolean accelerated() {
        return preferredBackend != javaBackend;
    }

    public boolean gpuDrawEnabled() {
        return preferredBackend instanceof GpuAccelerationBackend;
    }

    boolean gpuRigidGroupsEnabled() {
        return preferredBackend instanceof GpuAccelerationBackend gpu && gpu.supportsRigidGroups();
    }

    GpuSubmission.Scope openGpuDrawScope(RenderType material) {
        if (!(preferredBackend instanceof GpuAccelerationBackend gpu)) {
            throw new GpuAccelerationBackend.Failure(FallbackReason.GPU_UNAVAILABLE, false, null);
        }
        return gpu.openScope(material);
    }

    Consumer<MeshData> javaDrawCallback(RenderType material) {
        if (preferredBackend instanceof GpuAccelerationBackend gpu) {
            return geometry -> gpu.drawJava(material, geometry);
        }
        return material::draw;
    }

    public GpuAccelerationBackend.Prepared prepareGpuDraw(DrawBatch batch) {
        if (!isCurrent(batch.lease()) || !(preferredBackend instanceof GpuAccelerationBackend gpu)) {
            return null;
        }
        try {
            return gpu.prepare(batch, () -> isCurrent(batch.lease()));
        } catch (RuntimeException | LinkageError failure) {
            backendFailed(gpu, failure);
            return null;
        }
    }

    List<GpuAccelerationBackend.PreparedRange> prepareGpuGroup(List<DrawBatch> batches, long[] order) {
        if (!(preferredBackend instanceof GpuAccelerationBackend gpu) || batches.isEmpty()) {
            return null;
        }
        for (DrawBatch batch : batches) {
            if (!isCurrent(batch.lease())) {
                return null;
            }
        }
        try {
            return gpu.prepareGroup(batches, order, () -> isCurrent(batches.getFirst().lease()));
        } catch (RuntimeException | LinkageError failure) {
            backendFailed(gpu, failure);
            return null;
        }
    }

    public GpuAccelerationBackend.DrawResult drawPrepared(GpuAccelerationBackend.Prepared prepared,
                                                         GpuDrawContext context, int firstIndex, int indexCount) {
        if (!isCurrent(prepared.batch.lease())) {
            return GpuAccelerationBackend.DrawResult.STALE;
        }
        if (preferredBackend != prepared.backend) {
            return GpuAccelerationBackend.DrawResult.UNSUPPORTED;
        }
        try {
            return prepared.backend.drawPrepared(prepared, context, firstIndex, indexCount,
                    () -> isCurrent(prepared.batch.lease()));
        } catch (GpuAccelerationBackend.Failure failure) {
            backendFailed(prepared.backend, failure);
            return failure.issued ? GpuAccelerationBackend.DrawResult.ISSUED_BEFORE_FAILURE
                    : GpuAccelerationBackend.DrawResult.UNSUPPORTED;
        } catch (RuntimeException | LinkageError failure) {
            backendFailed(prepared.backend, failure);
            return GpuAccelerationBackend.DrawResult.ISSUED_BEFORE_FAILURE;
        }
    }

    void graphicsFailed(Throwable failure) {
        backendFailed(preferredBackend, failure);
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
        FallbackReason reason = failure instanceof GpuAccelerationBackend.Failure gpuFailure
                ? gpuFailure.reason : FallbackReason.RUNTIME_FAILURE;
        AccelerationFrameDiagnostics.fallback(reason.name());
        notify.accept(failure);
    }
}
