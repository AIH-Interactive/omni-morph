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
import top.aihmc.omnimorph.math.matrix.FiguraMat3;
import top.aihmc.omnimorph.math.matrix.FiguraMat4;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Executes real native BufferSource/RenderType flush boundaries with command-recording device operations. */
public final class GpuSubmissionSmokeTest {
    private static final Identifier TEXTURE = Identifier.parse("omni-morph:submission_contract");
    private static final GpuDrawContext CONTEXT = new GpuDrawContext(null, null, Map.of(), null, false, 0, 0, 0, 0);

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RenderSystem.initRenderThread();
        opaqueBoundary();
        transparentBoundary(0);
        transparentBoundary(1);
        transparentBoundary(2);
        retirementBeforeFlush();
        System.out.println("PASS H5 actual BufferSource flush, cross-batch authored-face sorting, pre/post-draw remaining-only Java fallback and stale queued rejection; GPU pixels MANUAL/PENDING");
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
        try (Fixture fixture = new Fixture(); ByteBufferBuilder memory = new ByteBufferBuilder(256)) {
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
            if (failure == 0) {
                require(fixture.javaFaces.isEmpty() && fixture.device.drawCalls == 4, "all sorted GPU faces consumed directly");
            } else if (failure == 1) {
                require(fixture.device.drawn.isEmpty() && fixture.javaFaces.equals(List.of(4f, 3f, 2f, 1f)),
                        "pre-draw failure replays all unsubmitted faces through original Java material");
            } else {
                require(fixture.device.drawn.equals(List.of(4f)) && fixture.javaFaces.equals(List.of(3f, 2f, 1f)),
                        "post-issue failure does not duplicate an already submitted face");
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

    private static final class RecordingDevice extends GpuAccelerationSmokeTest.FakeDevice {
        final List<Float> drawn = new ArrayList<>();
        int attempts;
        int failBeforeAt;
        int failAfterAt;

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

    private static final class Fixture implements AutoCloseable {
        final RecordingDevice device = new RecordingDevice();
        final GpuRetirement retirement = new GpuRetirement();
        final GpuAccelerationBackend gpu = new GpuAccelerationBackend(device, retirement, 1_000_000, 100_000);
        final AccelerationService service = new AccelerationService();
        final AccelerationOwner owner = new AccelerationOwner();
        final List<Float> javaFaces = new ArrayList<>();
        final GpuSubmission submission;
        int scopesOpened;
        int scopesClosed;

        Fixture() {
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
