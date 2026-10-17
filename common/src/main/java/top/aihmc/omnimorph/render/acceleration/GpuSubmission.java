package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.CompactVectorArray;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
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
            return false;
        }
        if (!service.isCurrent(batch.lease())) {
            return true;
        }
        Map<RenderType, Boundary> boundaries = sources.computeIfAbsent(nativeSource, ignored -> new HashMap<>());
        Boundary boundary = boundaries.get(material);
        if (boundary == null) {
            if (boundaries.size() >= 256) {
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
                for (Queued batch : batches) {
                    drawBatches(List.of(batch));
                }
            } else {
                // A shared translucent material must retain its global face order, including local fallback.
                drawBatches(batches);
            }
        }

        private void drawBatches(List<Queued> batches) {
            var prepared = new ArrayList<GpuAccelerationBackend.Prepared>(batches.size());
            for (Queued batch : batches) {
                var draw = service.prepareGpuDraw(batch.batch());
                if (draw == null) {
                    drawJava(original, batches, null, 0);
                    return;
                }
                prepared.add(draw);
            }
            List<Face> order = faces(batches, original.sortOnUpload());
            int remaining = 0;
            try (Scope scope = context.apply(original)) {
                while (remaining < order.size()) {
                    Face first = order.get(remaining);
                    int count = 1;
                    while (remaining + count < order.size()) {
                        Face next = order.get(remaining + count);
                        if (next.batch() != first.batch() || next.face() != first.face() + count) {
                            break;
                        }
                        count++;
                    }
                    var result = service.drawPrepared(prepared.get(first.batch()), scope.context, first.face() * 6, count * 6);
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
        int[] sortedFaces = RenderSystem.getProjectionType().vertexSorting().sort(centers);
        List<Face> result = new ArrayList<>(faces.size());
        for (int index : sortedFaces) {
            result.add(faces.get(index));
        }
        return result;
    }

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
