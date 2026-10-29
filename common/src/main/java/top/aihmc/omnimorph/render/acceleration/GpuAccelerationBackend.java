package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.MeshData;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
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
        private final GpuDeviceAdapter.Indices sortedIndices;

        private Prepared(GpuAccelerationBackend backend, DrawBatch batch, GpuDeviceAdapter.Material material,
                         GpuDeviceAdapter.Mesh mesh, GpuDeviceAdapter.Pose pose, int indices) {
            this(backend, batch, material, mesh, pose, indices, null);
        }

        private Prepared(GpuAccelerationBackend backend, DrawBatch batch, GpuDeviceAdapter.Material material,
                         GpuDeviceAdapter.Mesh mesh, GpuDeviceAdapter.Pose pose, int indices,
                         GpuDeviceAdapter.Indices sortedIndices) {
            this.backend = backend;
            this.batch = batch;
            this.material = material;
            this.mesh = mesh;
            this.pose = pose;
            this.indices = indices;
            this.sortedIndices = sortedIndices;
        }
    }

    record PreparedRange(Prepared prepared, int firstFace, int faceCount) {
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

    private static final class GroupKey {
        private final long owner;
        private final List<MeshHandle> meshes;
        private final int hash;

        GroupKey(List<DrawBatch> batches) {
            owner = batches.getFirst().lease().ownerGeneration();
            meshes = batches.stream().map(DrawBatch::mesh).toList();
            int result = Long.hashCode(owner);
            for (MeshHandle mesh : meshes) {
                result = 31 * result + mesh.geometryHash();
            }
            hash = result;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof GroupKey key) || owner != key.owner || meshes.size() != key.meshes.size()) {
                return false;
            }
            for (int index = 0; index < meshes.size(); index++) {
                if (!meshes.get(index).sameGeometry(key.meshes.get(index))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class Entry {
        final AccelerationOwner owner;
        final GpuDeviceAdapter.Mesh mesh;
        final int indices;
        long lastFrame;
        // Rigid groups only: the authored-order index buffer, owned and retired with the mesh.
        GpuDeviceAdapter.Indices authoredIndices;

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
    private final long frameUploadBudget;
    private final Map<Key, Entry> meshes = new HashMap<>();
    private final Map<GroupKey, Entry> groups = new HashMap<>();
    private final Map<RenderPipeline, GpuDeviceAdapter.Material> materials = new IdentityHashMap<>();
    private final Map<RenderPipeline, GpuDeviceAdapter.Material> groupMaterials = new IdentityHashMap<>();
    private final Map<RenderPipeline, Boolean> pendingMaterials = new LinkedHashMap<>();
    private final List<GpuDeviceAdapter.Resource> frameResources = new ArrayList<>();
    // Pose buffers return here only after their retirement fence completed; idle ones count as resident.
    private static final int MAX_POOLED_POSES = 256;
    private static final int POSE_DEMAND_WINDOW = 120;
    private final List<GpuDeviceAdapter.Pose> framePoses = new ArrayList<>();
    private final Map<GpuDeviceAdapter.Resource, AccelerationOwner> poseOwners = new IdentityHashMap<>();
    private final Map<AccelerationOwner, Map<Long, ArrayDeque<GpuDeviceAdapter.Pose>>> freePoses =
            new IdentityHashMap<>();
    private int pooledPoses;
    private int poseDemand;
    private long residentBytes;
    private long frameUploadBytes;
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

    GpuAccelerationBackend(GpuDeviceAdapter device, GpuRetirement retirement, long memoryBudget, long frameUploadBudget) {
        this.device = device;
        this.retirement = retirement;
        this.memoryBudget = memoryBudget;
        this.frameUploadBudget = frameUploadBudget;
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
            org.figuramc.figura.FiguraMod.LOGGER.warn("Optional SIMD preparation disabled; GPU remains ready: {}", failure.getClass().getSimpleName());
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
        AccelerationFrameDiagnostics.prepare(batch);
        if (!current.getAsBoolean()) {
            return null;
        }
        if (!supports(Operation.GPU_DRAW, batch)) {
            AccelerationFrameDiagnostics.fallback("PREPARE_UNSUPPORTED");
            return null;
        }
        if (!device.isCurrent()) {
            throw new Failure(FallbackReason.GPU_UNAVAILABLE, false, null);
        }
        Key key = new Key(batch);
        Entry entry = meshes.get(key);
        long geometryBytes = entry == null ? (long) batch.mesh().vertexCount() * GpuBatchData.VERTEX_BYTES
                + (long) batch.mesh().faceCount() * (batch.mesh().verticesPerFace() - 2) * 3 * Integer.BYTES : 0;
        if (frameUploadBytes + GpuBatchData.POSE_BYTES > frameUploadBudget
                || residentBytes + retirement.bytes() + geometryBytes + GpuBatchData.POSE_BYTES > memoryBudget) {
            AccelerationFrameDiagnostics.fallback(FallbackReason.RESOURCE_EXHAUSTED.name());
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
            pose = acquirePose(batch.lease().owner(), GpuBatchData.pose(batch));
            AccelerationFrameDiagnostics.poseUpload(pose.bytes(), 0, false);
        } catch (RuntimeException failure) {
            throw new Failure(FallbackReason.RESOURCE_EXHAUSTED, false, failure);
        }
        return new Prepared(this, batch, materials.get(batch.semantics().material().pipeline()), entry.mesh, pose, entry.indices);
    }

    List<PreparedRange> prepareGroup(List<DrawBatch> batches, long[] order, BooleanSupplier current) {
        device.assertThread();
        AccelerationFrameDiagnostics.prepareGroup(batches);
        if (!current.getAsBoolean() || !ready || closed || invalidated || !device.supportsRigidGroups()
                || !GpuRigidData.supports(batches)) {
            return null;
        }
        DrawBatch first = batches.getFirst();
        GpuDeviceAdapter.Material material = groupMaterials.get(first.semantics().material().pipeline());
        if (material == null) {
            return null;
        }
        for (DrawBatch batch : batches) {
            if (!batch.semantics().visible()) {
                return null;
            }
        }
        if (!device.isCurrent()) {
            throw new Failure(FallbackReason.GPU_UNAVAILABLE, false, null);
        }
        var data = new GpuRigidData(batches, order);
        AccelerationFrameDiagnostics.rigidDataCreated();
        var key = new GroupKey(batches);
        Entry entry = groups.get(key);
        long geometryBytes = entry == null ? data.geometryBytes() : 0;
        // Authored order is fixed by the cached geometry, so its index buffer is uploaded once per group.
        boolean authored = data.authoredOrder();
        boolean reuseIndices = authored && entry != null && entry.authoredIndices != null;
        long indexBytes = reuseIndices ? 0 : data.indexBytes();
        if (frameUploadBytes + indexBytes + data.poseBytes() > frameUploadBudget
                || residentBytes + retirement.bytes() + geometryBytes + indexBytes + data.poseBytes() > memoryBudget
                || !current.getAsBoolean()) {
            return null;
        }
        try {
            if (entry == null) {
                GpuDeviceAdapter.Mesh geometry = device.uploadGeometry(data.vertices(), GpuRigidData.initialIndices());
                entry = new Entry(first, geometry, Math.multiplyExact(order.length, 6), frame);
                groups.put(key, entry);
                residentBytes += geometry.bytes();
                uploads++;
            } else {
                cacheHits++;
                AccelerationFrameDiagnostics.groupGeometryCacheHit();
            }
            entry.lastFrame = frame;
            GpuDeviceAdapter.Indices indices;
            if (reuseIndices) {
                indices = entry.authoredIndices;
            } else if (authored) {
                indices = device.uploadIndices(data.indices());
                entry.authoredIndices = indices;
                residentBytes += indices.bytes();
                frameUploadBytes += indices.bytes();
            } else {
                indices = device.uploadIndices(data.indices());
                ownFrameResource(indices);
            }
            List<PreparedRange> result = new ArrayList<>();
            for (GpuRigidData.Range range : data.ranges()) {
                GpuDeviceAdapter.Pose pose = acquirePose(first.lease().owner(), data.pose(range));
                AccelerationFrameDiagnostics.poseUpload(pose.bytes(), range.parts().size(), true);
                var prepared = new Prepared(this, first, material, entry.mesh, pose, entry.indices, indices);
                result.add(new PreparedRange(prepared, range.firstFace(), range.faceCount()));
            }
            return List.copyOf(result);
        } catch (RuntimeException failure) {
            throw new Failure(FallbackReason.RESOURCE_EXHAUSTED, false, failure);
        }
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
            Runnable issuing = () -> {
                if (!current.getAsBoolean()) {
                    throw new Stale();
                }
                issued[0] = true;
            };
            if (prepared.sortedIndices == null) {
                device.draw(prepared.material, prepared.mesh, prepared.pose, context, firstIndex, indexCount, issuing);
            } else {
                device.drawWithIndices(prepared.material, prepared.mesh, prepared.pose, prepared.sortedIndices,
                        context, firstIndex, indexCount, issuing);
            }
            draws++;
            AccelerationFrameDiagnostics.draw(prepared.batch.semantics().material());
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
        updatePoseDemand();
        retirePoses();
        List<GpuDeviceAdapter.Resource> retired = new ArrayList<>(frameResources);
        frameResources.clear();
        frameUploadBytes = 0;
        var iterator = meshes.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (!entry.owner.isCurrent() || frame - entry.lastFrame > 120) {
                retired.add(entry.mesh);
                iterator.remove();
            }
        }
        var groupIterator = groups.entrySet().iterator();
        while (groupIterator.hasNext()) {
            Entry entry = groupIterator.next().getValue();
            if (!entry.owner.isCurrent() || frame - entry.lastFrame > 120) {
                retireGroup(entry, retired);
                groupIterator.remove();
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
        List<GpuDeviceAdapter.Resource> retired = new ArrayList<>(frameResources);
        retired.addAll(framePoses);
        for (Map<Long, ArrayDeque<GpuDeviceAdapter.Pose>> ownerPool : freePoses.values()) {
            for (ArrayDeque<GpuDeviceAdapter.Pose> free : ownerPool.values()) {
                retired.addAll(free);
            }
        }
        for (Entry entry : meshes.values()) {
            retired.add(entry.mesh);
        }
        for (Entry entry : groups.values()) {
            retireGroup(entry, retired);
        }
        retire(retired);
        meshes.clear();
        groups.clear();
        frameResources.clear();
        framePoses.clear();
        freePoses.clear();
        poseOwners.clear();
        pooledPoses = 0;
        materials.clear();
        groupMaterials.clear();
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

    boolean supportsRigidGroups() {
        return device.supportsRigidGroups();
    }

    long residentBytes() {
        return residentBytes;
    }

    private void prepareMaterial(RenderPipeline pipeline) {
        materials.put(pipeline, device.compile(pipeline));
        if (device.supportsRigidGroups()) {
            groupMaterials.put(pipeline, device.compileRigidGroup(pipeline));
        }
    }

    private void ownFrameResource(GpuDeviceAdapter.Resource resource) {
        frameResources.add(resource);
        residentBytes += resource.bytes();
        frameUploadBytes += resource.bytes();
    }

    /** Overwrites a fence-completed buffer of the same owner and layout size, or uploads a new one. */
    private GpuDeviceAdapter.Pose acquirePose(AccelerationOwner owner, ByteBuffer data) {
        long size = data.remaining();
        Map<Long, ArrayDeque<GpuDeviceAdapter.Pose>> ownerPool = freePoses.get(owner);
        ArrayDeque<GpuDeviceAdapter.Pose> free = ownerPool == null ? null : ownerPool.get(size);
        GpuDeviceAdapter.Pose pooled = free == null ? null : free.pollFirst();
        if (pooled != null) {
            pooledPoses--;
            boolean updated;
            try {
                updated = device.updatePose(pooled, data.duplicate());
            } catch (RuntimeException failure) {
                retire(List.of(pooled));
                throw failure;
            }
            if (updated) {
                // Already counted in residentBytes while idle; only the transfer counts toward this frame.
                ownFramePose(owner, pooled);
                frameUploadBytes += size;
                return pooled;
            }
            retire(List.of(pooled));
        }
        GpuDeviceAdapter.Pose pose = device.uploadPose(data);
        ownFramePose(owner, pose);
        residentBytes += pose.bytes();
        frameUploadBytes += pose.bytes();
        return pose;
    }

    private void ownFramePose(AccelerationOwner owner, GpuDeviceAdapter.Pose pose) {
        framePoses.add(pose);
        poseOwners.put(pose, owner);
    }

    /**
     * The pool never holds more buffers than one recent frame used. Demand restarts every window,
     * so an idle surplus is released instead of being kept indefinitely.
     */
    private void updatePoseDemand() {
        if (frame % POSE_DEMAND_WINDOW == 0) {
            poseDemand = framePoses.size();
        } else {
            poseDemand = Math.max(poseDemand, framePoses.size());
        }
        List<GpuDeviceAdapter.Resource> released = new ArrayList<>();
        var owners = freePoses.entrySet().iterator();
        while (owners.hasNext()) {
            var ownerPool = owners.next();
            boolean retiredOwner = !ownerPool.getKey().isCurrent();
            for (ArrayDeque<GpuDeviceAdapter.Pose> free : ownerPool.getValue().values()) {
                while (!free.isEmpty() && (retiredOwner || pooledPoses > poseDemand)) {
                    released.add(free.pollFirst());
                    pooledPoses--;
                }
            }
            ownerPool.getValue().values().removeIf(ArrayDeque::isEmpty);
            if (ownerPool.getValue().isEmpty()) {
                owners.remove();
            }
        }
        retire(released);
    }

    private void retirePoses() {
        if (framePoses.isEmpty()) {
            return;
        }
        List<GpuDeviceAdapter.Resource> retired = new ArrayList<>(framePoses);
        framePoses.clear();
        retirement.retire(device, retired, this::recyclePose);
        residentBytes -= retired.stream().mapToLong(GpuDeviceAdapter.Resource::bytes).sum();
    }

    /** Runs from retirement after the fence completed; may run after close, when it declines. */
    private boolean recyclePose(GpuDeviceAdapter.Resource resource) {
        AccelerationOwner owner = poseOwners.remove(resource);
        if (closed || invalidated || owner == null || !owner.isCurrent()
                || pooledPoses >= Math.min(MAX_POOLED_POSES, poseDemand)
                || !(resource instanceof GpuDeviceAdapter.Pose pose)) {
            return false;
        }
        freePoses.computeIfAbsent(owner, key -> new HashMap<>())
                .computeIfAbsent(pose.bytes(), size -> new ArrayDeque<>())
                .addLast(pose);
        pooledPoses++;
        residentBytes += pose.bytes();
        return true;
    }

    private static void retireGroup(Entry entry, List<GpuDeviceAdapter.Resource> retired) {
        retired.add(entry.mesh);
        if (entry.authoredIndices != null) {
            retired.add(entry.authoredIndices);
        }
    }

    private void retire(List<GpuDeviceAdapter.Resource> resources) {
        retirement.retire(device, resources);
        residentBytes -= resources.stream().mapToLong(GpuDeviceAdapter.Resource::bytes).sum();
    }
}
