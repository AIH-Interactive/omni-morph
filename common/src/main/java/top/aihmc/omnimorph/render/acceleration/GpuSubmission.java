package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.CompactVectorArray;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Consumer;

/** Both engines enter the same native material boundary; wrappers own no Minecraft targets or textures. */
public final class GpuSubmission {
    @FunctionalInterface
    public interface JavaEmitter {
        void emit(VertexConsumer vertices, int firstVertex, int vertexCount);
    }

    static final class Scope implements AutoCloseable {
        final GpuDrawContext context;
        private final Runnable cleanup;

        Scope(GpuDrawContext context, Runnable cleanup) {
            this.context = context;
            this.cleanup = cleanup;
        }

        @Override
        public void close() {
            cleanup.run();
        }
    }

    private record Queued(DrawBatch batch, JavaEmitter javaEmitter, Consumer<MeshData> javaDraw) {
    }

    private record Face(int batch, int face) {
    }

    private static final GpuSubmission GLOBAL = new GpuSubmission(AccelerationService.INSTANCE);
    private final AccelerationService service;
    private final Function<RenderType, Scope> context;
    private final Function<RenderType, Consumer<MeshData>> javaDraw;
    private final Map<MultiBufferSource.BufferSource, Map<RenderType, Boundary>> sources = new WeakHashMap<>();
    private long frame;

    GpuSubmission(AccelerationService service, Function<RenderType, Scope> context, BiConsumer<RenderType, MeshData> javaDraw) {
        this.service = service;
        this.context = context;
        this.javaDraw = material -> geometry -> javaDraw.accept(material, geometry);
    }

    private GpuSubmission(AccelerationService service) {
        this.service = service;
        this.context = service::openGpuDrawScope;
        this.javaDraw = service::javaDrawCallback;
    }

    public static boolean enqueue(MultiBufferSource source, DrawBatch batch, JavaEmitter fallback) {
        return GLOBAL.offer(source, batch, fallback);
    }

    static void beginFrame() {
        GLOBAL.nextFrame();
    }

    void nextFrame() {
        frame++;
        for (Map<RenderType, Boundary> boundaries : sources.values()) {
            var iterator = boundaries.values().iterator();
            while (iterator.hasNext()) {
                Boundary boundary = iterator.next();
                // A native buffer may still flush its placeholder, but old frame batches must never be replayed.
                boundary.queued.clear();
                if (frame - boundary.lastUsed > 120) {
                    iterator.remove();
                }
            }
        }
    }

    boolean offer(MultiBufferSource source, DrawBatch batch, JavaEmitter fallback) {
        RenderType material = batch.semantics().material();
        if (!service.gpuDrawEnabled() || !(source instanceof MultiBufferSource.BufferSource nativeSource)
                || material == null || material.isOutline() || batch.mesh().verticesPerFace() != 4
                || material.mode() != VertexFormat.Mode.QUADS || material.format() != DefaultVertexFormat.ENTITY
                || !GpuShaderProgram.supports(material.pipeline()) || nativeSource.fixedBuffers.containsKey(material)
                || batch.semantics().texture() == null || batch.semantics().texture().getNamespace().equals("minecraft")) {
            AccelerationFrameDiagnostics.fallback("OFFER_INELIGIBLE");
            return false;
        }
        if (!service.isCurrent(batch.lease())) {
            return true;
        }
        Map<RenderType, Boundary> boundaries = sources.computeIfAbsent(nativeSource, ignored -> new HashMap<>());
        Boundary boundary = boundaries.get(material);
        if (boundary == null) {
            if (boundaries.size() >= 256) {
                AccelerationFrameDiagnostics.fallback(FallbackReason.RESOURCE_EXHAUSTED.name());
                return false;
            }
            boundary = new Boundary(material);
            boundaries.put(material, boundary);
        }
        boolean needsPlaceholder = boundary.queued.isEmpty();
        boundary.queued.add(new Queued(batch, fallback, javaDraw.apply(material)));
        boundary.lastUsed = frame;
        if (needsPlaceholder) {
            VertexConsumer placeholder = nativeSource.getBuffer(boundary);
            for (int vertex = 0; vertex < 4; vertex++) {
                placeholder.addVertex(0, 0, 0).setColor(255, 255, 255, 255)
                        .setUv(0, 0).setOverlay(0).setLight(0).setNormal(0, 1, 0);
            }
        }
        return true;
    }

    private final class Boundary extends RenderType {
        final RenderType original;
        final List<Queued> queued = new ArrayList<>();
        long lastUsed;

        Boundary(RenderType original) {
            super("omnimorph_acceleration_boundary", original.state);
            this.original = original;
        }

        @Override
        public boolean sortOnUpload() {
            return false; // The real authored faces, rather than the placeholder, are sorted below.
        }

        @Override
        public void draw(MeshData placeholder) {
            placeholder.close();
            List<Queued> batches = queued.stream().filter(value -> service.isCurrent(value.batch().lease())).toList();
            queued.clear();
            if (batches.isEmpty()) {
                return;
            }
            if (!original.sortOnUpload()) {
                if (service.gpuRigidGroupsEnabled() && batches.size() > 1) {
                    // Opaque groups keep authored order; per-face objects are only built for a Java fallback.
                    long[] order = authoredOrder(batches);
                    int remaining = drawGroup(batches, order);
                    if (remaining >= 0) {
                        if (remaining < order.length) {
                            drawJava(original, batches, faces(batches, false), remaining);
                        }
                        return;
                    }
                }
                for (Queued batch : batches) {
                    drawBatches(List.of(batch));
                }
            } else {
                // A shared translucent material must retain its global face order, including local fallback.
                drawBatches(batches);
            }
        }

        private void drawBatches(List<Queued> batches) {
            var prepared = new GpuAccelerationBackend.Prepared[batches.size()];
            List<Face> order = faces(batches, original.sortOnUpload());
            int groupedRemaining = batches.size() < 2 || !service.gpuRigidGroupsEnabled()
                    ? -1 : drawGroup(batches, packed(order));
            if (groupedRemaining >= 0) {
                if (groupedRemaining < order.size()) {
                    drawJava(original, batches, order, groupedRemaining);
                }
                return;
            }
            int remaining = 0;
            try (Scope scope = context.apply(original);
                 var merged = service.openMergedDraws(scope.context)) {
                if (merged != null) {
                    // A shared pass forbids buffer writes, so every pose is prepared before it opens.
                    prepareInOrder(batches, order, prepared);
                }
                while (remaining < order.size()) {
                    Face first = order.get(remaining);
                    // Allocate in submission order. A later budget rejection must not discard earlier GPU work.
                    if (prepared[first.batch()] == null) {
                        if (merged != null) {
                            break;
                        }
                        prepared[first.batch()] = service.prepareGpuDraw(batches.get(first.batch()).batch());
                        if (prepared[first.batch()] == null) {
                            break;
                        }
                    }
                    int count = 1;
                    while (remaining + count < order.size()) {
                        Face next = order.get(remaining + count);
                        if (next.batch() != first.batch() || next.face() != first.face() + count) {
                            break;
                        }
                        count++;
                    }
                    var result = service.drawPrepared(prepared[first.batch()], scope.context, first.face() * 6, count * 6,
                            merged);
                    if (result == GpuAccelerationBackend.DrawResult.UNSUPPORTED) {
                        break;
                    }
                    remaining += count;
                    if (result == GpuAccelerationBackend.DrawResult.ISSUED_BEFORE_FAILURE) {
                        break;
                    }
                }
            } catch (RuntimeException | LinkageError failure) {
                service.graphicsFailed(failure);
            }
            if (remaining < order.size()) {
                drawJava(original, batches, order, remaining);
            }
        }

        /**
         * Prepares batches in the order their faces first appear and stops at the first rejection, exactly where
         * the per-draw path would stop allocating; later faces then fall back to Java as before.
         */
        private void prepareInOrder(List<Queued> batches, List<Face> order, GpuAccelerationBackend.Prepared[] prepared) {
            for (Face face : order) {
                if (prepared[face.batch()] != null) {
                    continue;
                }
                prepared[face.batch()] = service.prepareGpuDraw(batches.get(face.batch()).batch());
                if (prepared[face.batch()] == null) {
                    return;
                }
            }
        }

        /** Returns -1 when the ordinary per-batch path should handle this material. */
        private int drawGroup(List<Queued> batches, long[] sorted) {
            if (batches.size() < 2 || !service.gpuRigidGroupsEnabled()) {
                return -1;
            }
            var prepared = service.prepareGpuGroup(batches.stream().map(Queued::batch).toList(), sorted);
            if (prepared == null) {
                return -1;
            }
            int remaining = 0;
            try (Scope scope = context.apply(original);
                 var merged = service.openMergedDraws(scope.context)) {
                // Group ranges are all prepared above, so they can share one pass.
                for (GpuAccelerationBackend.PreparedRange range : prepared) {
                    var result = service.drawPrepared(range.prepared(), scope.context, range.firstIndex(),
                            range.indexCount(), merged);
                    if (result == GpuAccelerationBackend.DrawResult.UNSUPPORTED) {
                        break;
                    }
                    remaining += range.faceCount();
                    if (result == GpuAccelerationBackend.DrawResult.ISSUED_BEFORE_FAILURE) {
                        break;
                    }
                }
            } catch (RuntimeException | LinkageError failure) {
                service.graphicsFailed(failure);
            }
            return remaining;
        }
    }

    /** Batch index in the high word and local face in the low word, in authored submission order. */
    private static long[] authoredOrder(List<Queued> batches) {
        int total = 0;
        for (Queued batch : batches) {
            total = Math.addExact(total, batch.batch().mesh().faceCount());
        }
        long[] order = new long[total];
        int index = 0;
        for (int batch = 0; batch < batches.size(); batch++) {
            int faceCount = batches.get(batch).batch().mesh().faceCount();
            for (int face = 0; face < faceCount; face++) {
                order[index++] = (long) batch << 32 | Integer.toUnsignedLong(face);
            }
        }
        return order;
    }

    private static long[] packed(List<Face> order) {
        long[] packed = new long[order.size()];
        for (int index = 0; index < order.size(); index++) {
            Face face = order.get(index);
            packed[index] = (long) face.batch() << 32 | Integer.toUnsignedLong(face.face());
        }
        return packed;
    }

    private List<Face> faces(List<Queued> batches, boolean sorted) {
        List<Face> faces = new ArrayList<>();
        for (int batch = 0; batch < batches.size(); batch++) {
            for (int face = 0; face < batches.get(batch).batch().mesh().faceCount(); face++) {
                faces.add(new Face(batch, face));
            }
        }
        if (!sorted) {
            return faces;
        }
        CompactVectorArray centers = new CompactVectorArray(faces.size());
        double[] first = new double[4];
        double[] third = new double[4];
        for (int index = 0; index < faces.size(); index++) {
            Face face = faces.get(index);
            DrawBatch batch = batches.get(face.batch()).batch();
            batch.pose().transformedPosition(batch.mesh(), face.face() * 4, first);
            batch.pose().transformedPosition(batch.mesh(), face.face() * 4 + 2, third);
            offset(first, batch.semantics().vertexOffset());
            offset(third, batch.semantics().vertexOffset());
            centers.set(index, ((float) first[0] + (float) third[0]) / 2,
                    ((float) first[1] + (float) third[1]) / 2, ((float) first[2] + (float) third[2]) / 2);
        }
        int[] sortedFaces = sortFaces(RenderSystem.getProjectionType().vertexSorting(), centers);
        List<Face> result = new ArrayList<>(faces.size());
        for (int index : sortedFaces) {
            result.add(faces.get(index));
        }
        return result;
    }

    /**
     * The same order as {@code sorting.sort(centers)} for vanilla's two projection sortings, without the boxed
     * comparator merge sort: vanilla stable-sorts face indices by descending key with {@code Floats.compare}, where
     * the key is {@code origin.distanceSquared(center)} (perspective) or {@code -center.z} (orthographic). Keys are
     * computed with the same JOML call and mapped to ints in {@link Float#compare} order; packing the inverted key
     * above the index makes a primitive ascending sort produce exactly that descending, index-stable order.
     * Any other sorting is delegated unchanged.
     */
    static int[] sortFaces(VertexSorting sorting, CompactVectorArray centers) {
        boolean perspective = sorting == VertexSorting.DISTANCE_TO_ORIGIN;
        if (!perspective && sorting != VertexSorting.ORTHOGRAPHIC_Z) {
            return sorting.sort(centers);
        }
        int count = centers.size();
        long[] packed = new long[count];
        Vector3f center = new Vector3f();
        for (int index = 0; index < count; index++) {
            centers.get(index, center);
            float key = perspective ? ORIGIN.distanceSquared(center) : -center.z();
            int bits = Float.floatToIntBits(key);
            int ordered = bits ^ ((bits >> 31) & 0x7FFFFFFF);
            packed[index] = (long) ~ordered << 32 | Integer.toUnsignedLong(index);
        }
        Arrays.sort(packed);
        int[] result = new int[count];
        for (int index = 0; index < count; index++) {
            result[index] = (int) packed[index];
        }
        return result;
    }

    private static final Vector3fc ORIGIN = new Vector3f();

    private void drawJava(RenderType material, List<Queued> batches, List<Face> sorted, int firstFace) {
        int vertices = sorted == null ? batches.stream().mapToInt(value -> value.batch().mesh().vertexCount()).sum()
                : (sorted.size() - firstFace) * 4;
        try (ByteBufferBuilder memory = new ByteBufferBuilder(Math.max(256, Math.multiplyExact(vertices, material.format().getVertexSize())))) {
            BufferBuilder output = new BufferBuilder(memory, material.mode(), material.format());
            if (sorted == null) {
                for (Queued batch : batches) {
                    if (service.isCurrent(batch.batch().lease())) {
                        batch.javaEmitter().emit(output, 0, batch.batch().mesh().vertexCount());
                    }
                }
            } else {
                for (int index = firstFace; index < sorted.size(); index++) {
                    Face face = sorted.get(index);
                    Queued batch = batches.get(face.batch());
                    if (service.isCurrent(batch.batch().lease())) {
                        batch.javaEmitter().emit(output, face.face() * 4, 4);
                    }
                }
            }
            MeshData geometry = output.build();
            if (geometry != null) {
                if (sorted == null && material.sortOnUpload()) {
                    geometry.sortQuads(memory, RenderSystem.getProjectionType().vertexSorting());
                }
                batches.getFirst().javaDraw().accept(geometry);
            }
        }
    }

    private static void offset(double[] point, double offset) {
        if (offset != 0) {
            double length = Math.sqrt(point[0] * point[0] + point[1] * point[1] + point[2] * point[2] + point[3] * point[3]);
            for (int component = 0; component < 3; component++) {
                point[component] += point[component] / length * offset;
            }
        }
    }

    static Scope openMinecraftScope(RenderType material) {
        RenderSystem.assertOnRenderThread();
        var stack = RenderSystem.getModelViewStack();
        var modifier = material.state.layeringTransform.getModifier();
        if (modifier != null) {
            stack.pushMatrix();
        }
        try {
            if (modifier != null) {
                modifier.accept(stack);
            }
            var transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrix(),
                    new Vector4f(1, 1, 1, 1), new Vector3f(), material.state.textureTransform.getMatrix());
            var target = material.outputTarget().getRenderTarget();
            var color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
            var depth = target.useDepth ? (RenderSystem.outputDepthTextureOverride != null
                    ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView()) : null;
            var scissor = RenderSystem.getScissorStateForRenderTypeDraws();
            GpuDrawContext context = new GpuDrawContext(color, depth, material.state.getTextures(), transform,
                    scissor.enabled(), scissor.x(), scissor.y(), scissor.width(), scissor.height());
            return new Scope(context, () -> {
                if (modifier != null) {
                    stack.popMatrix();
                }
            });
        } catch (RuntimeException | Error failure) {
            if (modifier != null) {
                stack.popMatrix();
            }
            throw failure;
        }
    }
}
