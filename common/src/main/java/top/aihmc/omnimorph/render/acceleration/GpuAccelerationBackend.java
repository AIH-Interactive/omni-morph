package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.MeshData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Public GPU contract: immutable cached geometry, frame-owned poses and direct indexed draws. */
public final class GpuAccelerationBackend implements AccelerationBackend {
    public enum DrawResult { UNSUPPORTED, STALE, DRAWN, ISSUED_BEFORE_FAILURE }

    /** A frame-owned pose and cached mesh may be reused for several sorted face ranges. */
    public static final class Prepared {
        final GpuAccelerationBackend backend;
        final DrawBatch batch;
        private final GpuDeviceAdapter.Material material;
        private final GpuDeviceAdapter.Mesh mesh;
        private final GpuDeviceAdapter.Pose pose;
        private final int indices;

        private Prepared(GpuAccelerationBackend backend, DrawBatch batch, GpuDeviceAdapter.Material material,
                         GpuDeviceAdapter.Mesh mesh, GpuDeviceAdapter.Pose pose, int indices) {
            this.backend = backend;
            this.batch = batch;
            this.material = material;
            this.mesh = mesh;
            this.pose = pose;
            this.indices = indices;
        }
    }

    static final class Failure extends RuntimeException {
        final FallbackReason reason;
        final boolean issued;

        Failure(FallbackReason reason, boolean issued, Throwable cause) {
            super(reason.name(), cause);
            this.reason = reason;
            this.issued = issued;
        }
    }

    private static final class Stale extends RuntimeException {
    }

    private static final class Key {
        private final long owner;
        private final MeshHandle mesh;
        private final int joints;
        private final boolean floatPose;

        Key(DrawBatch batch) {
            owner = batch.lease().ownerGeneration();
            mesh = batch.mesh();
            joints = batch.pose().skinJointCount();
            floatPose = batch.pose().usesFloatPrecision();
        }

        @Override
        public int hashCode() {
            return 31 * (31 * (31 * Long.hashCode(owner) + mesh.geometryHash()) + joints) + Boolean.hashCode(floatPose);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && owner == key.owner && joints == key.joints
                    && floatPose == key.floatPose && mesh.sameGeometry(key.mesh);
        }
    }

    private static final class Entry {
        final AccelerationOwner owner;
        final GpuDeviceAdapter.Mesh mesh;
        final int indices;
        long lastFrame;

        Entry(DrawBatch batch, GpuDeviceAdapter.Mesh mesh, int indices, long frame) {
            owner = batch.lease().owner();
            this.mesh = mesh;
            this.indices = indices;
            lastFrame = frame;
        }
    }

    private final GpuDeviceAdapter device;
    private final GpuRetirement retirement;
    private final long memoryBudget;
    private final long poseBudget;
    private final Map<Key, Entry> meshes = new HashMap<>();
    private final Map<RenderPipeline, GpuDeviceAdapter.Material> materials = new IdentityHashMap<>();
    private final Map<RenderPipeline, Boolean> pendingMaterials = new LinkedHashMap<>();
    private final List<GpuDeviceAdapter.Resource> framePoses = new ArrayList<>();
    private long residentBytes;
    private long framePoseBytes;
    private long frame;
    private long draws;
    private long uploads;
    private long cacheHits;
    private int initializationStep;
    private boolean ready;
    private boolean closed;
    private volatile boolean invalidated;
    private volatile AccelerationBackend cpuBackend;
    private final JavaAccelerationBackend javaBackend = new JavaAccelerationBackend();

    GpuAccelerationBackend(GpuDeviceAdapter device, GpuRetirement retirement, long memoryBudget, long poseBudget) {
        this.device = device;
        this.retirement = retirement;
        this.memoryBudget = memoryBudget;
        this.poseBudget = poseBudget;
    }

    /** One bounded initialization unit per frame; model submission remains Java until all three pass. */
    boolean initializeStep() {
        device.assertThread();
        if (closed || !device.isCurrent()) {
            throw new Failure(FallbackReason.GPU_UNAVAILABLE, false, null);
        }
        if (ready) {
            return true;
        }
        try {
            switch (initializationStep) {
                case 0 -> device.selfTest();
                case 1 -> prepareMaterial(RenderPipelines.ENTITY_CUTOUT);
                case 2 -> prepareMaterial(RenderPipelines.ENTITY_CUTOUT_CULL);
                default -> throw new IllegalStateException("Invalid GPU initialization step");
            }
            initializationStep++;
            ready = initializationStep == 3;
            return ready;
        } catch (RuntimeException failure) {
            throw new Failure(FallbackReason.SHADER_FAILURE, false, failure);
        }
    }

    @Override
    public boolean supports(Operation operation, DrawBatch batch) {
        if (operation == Operation.VERTEX_TRANSFORM) {
            AccelerationBackend cpu = cpuBackend;
            return cpu != null && cpu.supports(operation, batch);
        }
        if (operation != Operation.GPU_DRAW) {
            return false;
        }
        device.assertThread();
        if (!ready || closed || invalidated || !batch.semantics().visible()
                || batch.semantics().material() == null || batch.semantics().isOutline()
                || !GpuBatchData.supports(batch)) {
            return false;
        }
        RenderPipeline pipeline = batch.semantics().material().pipeline();
        if (!GpuShaderProgram.supports(pipeline)) {
            return false;
        }
        if (!materials.containsKey(pipeline)) {
            if (materials.size() + pendingMaterials.size() < 32) {
                pendingMaterials.putIfAbsent(pipeline, false);
            }
            return false;
        }
        return true;
    }

    @Override
    public double[] transform(DrawBatch batch) {
        AccelerationBackend cpu = cpuBackend;
        if (cpu == null) {
            return javaBackend.transform(batch);
        }
        try {
            return cpu.transform(batch);
        } catch (NativeSimdBackend.NativeBatchException rejectedBatch) {
            throw rejectedBatch;
        } catch (RuntimeException | LinkageError failure) {
            cpuBackend = null;
            top.aihmc.omnimorph.FiguraMod.LOGGER.warn("Optional SIMD preparation disabled; GPU remains ready: {}", failure.getClass().getSimpleName());
            return javaBackend.transform(batch);
        }
    }

    void useCpuBackend(AccelerationBackend backend) {
        cpuBackend = backend;
    }

    GpuSubmission.Scope openScope(RenderType material) {
        device.assertThread();
        if (closed || invalidated || !device.isCurrent()) {
            throw new Failure(FallbackReason.GPU_UNAVAILABLE, false, null);
        }
        return device.openScope(material);
    }

    void drawJava(RenderType material, MeshData geometry) {
        device.drawJava(material, geometry);
    }

    @Override
    public DrawResult draw(DrawBatch batch, GpuDrawContext context, BooleanSupplier current) {
        if (!current.getAsBoolean()) {
            return DrawResult.STALE;
        }
        Prepared prepared = prepare(batch, current);
        return prepared == null ? DrawResult.UNSUPPORTED : drawPrepared(prepared, context, 0, prepared.indices, current);
    }

    Prepared prepare(DrawBatch batch, BooleanSupplier current) {
        device.assertThread();
        if (!current.getAsBoolean()) {
            return null;
        }
        if (!supports(Operation.GPU_DRAW, batch)) {
            return null;
        }
        if (!device.isCurrent()) {
            throw new Failure(FallbackReason.GPU_UNAVAILABLE, false, null);
        }
        Key key = new Key(batch);
        Entry entry = meshes.get(key);
        long geometryBytes = entry == null ? (long) batch.mesh().vertexCount() * GpuBatchData.VERTEX_BYTES
                + (long) batch.mesh().faceCount() * (batch.mesh().verticesPerFace() - 2) * 3 * Integer.BYTES : 0;
        if (framePoseBytes + GpuBatchData.POSE_BYTES > poseBudget
                || residentBytes + retirement.bytes() + geometryBytes + GpuBatchData.POSE_BYTES > memoryBudget) {
            return null;
        }
        if (entry == null) {
            try {
                GpuDeviceAdapter.Mesh uploaded = device.uploadGeometry(GpuBatchData.vertices(batch.mesh(),
                        batch.pose().skinJointCount(), batch.pose().usesFloatPrecision()), GpuBatchData.indices(batch.mesh()));
                int indexCount = batch.mesh().faceCount() * (batch.mesh().verticesPerFace() - 2) * 3;
                entry = new Entry(batch, uploaded, indexCount, frame);
                meshes.put(key, entry);
                residentBytes += uploaded.bytes();
                uploads++;
            } catch (RuntimeException failure) {
                throw new Failure(FallbackReason.RESOURCE_EXHAUSTED, false, failure);
            }
        } else {
            cacheHits++;
        }
        entry.lastFrame = frame;
        GpuDeviceAdapter.Pose pose;
        try {
            pose = device.uploadPose(GpuBatchData.pose(batch));
            framePoses.add(pose);
            residentBytes += pose.bytes();
            framePoseBytes += pose.bytes();
        } catch (RuntimeException failure) {
            throw new Failure(FallbackReason.RESOURCE_EXHAUSTED, false, failure);
        }
        return new Prepared(this, batch, materials.get(batch.semantics().material().pipeline()), entry.mesh, pose, entry.indices);
    }

    DrawResult drawPrepared(Prepared prepared, GpuDrawContext context, int firstIndex, int indexCount, BooleanSupplier current) {
        device.assertThread();
        if (!current.getAsBoolean()) {
            return DrawResult.STALE;
        }
        if (closed || invalidated || prepared.backend != this) {
            return DrawResult.UNSUPPORTED;
        }
        if (firstIndex < 0 || indexCount < 0 || firstIndex > prepared.indices - indexCount) {
            throw new IllegalArgumentException("GPU draw range outside prepared mesh");
        }
        if (!device.isCurrent()) {
            throw new Failure(FallbackReason.GPU_UNAVAILABLE, false, null);
        }
        boolean[] issued = {false};
        try {
            device.draw(prepared.material, prepared.mesh, prepared.pose, context, firstIndex, indexCount, () -> {
                if (!current.getAsBoolean()) {
                    throw new Stale();
                }
                issued[0] = true;
            });
            draws++;
            return DrawResult.DRAWN;
        } catch (Stale ignored) {
            return DrawResult.STALE;
        } catch (RuntimeException failure) {
            throw new Failure(FallbackReason.RUNTIME_FAILURE, issued[0], failure);
        }
    }

    @Override
    public void beginFrame() {
        device.assertThread();
        if (closed) {
            return;
        }
        if (invalidated) {
            close();
            return;
        }
        frame++;
        List<GpuDeviceAdapter.Resource> retired = new ArrayList<>(framePoses);
        framePoses.clear();
        framePoseBytes = 0;
        var iterator = meshes.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (!entry.owner.isCurrent() || frame - entry.lastFrame > 120) {
                retired.add(entry.mesh);
                iterator.remove();
            }
        }
        retire(retired);
        retirement.poll();
        // Unknown entity variants are compiled outside model traversal, one per frame; failures stay local.
        var pending = pendingMaterials.entrySet().iterator();
        while (pending.hasNext()) {
            var material = pending.next();
            if (!material.getValue()) {
                try {
                    prepareMaterial(material.getKey());
                    pending.remove();
                } catch (RuntimeException failure) {
                    material.setValue(true);
                }
                break;
            }
        }
    }

    @Override
    public void invalidateResources() {
        // Resource reload callbacks may run outside the render thread. Only mark here; retire next frame.
        invalidated = true;
    }

    @Override
    public void close() {
        device.assertThread();
        if (closed) {
            return;
        }
        closed = true;
        ready = false;
        cpuBackend = null;
        List<GpuDeviceAdapter.Resource> retired = new ArrayList<>(framePoses);
        for (Entry entry : meshes.values()) {
            retired.add(entry.mesh);
        }
        retire(retired);
        meshes.clear();
        framePoses.clear();
        materials.clear();
        pendingMaterials.clear();
    }

    long drawCount() {
        return draws;
    }

    long geometryUploads() {
        return uploads;
    }

    long cacheHits() {
        return cacheHits;
    }

    long residentBytes() {
        return residentBytes;
    }

    private void prepareMaterial(RenderPipeline pipeline) {
        materials.put(pipeline, device.compile(pipeline));
    }

    private void retire(List<GpuDeviceAdapter.Resource> resources) {
        retirement.retire(device, resources);
        residentBytes -= resources.stream().mapToLong(GpuDeviceAdapter.Resource::bytes).sum();
    }
}
