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
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import java.util.ArrayList;
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
        poseReuseAfterFence();
        poseBlockVariants(false);
        poseBlockVariants(true);
        poseArena();
        storageRouting();
        storageSingleRangeGroup();
        storageMergedPass();
        modelRouting();
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

    private static void poseReuseAfterFence() {
        var device = new FakeDevice();
        var retirement = new GpuRetirement();
        var gpu = ready(device, retirement, 1_000_000);
        var service = new AccelerationService();
        var owner = new AccelerationOwner();
        service.useBackend(gpu);

        require(service.tryDraw(batch(service, owner, quad(1), 1, material()), CONTEXT)
                == GpuAccelerationBackend.DrawResult.DRAWN, "first pose draws");
        FakeResource firstPose = device.lastPose;
        service.beginFrame();
        require(service.tryDraw(batch(service, owner, quad(1), 2, material()), CONTEXT)
                == GpuAccelerationBackend.DrawResult.DRAWN, "second pose draws");
        FakeResource secondPose = device.lastPose;
        require(secondPose != firstPose && firstPose.data.getFloat(12 * 4) == 1,
                "unsignalled fence prevents reuse and preserves the first pose");

        device.complete = true;
        service.beginFrame();
        require(service.tryDraw(batch(service, owner, quad(1), 3, material()), CONTEXT)
                == GpuAccelerationBackend.DrawResult.DRAWN, "completed pose draws again");
        require((device.lastPose == firstPose || device.lastPose == secondPose)
                        && device.lastPose.data.getFloat(12 * 4) == 3,
                "completed pose buffer is reused with updated contents");

        service.useBackend(null);
        retirement.poll();
        require(device.liveBytes == 0, "closing the backend releases pooled and in-flight resources");
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
        require(gpu.groupLayoutBuilds() == 1 && gpu.groupRangeBuilds() == 1,
                "a stable group rebuilds neither its layout nor its ranges when only poses change");

        gpu.beginFrame();
        require(gpu.prepareGroup(batches, reversed, () -> true) != null && device.indexUploads == 2,
                "a sorted order still uploads its own per-frame index buffer");
        gpu.beginFrame();
        require(gpu.prepareGroup(batches, reversed, () -> true) != null && device.indexUploads == 3,
                "sorted indices are not cached across frames");
        require(gpu.groupLayoutBuilds() == 2, "a changed sort order rebuilds the layout once, then reuses it");
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

    private static void storageRouting() throws Exception {
        var device = new FakeDevice();
        device.storage = new GpuStorageSupport.Limits(256, 1 << 20, true);
        var retirement = new GpuRetirement();
        var gpu = ready(device, retirement, 1_000_000);
        var service = new AccelerationService();
        var owner = new AccelerationOwner();
        service.useBackend(gpu);

        require(service.tryDraw(batch(service, owner, quad(1), 5, material()), CONTEXT)
                == GpuAccelerationBackend.DrawResult.DRAWN, "first draw before the storage variant compiles");
        require(device.lastDrawnPose instanceof FakeResource && device.storageCompiles == 0,
                "an uncompiled storage variant keeps uniform pose blocks and never compiles inside submission");
        service.beginFrame();
        require(device.storageCompiles == 1, "the storage variant compiles in the frame step");

        Path recordingPath = Files.createTempFile("omnimorph-ssbo", ".jfr");
        var recording = new Recording();
        recording.enable(AccelerationFrameDiagnostics.FrameEvent.class).withoutStackTrace();
        recording.start();
        AccelerationFrameDiagnostics.beginFrame();

        require(service.tryDraw(batch(service, owner, quad(1), 7, material()), CONTEXT)
                == GpuAccelerationBackend.DrawResult.DRAWN, "storage draw");
        require(device.lastDrawnPose instanceof GpuDeviceAdapter.StoragePose, "the compiled variant draws from the arena");
        var first = (GpuDeviceAdapter.StoragePose) device.lastDrawnPose;
        var arena = (FakeResource) first.storage;
        int header = first.pose.offset();
        require(first.pose.bytes() == 208 && first.remap == null
                        && arena.data.getFloat(header) == 127 / 255f
                        && arena.data.getInt(header + 20) == 10
                        && arena.data.getFloat(header + GpuBatchData.STORAGE_HEADER_BYTES + 12 * 4) == 7,
                "the arena holds the std430 header followed by the batch transform");
        require(service.tryDraw(batch(service, owner, quad(1), 9, material()), CONTEXT)
                == GpuAccelerationBackend.DrawResult.DRAWN, "second storage draw in the same frame");
        var second = (GpuDeviceAdapter.StoragePose) device.lastDrawnPose;
        require(second.storage == arena && second.pose.offset() == 256 && device.storageCreates == 1
                        && device.storageWrites.size() == 2 && device.storageWrites.get(1)[0] == 208
                        && arena.data.getFloat(256 + GpuBatchData.STORAGE_HEADER_BYTES + 12 * 4) == 9
                        && gpu.storageDraws() == 2,
                "later draws share the frame arena and write only their own bytes");
        AccelerationFrameDiagnostics.beginFrame();
        recording.stop();
        recording.dump(recordingPath);
        recording.close();
        var events = RecordingFile.readAllEvents(recordingPath);
        Files.deleteIfExists(recordingPath);
        AccelerationFrameDiagnostics.beginFrame();
        require(events.size() == 1 && events.getFirst().getLong("ssboDraws") == 2
                        && events.getFirst().getLong("arenaUploads") == 2
                        && events.getFirst().getLong("arenaBytes") == 208 + 256
                        && events.getFirst().getLong("ssboFallbacks") == 0,
                "the frame event counts storage draws and the incremental arena bytes");

        device.failStorageWrite = true;
        for (int attempt = 0; attempt < 3; attempt++) {
            service.beginFrame();
            var failed = service.tryDraw(batch(service, owner, quad(1), 11, material()), CONTEXT);
            require(failed == GpuAccelerationBackend.DrawResult.UNSUPPORTED,
                    "a storage failure before issue stays local (attempt " + attempt + ": " + failed + ")");
            require(service.gpuDrawEnabled(), "storage failures never disable the GPU backend");
        }
        require(!gpu.storageActive() && gpu.storageFallbacks() >= 3, "three consecutive failures switch storage off");
        service.beginFrame();
        require(service.tryDraw(batch(service, owner, quad(1), 13, material()), CONTEXT)
                        == GpuAccelerationBackend.DrawResult.DRAWN && device.lastDrawnPose instanceof FakeResource,
                "after storage is switched off, uniform pose blocks draw again");

        service.useBackend(null);
        device.complete = true;
        retirement.poll();
        require(device.liveBytes == 0, "closing releases arenas, pooled poses and geometry");
        System.out.println("PASS pose storage routing: lazy variant, arena draws, incremental writes, local failures and fallback");
    }

    /** R4-1: with storage blocks, a group of more than 64 interleaved parts prepares one range and one draw. */
    private static void storageSingleRangeGroup() {
        var device = new RigidDevice();
        device.storage = new GpuStorageSupport.Limits(256, 1 << 20, true);
        var retirement = new GpuRetirement();
        var gpu = ready(device, retirement, 4_000_000);
        var service = new AccelerationService();
        var owner = new AccelerationOwner();
        List<DrawBatch> batches = new ArrayList<>();
        for (int index = 0; index < 130; index++) {
            batches.add(batch(service, owner, quad(1), index % 65, material()));
        }
        long[] interleaved = new long[batches.size()];
        for (int index = 0; index < interleaved.length; index++) {
            int batch = index % 2 == 0 ? index / 2 : batches.size() - 1 - index / 2;
            interleaved[index] = (long) batch << 32;
        }
        // Compile the rigid group material through the normal pending-material frame step.
        gpu.supports(AccelerationBackend.Operation.GPU_DRAW, batches.getFirst());
        gpu.beginFrame();
        gpu.beginFrame();

        var uniform = gpu.prepareGroup(batches, interleaved, () -> true);
        require(uniform != null && uniform.size() > 1,
                "before the storage variant compiles, the group keeps 64-part palette ranges");
        gpu.beginFrame();
        require(device.storageCompiles == 1, "the rigid storage variant compiles in the frame step");

        var single = gpu.prepareGroup(batches, interleaved, () -> true);
        require(single != null && single.size() == 1 && single.getFirst().firstFace() == 0
                        && single.getFirst().faceCount() == batches.size(),
                "with storage blocks the whole interleaved group is one range");
        require(gpu.drawPrepared(single.getFirst().prepared(), CONTEXT, 0, batches.size() * 6, () -> true)
                        == GpuAccelerationBackend.DrawResult.DRAWN
                        && device.lastDrawnPose instanceof GpuDeviceAdapter.StoragePose,
                "the single range draws once from the arena");
        var drawn = (GpuDeviceAdapter.StoragePose) device.lastDrawnPose;
        require(drawn.pose.bytes() == 48 + 65 * 160 && drawn.remap.bytes() == 130 * Integer.BYTES,
                "one pose block holds all 65 parts and the remap one uint per batch");

        gpu.close();
        device.complete = true;
        retirement.poll();
        require(device.liveBytes == 0, "closing releases the arena, indices and group geometry");
        System.out.println("PASS R4-1 backend: interleaved 65-part group prepares one storage range and one draw");
    }

    /** R4-2: a shared pass never stays open across an arena write. */
    private static void storageMergedPass() {
        var device = new FakeDevice();
        device.storage = new GpuStorageSupport.Limits(256, 1 << 20, true);
        var retirement = new GpuRetirement();
        var gpu = ready(device, retirement, 1_000_000);
        var service = new AccelerationService();
        var owner = new AccelerationOwner();
        service.useBackend(gpu);
        gpu.prepare(batch(service, owner, quad(1), 1, material()), () -> true);
        service.beginFrame();
        require(device.storageCompiles == 1, "storage variant ready");

        var first = gpu.prepare(batch(service, owner, quad(1), 2, material()), () -> true);
        var second = gpu.prepare(batch(service, owner, quad(1), 3, material()), () -> true);
        try (var merged = gpu.openMergedDraws(CONTEXT)) {
            require(merged.draw(first, 0, 6, () -> true) == GpuAccelerationBackend.DrawResult.DRAWN
                            && merged.draw(second, 0, 6, () -> true) == GpuAccelerationBackend.DrawResult.DRAWN,
                    "prepared-before-pass draws");
            require(gpu.passCount() == 1 && device.storageWrites.size() == 1,
                    "draws prepared before the pass share it after one arena write");
            var late = gpu.prepare(batch(service, owner, quad(1), 4, material()), () -> true);
            require(merged.draw(late, 0, 6, () -> true) == GpuAccelerationBackend.DrawResult.DRAWN,
                    "a pose staged while the pass is open still draws");
            require(gpu.passCount() == 2 && device.storageWrites.size() == 2,
                    "pending arena bytes close the pass before they are written, then a new pass opens");
        }
        service.useBackend(null);
        device.complete = true;
        retirement.poll();
        require(device.liveBytes == 0, "closing releases everything");
        System.out.println("PASS R4-2 storage: shared pass reuse and closing before pending arena writes");
    }

    /** R4-3 M3: skinned, differently lit and triangle batches of one boundary prepare one model range. */
    private static void modelRouting() {
        var device = new RigidDevice();
        device.storage = new GpuStorageSupport.Limits(256, 1 << 20, true);
        var retirement = new GpuRetirement();
        var gpu = ready(device, retirement, 4_000_000);
        var service = new AccelerationService();
        var owner = new AccelerationOwner();
        var triangle = new MeshHandle(new double[]{
                0, 0, 0, 1, 0, 1, 0, 0, 0,
                1, 0, 0, 1, 0, 1, 0, 1, 0,
                0, 1, 0, 1, 0, 1, 0, 0, 1,
                1, 1, 0, 1, 0, 1, 0, 1, 1,
                2, 1, 0, 1, 0, 1, 0, 1, 1,
                1, 2, 0, 1, 0, 1, 0, 0, 1}, 3);
        List<DrawBatch> batches = new ArrayList<>(List.of(
                batch(service, owner, quad(1), 1, material()),
                batch(service, owner, triangle, 2, material()),
                batch(service, owner, quad(2), 3, material())));
        var tinted = batches.get(2);
        var semantics = tinted.semantics();
        batches.set(2, new DrawBatch(tinted.lease(), tinted.mesh(), tinted.pose(), new DrawSemantics(semantics.material(),
                semantics.texture(), semantics.layer(), "world", 0, tinted.mesh().faceCount(), 0x00f000f0,
                semantics.overlay(), 1, 0, 0, 1, 0, true)));
        long[] order = {0, 1L << 32, (1L << 32) | 1, 2L << 32};
        require(!GpuRigidData.supports(batches) && GpuModelData.supports(batches),
                "triangles and a differently lit batch reject rigid groups but fit one model draw");
        gpu.supports(AccelerationBackend.Operation.GPU_DRAW, batches.getFirst());
        gpu.beginFrame();
        gpu.beginFrame();

        AccelerationOptions.setStorageBlocks(true);
        AccelerationOptions.setModelMesh(false);
        try {
            require(gpu.prepareGroup(batches, order, () -> true) == null, "with model draws off the boundary goes batch by batch");
            AccelerationOptions.setModelMesh(true);
            require(gpu.prepareGroup(batches, order, () -> true) == null && device.modelCompiles == 0,
                    "the model variant is never compiled inside submission");
            gpu.beginFrame();
            require(device.modelCompiles == 1, "the model variant compiles in the frame step");
            var ranges = gpu.prepareGroup(batches, order, () -> true);
            require(ranges != null && ranges.size() == 1 && ranges.getFirst().faceCount() == 4
                            && ranges.getFirst().indexCount() == 6 + 3 + 3 + 6,
                    "one model range covers quads and fan-triangulated triangles");
            require(gpu.drawPrepared(ranges.getFirst().prepared(), CONTEXT, 0, ranges.getFirst().indexCount(), () -> true)
                            == GpuAccelerationBackend.DrawResult.DRAWN
                            && device.lastDrawnPose instanceof GpuDeviceAdapter.StoragePose storagePose
                            && storagePose.records != null && storagePose.records.bytes() == 3 * 48
                            && storagePose.remap == null && storagePose.pose.bytes() == 48 + 3 * 160,
                    "the single draw binds the part table and one record per batch");
        } finally {
            AccelerationOptions.setStorageBlocks(false);
            AccelerationOptions.setModelMesh(false);
        }
        gpu.close();
        device.complete = true;
        retirement.poll();
        require(device.liveBytes == 0, "closing releases model geometry, indices and the arena");
        System.out.println("PASS R4-3 routing: skinned-capable model draw for lit/tinted quad and triangle batches, gated by options");
    }

    private static void poseArena() {
        var device = new FakeDevice();
        var retirement = new GpuRetirement();
        var arena = new GpuPoseArena(device, retirement, 256, 300_000);

        var first = arena.append(208, buffer -> buffer.put(new byte[208]));
        var second = arena.append(368, buffer -> {
            buffer.putInt(7);
            buffer.put(new byte[364]);
        });
        require(first.offset() == 0 && second.offset() == 256 && second.bytes() == 368,
                "slices start at the storage offset alignment");
        require(arena.append(300_000, buffer -> {
            throw new AssertionError("a rejected slice must not run the writer");
        }) == null, "a slice beyond the arena limit falls back");
        var arenaBuffer = (FakeResource) arena.flush();
        require(device.storageCreates == 1 && arenaBuffer.bytes() == GpuPoseArena.MIN_CAPACITY
                        && arenaBuffer.data.getInt(256) == 7 && device.storageWrites.size() == 1
                        && device.storageWrites.getFirst()[0] == 0 && device.storageWrites.getFirst()[1] == 624,
                "the first flush creates the frame arena and writes every staged slice once");
        require(arena.flush() == arenaBuffer && device.storageWrites.size() == 1, "a flush without new slices writes nothing");

        var third = arena.append(16, buffer -> buffer.putInt(9).put(new byte[12]));
        require(third.offset() == 768 && arena.flush() == arenaBuffer && device.storageWrites.size() == 2
                        && device.storageWrites.get(1)[0] == 624 && device.storageWrites.get(1)[1] == 160
                        && arenaBuffer.data.getInt(768) == 9 && arenaBuffer.data.getInt(256) == 7,
                "a later material boundary writes only its new bytes into the same arena");
        require(arena.append(70_000, buffer -> {
            throw new AssertionError("an overflowing slice must not run the writer");
        }) == null, "a slice beyond this frame's arena capacity falls back");
        require(arena.residentBytes() == GpuPoseArena.MIN_CAPACITY && arena.uploads() == 2 && arena.uploadedBytes() == 784,
                "the active arena counts as resident and uploads count written bytes");

        arena.beginFrame();
        require(arena.residentBytes() == 0 && retirement.bytes() == GpuPoseArena.MIN_CAPACITY,
                "a finished frame's arena moves behind its fence");
        arena.append(208, buffer -> buffer.put(new byte[208]));
        arena.flush();
        require(device.storageCreates == 2 && !arenaBuffer.closed, "an arena still in flight is never reused or closed");

        device.complete = true;
        arena.beginFrame();
        retirement.poll();
        require(arena.residentBytes() == 2L * GpuPoseArena.MIN_CAPACITY && retirement.bytes() == 0,
                "completed arenas return to the bounded free list");
        arena.append(208, buffer -> buffer.put(new byte[208]));
        require(arena.flush() != null && device.storageCreates == 2, "a recycled arena serves the next frame");

        arena.beginFrame();
        var large = arena.append(100_000, buffer -> buffer.put(new byte[100_000]));
        var grown = (FakeResource) arena.flush();
        require(large.offset() == 0 && grown.bytes() == 128 * 1024 && device.storageCreates == 3,
                "a larger frame grows to the next power of two and retires arenas that are too small");

        arena.beginFrame();
        arena.append(208, buffer -> buffer.put(new byte[208]));
        var reserved = (FakeResource) arena.flush();
        require(reserved.bytes() == 128 * 1024 && device.storageCreates == 4,
                "a new frame reserves at least the previous frame's size for later boundaries");

        arena.beginFrame();
        try {
            arena.append(32, buffer -> buffer.put(new byte[16]));
            throw new AssertionError("a short write must reject");
        } catch (IllegalStateException expected) {
            // A writer that disagrees with its declared size would corrupt neighbouring slices.
        }
        arena.close();
        retirement.poll();
        require(arena.residentBytes() == 0 && device.liveBytes == 0, "closing releases idle and in-flight arenas");
        System.out.println("PASS pose storage arena: alignment, limit fallback, incremental flushes, fence reuse, growth and release");
    }

    private static void poseBlockVariants(boolean failVariant) {
        var device = new VariantDevice();
        device.failVariant = failVariant;
        var retirement = new GpuRetirement();
        var gpu = ready(device, retirement, 1_000_000);
        var service = new AccelerationService();
        var owner = new AccelerationOwner();
        service.useBackend(gpu);

        require(service.tryDraw(batch(service, owner, quad(1), 5, material()), CONTEXT)
                == GpuAccelerationBackend.DrawResult.DRAWN, "first draw uses the full pose block");
        require(device.lastPose.data.capacity() == GpuBatchData.POSE_BYTES && device.variantRequests.isEmpty(),
                "a missing variant is never compiled inside model submission");
        service.beginFrame();
        service.beginFrame();
        require(device.variantRequests.equals(List.of("entity:1")),
                "one rigid-transform variant is compiled once, outside traversal");
        require(service.tryDraw(batch(service, owner, quad(1), 7, material()), CONTEXT)
                == GpuAccelerationBackend.DrawResult.DRAWN, "draw after the variant step");
        if (failVariant) {
            require(device.lastPose.data.capacity() == GpuBatchData.POSE_BYTES,
                    "a failed variant keeps drawing with the full layout");
        } else {
            int parameters = GpuBatchData.parameterOffset(1);
            require(device.lastPose.data.capacity() == GpuBatchData.poseBytes(1)
                            && device.lastPose.data.getFloat(12 * 4) == 7
                            && device.lastPose.data.getFloat(parameters) == 127 / 255f
                            && device.lastPose.data.getInt(parameters + 20) == 10,
                    "a one-part variant uploads 208 bytes with its parameters right after the part");
        }

        service.useBackend(null);
        device.complete = true;
        retirement.poll();
        require(device.liveBytes == 0, "variant pose buffers are released with the backend");
    }

    private static final class VariantDevice extends FakeDevice {
        final List<String> variantRequests = new ArrayList<>();
        boolean failVariant;

        @Override
        public Material compileVariant(RenderPipeline original, boolean grouped, int parts) {
            variantRequests.add((grouped ? "group:" : "entity:") + parts);
            if (failVariant) {
                throw new IllegalStateException("variant shader rejected");
            }
            return new Material(original, original);
        }
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

    static final class FakeResource implements GpuDeviceAdapter.Mesh, GpuDeviceAdapter.Pose, GpuDeviceAdapter.Indices,
            GpuDeviceAdapter.Storage {
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

        @Override
        public void drawWithIndices(Material material, Mesh mesh, Pose pose, Indices indices, GpuDrawContext context,
                                    int firstIndex, int indexCount, Runnable issuingDraw) {
            issuingDraw.run();
            drawCalls++;
            lastDrawnPose = pose;
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
        int storageCreates;
        int storageCompiles;
        boolean failStorageWrite;
        GpuStorageSupport.Limits storage;
        Pose lastDrawnPose;
        final List<long[]> storageWrites = new ArrayList<>();
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
        public GpuStorageSupport.Limits storageLimits() {
            return storage;
        }

        @Override
        public Material compileStorage(RenderPipeline original, boolean grouped) {
            storageCompiles++;
            return new Material(original, original);
        }

        int modelCompiles;

        @Override
        public Material compileStorageModel(RenderPipeline original) {
            modelCompiles++;
            return new Material(original, original);
        }

        @Override
        public Storage createStorage(long capacity) {
            storageCreates++;
            return new FakeResource(this, ByteBuffer.allocate(Math.toIntExact(capacity)), capacity);
        }

        @Override
        public void writeStorage(Storage storage, long offset, ByteBuffer data) {
            FakeResource arena = (FakeResource) storage;
            require(!arena.closed && offset >= 0 && offset + data.remaining() <= arena.data.capacity(),
                    "storage writes stay inside a live arena");
            if (failStorageWrite) {
                throw new IllegalStateException("injected storage write failure");
            }
            storageWrites.add(new long[]{offset, data.remaining()});
            arena.data.clear();
            arena.data.position(Math.toIntExact(offset));
            arena.data.put(data.duplicate());
            arena.data.clear();
        }

        @Override
        public boolean updatePose(Pose pose, ByteBuffer data) {
            FakeResource existing = (FakeResource) pose;
            if (existing.closed || existing.data.capacity() != data.remaining()) {
                return false;
            }
            existing.data.clear();
            existing.data.put(data.duplicate()).flip();
            lastPose = existing;
            return true;
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
            lastDrawnPose = pose;
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
