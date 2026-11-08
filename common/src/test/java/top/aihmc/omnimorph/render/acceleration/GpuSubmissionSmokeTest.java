package top.aihmc.omnimorph.render.acceleration;

import top.aihmc.omnimorph.render.RenderOwner;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.joml.Matrix4f;

import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** 1.21.1 MVP: rejected GPU submissions retain the caller's real native Java emission path. */
public final class GpuSubmissionSmokeTest {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("omni-morph:submission_contract");

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RenderSystem.initRenderThread();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.DISTANCE_TO_ORIGIN);
        opaqueBoundary();
        for (boolean merged : new boolean[] {false, true}) {
            AccelerationOptions.setMergePasses(merged);
            try {
                transparentBoundary(VertexSorting.DISTANCE_TO_ORIGIN, List.of(4f, 3f, 2f, 1f));
                transparentBoundary(VertexSorting.ORTHOGRAPHIC_Z, List.of(1f, 2f, 3f, 4f));
                groupedPoses();
            } finally {
                AccelerationOptions.setMergePasses(false);
            }
        }
        staleOwnership();
        queuedBoundary();
        for (int cycle = 0; cycle < 100; cycle++) {
            opaqueBoundary();
        }
        diagnosticRecording();
        System.out.println("PASS unsupported-source fallback, GPU queue acceptance/flush, mixed native sorting, stale leases, 65 poses and 100 buffer cycles; standalone fixture does not install Mixins");
    }

    private static void queuedBoundary() {
        var service = AccelerationService.INSTANCE;
        var owner = new RenderOwner();
        var material = new RecordingType(true);
        int[] gpuDraws = {0};
        service.useBackend(new AccelerationBackend() {
            public boolean supports(Operation operation, DrawBatch batch) {
                return operation == Operation.GPU_DRAW;
            }
            public double[] transform(DrawBatch batch) {
                return new JavaAccelerationBackend().transform(batch);
            }
            public boolean drawBatches(List<DrawBatch> batches) {
                gpuDraws[0] += batches.size();
                return true;
            }
        });
        try (var memory = new ByteBufferBuilder(256)) {
            var queue = new GpuBufferQueue();
            // Supply the accessor normally installed by Mixin in the plain JVM fixture.
            class Builder extends com.mojang.blaze3d.vertex.BufferBuilder
                    implements top.aihmc.omnimorph.mixin.render.GpuBufferBuilderAccessor {
                private int count;
                Builder() {
                    super(memory, material.mode(), material.format());
                }
                public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) {
                    count++;
                    return super.addVertex(x, y, z);
                }
                public int omniMorph$vertexCount() {
                    return count;
                }
            }
            var builder = new Builder();
            class Source implements MultiBufferSource, GpuBufferQueue.Owner {
                public GpuBufferQueue omniMorph$gpuQueue() {
                    return queue;
                }
                public com.mojang.blaze3d.vertex.VertexConsumer getBuffer(RenderType type) {
                    queue.emitNative(this, type);
                    return builder;
                }
            }
            var source = new Source();
            DrawBatch first = batch(service, owner, material, 1);
            DrawBatch second = batch(service, owner, material, 3);
            int[] fallbacks = {0};
            GpuSubmission.JavaEmitter fallback = (vertices, start, count) -> {
                fallbacks[0]++;
                for (int index = 0; index < count; index++) {
                    vertices.addVertex(0, 0, fallbacks[0] == 1 ? 1 : 3).setColor(-1)
                            .setUv(0, 0).setOverlay(0).setLight(0).setNormal(0, 1, 0);
                }
            };
            require(GpuSubmission.enqueue(source, first, fallback)
                    && GpuSubmission.enqueue(source, second, fallback), "both engine batches queue without early draw");
            require(gpuDraws[0] == 0 && fallbacks[0] == 0, "preparation never flushes a material");
            queue.flush(material);
            require(gpuDraws[0] == 2 && fallbacks[0] == 0, "one boundary submits both batches exactly once");
            require(GpuSubmission.enqueue(source, first, fallback)
                    && GpuSubmission.enqueue(source, second, fallback), "next GPU-only group accepted");
            source.getBuffer(material); // Native caller: replay before any later native vertices.
            require(fallbacks[0] == 2, "mixed native material falls back as one sort group");
            var mesh = builder.buildOrThrow();
            mesh.sortQuads(memory, VertexSorting.DISTANCE_TO_ORIGIN);
            material.draw(mesh);
            queue.flush(material);
            require(material.depths.equals(List.of(3f, 1f)) && gpuDraws[0] == 2,
                    "mixed fallback keeps global native quad order without duplicate GPU draws");
            material.requireReleased();
            var stale = batch(service, owner, material, 5);
            queue.add(stale, fallback);
            owner.retire();
            queue.flush(material);
            require(gpuDraws[0] == 2 && fallbacks[0] == 2,
                    "retired queued owner is discarded without GPU draw or Java replay");
        } finally {
            service.useBackend(null);
        }
    }

    private static void opaqueBoundary() {
        var service = new AccelerationService();
        var owner = new RenderOwner();
        var material = new RecordingType(false);
        try (var memory = new ByteBufferBuilder(256)) {
            var source = MultiBufferSource.immediate(memory);
            offer(service, source, batch(service, owner, material, 1, 2));
            offer(service, source, batch(service, owner, material, 3));
            require(material.draws == 0, "model preparation does not flush native geometry early");
            source.endBatch();
            require(material.draws == 1 && material.depths.equals(List.of(1f, 2f, 3f)),
                    "native opaque flush emits every authored face exactly once in order");
            material.requireReleased();
        }
    }

    private static void transparentBoundary(VertexSorting sorting, List<Float> expected) {
        RenderSystem.setProjectionMatrix(new Matrix4f(), sorting);
        var service = new AccelerationService();
        var owner = new RenderOwner();
        var material = new RecordingType(true);
        try (var memory = new ByteBufferBuilder(256)) {
            var source = MultiBufferSource.immediate(memory);
            offer(service, source, batch(service, owner, material, 1, 3));
            offer(service, source, batch(service, owner, material, 4, 2));
            source.endBatch();
            require(material.draws == 1 && material.depths.equals(expected),
                    "native translucent sorting spans rejected submissions without missing or duplicated faces");
            material.requireReleased();
        }
    }

    private static void groupedPoses() {
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.DISTANCE_TO_ORIGIN);
        var service = new AccelerationService();
        var owner = new RenderOwner();
        for (int frame = 0; frame < 3; frame++) {
            service.beginFrame();
            var material = new RecordingType(true);
            var expected = new ArrayList<Float>();
            try (var memory = new ByteBufferBuilder(256)) {
                var source = MultiBufferSource.immediate(memory);
                for (int depth = 1; depth <= 65; depth++) {
                    float authored = depth + (frame == 2 && depth == 1 ? .5f : 0);
                    float translation = frame == 1 ? 130 - 2 * depth : depth / 1024f;
                    var original = batch(service, owner, material, authored);
                    var pose = new PoseSnapshot(FiguraMat4.of().translate(0, 0, translation), FiguraMat3.of());
                    offer(service, source, new DrawBatch(original.lease(), original.mesh(), pose, original.semantics()));
                    expected.add(authored + translation);
                }
                source.endBatch();
                expected.sort(java.util.Comparator.reverseOrder());
                require(material.depths.equals(expected), "65 independent poses and geometry edits preserve all sorted faces");
                material.requireReleased();
            }
        }
    }

    private static void staleOwnership() {
        for (int invalidation = 0; invalidation < 4; invalidation++) {
            var service = new AccelerationService();
            var owner = new RenderOwner();
            var material = new RecordingType(false);
            var captured = batch(service, owner, material, 1);
            switch (invalidation) {
                case 0 -> owner.retire();
                case 1 -> service.beginFrame();
                case 2 -> service.invalidateResources();
                case 3 -> service.useBackend(null);
                default -> throw new AssertionError("unexpected invalidation");
            }
            try (var memory = new ByteBufferBuilder(256)) {
                var source = MultiBufferSource.immediate(memory);
                offer(service, source, captured);
                source.endBatch();
                require(material.draws == 0 && material.depths.isEmpty(),
                        "caller rejects retired owner/frame/resource/backend leases before native emission");
            }
        }
    }

    private static void offer(AccelerationService service, MultiBufferSource source, DrawBatch batch) {
        var material = (RecordingType) batch.semantics().material();
        int[] calls = {0};
        GpuSubmission.JavaEmitter emitter = (consumer, first, count) -> {
            calls[0]++;
            var transformed = service.transform(batch.lease(), ignored -> batch);
            require(transformed != null, "only current batches reach the caller's emitter");
            require(first == 0 && count == batch.mesh().vertexCount(), "MVP fallback retains the complete batch");
            for (int vertex = first; vertex < first + count; vertex++) {
                int offset = vertex * MeshHandle.STRIDE;
                consumer.addVertex((float) transformed[offset], (float) transformed[offset + 1], (float) transformed[offset + 2])
                        .setColor(255, 255, 255, 255)
                        .setUv((float) transformed[offset + 7], (float) transformed[offset + 8])
                        .setOverlay(0).setLight(0)
                        .setNormal((float) transformed[offset + 4], (float) transformed[offset + 5], (float) transformed[offset + 6]);
            }
        };
        int draws = material.draws;
        require(!service.gpuDrawEnabled() && !GpuSubmission.enqueue(source, batch, emitter),
                "1.21.1 does not accept a GPU submission");
        require(calls[0] == 0 && material.draws == draws, "rejection does not consume the caller's fallback or flush material");
        if (service.isCurrent(batch.lease())) {
            emitter.emit(source.getBuffer(material), 0, batch.mesh().vertexCount());
            require(calls[0] == 1, "the caller emits the entire batch once");
        }
    }

    private static void diagnosticRecording() throws Exception {
        var path = Files.createTempFile("omnimorph-mvp-submission", ".jfr");
        try {
            try (var recording = new Recording()) {
                recording.enable(AccelerationFrameDiagnostics.FrameEvent.class).withoutStackTrace();
                recording.start();
                AccelerationFrameDiagnostics.beginFrame();
                opaqueBoundary();
                AccelerationFrameDiagnostics.beginFrame();
                recording.stop();
                recording.dump(path);
            }
            var events = RecordingFile.readAllEvents(path);
            require(events.size() == 1, "one completed diagnostic frame");
            var event = events.getFirst();
            require(event.getLong("draws") == 0 && event.getLong("poseUploads") == 0
                            && event.getLong("poseBytes") == 0 && event.getLong("passes") == 0,
                    "native CPU output does not masquerade as GPU work in diagnostics");
        } finally {
            Files.deleteIfExists(path);
            AccelerationFrameDiagnostics.beginFrame();
        }
    }

    private static DrawBatch batch(AccelerationService service, RenderOwner owner, RenderType material, double... depths) {
        double[] vertices = new double[depths.length * 4 * MeshHandle.STRIDE];
        for (int face = 0; face < depths.length; face++) {
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = (face * 4 + vertex) * MeshHandle.STRIDE;
                vertices[offset] = vertex == 1 || vertex == 2 ? .01 : 0;
                vertices[offset + 1] = vertex >= 2 ? .01 : 0;
                vertices[offset + 2] = depths[face];
                vertices[offset + 3] = 1;
                vertices[offset + 5] = 1;
            }
        }
        var semantics = new DrawSemantics(material, TEXTURE, DrawSemantics.Layer.PRIMARY, "world", 0,
                depths.length, 0, 0, 1, 1, 1, 1, 0, true);
        return new DrawBatch(service.capture(owner, "world"), new MeshHandle(vertices, 4),
                new PoseSnapshot(FiguraMat4.of(), FiguraMat3.of()), semantics);
    }

    /** The real BufferSource builds/sorts MeshData; only the final GPU draw is replaced by inspection. */
    private static final class RecordingType extends RenderType {
        private final List<Float> depths = new ArrayList<>();
        private final List<MeshData> meshes = new ArrayList<>();
        private int draws;

        private RecordingType(boolean translucent) {
            super("mvp_submission", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    256, false, translucent, () -> {}, () -> {});
        }

        @Override
        public void draw(MeshData mesh) {
            try (mesh) {
                draws++;
                meshes.add(mesh);
                var vertices = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                var indices = mesh.indexBuffer();
                if (indices != null) {
                    indices.order(ByteOrder.nativeOrder());
                }
                int stride = mesh.drawState().format().getVertexSize();
                for (int face = 0; face < mesh.drawState().vertexCount() / 4; face++) {
                    int firstVertex = face * 4;
                    if (indices != null) {
                        int index = face * 6;
                        firstVertex = mesh.drawState().indexType() == VertexFormat.IndexType.SHORT
                                ? Short.toUnsignedInt(indices.getShort(index * Short.BYTES))
                                : indices.getInt(index * Integer.BYTES);
                    }
                    depths.add(vertices.getFloat(firstVertex * stride + 8));
                }
            }
        }

        private void requireReleased() {
            for (var mesh : meshes) {
                try {
                    mesh.vertexBuffer();
                    throw new AssertionError("flushed native mesh must be released");
                } catch (IllegalStateException expected) {
                    // ByteBufferBuilder.Result rejects access after MeshData.close().
                }
            }
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
