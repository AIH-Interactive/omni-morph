package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.MeshData;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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

    /** Faces count toward Java fallback; indices address the draw (model draws may mix triangles and quads). */
    record PreparedRange(Prepared prepared, int firstFace, int faceCount, int firstIndex, int indexCount) {
        /** Rigid groups are quads only: six indices per face. */
        PreparedRange(Prepared prepared, int firstFace, int faceCount) {
            this(prepared, firstFace, faceCount, firstFace * 6, faceCount * 6);
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

    private record VariantKey(RenderPipeline pipeline, boolean grouped, int parts) {
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
        // Rigid groups only: pose-independent order structure, retired with the entry.
        GpuRigidData.Layout layout;

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
    private final Map<ModelKey, Entry> models = new HashMap<>();
    private final Map<RenderPipeline, GpuDeviceAdapter.Material> materials = new IdentityHashMap<>();
    private final Map<RenderPipeline, GpuDeviceAdapter.Material> groupMaterials = new IdentityHashMap<>();
    private final Map<RenderPipeline, Boolean> pendingMaterials = new LinkedHashMap<>();
    // Smaller pose block variants: false waits for compilation, true marks a failed compile.
    private final Map<VariantKey, GpuDeviceAdapter.Material> variants = new HashMap<>();
    private final Map<VariantKey, Boolean> pendingVariants = new LinkedHashMap<>();
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
    private long groupLayoutBuilds;
    private long groupRangeBuilds;
    private long passes;
    // Pose storage blocks: optional layer above uniform pose blocks, created on first use.
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("OmniMorph/Acceleration");
    private static final int STORAGE_VARIANT = 0;
    private static final int MODEL_VARIANT = -1;
    private static final int MAX_STORAGE_FAILURES = 3;
    private static final long ARENA_LIMIT_BYTES = 16L * 1024 * 1024;
    private GpuPoseArena arena;
    private boolean simdPackDisabled;
    private boolean storageDisabled;
    private int storageFailures;
    private long storageDraws;
    private long storageFallbacks;
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
                || residentBytes + storageResidentBytes() + retirement.bytes() + geometryBytes + GpuBatchData.POSE_BYTES
                > memoryBudget) {
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
        RenderPipeline pipeline = batch.semantics().material().pipeline();
        GpuDeviceAdapter.Material storageMaterial = storageMaterial(pipeline, false);
        if (storageMaterial != null) {
            int bytes = GpuBatchData.storagePoseBytes(GpuBatchData.partsUsed(batch.pose()));
            var storagePose = appendStorage(bytes, target -> GpuBatchData.writeStoragePose(target, batch), 0, null);
            if (storagePose != null) {
                AccelerationFrameDiagnostics.poseUpload(storagePose.bytes(), 0, false);
                return new Prepared(this, batch, storageMaterial, entry.mesh, storagePose, entry.indices);
            }
        }
        int tier = variantTier(pipeline, false, GpuBatchData.partsUsed(batch.pose()));
        GpuDeviceAdapter.Material material = tier == GpuBatchData.MAX_PARTS
                ? materials.get(pipeline) : variants.get(new VariantKey(pipeline, false, tier));
        GpuDeviceAdapter.Pose pose;
        try {
            pose = acquirePose(batch.lease().owner(), GpuBatchData.pose(batch, tier));
            AccelerationFrameDiagnostics.poseUpload(pose.bytes(), 0, false);
        } catch (RuntimeException failure) {
            throw new Failure(FallbackReason.RESOURCE_EXHAUSTED, false, failure);
        }
        return new Prepared(this, batch, material, entry.mesh, pose, entry.indices);
    }

    List<PreparedRange> prepareGroup(List<DrawBatch> batches, long[] order, BooleanSupplier current) {
        device.assertThread();
        AccelerationFrameDiagnostics.prepareGroup(batches);
        if (!current.getAsBoolean() || !ready || closed || invalidated || !device.supportsRigidGroups()) {
            return null;
        }
        if (!GpuRigidData.supports(batches)) {
            // Skinned or differently tinted/lit batches reject rigid groups; a model draw may still cover them all.
            return prepareModel(batches, order, current);
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
        var key = new GroupKey(batches);
        Entry entry = groups.get(key);
        var data = new GpuRigidData(batches, order, entry == null ? null : entry.layout, simdPacker());
        AccelerationFrameDiagnostics.rigidDataCreated();
        if (data.nativePacked()) {
            AccelerationFrameDiagnostics.simdPackedParts(batches.size());
        } else if (data.nativePackFailure() != null) {
            // Java casts already produced the identical bytes for this group; stop asking native this session.
            simdPackDisabled = true;
            LOGGER.warn("Native SIMD pose packing disabled; Java packing remains", data.nativePackFailure());
        }
        if (!data.layoutReused()) {
            groupLayoutBuilds++;
        }
        if (!data.rangesReused()) {
            groupRangeBuilds++;
        }
        long geometryBytes = entry == null ? data.geometryBytes() : 0;
        // Authored order is fixed by the cached geometry, so its index buffer is uploaded once per group.
        boolean authored = data.authoredOrder();
        boolean reuseIndices = authored && entry != null && entry.authoredIndices != null;
        long indexBytes = reuseIndices ? 0 : data.indexBytes();
        if (frameUploadBytes + indexBytes + data.poseBytes() > frameUploadBudget
                || residentBytes + storageResidentBytes() + retirement.bytes() + geometryBytes + indexBytes
                + data.poseBytes() > memoryBudget
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
            entry.layout = data.layout();
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
            RenderPipeline pipeline = first.semantics().material().pipeline();
            GpuDeviceAdapter.Material storageMaterial = storageMaterial(pipeline, true);
            if (storageMaterial != null) {
                // The unsized part array needs no palette split: the whole group is one range and one draw.
                var storagePose = appendStorage(Math.toIntExact(data.storagePoseBytes()), data::writeStoragePose,
                        Math.toIntExact(data.storageRemapBytes()), data::writeStorageRemap);
                if (storagePose != null) {
                    AccelerationFrameDiagnostics.poseUpload(storagePose.bytes(), data.partCount(), true);
                    var prepared = new Prepared(this, first, storageMaterial, entry.mesh, storagePose, entry.indices, indices);
                    return List.of(new PreparedRange(prepared, 0, data.faceCount()));
                }
            }
            for (GpuRigidData.Range range : data.ranges()) {
                int tier = variantTier(pipeline, true, range.parts().size());
                GpuDeviceAdapter.Material rangeMaterial = tier == GpuBatchData.MAX_PARTS
                        ? material : variants.get(new VariantKey(pipeline, true, tier));
                GpuDeviceAdapter.Pose pose = acquirePose(first.lease().owner(), data.pose(range, tier));
                AccelerationFrameDiagnostics.poseUpload(pose.bytes(), range.parts().size(), true);
                var prepared = new Prepared(this, first, rangeMaterial, entry.mesh, pose, entry.indices, indices);
                result.add(new PreparedRange(prepared, range.firstFace(), range.faceCount()));
            }
            return List.copyOf(result);
        } catch (RuntimeException failure) {
            throw new Failure(FallbackReason.RESOURCE_EXHAUSTED, false, failure);
        }
    }

    /**
     * Consecutive draws of one material boundary sharing a render pass while their material stays the same.
     * Opened after the boundary's preparations; any pending arena bytes close the pass before they are written.
     */
    final class MergedDraws implements AutoCloseable {
        private final GpuDrawContext context;
        private GpuDeviceAdapter.DrawPass pass;

        private MergedDraws(GpuDrawContext context) {
            this.context = context;
        }

        DrawResult draw(Prepared prepared, int firstIndex, int indexCount, BooleanSupplier current) {
            return drawPrepared(prepared, context, firstIndex, indexCount, current, this);
        }

        private void issue(Prepared prepared, int firstIndex, int indexCount, Runnable issuing) {
            if (pass != null && pass.material() != prepared.material) {
                closePass();
            }
            if (pass == null) {
                pass = device.openDrawPass(prepared.material, context);
                passes++;
                AccelerationFrameDiagnostics.pass();
            }
            pass.draw(prepared.mesh, prepared.pose, prepared.sortedIndices, firstIndex, indexCount, issuing);
        }

        private void closePass() {
            if (pass != null) {
                GpuDeviceAdapter.DrawPass open = pass;
                pass = null;
                open.close();
            }
        }

        @Override
        public void close() {
            closePass();
        }
    }

    MergedDraws openMergedDraws(GpuDrawContext context) {
        device.assertThread();
        return new MergedDraws(context);
    }

    DrawResult drawPrepared(Prepared prepared, GpuDrawContext context, int firstIndex, int indexCount, BooleanSupplier current) {
        return drawPrepared(prepared, context, firstIndex, indexCount, current, null);
    }

    private DrawResult drawPrepared(Prepared prepared, GpuDrawContext context, int firstIndex, int indexCount,
                                    BooleanSupplier current, MergedDraws merged) {
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
        var storagePose = prepared.pose instanceof GpuDeviceAdapter.StoragePose arenaPose ? arenaPose : null;
        if (storagePose != null) {
            // Blaze3D rejects buffer writes inside a render pass, so the arena is flushed before the device opens one.
            if (arena == null) {
                return DrawResult.UNSUPPORTED;
            }
            if (merged != null && arena.hasPending()) {
                merged.closePass();
            }
            try {
                storagePose.storage = arena.flush();
            } catch (RuntimeException failure) {
                storageFailed(failure);
                return DrawResult.UNSUPPORTED;
            }
        }
        boolean[] issued = {false};
        try {
            Runnable issuing = () -> {
                if (!current.getAsBoolean()) {
                    throw new Stale();
                }
                issued[0] = true;
            };
            if (merged != null) {
                merged.issue(prepared, firstIndex, indexCount, issuing);
            } else if (prepared.sortedIndices == null) {
                device.draw(prepared.material, prepared.mesh, prepared.pose, context, firstIndex, indexCount, issuing);
            } else {
                device.drawWithIndices(prepared.material, prepared.mesh, prepared.pose, prepared.sortedIndices,
                        context, firstIndex, indexCount, issuing);
            }
            draws++;
            if (storagePose != null) {
                storageDraws++;
                AccelerationFrameDiagnostics.ssboDraw();
                storageFailures = 0;
            }
            AccelerationFrameDiagnostics.draw(prepared.batch.semantics().material());
            return DrawResult.DRAWN;
        } catch (Stale ignored) {
            return DrawResult.STALE;
        } catch (RuntimeException failure) {
            if (merged != null) {
                // A failed draw must not leave its pass, or its GL state, open for the Java fallback.
                try {
                    merged.closePass();
                } catch (RuntimeException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            if (storagePose != null && !issued[0]) {
                // Nothing reached the GPU: only the storage layer is at fault, the rest falls back locally.
                storageFailed(failure);
                return DrawResult.UNSUPPORTED;
            }
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
        var modelIterator = models.entrySet().iterator();
        while (modelIterator.hasNext()) {
            Entry entry = modelIterator.next().getValue();
            if (!entry.owner.isCurrent() || frame - entry.lastFrame > 120) {
                retireGroup(entry, retired);
                modelIterator.remove();
            }
        }
        retire(retired);
        if (arena != null) {
            arena.beginFrame();
        }
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
                return;
            }
        }
        compilePendingVariant();
    }

    /** Smaller pose block variants share the same one-compile-per-frame bound; failures keep the full layout. */
    private void compilePendingVariant() {
        for (var variant : pendingVariants.entrySet()) {
            if (!variant.getValue()) {
                VariantKey key = variant.getKey();
                try {
                    variants.put(key, switch (key.parts()) {
                        case STORAGE_VARIANT -> device.compileStorage(key.pipeline(), key.grouped());
                        case MODEL_VARIANT -> device.compileStorageModel(key.pipeline());
                        default -> device.compileVariant(key.pipeline(), key.grouped(), key.parts());
                    });
                    pendingVariants.remove(key);
                } catch (RuntimeException failure) {
                    variant.setValue(true);
                }
                return;
            }
        }
    }

    /** Smallest compiled variant for the parts; a missing one is queued while the full layout draws. */
    private int variantTier(RenderPipeline pipeline, boolean grouped, int parts) {
        int tier = GpuBatchData.tierFor(parts);
        if (tier == GpuBatchData.MAX_PARTS) {
            return tier;
        }
        var key = new VariantKey(pipeline, grouped, tier);
        if (variants.containsKey(key)) {
            return tier;
        }
        pendingVariants.putIfAbsent(key, false);
        return GpuBatchData.MAX_PARTS;
    }

    /**
     * The compiled storage variant of a pipeline, or null while storage blocks are inactive, disabled for this
     * session, or the variant still waits for its compile step (it is queued here on first use).
     */
    private GpuDeviceAdapter.Material storageMaterial(RenderPipeline pipeline, boolean grouped) {
        return storageMaterial(pipeline, grouped, STORAGE_VARIANT);
    }

    private GpuDeviceAdapter.Material storageMaterial(RenderPipeline pipeline, int variant) {
        return storageMaterial(pipeline, true, variant);
    }

    private GpuDeviceAdapter.Material storageMaterial(RenderPipeline pipeline, boolean grouped, int variant) {
        if (storageDisabled || device.storageLimits() == null) {
            return null;
        }
        var key = new VariantKey(pipeline, grouped, variant);
        GpuDeviceAdapter.Material material = variants.get(key);
        if (material == null) {
            pendingVariants.putIfAbsent(key, false);
        }
        return material;
    }

    /** Stages a pose (and optional remap) block in the frame arena; null keeps the uniform pose path. */
    private GpuDeviceAdapter.StoragePose appendStorage(int poseBytes, Consumer<ByteBuffer> poseWriter,
                                                       int remapBytes, Consumer<ByteBuffer> remapWriter) {
        if (frameUploadBytes + poseBytes + remapBytes > frameUploadBudget) {
            storageFallbacks++;
            AccelerationFrameDiagnostics.ssboFallback("UPLOAD_BUDGET");
            return null;
        }
        ensureArena();
        GpuPoseArena.Slice pose = arena.append(poseBytes, poseWriter);
        GpuPoseArena.Slice remap = null;
        if (pose != null && remapWriter != null) {
            remap = arena.append(remapBytes, remapWriter);
        }
        if (pose == null || (remapWriter != null && remap == null)) {
            storageFallbacks++;
            AccelerationFrameDiagnostics.ssboFallback("ARENA_FULL");
            return null;
        }
        frameUploadBytes += poseBytes + remapBytes;
        return new GpuDeviceAdapter.StoragePose(pose, remap);
    }

    /**
     * R4-3: one storage draw for a whole material boundary whose batches may be skinned or differ in tint and light.
     * Returns null (the caller draws batch by batch) when model draws are off, unsupported, still compiling, or the
     * arena cannot take the boundary this frame.
     */
    private List<PreparedRange> prepareModel(List<DrawBatch> batches, long[] order, BooleanSupplier current) {
        if (!AccelerationOptions.modelMesh() || storageDisabled || device.storageLimits() == null
                || !device.storageLimits().models()) {
            return null;
        }
        if (!GpuModelData.supports(batches)) {
            AccelerationFrameDiagnostics.fallback("MODEL_UNSUPPORTED");
            return null;
        }
        DrawBatch first = batches.getFirst();
        GpuDeviceAdapter.Material material = storageMaterial(first.semantics().material().pipeline(), MODEL_VARIANT);
        if (material == null) {
            return null;
        }
        if (!device.isCurrent()) {
            throw new Failure(FallbackReason.GPU_UNAVAILABLE, false, null);
        }
        var data = new GpuModelData(batches, order);
        var key = new ModelKey(new GroupKey(batches), data.encodingSignature());
        Entry entry = models.get(key);
        long geometryBytes = entry == null ? data.geometryBytes() : 0;
        boolean authored = data.authoredOrder();
        boolean reuseIndices = authored && entry != null && entry.authoredIndices != null;
        long indexBytes = reuseIndices ? 0 : data.indexBytes();
        if (frameUploadBytes + indexBytes > frameUploadBudget
                || residentBytes + storageResidentBytes() + retirement.bytes() + geometryBytes + indexBytes > memoryBudget
                || !current.getAsBoolean()) {
            AccelerationFrameDiagnostics.fallback("MODEL_BUDGET");
            return null;
        }
        try {
            if (entry == null) {
                GpuDeviceAdapter.Mesh geometry = device.uploadGeometry(data.vertices(), GpuRigidData.initialIndices());
                entry = new Entry(first, geometry, data.indexCount(), frame);
                models.put(key, entry);
                residentBytes += geometry.bytes();
                uploads++;
            } else {
                cacheHits++;
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
            var storagePose = appendModelStorage(data);
            if (storagePose == null) {
                AccelerationFrameDiagnostics.fallback("MODEL_ARENA");
                return null;
            }
            AccelerationFrameDiagnostics.poseUpload(storagePose.bytes(), data.partCount(), true);
            AccelerationFrameDiagnostics.modelPrepared(batches.size());
            var prepared = new Prepared(this, first, material, entry.mesh, storagePose, data.indexCount(), indices);
            return List.of(new PreparedRange(prepared, 0, data.faceCount(), 0, data.indexCount()));
        } catch (RuntimeException failure) {
            throw new Failure(FallbackReason.RESOURCE_EXHAUSTED, false, failure);
        }
    }

    private GpuDeviceAdapter.StoragePose appendModelStorage(GpuModelData data) {
        int poseBytes = Math.toIntExact(data.storagePoseBytes());
        int recordBytes = Math.toIntExact(data.recordBytes());
        if (frameUploadBytes + poseBytes + recordBytes > frameUploadBudget) {
            storageFallbacks++;
            return null;
        }
        ensureArena();
        GpuPoseArena.Slice pose = arena.append(poseBytes, data::writeStoragePose);
        GpuPoseArena.Slice records = pose == null ? null : arena.append(recordBytes, data::writeRecords);
        if (records == null) {
            storageFallbacks++;
            return null;
        }
        frameUploadBytes += poseBytes + recordBytes;
        return new GpuDeviceAdapter.StoragePose(pose, null, records);
    }

    private void ensureArena() {
        if (arena == null) {
            GpuStorageSupport.Limits limits = device.storageLimits();
            arena = new GpuPoseArena(device, retirement, limits.offsetAlignment(), ARENA_LIMIT_BYTES);
        }
    }

    private record ModelKey(GroupKey group, int[] signature) {
        @Override
        public boolean equals(Object other) {
            return other instanceof ModelKey key && group.equals(key.group) && Arrays.equals(signature, key.signature);
        }

        @Override
        public int hashCode() {
            return 31 * group.hashCode() + Arrays.hashCode(signature);
        }
    }

    /** Consecutive storage-only failures switch the layer off for the session; uniform pose blocks remain. */
    private void storageFailed(RuntimeException failure) {
        storageFallbacks++;
        AccelerationFrameDiagnostics.ssboFallback("FAILURE");
        storageFailures++;
        if (storageFailures < MAX_STORAGE_FAILURES || storageDisabled) {
            return;
        }
        storageDisabled = true;
        LOGGER.warn("GPU pose storage blocks disabled after {} failures; uniform pose blocks remain",
                storageFailures, failure);
        if (arena != null) {
            arena.close();
            arena = null;
        }
    }

    /** The SIMD backend packs rigid group poses only when requested, attached and not yet rejected. */
    private NativeSimdBackend simdPacker() {
        if (simdPackDisabled || !AccelerationOptions.simdPack()) {
            return null;
        }
        return cpuBackend instanceof NativeSimdBackend simd ? simd : null;
    }

    boolean simdPackActive() {
        return simdPacker() != null;
    }

    private long storageResidentBytes() {
        return arena == null ? 0 : arena.residentBytes();
    }

    boolean storageActive() {
        return !storageDisabled && device.storageLimits() != null;
    }

    long storageDraws() {
        return storageDraws;
    }

    long storageFallbacks() {
        return storageFallbacks;
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
        for (Entry entry : models.values()) {
            retireGroup(entry, retired);
        }
        models.clear();
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
        variants.clear();
        pendingVariants.clear();
        if (arena != null) {
            arena.close();
            arena = null;
        }
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

    long passCount() {
        return passes;
    }

    long groupLayoutBuilds() {
        return groupLayoutBuilds;
    }

    long groupRangeBuilds() {
        return groupRangeBuilds;
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
