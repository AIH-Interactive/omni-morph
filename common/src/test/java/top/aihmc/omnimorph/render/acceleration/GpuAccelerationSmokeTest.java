package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Fast CPU contract tests. Fake device command records are explicitly not GPU/visual evidence. */
public final class GpuAccelerationSmokeTest {
    private static final GpuDrawContext CONTEXT = new GpuDrawContext(null, null, Map.of(), null, false, 0, 0, 0, 0);

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        shaderContract();
        cacheAndRetirement();
        boundsAndLifetimes();
        failureRouting(false);
        failureRouting(true);
        initializationFailure();
        allocationAndDeviceFailure();
        authoredGroupIndexReuse();
        System.out.println("PASS H4 CPU contracts: raw cached geometry, frame poses, material/shader recipe, bounds, generations, fences and partial-draw fallback; real GPU pixels MANUAL/PENDING");
    }

    private static void shaderContract() throws Exception {
        try (var source = GpuAccelerationSmokeTest.class.getClassLoader().getResourceAsStream("assets/minecraft/shaders/core/entity.vsh")) {
            require(source != null, "actual Minecraft entity shader available");
            String original = new String(source.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String wrapped = GpuShaderProgram.wrapEntitySource(original);
            require(wrapped.contains("omnimorph_original_main()") && wrapped.contains("OmniParts[64]")
                    && wrapped.contains("minecraft_mix_light") && wrapped.contains("fog_spherical_distance")
                    && wrapped.contains("UV1 = OmniOverlayLight.xy"), "active material lighting/fog/overlay retained");
            String grouped = GpuShaderProgram.wrapRigidEntitySource(original);
            // GLSL reserves "packed" as a layout keyword, even for local integer declarations.
            require(!java.util.regex.Pattern.compile("\\bint\\s+packed\\b").matcher(grouped).find(),
                    "rigid shader must not declare the GLSL layout keyword packed as a variable");
            require(grouped.contains("ivec4 OmniRemap[256]") && grouped.contains("OmniParts[part].normal2.w")
                    && grouped.contains("minecraft_mix_light") && grouped.contains("fog_spherical_distance"),
                    "rigid palette shader keeps original lighting and uses per-part options");
            try {
                GpuShaderProgram.wrapEntitySource(original.replace("in vec3 Normal;", "in vec2 ChangedNormal;"));
                throw new AssertionError("changed shader input must reject");
            } catch (IllegalArgumentException expected) {
                // An incompatible resource-pack shader cannot be silently treated as the stock contract.
            }
        }
        RenderPipeline original = RenderPipelines.ENTITY_CUTOUT_CULL;
        RenderPipeline accelerated = GpuShaderProgram.pipeline(original, Identifier.parse("omni-morph:gpu_contract"));
        require(accelerated.getVertexFormat().getVertexSize() == GpuBatchData.VERTEX_BYTES, "actual VertexFormat stride");
        require(accelerated.isCull() == original.isCull()
                && accelerated.getColorTargetState().equals(original.getColorTargetState())
                && accelerated.getDepthStencilState().equals(original.getDepthStencilState())
                && accelerated.getShaderDefines().equals(original.getShaderDefines())
                && accelerated.getFragmentShader().equals(original.getFragmentShader()), "material state/defines/fragment preserved");
        require(accelerated.getUniforms().stream().anyMatch(value -> value.name().equals(GpuShaderProgram.UNIFORM)), "actual matrix uniform declared");
    }

    private static void cacheAndRetirement() {
        var device = new FakeDevice();
        var retirement = new GpuRetirement();
        var gpu = backend(device, retirement, 1_000_000);
        var service = new AccelerationService();
        AtomicInteger units = new AtomicInteger();
        var controller = new AccelerationController(service, mode -> {
            units.incrementAndGet();
            if (!gpu.initializeStep()) {
                throw new AccelerationController.ProbePending();
            }
            return new AccelerationController.Activation(gpu, CapabilityReport.Effective.GPU, 0);
        }, ignored -> {});
        controller.request(true, CapabilityReport.Mode.GPU);
        for (int step = 0; step < 3; step++) {
            controller.beginFrame();
            require(controller.report().state() == (step < 2 ? CapabilityReport.State.PROBING : CapabilityReport.State.READY),
                    "bounded GPU probing does not advertise premature readiness");
            service.beginFrame();
        }
        require(units.get() == 3 && device.selfTests == 1 && device.compiles == 2, "three bounded initialization units");
        var owner = new AccelerationOwner();
        MeshHandle first = quad(1);
        DrawBatch batch = batch(service, owner, first, 5, material());
        require(service.tryDraw(batch, CONTEXT) == GpuAccelerationBackend.DrawResult.DRAWN, "actual shared draw operation routed");
        require(device.lastMesh.data.getFloat(0) == 1 && device.lastPose.data.getFloat(12 * 4) == 5,
                "GPU receives untransformed geometry plus separate position matrix");
        int parameters = GpuBatchData.PARAMETER_OFFSET;
        require(device.lastPose.data.getFloat(parameters) == 127 / 255f
                && device.lastPose.data.getInt(parameters + 16) == 0
                && device.lastPose.data.getInt(parameters + 20) == 10, "tint quantization and overlay preserved");
        require(service.tryDraw(batch(service, owner, quad(1), 7, material()), CONTEXT) == GpuAccelerationBackend.DrawResult.DRAWN,
                "equivalent immutable mesh with a changed pose");
        require(gpu.geometryUploads() == 1 && gpu.cacheHits() == 1 && device.lastPose.data.getFloat(12 * 4) == 7,
                "pose changes do not re-upload static geometry");
        require(service.tryDraw(batch(service, owner, quad(2), 7, material()), CONTEXT) == GpuAccelerationBackend.DrawResult.DRAWN
                && gpu.geometryUploads() == 2, "dynamic geometry obtains a new cache entry");
        FakeResource oldPose = device.lastPose;
        service.beginFrame();
        require(retirement.bytes() > 0 && !oldPose.closed && device.liveBytes > 0, "in-flight poses retained behind a fence");
        owner.retire();
        service.beginFrame();
        require(gpu.residentBytes() == 0 && retirement.batches() > 0, "retired model stops owning active geometry");
        device.complete = true;
        retirement.poll();
        require(device.liveBytes == 0 && retirement.bytes() == 0, "fence completion releases every geometry and pose exactly once");
        controller.request(false, CapabilityReport.Mode.GPU);
        controller.beginFrame();
    }

    private static void boundsAndLifetimes() {
        var device = new FakeDevice();
        var retirement = new GpuRetirement();
        var gpu = ready(device, retirement, 1_000_000);
        var service = new AccelerationService();
        service.useBackend(gpu);
        var owner = new AccelerationOwner();
        DrawBatch stale = batch(service, owner, quad(1), 0, material());
        service.beginFrame();
        require(service.tryDraw(stale, CONTEXT) == GpuAccelerationBackend.DrawResult.STALE && device.uploads == 0,
                "old frame cannot allocate or draw");
        DrawBatch translucent = batch(service, owner, quad(1), 0,
                RenderTypes.entityTranslucent(Identifier.parse("omni-morph:contract_texture")));
        require(service.tryDraw(translucent, CONTEXT) == GpuAccelerationBackend.DrawResult.UNSUPPORTED,
                "H4 unsupported transparent sorting stays Java");
        var five = new MeshHandle.Influences(new int[]{0, 1, 2, 3, 4}, new float[]{1, 1, 1, 1, 1});
        MeshHandle weighted = new MeshHandle(quad(1).copyVertices(), 4, List.of(five, five, five, five));
        DrawBatch heavyWeights = new DrawBatch(service.capture(owner, "world"), weighted,
                new PoseSnapshot(FiguraMat4.of(), FiguraMat3.of(), FiguraMat3.of(), new FiguraMat4[]{
                        FiguraMat4.of(), FiguraMat4.of(), FiguraMat4.of(), FiguraMat4.of(), FiguraMat4.of()}), translucent.semantics());
        require(!GpuBatchData.supports(heavyWeights), "five weights fallback without truncation");
        PoseStack vanilla = new PoseStack();
        require(new PoseSnapshot(vanilla.last()).normalPolicy() == 0, "trusted vanilla normals remain raw");
        vanilla.scale(-2, 0.5f, 3);
        require(new PoseSnapshot(vanilla.last()).normalPolicy() == 1, "untrusted vanilla normals normalize");
        double[] bad = quad(1).copyVertices();
        bad[0] = Double.POSITIVE_INFINITY;
        require(!GpuBatchData.supports(batch(service, owner, new MeshHandle(bad, 4), 0, material())), "non-finite geometry rejects");
        DrawBatch overflow = new DrawBatch(service.capture(owner, "world"), quad(Float.MAX_VALUE),
                new PoseSnapshot(FiguraMat4.of().scale(4, 4, 4), FiguraMat3.of()), stale.semantics());
        require(!GpuBatchData.supports(overflow), "finite input with overflowing graphics output rejects");
        require(service.tryDraw(batch(service, owner, quad(1), 0, material()), CONTEXT) == GpuAccelerationBackend.DrawResult.DRAWN,
                "supported geometry after rejected batches remains accelerated");
        DrawBatch oldResources = batch(service, owner, quad(1), 0, material());
        service.invalidateResources();
        require(service.tryDraw(oldResources, CONTEXT) == GpuAccelerationBackend.DrawResult.STALE, "old resource generation rejects");
        service.beginFrame();
        require(gpu.residentBytes() == 0, "resource invalidation retires all GPU ownership at the render boundary");
        device.complete = true;
        retirement.poll();
        require(device.liveBytes == 0, "resource rebuild cleanup completes");
        service.useBackend(null);

        var small = new FakeDevice();
        var smallGpu = ready(small, new GpuRetirement(), 128);
        service.useBackend(smallGpu);
        require(service.tryDraw(batch(service, new AccelerationOwner(), quad(1), 0, material()), CONTEXT)
                == GpuAccelerationBackend.DrawResult.UNSUPPORTED && small.uploads == 0, "budget rejection precedes allocations");
        service.useBackend(null);
    }

    private static void failureRouting(boolean afterIssue) {
        var device = new FakeDevice();
        device.failBeforeDraw = !afterIssue;
        device.failAfterDraw = afterIssue;
        device.complete = true;
        var retirement = new GpuRetirement();
        var gpu = ready(device, retirement, 1_000_000);
        var service = new AccelerationService();
        AtomicInteger failures = new AtomicInteger();
        service.useBackend(gpu, ignored -> failures.incrementAndGet());
        var owner = new AccelerationOwner();
        DrawBatch batch = batch(service, owner, quad(1), 5, material());
        var result = service.tryDraw(batch, CONTEXT);
        require(result == (afterIssue ? GpuAccelerationBackend.DrawResult.ISSUED_BEFORE_FAILURE
                : GpuAccelerationBackend.DrawResult.UNSUPPORTED), "partial draw status determines whether replay is safe");
        require(failures.get() == 1, "one controlled failure notification");
        require(service.transform(batch.lease(), ignored -> batch)[0] == 6, "following/unsubmitted work actually uses Java");
        require(service.tryDraw(batch, CONTEXT) == GpuAccelerationBackend.DrawResult.UNSUPPORTED
                && device.drawCalls == (afterIssue ? 1 : 0), "failed backend stops new draws without duplicate submission");
        retirement.poll();
        require(device.liveBytes == 0, "failed backend resources retire safely");
    }

    private static void initializationFailure() {
        var device = new FakeDevice();
        device.failCompile = true;
        var gpu = backend(device, new GpuRetirement(), 1_000_000);
        require(!gpu.initializeStep(), "diagnostic first step stays probing");
        try {
            gpu.initializeStep();
            throw new AssertionError("forced shader compile must fail");
        } catch (GpuAccelerationBackend.Failure failure) {
            require(failure.reason == FallbackReason.SHADER_FAILURE, "accurate shader failure reason");
        }
        gpu.close();
        require(device.liveBytes == 0, "failed initialization owns no leaked geometry/pose buffers");
    }

    private static void allocationAndDeviceFailure() {
        for (int failurePoint = 0; failurePoint < 3; failurePoint++) {
            var device = new FakeDevice();
            device.complete = true;
            device.failGeometryUpload = failurePoint == 0;
            device.failPoseUpload = failurePoint == 1;
            var retirement = new GpuRetirement();
            var gpu = ready(device, retirement, 1_000_000);
            var service = new AccelerationService();
            var reasons = new java.util.ArrayList<FallbackReason>();
            service.useBackend(gpu, failure -> reasons.add(((GpuAccelerationBackend.Failure) failure).reason));
            if (failurePoint == 2) {
                device.current = false;
            }
            DrawBatch batch = batch(service, new AccelerationOwner(), quad(1), 5, material());
            require(service.tryDraw(batch, CONTEXT) == GpuAccelerationBackend.DrawResult.UNSUPPORTED,
                    "allocation/device loss before draw permits safe Java replay");
            require(service.transform(batch.lease(), ignored -> batch)[0] == 6 && device.drawCalls == 0,
                    "allocation/device failure preserves actual Java geometry without a GPU draw");
            require(reasons.equals(List.of(failurePoint == 2 ? FallbackReason.GPU_UNAVAILABLE : FallbackReason.RESOURCE_EXHAUSTED)),
                    "resource and device failures have accurate reasons");
            retirement.poll();
            require(device.liveBytes == 0, "partially allocated GPU state is retired");
        }
    }

    private static void authoredGroupIndexReuse() {
        var device = new IndexCountingDevice();
        var retirement = new GpuRetirement();
        var gpu = ready(device, retirement, 1_000_000);
        var service = new AccelerationService();
        var owner = new AccelerationOwner();
        var batches = List.of(batch(service, owner, quad(1), 0, material()), batch(service, owner, quad(4), 2, material()));
        // Compile the rigid group material through the normal pending-material frame step.
        gpu.supports(AccelerationBackend.Operation.GPU_DRAW, batches.getFirst());
        gpu.beginFrame();
        gpu.beginFrame();
        long[] authored = {0, 1L << 32};
        long[] reversed = {1L << 32, 0};

        var first = gpu.prepareGroup(batches, authored, () -> true);
        require(first != null && device.indexUploads == 1, "first authored group uploads its index buffer");
        gpu.beginFrame();
        var moved = List.of(batch(service, owner, quad(1), 5, material()), batch(service, owner, quad(4), 7, material()));
        var second = gpu.prepareGroup(moved, authored, () -> true);
        require(second != null && device.indexUploads == 1 && gpu.geometryUploads() == 1,
                "stable authored group reuses geometry and indices when only poses change");

        gpu.beginFrame();
        require(gpu.prepareGroup(batches, reversed, () -> true) != null && device.indexUploads == 2,
                "a sorted order still uploads its own per-frame index buffer");
        gpu.beginFrame();
        require(gpu.prepareGroup(batches, reversed, () -> true) != null && device.indexUploads == 3,
                "sorted indices are not cached across frames");
        require(gpu.prepareGroup(batches, authored, () -> true) != null && device.indexUploads == 3,
                "the authored buffer survives interleaved sorted frames");

        var changed = List.of(batch(service, owner, quad(1), 0, material()), batch(service, owner, quad(9), 0, material()));
        require(gpu.prepareGroup(changed, authored, () -> true) != null && device.indexUploads == 4
                        && gpu.geometryUploads() == 2, "different group geometry gets its own entry and indices");

        owner.retire();
        gpu.beginFrame();
        require(gpu.residentBytes() == 0, "retired owner releases group geometry and authored indices");
        gpu.close();
        device.complete = true;
        retirement.poll();
        require(device.liveBytes == 0, "every group mesh and index buffer is released exactly once");
        System.out.println("PASS authored rigid group index reuse, sorted per-frame indices and retirement");
    }

    private static final class IndexCountingDevice extends RigidDevice {
        int indexUploads;

        @Override
        public Indices uploadIndices(ByteBuffer data) {
            indexUploads++;
            return super.uploadIndices(data);
        }
    }

    private static GpuAccelerationBackend backend(FakeDevice device, GpuRetirement retirement, long budget) {
        return new GpuAccelerationBackend(device, retirement, budget, 100_000);
    }

    private static GpuAccelerationBackend ready(FakeDevice device, GpuRetirement retirement, long budget) {
        var gpu = backend(device, retirement, budget);
        require(!gpu.initializeStep() && !gpu.initializeStep() && gpu.initializeStep(), "bounded initialization");
        return gpu;
    }

    private static RenderType material() {
        return RenderTypes.entityCutout(Identifier.parse("omni-morph:contract_texture"));
    }

    private static DrawBatch batch(AccelerationService service, AccelerationOwner owner, MeshHandle mesh, double translation, RenderType material) {
        var semantics = new DrawSemantics(material, Identifier.parse("omni-morph:contract_texture"), DrawSemantics.Layer.PRIMARY,
                "world", 0, mesh.faceCount(), 0x00a000a0, 0x000a0000, 0.5f, 1, 1, 1, 0, true);
        return new DrawBatch(service.capture(owner, "world"), mesh,
                new PoseSnapshot(FiguraMat4.of().translate(translation, 0, 0), FiguraMat3.of()), semantics);
    }

    private static MeshHandle quad(double x) {
        return new MeshHandle(new double[]{
                x, 2, 3, 1, 0, 1, 0, 0, 0,
                x + 1, 2, 3, 1, 0, 1, 0, 1, 0,
                x + 1, 3, 3, 1, 0, 1, 0, 1, 1,
                x, 3, 3, 1, 0, 1, 0, 0, 1}, 4);
    }

    static final class FakeResource implements GpuDeviceAdapter.Mesh, GpuDeviceAdapter.Pose, GpuDeviceAdapter.Indices {
        final FakeDevice owner;
        final ByteBuffer data;
        final long bytes;
        boolean closed;

        FakeResource(FakeDevice owner, ByteBuffer data, long bytes) {
            this.owner = owner;
            this.data = ByteBuffer.allocate(data.remaining()).order(ByteOrder.nativeOrder());
            this.data.put(data.duplicate()).flip();
            this.bytes = bytes;
            owner.liveBytes += bytes;
        }

        @Override
        public long bytes() {
            return bytes;
        }

        @Override
        public void close() {
            require(!closed, "resource must not be double-closed");
            closed = true;
            owner.liveBytes -= bytes;
        }
    }

    static class RigidDevice extends FakeDevice {
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
            return new FakeResource(this, data, data.remaining());
        }
    }

    static class FakeDevice implements GpuDeviceAdapter {
        final Thread thread = Thread.currentThread();
        long liveBytes;
        int selfTests;
        int compiles;
        int uploads;
        int drawCalls;
        boolean complete;
        boolean failCompile;
        boolean failBeforeDraw;
        boolean failAfterDraw;
        boolean failGeometryUpload;
        boolean failPoseUpload;
        boolean current = true;
        FakeResource lastMesh;
        FakeResource lastPose;

        @Override
        public void assertThread() {
            require(Thread.currentThread() == thread, "GPU API must stay on the render thread");
        }

        @Override
        public boolean isCurrent() {
            return current;
        }

        @Override
        public Material compile(RenderPipeline original) {
            compiles++;
            if (failCompile) {
                throw new IllegalStateException("injected shader failure");
            }
            return new Material(original, original);
        }

        @Override
        public Mesh uploadGeometry(ByteBuffer vertices, ByteBuffer indices) {
            if (failGeometryUpload) {
                throw new IllegalStateException("injected geometry allocation failure");
            }
            uploads++;
            return lastMesh = new FakeResource(this, vertices, (long) vertices.remaining() + indices.remaining());
        }

        @Override
        public Pose uploadPose(ByteBuffer data) {
            if (failPoseUpload) {
                throw new IllegalStateException("injected pose allocation failure");
            }
            return lastPose = new FakeResource(this, data, data.remaining());
        }

        @Override
        public GpuSubmission.Scope openScope(RenderType material) {
            return new GpuSubmission.Scope(CONTEXT, () -> {});
        }

        @Override
        public void draw(Material material, Mesh mesh, Pose pose, GpuDrawContext context, int firstIndex, int indexCount, Runnable issuingDraw) {
            require(indexCount == 6 && firstIndex == 0, "whole authored quad triangulated for actual draw");
            if (failBeforeDraw) {
                throw new IllegalStateException("injected pre-draw failure");
            }
            issuingDraw.run();
            drawCalls++;
            if (failAfterDraw) {
                throw new IllegalStateException("injected post-issue failure");
            }
        }

        @Override
        public GpuFence fence() {
            return new GpuFence() {
                @Override
                public void close() {
                }

                @Override
                public boolean awaitCompletion(long timeout) {
                    require(timeout == 0, "normal frame retirement cannot block waiting for GPU");
                    return complete;
                }
            };
        }

        @Override
        public void selfTest() {
            selfTests++;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
