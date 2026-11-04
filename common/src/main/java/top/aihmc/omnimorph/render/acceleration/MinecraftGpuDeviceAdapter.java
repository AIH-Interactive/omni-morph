package top.aihmc.omnimorph.render.acceleration;

import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.GpuFence;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.device.GpuDevice;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.BiFunction;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;

/** All production uploads/draws use Blaze3D. The only readback is the bounded activation diagnostic. */
final class MinecraftGpuDeviceAdapter implements GpuDeviceAdapter {
    private final GpuDevice device;
    private final ShaderSource sources;
    private final Map<RenderPipeline, CompiledRenderPipeline> programs = new IdentityHashMap<>();
    private final Map<RenderPipeline, BiFunction<Identifier, ShaderType, String>> programSources = new IdentityHashMap<>();
    private final Map<StageKey, RenderPipeline> stagePipelines = new HashMap<>();

    private record StageKey(RenderPipeline accelerated, RenderPipeline stage) {
    }
    private final Map<RenderPipeline, Material> compiled = new IdentityHashMap<>();
    private final Map<RenderPipeline, Material> compiledGroups = new IdentityHashMap<>();
    // Smaller pose block variants: at most three per full-layout pipeline, under their own budget.
    private final Map<VariantKey, Material> compiledVariants = new HashMap<>();
    private final Map<RenderPipeline, Material> compiledStorage = new IdentityHashMap<>();
    private final Map<RenderPipeline, Material> compiledStorageGroups = new IdentityHashMap<>();
    private final Map<RenderPipeline, Material> compiledStorageModels = new IdentityHashMap<>();
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

    MinecraftGpuDeviceAdapter(GpuDevice device, ShaderSource sources) {
        this.device = Objects.requireNonNull(device);
        this.sources = Objects.requireNonNull(sources);
    }

    private CompiledRenderPipeline compilePipeline(
            RenderPipeline pipeline, BiFunction<Identifier, ShaderType, String> shader) {
        var cached = programs.get(pipeline);
        if (cached != null) {
            return cached;
        }
        var source = new ShaderSource() {
            @Override
            public String getShader(Identifier id, ShaderType type) {
                return shader.apply(id, type);
            }

            @Override
            public CachedIncludeSource getInclude(Identifier id) {
                return sources.getInclude(id);
            }

            @Override
            public void close() {
                // The game's reload cache owns the shared include sources.
            }
        };
        // Match vanilla PipelineCache: only CPU compilation runs on the background executor.
        // finishCompile runs here on the render thread, so no render-thread future is awaited.
        var program = device.compilePipeline(pipeline, source, net.minecraft.util.Util.backgroundExecutor())
                .join().finishCompile();
        if (program != null) {
            programs.put(pipeline, program);
            programSources.put(pipeline, shader);
        }
        return program;
    }

    void closePrograms() {
        assertThread();
        programs.values().forEach(CompiledRenderPipeline::close);
        programs.clear();
        programSources.clear();
        stagePipelines.clear();
    }

    private CompiledRenderPipeline program(Material material,
            RenderPipeline stage) {
        if (stage == material.original()) {
            return Objects.requireNonNull(programs.get(material.accelerated()));
        }
        var key = new StageKey(material.accelerated(), Objects.requireNonNull(stage));
        RenderPipeline pipeline = stagePipelines.computeIfAbsent(key,
                ignored -> GpuShaderProgram.stage(material.accelerated(), stage));
        return Objects.requireNonNull(compilePipeline(pipeline, programSources.get(material.accelerated())),
                "Acceleration stage shader compilation failed");
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
    public boolean shaderSourcesReady() {
        return sources.getShader(RenderPipelines.ENTITY_CUTOUT.getShaders().get(ShaderType.VERTEX), ShaderType.VERTEX) != null;
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

    record BufferStorage(GpuBuffer buffer, long bytes) implements Storage {
        @Override
        public void close() {
            buffer.close();
        }
    }

    @Override
    public Storage createStorage(long capacity) {
        assertThread();
        return new BufferStorage(device.createBuffer(() -> "OmniMorph pose storage arena",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST, capacity), capacity);
    }

    @Override
    public void writeStorage(Storage storage, long offset, ByteBuffer data) {
        assertThread();
        BufferStorage arena = (BufferStorage) storage;
        if (offset < 0 || offset + data.remaining() > arena.bytes() || arena.buffer().isClosed()) {
            throw new IllegalStateException("Pose storage write exceeds its arena");
        }
        device.createCommandEncoder().writeToBuffer(arena.buffer().slice(offset, data.remaining()), data);
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
        String originalSource = sources.getShader(original.getShaders().get(ShaderType.VERTEX), ShaderType.VERTEX);
        String source = grouped
                ? GpuShaderProgram.wrapStorageRigidEntitySource(originalSource)
                : GpuShaderProgram.wrapStorageEntitySource(originalSource);
        RenderPipeline accelerated = GpuShaderProgram.pipeline(original, location, false);
        if (compilePipeline(accelerated, (id, type) -> id.equals(location) && type == ShaderType.VERTEX
                ? source : sources.getShader(id, type)) == null) {
            throw new IllegalStateException("Acceleration storage entity shader validation failed");
        }
        Material material = new Material(original, accelerated);
        cache.put(original, material);
        return material;
    }

    @Override
    public Material compileStorageModel(RenderPipeline original) {
        assertThread();
        if (storageLimits == null || !storageLimits.models()) {
            throw new IllegalStateException("Model storage draws were not activated");
        }
        Material existing = compiledStorageModels.get(original);
        if (existing != null) {
            return existing;
        }
        if (compiledStorageModels.size() >= 64) {
            throw new IllegalStateException("Acceleration model pipeline cache budget exhausted");
        }
        Identifier location = Identifier.fromNamespaceAndPath("omni-morph", "acceleration/" + original.getLocation().getNamespace()
                + "/" + original.getLocation().getPath() + "/model");
        String source = GpuShaderProgram.wrapStorageModelEntitySource(sources.getShader(original.getShaders().get(ShaderType.VERTEX), ShaderType.VERTEX));
        RenderPipeline accelerated = GpuShaderProgram.pipeline(original, location, false);
        if (compilePipeline(accelerated, (id, type) -> id.equals(location) && type == ShaderType.VERTEX
                ? source : sources.getShader(id, type)) == null) {
            throw new IllegalStateException("Acceleration model entity shader validation failed");
        }
        Material material = new Material(original, accelerated);
        compiledStorageModels.put(original, material);
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
        String originalSource = sources.getShader(original.getShaders().get(ShaderType.VERTEX), ShaderType.VERTEX);
        String source = grouped
                ? GpuShaderProgram.wrapRigidEntitySource(originalSource, parts)
                : GpuShaderProgram.wrapEntitySource(originalSource, parts);
        RenderPipeline accelerated = GpuShaderProgram.pipeline(original, location);
        if (compilePipeline(accelerated, (id, type) -> id.equals(location) && type == ShaderType.VERTEX
                ? source : sources.getShader(id, type)) == null) {
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
        try (DrawPass pass = openDrawPass(material, context)) {
            pass.draw(mesh, pose, indices, firstIndex, indexCount, issuingDraw);
        }
    }

    @Override
    public DrawPass openDrawPass(Material material, GpuDrawContext context) {
        assertThread();
        return new BufferDrawPass(material, context);
    }

    /** Pipeline, scissor, default uniforms and textures are bound once; each draw binds only buffers and pose. */
    private final class BufferDrawPass implements DrawPass {
        private final Material material;
        private final RenderPass pass;

        BufferDrawPass(Material material, GpuDrawContext context) {
            this.material = material;
            pass = Objects.requireNonNull(context.pass());
            try {
                pass.setPipeline(program(material, context.pipeline()));
                if (context.scissorEnabled()) {
                    pass.enableScissor(context.scissorX(), context.scissorY(), context.scissorWidth(), context.scissorHeight());
                }
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", Objects.requireNonNull(context.dynamicTransform()));
                for (var binding : context.textures()) {
                    pass.setUniform(binding.name(), binding.textureView(), binding.sampler());
                }
            } catch (RuntimeException | Error failure) {
                throw failure;
            }
        }

        @Override
        public Material material() {
            return material;
        }

        @Override
        public void draw(Mesh mesh, Pose pose, Indices indices, int firstIndex, int indexCount, Runnable issuingDraw) {
            assertThread();
            BufferMesh geometry = (BufferMesh) mesh;
            StoragePose storagePose = pose instanceof StoragePose arenaPose ? arenaPose : null;
            if (storagePose == null) {
                pass.setUniform(GpuShaderProgram.UNIFORM, ((BufferPose) pose).buffer());
            }
            pass.setVertexBuffer(0, geometry.vertices().slice());
            pass.setIndexBuffer(indices == null ? geometry.indices() : ((BufferIndices) indices).buffer(), IndexType.INT);
            if (storagePose != null) {
                GpuBuffer arena = ((BufferStorage) storagePose.storage).buffer();
                GpuStorageSupport.bind(pass, arena, storagePose.offsets);
            }
            issuingDraw.run();
            pass.drawIndexed(indexCount, 1, firstIndex, 0, 0);
        }

        @Override
        public void close() {
            // Minecraft owns the enclosing feature pass and its attachments.
        }
    }

    @Override
    public GpuFence fence() {
        assertThread();
        return device.createCommandEncoder().createFence();
    }

    @Override
    public void submit() {
        assertThread();
        device.createCommandEncoder().submit();
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
                storageSelfTest(StorageProbe.ENTITY, limits);
                storageSelfTest(StorageProbe.RIGID, limits);
                boolean models = limits.models();
                if (models) {
                    try {
                        storageSelfTest(StorageProbe.MODEL, limits);
                    } catch (RuntimeException failure) {
                        // Model draws are a further optional layer; plain storage blocks stay active.
                        models = false;
                        org.figuramc.figura.FiguraMod.LOGGER.warn("GPU model storage self-test failed; model draws stay off", failure);
                    }
                }
                limits = new GpuStorageSupport.Limits(limits.offsetAlignment(), limits.maxBlockBytes(), models);
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

    private enum StorageProbe { ENTITY, RIGID, MODEL }

    private void storageSelfTest(StorageProbe probe, GpuStorageSupport.Limits limits) {
        assertThread();
        Identifier location = Identifier.fromNamespaceAndPath("omni-morph",
                "acceleration/activation_probe_storage_" + probe.name().toLowerCase(java.util.Locale.ROOT));
        RenderPipeline pipeline = RenderPipeline.builder().withLocation(location).withVertexShader(location).withFragmentShader(location)
                .withColorTargetState(ColorTargetState.DEFAULT).withDepthStencilState(Optional.empty()).withCull(false)
                .withVertexBinding(0, GpuShaderProgram.format()).withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withBindGroupLayout(GpuShaderProgram.storageLayout()).build();
        String vertex = switch (probe) {
            case ENTITY -> GpuShaderProgram.wrapStorageEntitySource(PROBE_VERTEX);
            case RIGID -> GpuShaderProgram.wrapStorageRigidEntitySource(PROBE_VERTEX);
            case MODEL -> GpuShaderProgram.wrapStorageModelEntitySource(PROBE_VERTEX);
        };
        if (compilePipeline(pipeline, (id, type) -> type == ShaderType.VERTEX ? vertex : PROBE_FRAGMENT) == null) {
            throw new IllegalStateException("Acceleration storage diagnostic shader rejected");
        }
        MeshHandle mesh = probeMesh();
        DrawBatch batch = new DrawBatch(AccelerationService.INSTANCE.capture(new AccelerationOwner(), "activation-probe"),
                mesh, new PoseSnapshot(FiguraMat4.of().translate(0.5, 0, 0), FiguraMat3.of()));
        ByteBuffer vertexData;
        ByteBuffer poseData;
        ByteBuffer remapData = null;
        if (probe == StorageProbe.RIGID) {
            GpuRigidData group = new GpuRigidData(List.of(batch), new long[]{0});
            vertexData = group.vertices();
            poseData = ByteBuffer.allocateDirect((int) group.storagePoseBytes()).order(ByteOrder.nativeOrder());
            group.writeStoragePose(poseData);
            remapData = ByteBuffer.allocateDirect((int) group.storageRemapBytes()).order(ByteOrder.nativeOrder());
            group.writeStorageRemap(remapData);
            remapData.flip();
        } else if (probe == StorageProbe.MODEL) {
            GpuModelData model = new GpuModelData(List.of(batch), new long[]{0});
            vertexData = model.vertices();
            poseData = ByteBuffer.allocateDirect((int) model.storagePoseBytes()).order(ByteOrder.nativeOrder());
            model.writeStoragePose(poseData);
            remapData = ByteBuffer.allocateDirect((int) model.recordBytes()).order(ByteOrder.nativeOrder());
            model.writeRecords(remapData);
            remapData.flip();
        } else {
            vertexData = GpuBatchData.vertices(mesh, 0, false);
            poseData = ByteBuffer.allocateDirect(GpuBatchData.storagePoseBytes(1)).order(ByteOrder.nativeOrder());
            GpuBatchData.writeStoragePose(poseData, batch);
        }
        poseData.flip();
        int alignment = limits.offsetAlignment();
        int capacity = Math.toIntExact(limits.maxBlockBytes());
        int poseBytes = poseData.remaining();
        int secondaryBytes = remapData == null ? 0 : remapData.remaining();
        // Read near the end of the largest arena: a tiny successful buffer would not validate its capacity.
        int poseOffset = (capacity - poseBytes - secondaryBytes - 2 * alignment) & -alignment;
        var poseSlice = new GpuPoseArena.Slice(poseOffset, poseBytes);
        int secondaryOffset = (poseOffset + poseBytes + alignment - 1) & -alignment;
        var secondarySlice = remapData == null ? null : new GpuPoseArena.Slice(secondaryOffset, secondaryBytes);
        var offsets = new GpuPoseArena.Slice(0, GpuStorageSupport.OFFSETS_BYTES);
        ByteBuffer arenaData = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
        arenaData.position(poseOffset).put(poseData);
        if (remapData != null) {
            arenaData.position(secondaryOffset).put(remapData);
        }
        arenaData.position(0);
        GpuStorageSupport.writeOffsets(arenaData, poseSlice,
                probe == StorageProbe.RIGID ? secondarySlice : null,
                probe == StorageProbe.MODEL ? secondarySlice : null);
        arenaData.clear();
        try (BufferMesh geometry = (BufferMesh) uploadGeometry(vertexData, GpuBatchData.indices(mesh));
             GpuBuffer arena = device.createBuffer(() -> "OmniMorph texel arena probe",
                     GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER, arenaData);
             var target = device.createTexture("OmniMorph storage diagnostic", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                     GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
             var view = device.createTextureView(target);
             var readback = device.createBuffer(() -> "OmniMorph storage pixels", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, 8 * 8 * 4)) {
            var encoder = device.createCommandEncoder();
            try (var pass = encoder.createRenderPass(() -> "OmniMorph storage self-test", view, Optional.of(new org.joml.Vector4f(0, 0, 0, 1)))) {
                pass.setPipeline(Objects.requireNonNull(programs.get(pipeline)));
                pass.setVertexBuffer(0, geometry.vertices().slice());
                pass.setIndexBuffer(geometry.indices(), IndexType.INT);
                GpuStorageSupport.bind(pass, arena, offsets);
                pass.drawIndexed(6, 1, 0, 0, 0);
            }
            encoder.copyTextureToBuffer(target, readback, 0, () -> {}, 0);
            try (GpuFence completed = encoder.createFence()) {
                // Fences track submit batches in 26.2; submit the captured batch before blocking.
                encoder.submit();
                if (!completed.awaitCompletion(1_000_000_000L)) {
                    throw new IllegalStateException("Acceleration storage self-test timed out");
                }
            }
            RenderSystem.executePendingTasks();
            try (var mapped = readback.map(true, false)) {
                ByteBuffer pixels = mapped.data();
                if (!green(pixels, 6, 4) || green(pixels, 2, 4)) {
                    throw new IllegalStateException("Acceleration storage self-test pixel mismatch");
                }
            }
        }
    }

    private void selfTest(boolean grouped) {
        assertThread();
        Identifier location = Identifier.fromNamespaceAndPath("omni-morph", "acceleration/activation_probe" + (grouped ? "_rigid" : ""));
        RenderPipeline pipeline = grouped ? diagnosticGroupPipeline : diagnosticPipeline;
        if (pipeline == null) {
            pipeline = RenderPipeline.builder().withLocation(location).withVertexShader(location).withFragmentShader(location)
                .withBindGroupLayout(BindGroupLayout.builder().withUniform(GpuShaderProgram.UNIFORM, com.mojang.renderpearl.api.pipeline.UniformType.UNIFORM_BUFFER).build())
                .withColorTargetState(ColorTargetState.DEFAULT).withDepthStencilState(Optional.empty()).withCull(false)
                .withVertexBinding(0, GpuShaderProgram.format()).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();
            if (grouped) {
                diagnosticGroupPipeline = pipeline;
            } else {
                diagnosticPipeline = pipeline;
            }
        }
        String vertex = grouped ? GpuShaderProgram.wrapRigidEntitySource(PROBE_VERTEX) : GpuShaderProgram.wrapEntitySource(PROBE_VERTEX);
        if (compilePipeline(pipeline, (id, type) -> type == ShaderType.VERTEX ? vertex : PROBE_FRAGMENT) == null) {
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
                     GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
             var view = device.createTextureView(target);
             var readback = device.createBuffer(() -> "OmniMorph activation pixels", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, 8 * 8 * 4)) {
            var encoder = device.createCommandEncoder();
            try (var pass = encoder.createRenderPass(() -> "OmniMorph activation self-test", view, Optional.of(new org.joml.Vector4f(0, 0, 0, 1)))) {
                pass.setPipeline(Objects.requireNonNull(programs.get(pipeline)));
                pass.setUniform(GpuShaderProgram.UNIFORM, pose.buffer());
                pass.setVertexBuffer(0, geometry.vertices().slice());
                pass.setIndexBuffer(geometry.indices(), IndexType.INT);
                pass.drawIndexed(6, 1, 0, 0, 0);
            }
            encoder.copyTextureToBuffer(target, readback, 0, () -> {}, 0);
            try (GpuFence completed = encoder.createFence()) {
                // Create the fence before submitting so it refers to the diagnostic commands.
                encoder.submit();
                if (!completed.awaitCompletion(1_000_000_000L)) {
                    throw new IllegalStateException("Acceleration activation self-test timed out");
                }
            }
            RenderSystem.executePendingTasks();
            try (var mapped = readback.map(true, false)) {
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
