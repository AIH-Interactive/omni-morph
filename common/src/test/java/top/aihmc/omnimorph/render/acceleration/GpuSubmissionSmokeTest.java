package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.mojang.blaze3d.pipeline.RenderPipeline;

/** Executes real native BufferSource/RenderType flush boundaries with command-recording device operations. */
public final class GpuSubmissionSmokeTest {
    private static final Identifier TEXTURE = Identifier.parse("omni-morph:submission_contract");
    private static final GpuDrawContext CONTEXT = new GpuDrawContext(null, null, Map.of(), null, false, 0, 0, 0, 0);

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RenderSystem.initRenderThread();
        opaqueBoundary();
        // R4-2: the same orderings and failure semantics must hold with and without the shared pass.
        for (boolean mergePass : new boolean[]{false, true}) {
            System.setProperty(AccelerationService.MERGE_PASS_PROPERTY, Boolean.toString(mergePass));
            try {
                transparentBoundary(0);
                transparentBoundary(1);
                transparentBoundary(2);
                transparentBoundary(3);
                for (int failure = 0; failure < 6; failure++) {
                    groupedBoundary(failure);
                }
            } finally {
                System.clearProperty(AccelerationService.MERGE_PASS_PROPERTY);
            }
        }
        System.out.println("PASS R4-2 shared pass: identical face order, partial-draw and fallback semantics with merged passes");
        groupedCacheAndLifetimes();
        repeatedGroupedBackends();
        retirementBeforeFlush();
        diagnosticRecording();
        System.out.println("PASS actual BufferSource ordering, rigid palettes, bounded fallback, owner retirement and 100 backend cycles; GPU pixels MANUAL/PENDING");
    }

    private static void diagnosticRecording() throws Exception {
        Path recordingPath = Files.createTempFile("omnimorph-acceleration", ".jfr");
        try {
            try (Recording recording = new Recording()) {
                recording.enable(AccelerationFrameDiagnostics.FrameEvent.class).withoutStackTrace();
                recording.start();
                AccelerationFrameDiagnostics.beginFrame();
                groupedBoundary(0);
                AccelerationFrameDiagnostics.beginFrame();
                recording.stop();
                recording.dump(recordingPath);
            }
            var events = RecordingFile.readAllEvents(recordingPath);
            require(events.size() == 1, "one completed diagnostic frame");
            var event = events.getFirst();
            require(event.getLong("groupPrepareCalls") > 0 && event.getLong("rigidDataCreated") > 0,
                    "group preparation is counted");
            require(event.getLong("poseUploads") > 0 && event.getLong("poseBytes") > 0
                            && event.getLong("draws") > 0,
                    "actual pose uploads and GPU draws are counted");
            require(!event.getString("paletteSizes").equals("{}") && !event.getString("materials").equals("{}"),
                    "palette and material distributions are recorded");
        } finally {
            Files.deleteIfExists(recordingPath);
            AccelerationFrameDiagnostics.beginFrame();
        }
    }

    private static void opaqueBoundary() {
        try (Fixture fixture = new Fixture(); ByteBufferBuilder memory = new ByteBufferBuilder(256)) {
            MultiBufferSource.BufferSource source = MultiBufferSource.immediate(memory);
            RenderType material = RenderTypes.entityCutout(TEXTURE);
            fixture.offer(source, batch(fixture.service, fixture.owner, material, 1, 2));
            fixture.offer(source, batch(fixture.service, fixture.owner, material, 3));
            require(fixture.device.drawCalls == 0, "no GPU draw during model preparation");
            source.endBatch();
            require(fixture.device.drawn.equals(List.of(1f, 2f, 3f)) && fixture.javaFaces.isEmpty(),
                    "native material flush consumes GPU geometry in authored order exactly once");
            require(fixture.device.uploads == 2 && fixture.scopesOpened == fixture.scopesClosed,
                    "one geometry/pose per batch, balanced native transform scope");
        }
    }

    private static void transparentBoundary(int failure) {
        try (Fixture fixture = new Fixture(failure == 3 ? GpuBatchData.POSE_BYTES : 100_000);
             ByteBufferBuilder memory = new ByteBufferBuilder(256)) {
            RenderType material = RenderTypes.entityTranslucent(TEXTURE);
            // First encounter queues compilation; the next frame prepares it outside model traversal.
            fixture.gpu.supports(AccelerationBackend.Operation.GPU_DRAW, batch(fixture.service, fixture.owner, material, 1));
            fixture.service.beginFrame();
            fixture.submission.nextFrame();
            fixture.device.failBeforeAt = failure == 1 ? 1 : 0;
            fixture.device.failAfterAt = failure == 2 ? 1 : 0;
            MultiBufferSource.BufferSource source = MultiBufferSource.immediate(memory);
            fixture.offer(source, batch(fixture.service, fixture.owner, material, 1, 3));
            fixture.offer(source, batch(fixture.service, fixture.owner, material, 4, 2));
            source.endBatch();
            List<Float> total = new ArrayList<>(fixture.device.drawn);
            total.addAll(fixture.javaFaces);
            require(total.equals(List.of(4f, 3f, 2f, 1f)), "transparent face order matches native distance sorting across model batches");
            require(fixture.device.passesOpened == fixture.device.passesClosed, "every shared pass is closed");
            if (Boolean.getBoolean(AccelerationService.MERGE_PASS_PROPERTY) && failure == 0) {
                require(fixture.device.passesOpened == 1, "one boundary of one material draws inside a single pass");
            } else if (!Boolean.getBoolean(AccelerationService.MERGE_PASS_PROPERTY)) {
                require(fixture.device.passesOpened == 0, "without the flag no shared pass is opened");
            }
            if (failure == 0) {
                require(fixture.javaFaces.isEmpty() && fixture.device.drawCalls == 4, "all sorted GPU faces consumed directly");
            } else if (failure == 1) {
                require(fixture.device.drawn.isEmpty() && fixture.javaFaces.equals(List.of(4f, 3f, 2f, 1f)),
                        "pre-draw failure replays all unsubmitted faces through original Java material");
            } else if (failure == 2) {
                require(fixture.device.drawn.equals(List.of(4f)) && fixture.javaFaces.equals(List.of(3f, 2f, 1f)),
                        "post-issue failure does not duplicate an already submitted face");
            } else {
                require(fixture.device.drawn.equals(List.of(4f)) && fixture.javaFaces.equals(List.of(3f, 2f, 1f)),
                        "pose budget exhaustion retains submitted GPU faces and replays only remaining sorted faces");
            }
            require(fixture.scopesOpened == fixture.scopesClosed, "native model-view layering scope always restored");
        }
    }

    private static void retirementBeforeFlush() {
        for (boolean frameChange : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture(); ByteBufferBuilder memory = new ByteBufferBuilder(256)) {
                MultiBufferSource.BufferSource source = MultiBufferSource.immediate(memory);
                fixture.offer(source, batch(fixture.service, fixture.owner, RenderTypes.entityCutout(TEXTURE), 1));
                if (frameChange) {
                    fixture.submission.nextFrame();
                    fixture.service.beginFrame();
                } else {
                    fixture.owner.retire();
                }
                source.endBatch();
                require(fixture.device.drawCalls == 0 && fixture.javaFaces.isEmpty() && fixture.device.uploads == 0,
                        "queued old frame/owner never draws or allocates during a later flush");
            }
        }
    }

    private static void groupedBoundary(int failure) {
        var device = new GroupingDevice();
        try (Fixture fixture = new Fixture(failure == 3 ? 20_000 : 100_000, device);
             ByteBufferBuilder memory = new ByteBufferBuilder(256)) {
            RenderType material = RenderTypes.entityTranslucent(TEXTURE);
            fixture.gpu.supports(AccelerationBackend.Operation.GPU_DRAW, batch(fixture.service, fixture.owner, material, 1));
            fixture.service.beginFrame();
            fixture.submission.nextFrame();
            device.failBeforeAt = failure == 1 ? 1 : 0;
            device.failAfterAt = failure == 2 ? 1 : 0;
            device.failIndices = failure == 4;
            device.failPoseUpload = failure == 5;
            MultiBufferSource.BufferSource source = MultiBufferSource.immediate(memory);
            for (int depth = 1; depth <= 65; depth++) {
                DrawBatch original = batch(fixture.service, fixture.owner, material, depth);
                var pose = new PoseSnapshot(FiguraMat4.of().translate(0, 0, depth / 1024.0), FiguraMat3.of());
                fixture.offer(source, new DrawBatch(original.lease(), original.mesh(), pose, original.semantics()));
            }
            source.endBatch();
            List<Float> expected = new ArrayList<>();
            for (int depth = 65; depth > 0; depth--) {
                expected.add(depth + depth / 1024f);
            }
            List<Float> actual = new ArrayList<>(device.drawn);
            actual.addAll(fixture.javaFaces);
            require(actual.equals(expected), "grouped GPU and remaining Java retain every globally sorted face exactly once");
            if (failure == 0) {
                require(device.drawCalls == 2 && fixture.javaFaces.isEmpty() && device.uploads == 1,
                        "65 different poses use two draws and a single cached raw mesh");
            } else if (failure == 1) {
                require(device.drawn.isEmpty() && fixture.javaFaces.size() == 65,
                        "group failure before issue replays every face");
            } else if (failure == 2) {
                require(device.drawn.size() == 64 && fixture.javaFaces.size() == 1,
                        "group failure after issue skips the entire submitted range");
            } else if (failure == 3) {
                require(device.drawn.size() == 1 && fixture.javaFaces.size() == 64,
                        "insufficient group budget uses bounded per-batch preparation and sorted remaining fallback");
            } else {
                require(device.drawn.isEmpty() && fixture.javaFaces.size() == 65 && !fixture.service.gpuDrawEnabled(),
                        "group allocation failure fuses safely to Java before any face is issued");
            }
            require(fixture.scopesOpened == fixture.scopesClosed, "grouped material scope always restores transforms");
        }
    }

    private static void groupedCacheAndLifetimes() {
        var device = new GroupingDevice();
        device.complete = true;
        try (Fixture fixture = new Fixture(100_000, device); ByteBufferBuilder memory = new ByteBufferBuilder(256)) {
            RenderType material = RenderTypes.entityTranslucent(TEXTURE);
            fixture.gpu.supports(AccelerationBackend.Operation.GPU_DRAW, batch(fixture.service, fixture.owner, material, 1));
            MultiBufferSource.BufferSource source = MultiBufferSource.immediate(memory);
            for (int frame = 0; frame < 3; frame++) {
                fixture.service.beginFrame();
                fixture.submission.nextFrame();
                device.drawn.clear();
                List<Float> expected = new ArrayList<>();
                for (int depth = 1; depth <= 65; depth++) {
                    float authored = depth + (frame == 2 && depth == 1 ? .5f : 0);
                    float translation = frame == 1 ? 130 - 2 * depth : depth / 1024f;
                    DrawBatch original = batch(fixture.service, fixture.owner, material, authored);
                    var pose = new PoseSnapshot(FiguraMat4.of().translate(0, 0, translation), FiguraMat3.of());
                    fixture.offer(source, new DrawBatch(original.lease(), original.mesh(), pose, original.semantics()));
                    expected.add(authored + translation);
                }
                source.endBatch();
                expected.sort(java.util.Comparator.reverseOrder());
                require(device.drawn.equals(expected) && fixture.javaFaces.isEmpty(),
                        "pose changes rebuild sorted indices and preserve every face across cached frames");
                require(device.uploads == (frame == 2 ? 2 : 1),
                        "pose-only changes reuse geometry; direct geometry changes produce a new cached mesh");
            }
            device.drawn.clear();
            fixture.offer(source, batch(fixture.service, fixture.owner, material, 1));
            fixture.owner.retire();
            source.endBatch();
            fixture.service.beginFrame();
            require(device.drawn.isEmpty() && device.liveBytes == 0 && fixture.gpu.residentBytes() == 0,
                    "retired owner rejects pending faces and fences every combined mesh, pose and index buffer");
        }
    }

    private static void repeatedGroupedBackends() {
        for (int cycle = 0; cycle < 100; cycle++) {
            var device = new GroupingDevice();
            device.complete = true;
            try (Fixture fixture = new Fixture(100_000, device); ByteBufferBuilder memory = new ByteBufferBuilder(256)) {
                RenderType material = RenderTypes.entityCutout(TEXTURE);
                MultiBufferSource.BufferSource source = MultiBufferSource.immediate(memory);
                fixture.offer(source, batch(fixture.service, fixture.owner, material, 1));
                fixture.offer(source, batch(fixture.service, fixture.owner, material, 2));
                source.endBatch();
                require(device.drawCalls == 1 && device.uploads == 1 && fixture.javaFaces.isEmpty(),
                        "each backend cycle submits a real grouped command before releasing it");
            }
            require(device.liveBytes == 0, "100 backend enable/close cycles retain no GPU resource records");
        }
    }

    private static DrawBatch batch(AccelerationService service, AccelerationOwner owner, RenderType material, double... depths) {
        double[] vertices = new double[depths.length * 4 * 9];
        for (int face = 0; face < depths.length; face++) {
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = (face * 4 + vertex) * 9;
                vertices[offset] = vertex == 1 || vertex == 2 ? 0.01 : 0;
                vertices[offset + 1] = vertex >= 2 ? 0.01 : 0;
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

    private static class RecordingDevice extends GpuAccelerationSmokeTest.FakeDevice {
        final List<Float> drawn = new ArrayList<>();
        int attempts;
        int failBeforeAt;
        int failAfterAt;
        int passesOpened;
        int passesClosed;

        @Override
        public DrawPass openDrawPass(Material material, GpuDrawContext context) {
            passesOpened++;
            DrawPass delegate = super.openDrawPass(material, context);
            return new DrawPass() {
                @Override
                public Material material() {
                    return material;
                }

                @Override
                public void draw(Mesh mesh, Pose pose, Indices indices, int firstIndex, int indexCount, Runnable issuingDraw) {
                    delegate.draw(mesh, pose, indices, firstIndex, indexCount, issuingDraw);
                }

                @Override
                public void close() {
                    passesClosed++;
                    delegate.close();
                }
            };
        }

        @Override
        public void draw(Material material, Mesh mesh, Pose pose, GpuDrawContext context,
                         int firstIndex, int indexCount, Runnable issuingDraw) {
            attempts++;
            if (attempts == failBeforeAt) {
                throw new IllegalStateException("injected draw-boundary failure before issue");
            }
            require(firstIndex % 6 == 0 && indexCount % 6 == 0, "GPU ranges preserve complete authored quads");
            issuingDraw.run();
            drawCalls++;
            ByteBuffer geometry = ((GpuAccelerationSmokeTest.FakeResource) mesh).data;
            for (int face = firstIndex / 6; face < (firstIndex + indexCount) / 6; face++) {
                drawn.add(geometry.getFloat(face * 4 * GpuBatchData.VERTEX_BYTES + 8));
            }
            if (attempts == failAfterAt) {
                throw new IllegalStateException("injected draw-boundary failure after issue");
            }
        }
    }

    private static final class GroupingDevice extends RecordingDevice {
        boolean failIndices;
        @Override
        public boolean supportsRigidGroups() {
            return true;
        }

        @Override
        public Material compileRigidGroup(RenderPipeline original) {
            return compile(original);
        }

        @Override
        public Indices uploadIndices(ByteBuffer data) {
            if (failIndices) {
                throw new IllegalStateException("injected sorted index allocation failure");
            }
            return new GpuAccelerationSmokeTest.FakeResource(this, data, data.remaining());
        }

        @Override
        public void draw(Material material, Mesh mesh, Pose pose, GpuDrawContext context,
                         int firstIndex, int indexCount, Runnable issuingDraw) {
            drawWithIndices(material, mesh, pose, null, context, firstIndex, indexCount, issuingDraw);
        }

        @Override
        public void drawWithIndices(Material material, Mesh mesh, Pose pose, Indices indices, GpuDrawContext context,
                                    int firstIndex, int indexCount, Runnable issuingDraw) {
            attempts++;
            if (attempts == failBeforeAt) {
                throw new IllegalStateException("injected group failure before issue");
            }
            issuingDraw.run();
            drawCalls++;
            ByteBuffer geometry = ((GpuAccelerationSmokeTest.FakeResource) mesh).data;
            ByteBuffer matrices = ((GpuAccelerationSmokeTest.FakeResource) pose).data;
            ByteBuffer sorted = indices == null ? null : ((GpuAccelerationSmokeTest.FakeResource) indices).data;
            for (int face = firstIndex / 6; face < (firstIndex + indexCount) / 6; face++) {
                int vertex = sorted == null ? face * 4 : sorted.getInt(face * 6 * Integer.BYTES);
                int offset = vertex * GpuBatchData.VERTEX_BYTES;
                int matrix = 0;
                if (sorted != null) {
                    int batch = geometry.getInt(offset + 48);
                    int packed = matrices.getInt(GpuRigidData.REMAP_OFFSET + batch / 4 * Integer.BYTES);
                    matrix = (packed >>> ((batch % 4) * 8) & 0xff) * GpuBatchData.PART_BYTES;
                }
                float depth = matrices.getFloat(matrix + 8) * geometry.getFloat(offset)
                        + matrices.getFloat(matrix + 24) * geometry.getFloat(offset + 4)
                        + matrices.getFloat(matrix + 40) * geometry.getFloat(offset + 8) + matrices.getFloat(matrix + 56);
                drawn.add(depth);
            }
            if (attempts == failAfterAt) {
                throw new IllegalStateException("injected group failure after issue");
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final RecordingDevice device;
        final GpuRetirement retirement = new GpuRetirement();
        final GpuAccelerationBackend gpu;
        final AccelerationService service = new AccelerationService();
        final AccelerationOwner owner = new AccelerationOwner();
        final List<Float> javaFaces = new ArrayList<>();
        final GpuSubmission submission;
        int scopesOpened;
        int scopesClosed;

        Fixture() {
            this(100_000);
        }

        Fixture(long poseBudget) {
            this(poseBudget, new RecordingDevice());
        }

        Fixture(long poseBudget, RecordingDevice device) {
            this.device = device;
            gpu = new GpuAccelerationBackend(device, retirement, 1_000_000, poseBudget);
            require(!gpu.initializeStep() && !gpu.initializeStep() && gpu.initializeStep(), "GPU contract fixture initialization");
            service.useBackend(gpu);
            submission = new GpuSubmission(service, material -> {
                scopesOpened++;
                return new GpuSubmission.Scope(CONTEXT, () -> scopesClosed++);
            }, (material, geometry) -> {
                try (geometry) {
                    ByteBuffer data = geometry.vertexBuffer();
                    ByteBuffer indices = geometry.indexBuffer();
                    int stride = geometry.drawState().format().getVertexSize();
                    for (int face = 0; face < geometry.drawState().vertexCount() / 4; face++) {
                        int index = face * 4;
                        if (indices != null) {
                            index = geometry.drawState().indexType() == VertexFormat.IndexType.INT
                                    ? indices.getInt(face * 6 * 4) : Short.toUnsignedInt(indices.getShort(face * 6 * 2));
                        }
                        javaFaces.add(data.getFloat(index * stride + 8));
                    }
                }
            });
        }

        void offer(MultiBufferSource source, DrawBatch batch) {
            double[][] transformed = {null};
            require(submission.offer(source, batch, (vertices, first, count) -> {
                if (transformed[0] == null) {
                    transformed[0] = service.transform(batch.lease(), ignored -> batch);
                }
                if (transformed[0] == null) {
                    return;
                }
                for (int vertex = first; vertex < first + count; vertex++) {
                    int offset = vertex * 9;
                    vertices.addVertex((float) transformed[0][offset], (float) transformed[0][offset + 1], (float) transformed[0][offset + 2])
                            .setColor(1f, 1f, 1f, 1f).setUv(0, 0).setOverlay(0).setLight(0).setNormal(0, 1, 0);
                }
            }), "common public batch accepted by actual BufferSource boundary");
        }

        @Override
        public void close() {
            service.useBackend(null);
            device.complete = true;
            retirement.poll();
            require(device.liveBytes == 0, "draw-boundary fixture releases all GPU resource records");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
