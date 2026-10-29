package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;

/** All production uploads/draws use Blaze3D. The only readback is the bounded activation diagnostic. */
final class MinecraftGpuDeviceAdapter implements GpuDeviceAdapter {
    private final GpuDevice device;
    private final BiFunction<Identifier, ShaderType, String> sources;
    private final Map<RenderPipeline, Material> compiled = new IdentityHashMap<>();
    private final Map<RenderPipeline, Material> compiledGroups = new IdentityHashMap<>();
    // Smaller pose block variants: at most three per full-layout pipeline, under their own budget.
    private final Map<VariantKey, Material> compiledVariants = new HashMap<>();
    private final Map<RenderPipeline, Material> compiledStorage = new IdentityHashMap<>();
    private final Map<RenderPipeline, Material> compiledStorageGroups = new IdentityHashMap<>();
    private RenderPipeline diagnosticPipeline;
    private RenderPipeline diagnosticGroupPipeline;
    private GpuStorageSupport.Limits storageLimits;

    private record BufferMesh(GpuBuffer vertices, GpuBuffer indices, long bytes) implements Mesh {
        @Override
        public void close() {
            try {
                vertices.close();
            } finally {
                indices.close();
            }
        }
    }

    private record BufferPose(GpuBuffer buffer, long bytes) implements Pose {
        @Override
        public void close() {
            buffer.close();
        }
    }

    private record BufferIndices(GpuBuffer buffer, long bytes) implements Indices {
        @Override
        public void close() {
            buffer.close();
        }
    }

    MinecraftGpuDeviceAdapter(GpuDevice device, BiFunction<Identifier, ShaderType, String> sources) {
        this.device = Objects.requireNonNull(device);
        this.sources = Objects.requireNonNull(sources);
    }

    @Override
    public void assertThread() {
        RenderSystem.assertOnRenderThread();
    }

    @Override
    public boolean isCurrent() {
        return RenderSystem.tryGetDevice() == device;
    }

    @Override
    public Material compile(RenderPipeline original) {
        return compile(original, false);
    }

    @Override
    public boolean supportsRigidGroups() {
        return true;
    }

    @Override
    public Material compileRigidGroup(RenderPipeline original) {
        return compile(original, true);
    }

    @Override
    public Material compileVariant(RenderPipeline original, boolean grouped, int parts) {
        if (parts == GpuBatchData.MAX_PARTS) {
            return compile(original, grouped);
        }
        assertThread();
        var key = new VariantKey(original, grouped, parts);
        Material existing = compiledVariants.get(key);
        if (existing != null) {
            return existing;
        }
        if (compiledVariants.size() >= 3 * 64) {
            throw new IllegalStateException("Acceleration pose variant cache budget exhausted");
        }
        Material material = build(original, grouped, parts);
        compiledVariants.put(key, material);
        return material;
    }

    @Override
    public GpuStorageSupport.Limits storageLimits() {
        return storageLimits;
    }

    @Override
    public Material compileStorage(RenderPipeline original, boolean grouped) {
        assertThread();
        if (storageLimits == null) {
            throw new IllegalStateException("Pose storage blocks were not activated");
        }
        Map<RenderPipeline, Material> cache = grouped ? compiledStorageGroups : compiledStorage;
        Material existing = cache.get(original);
        if (existing != null) {
            return existing;
        }
        if (compiledStorage.size() + compiledStorageGroups.size() >= 64) {
            throw new IllegalStateException("Acceleration storage pipeline cache budget exhausted");
        }
        Identifier location = Identifier.fromNamespaceAndPath("omni-morph", "acceleration/" + original.getLocation().getNamespace()
                + "/" + original.getLocation().getPath() + (grouped ? "/rigid_group" : "") + "/storage");
        String originalSource = sources.apply(original.getVertexShader(), ShaderType.VERTEX);
        String source = grouped
                ? GpuShaderProgram.wrapStorageRigidEntitySource(originalSource)
                : GpuShaderProgram.wrapStorageEntitySource(originalSource);
        RenderPipeline accelerated = GpuShaderProgram.pipeline(original, location, false);
        if (!device.precompilePipeline(accelerated, (id, type) -> id.equals(location) && type == ShaderType.VERTEX
                ? source : sources.apply(id, type)).isValid()) {
            throw new IllegalStateException("Acceleration storage entity shader validation failed");
        }
        Material material = new Material(original, accelerated);
        cache.put(original, material);
        return material;
    }

    private Material compile(RenderPipeline original, boolean grouped) {
        assertThread();
        Map<RenderPipeline, Material> cache = grouped ? compiledGroups : compiled;
        Material existing = cache.get(original);
        if (existing != null) {
            return existing;
        }
        if (compiled.size() + compiledGroups.size() >= 64) {
            throw new IllegalStateException("Acceleration pipeline cache budget exhausted");
        }
        Material material = build(original, grouped, GpuBatchData.MAX_PARTS);
        cache.put(original, material);
        return material;
    }

    private Material build(RenderPipeline original, boolean grouped, int parts) {
        String variant = parts == GpuBatchData.MAX_PARTS ? "" : "/parts_" + parts;
        Identifier location = Identifier.fromNamespaceAndPath("omni-morph", "acceleration/" + original.getLocation().getNamespace()
                + "/" + original.getLocation().getPath() + (grouped ? "/rigid_group" : "") + variant);
        String originalSource = sources.apply(original.getVertexShader(), ShaderType.VERTEX);
        String source = grouped
                ? GpuShaderProgram.wrapRigidEntitySource(originalSource, parts)
                : GpuShaderProgram.wrapEntitySource(originalSource, parts);
        RenderPipeline accelerated = GpuShaderProgram.pipeline(original, location);
        if (!device.precompilePipeline(accelerated, (id, type) -> id.equals(location) && type == ShaderType.VERTEX
                ? source : sources.apply(id, type)).isValid()) {
            throw new IllegalStateException("Acceleration entity shader validation failed");
        }
        return new Material(original, accelerated);
    }

    private record VariantKey(RenderPipeline original, boolean grouped, int parts) {
    }

    @Override
    public Mesh uploadGeometry(ByteBuffer vertices, ByteBuffer indices) {
        assertThread();
        long size = (long) vertices.remaining() + indices.remaining();
        GpuBuffer vertexBuffer = device.createBuffer(() -> "OmniMorph cached geometry", GpuBuffer.USAGE_VERTEX, vertices);
        try {
            GpuBuffer indexBuffer = device.createBuffer(() -> "OmniMorph cached indices", GpuBuffer.USAGE_INDEX, indices);
            return new BufferMesh(vertexBuffer, indexBuffer, size);
        } catch (RuntimeException | Error failure) {
            vertexBuffer.close();
            throw failure;
        }
    }

    @Override
    public Pose uploadPose(ByteBuffer data) {
        assertThread();
        long size = data.remaining();
        return new BufferPose(device.createBuffer(() -> "OmniMorph frame pose",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, data), size);
    }

    @Override
    public boolean updatePose(Pose pose, ByteBuffer data) {
        assertThread();
        BufferPose existing = (BufferPose) pose;
        if (existing.bytes() != data.remaining() || existing.buffer().isClosed()) {
            return false;
        }
        device.createCommandEncoder().writeToBuffer(existing.buffer().slice(), data);
        return true;
    }

    @Override
    public Indices uploadIndices(ByteBuffer data) {
        assertThread();
        long size = data.remaining();
        return new BufferIndices(device.createBuffer(() -> "OmniMorph sorted frame indices", GpuBuffer.USAGE_INDEX, data), size);
    }

    @Override
    public GpuSubmission.Scope openScope(RenderType material) {
        return GpuSubmission.openMinecraftScope(material);
    }

    @Override
    public void draw(Material material, Mesh mesh, Pose pose, GpuDrawContext context,
                     int firstIndex, int indexCount, Runnable issuingDraw) {
        drawWithIndices(material, mesh, pose, null, context, firstIndex, indexCount, issuingDraw);
    }

    @Override
    public void drawWithIndices(Material material, Mesh mesh, Pose pose, Indices indices, GpuDrawContext context,
                                int firstIndex, int indexCount, Runnable issuingDraw) {
        assertThread();
        BufferMesh geometry = (BufferMesh) mesh;
        BufferPose matrices = (BufferPose) pose;
        var encoder = device.createCommandEncoder();
        try (var pass = encoder.createRenderPass(() -> "OmniMorph accelerated entity", Objects.requireNonNull(context.color()),
                OptionalInt.empty(), context.depth(), OptionalDouble.empty())) {
            pass.setPipeline(material.accelerated());
            if (context.scissorEnabled()) {
                pass.enableScissor(context.scissorX(), context.scissorY(), context.scissorWidth(), context.scissorHeight());
            }
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", Objects.requireNonNull(context.dynamicTransform()));
            pass.setUniform(GpuShaderProgram.UNIFORM, matrices.buffer());
            for (var binding : context.textures().entrySet()) {
                pass.bindTexture(binding.getKey(), binding.getValue().textureView(), binding.getValue().sampler());
            }
            pass.setVertexBuffer(0, geometry.vertices());
            pass.setIndexBuffer(indices == null ? geometry.indices() : ((BufferIndices) indices).buffer(), VertexFormat.IndexType.INT);
            issuingDraw.run();
            pass.drawIndexed(0, firstIndex, indexCount, 1);
        }
    }

    @Override
    public GpuFence fence() {
        assertThread();
        return device.createCommandEncoder().createFence();
    }

    @Override
    public void selfTest() {
        selfTest(false);
        selfTest(true);
        storageLimits = null;
        if (!GpuStorageSupport.requested()) {
            return;
        }
        // Storage blocks are an optional layer: any failure keeps the uniform pose blocks that just passed.
        try {
            GpuStorageSupport.Limits limits = GpuStorageSupport.probe();
            if (limits != null) {
                storageSelfTest(false);
                storageSelfTest(true);
                storageLimits = limits;
                org.figuramc.figura.FiguraMod.LOGGER.info("GPU pose storage blocks active (alignment {}, max block {} bytes)",
                        limits.offsetAlignment(), limits.maxBlockBytes());
            } else {
                org.figuramc.figura.FiguraMod.LOGGER.info("GPU pose storage blocks unavailable: {}",
                        GpuStorageSupport.unavailableReason());
            }
        } catch (RuntimeException failure) {
            org.figuramc.figura.FiguraMod.LOGGER.warn("GPU pose storage self-test failed; uniform pose blocks remain", failure);
        }
    }

    private void storageSelfTest(boolean grouped) {
        assertThread();
        Identifier location = Identifier.fromNamespaceAndPath("omni-morph",
                "acceleration/activation_probe_storage" + (grouped ? "_rigid" : ""));
        RenderPipeline pipeline = RenderPipeline.builder().withLocation(location).withVertexShader(location).withFragmentShader(location)
                .withColorTargetState(ColorTargetState.DEFAULT).withDepthStencilState(Optional.empty()).withCull(false)
                .withVertexFormat(GpuShaderProgram.format(), VertexFormat.Mode.TRIANGLES).build();
        String vertex = grouped
                ? GpuShaderProgram.wrapStorageRigidEntitySource(PROBE_VERTEX)
                : GpuShaderProgram.wrapStorageEntitySource(PROBE_VERTEX);
        if (!device.precompilePipeline(pipeline, (id, type) -> type == ShaderType.VERTEX ? vertex : PROBE_FRAGMENT).isValid()) {
            throw new IllegalStateException("Acceleration storage diagnostic shader rejected");
        }
        MeshHandle mesh = probeMesh();
        DrawBatch batch = new DrawBatch(AccelerationService.INSTANCE.capture(new AccelerationOwner(), "activation-probe"),
                mesh, new PoseSnapshot(FiguraMat4.of().translate(0.5, 0, 0), FiguraMat3.of()));
        GpuRigidData group = grouped ? new GpuRigidData(List.of(batch), new long[]{0}) : null;
        ByteBuffer vertexData = grouped ? group.vertices() : GpuBatchData.vertices(mesh, 0, false);
        ByteBuffer poseData;
        ByteBuffer remapData = null;
        if (grouped) {
            var range = group.ranges().getFirst();
            poseData = ByteBuffer.allocateDirect((int) group.storagePoseBytes(range)).order(ByteOrder.nativeOrder());
            group.writeStoragePose(poseData, range);
            remapData = ByteBuffer.allocateDirect((int) group.storageRemapBytes()).order(ByteOrder.nativeOrder());
            group.writeStorageRemap(remapData, range);
            remapData.flip();
        } else {
            poseData = ByteBuffer.allocateDirect(GpuBatchData.storagePoseBytes(1)).order(ByteOrder.nativeOrder());
            GpuBatchData.writeStoragePose(poseData, batch);
        }
        poseData.flip();
        long poseBytes = poseData.remaining();
        long remapBytes = remapData == null ? 0 : remapData.remaining();
        GpuBuffer remap = remapData == null ? null
                : device.createBuffer(() -> "OmniMorph storage remap probe", GpuBuffer.USAGE_UNIFORM, remapData);
        try (BufferMesh geometry = (BufferMesh) uploadGeometry(vertexData, GpuBatchData.indices(mesh));
             GpuBuffer pose = device.createBuffer(() -> "OmniMorph storage pose probe", GpuBuffer.USAGE_UNIFORM, poseData);
             var target = device.createTexture("OmniMorph storage diagnostic", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                     TextureFormat.RGBA8, 8, 8, 1, 1);
             var view = device.createTextureView(target);
             var readback = device.createBuffer(() -> "OmniMorph storage pixels", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, 8 * 8 * 4)) {
            var encoder = device.createCommandEncoder();
            try (var pass = encoder.createRenderPass(() -> "OmniMorph storage self-test", view, OptionalInt.of(0xff000000))) {
                pass.setPipeline(pipeline);
                pass.setVertexBuffer(0, geometry.vertices());
                pass.setIndexBuffer(geometry.indices(), VertexFormat.IndexType.INT);
                GpuStorageSupport.bind(GpuShaderProgram.STORAGE_BINDING, pose, 0, poseBytes);
                if (remap != null) {
                    GpuStorageSupport.bind(GpuShaderProgram.REMAP_BINDING, remap, 0, remapBytes);
                }
                try {
                    pass.drawIndexed(0, 0, 6, 1);
                } finally {
                    GpuStorageSupport.unbind(GpuShaderProgram.STORAGE_BINDING);
                    GpuStorageSupport.unbind(GpuShaderProgram.REMAP_BINDING);
                }
            }
            encoder.copyTextureToBuffer(target, readback, 0, () -> {}, 0);
            try (GpuFence completed = encoder.createFence()) {
                if (!completed.awaitCompletion(1_000_000_000L)) {
                    throw new IllegalStateException("Acceleration storage self-test timed out");
                }
            }
            RenderSystem.executePendingTasks();
            try (var mapped = encoder.mapBuffer(readback, true, false)) {
                ByteBuffer pixels = mapped.data();
                if (!green(pixels, 6, 4) || green(pixels, 2, 4)) {
                    throw new IllegalStateException("Acceleration storage self-test pixel mismatch");
                }
            }
        } finally {
            if (remap != null) {
                remap.close();
            }
        }
    }

    private void selfTest(boolean grouped) {
        assertThread();
        Identifier location = Identifier.fromNamespaceAndPath("omni-morph", "acceleration/activation_probe" + (grouped ? "_rigid" : ""));
        RenderPipeline pipeline = grouped ? diagnosticGroupPipeline : diagnosticPipeline;
        if (pipeline == null) {
            pipeline = RenderPipeline.builder().withLocation(location).withVertexShader(location).withFragmentShader(location)
                .withUniform(GpuShaderProgram.UNIFORM, com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
                .withColorTargetState(ColorTargetState.DEFAULT).withDepthStencilState(Optional.empty()).withCull(false)
                .withVertexFormat(GpuShaderProgram.format(), VertexFormat.Mode.TRIANGLES).build();
            if (grouped) {
                diagnosticGroupPipeline = pipeline;
            } else {
                diagnosticPipeline = pipeline;
            }
        }
        String vertex = grouped ? GpuShaderProgram.wrapRigidEntitySource(PROBE_VERTEX) : GpuShaderProgram.wrapEntitySource(PROBE_VERTEX);
        if (!device.precompilePipeline(pipeline, (id, type) -> type == ShaderType.VERTEX ? vertex : PROBE_FRAGMENT).isValid()) {
            throw new IllegalStateException("Acceleration diagnostic shader rejected");
        }
        MeshHandle mesh = probeMesh();
        DrawBatch batch = new DrawBatch(AccelerationService.INSTANCE.capture(new AccelerationOwner(), "activation-probe"),
                mesh, new PoseSnapshot(FiguraMat4.of().translate(0.5, 0, 0), FiguraMat3.of()));
        GpuRigidData group = grouped ? new GpuRigidData(List.of(batch), new long[]{0}) : null;
        ByteBuffer vertexData = grouped ? group.vertices() : GpuBatchData.vertices(mesh, 0, false);
        ByteBuffer poseData = grouped ? group.pose(group.ranges().getFirst()) : GpuBatchData.pose(batch);
        try (BufferMesh geometry = (BufferMesh) uploadGeometry(vertexData, GpuBatchData.indices(mesh));
             BufferPose pose = (BufferPose) uploadPose(poseData);
             var target = device.createTexture("OmniMorph activation diagnostic", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                     TextureFormat.RGBA8, 8, 8, 1, 1);
             var view = device.createTextureView(target);
             var readback = device.createBuffer(() -> "OmniMorph activation pixels", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, 8 * 8 * 4)) {
            var encoder = device.createCommandEncoder();
            try (var pass = encoder.createRenderPass(() -> "OmniMorph activation self-test", view, OptionalInt.of(0xff000000))) {
                pass.setPipeline(pipeline);
                pass.setUniform(GpuShaderProgram.UNIFORM, pose.buffer());
                pass.setVertexBuffer(0, geometry.vertices());
                pass.setIndexBuffer(geometry.indices(), VertexFormat.IndexType.INT);
                pass.drawIndexed(0, 0, 6, 1);
            }
            encoder.copyTextureToBuffer(target, readback, 0, () -> {}, 0);
            try (GpuFence completed = encoder.createFence()) {
                if (!completed.awaitCompletion(1_000_000_000L)) {
                    throw new IllegalStateException("Acceleration activation self-test timed out");
                }
            }
            RenderSystem.executePendingTasks();
            try (var mapped = encoder.mapBuffer(readback, true, false)) {
                ByteBuffer pixels = mapped.data();
                if (!green(pixels, 6, 4) || green(pixels, 2, 4)) {
                    throw new IllegalStateException("Acceleration matrix self-test pixel mismatch");
                }
            }
        }
    }

    private static final String PROBE_VERTEX = """
            #version 330
            in vec3 Position;
            in vec3 Normal;
            in vec2 UV0;
            in vec4 Color;
            in ivec2 UV1;
            in ivec2 UV2;
            void main() { gl_Position = vec4(Position, 1.0); }
            """;
    private static final String PROBE_FRAGMENT = """
            #version 330
            out vec4 fragColor;
            void main() { fragColor = vec4(0.125, 0.875, 0.25, 1.0); }
            """;

    /** A unit quad; the probe pose moves it right by half the viewport, so only the right side turns green. */
    private static MeshHandle probeMesh() {
        return new MeshHandle(new double[]{
                -0.5, -0.5, 0, 1, 0, 1, 0, 0, 0,
                0.5, -0.5, 0, 1, 0, 1, 0, 1, 0,
                0.5, 0.5, 0, 1, 0, 1, 0, 1, 1,
                -0.5, 0.5, 0, 1, 0, 1, 0, 0, 1}, 4);
    }

    private static boolean green(ByteBuffer pixels, int x, int y) {
        int offset = (y * 8 + x) * 4;
        return Byte.toUnsignedInt(pixels.get(offset + 1)) > 180 && Byte.toUnsignedInt(pixels.get(offset)) < 60;
    }
}
