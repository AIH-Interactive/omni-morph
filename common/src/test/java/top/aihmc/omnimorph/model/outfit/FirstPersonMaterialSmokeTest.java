package top.aihmc.omnimorph.model.outfit;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.local.LocalAvatarLoader;
import org.figuramc.figura.model.FiguraModelPart;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.model.rendering.PartFilterScheme;
import org.joml.Matrix4f;
import top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer;
import top.aihmc.omnimorph.model.rendering.material.MaterialVertexFormat;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;

import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real first-person drawFigura -> pose capture -> renderer -> custom attribute writer. No pixel claim. */
public final class FirstPersonMaterialSmokeTest {
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("first-person-material-");
        try {
            Files.createDirectories(temporary.resolve("model"));
            for (String relative : List.of("model/model.bbmodel", "script.lua")) {
                try (var input = FirstPersonMaterialSmokeTest.class.getResourceAsStream(
                        "/assets/omni-morph/cosmetic_templates/accessories/prismatic-badge/" + relative)) {
                    Files.copy(java.util.Objects.requireNonNull(input), temporary.resolve(relative));
                }
            }
            Files.writeString(temporary.resolve("avatar.json"), "{\"name\":\"First-person material fixture\"}");
            YsmMaterialNativeSmokeTest.replayFiguraAcceleration(temporary, base -> {
                try {
                    verify(base, temporary);
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            });
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private static void verify(Avatar base, Path source) throws Exception {
        PerformanceHttpSmokeTest.verify(base);
        var client = Minecraft.getInstance();
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var rendererField = field(Minecraft.class, "gameRenderer");
        var previousRenderer = rendererField.get(client);
        var cameraRenderer = (GameRenderer) unsafe.allocateInstance(GameRenderer.class);
        field(GameRenderer.class, "mainCamera").set(cameraRenderer, new Camera());
        rendererField.set(client, cameraRenderer);
        var deviceField = field(RenderSystem.class, "DEVICE");
        Object previousDevice = deviceField.get(null);
        var recorder = new MaterialCompiler((GpuDevice) previousDevice);
        var previousCache = RenderSystem.fallbackPipelineCache;
        var previousCurrentCache = RenderSystem.setCurrentPipelineCache(null);
        var cache = new com.mojang.blaze3d.pipeline.PipelineCache(recorder.device,
                top.aihmc.omnimorph.model.rendering.material.AvatarMaterial.sources(new ShaderSource() {
                    @Override
                    public String getShader(net.minecraft.resources.Identifier id,
                            com.mojang.renderpearl.api.pipeline.ShaderType type) {
                        throw new AssertionError("Unexpected ordinary shader: " + id);
                    }

                    @Override
                    public CachedIncludeSource getInclude(net.minecraft.resources.Identifier id) {
                        return null;
                    }

                    @Override
                    public void close() {
                    }
                }));
        RenderSystem.fallbackPipelineCache = cache;
        Avatar accessory = null;
        try {
            var limits = OutfitLimits.defaults();
            var baseResource = resource();
            var accessoryResource = resource();
            OutfitNativeRenderSmokeTest.addQuad(base);
            var indexes = new SkeletonIndex.Cache();
            var skeleton = new FiguraSkeletonAdapter((ImmediateFiguraRenderer) base.renderer,
                    baseResource, null, indexes, limits).binding().definition();
            var anchor = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null,
                    skeleton.reference(null, skeleton.uniqueNamed("Badge").orElseThrow()), null, null, null);
            var instance = new OutfitConfig.Instance(UUID.randomUUID(), accessoryResource, 1, true,
                    anchor, OutfitConfig.Transform.identity(), List.of(), Map.of(), Map.of());
            var context = new CosmeticInstanceContext(base.owner, UUID.randomUUID(), base.owner, instance,
                    limits, CosmeticInstanceContext.Scope.DRAFT);
            accessory = Avatar.createCosmetic(context, base.permissions, "first-person material accessory");
            accessory.load(LocalAvatarLoader.compileFiguraAvatar(source, UUID.randomUUID()));
            accessory.initializePreviewPlayer(null);
            require(!base.scriptError && !accessory.scriptError, "Both authored materials must initialize: base=" + base.errorText + "; accessory=" + accessory.errorText);
            var basePart = find(base.renderer.root, "Badge");
            var accessoryPart = find(accessory.renderer.root, "Badge");
            var baseMaterial = basePart.shaderMaterial;
            var accessoryMaterial = accessoryPart.shaderMaterial;
            deviceField.set(null, recorder.device);
            baseMaterial.renderType();
            accessoryMaterial.renderType();
            // Keep ordinary and custom-material geometry in the same discard pass.
            OutfitNativeRenderSmokeTest.addQuad(accessory);
            for (Avatar avatar : List.of(base, accessory)) {
                lua(avatar, """
                        fp_pre, fp_post = 0, 0
                        local badge = models.model.model.Cosmetic.Badge
                        badge:setPreRender(function()
                            fp_pre = fp_pre + 1
                            badge:setPos(fp_pre * 16, 0, 0)
                            badge:setMaterialTime(0.75)
                        end)
                        badge:setPostRender(function() fp_post = fp_post + 1 end)
                        """);
            }
            base.renderMode = EntityRenderMode.FIRST_PERSON_WORLD;
            base.renderer.allowMatrixUpdate = true;
            var config = new OutfitConfig(1, 0, baseResource, List.of(instance), List.of(),
                    Map.of(accessoryResource.identity(), 1), false);
            var state = new AvatarRenderState();
            state.lightCoords = 0x00f000f0;
            var draw = OutfitFirstPersonScripts.class.getDeclaredMethod("drawFigura",
                    OutfitComposition.Frame.class, Avatar.class, AvatarRenderState.class,
                    net.minecraft.client.player.LocalPlayer.class, float.class, Matrix4f.class,
                    float.class, top.aihmc.omnimorph.model.rendering.FiguraVertexSource.class);
            draw.setAccessible(true);
            Matrix4f previousPose = null;
            try (var composition = new OutfitComposition(config, limits, null,
                    Map.of(instance.instanceId(), accessory), indexes)) {
                for (int iteration = 0; iteration < 2; iteration++) {
                    top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
                    base.render.reset(Integer.MAX_VALUE);
                    accessory.render.reset(Integer.MAX_VALUE);
                    try (var frame = composition.retain()) {
                        frame.prepareFiguraBase(base);
                        // Production factory and exact crash chain, not a replacement test sink.
                        draw.invoke(null, frame, base, state, null, 0.5f, new Matrix4f(), 0f,
                                OutfitFirstPersonScripts.discardBuffers());
                    }
                    var pose = basePart.savedPartToWorldMat.toMatrix4f();
                    require(previousPose == null || !previousPose.equals(pose, 0.0001f),
                            "Full-body pose must refresh on successive first-person frames");
                    previousPose = pose;
                }
                for (Avatar avatar : List.of(base, accessory)) {
                    String role = avatar == base ? "base" : "accessory";
                    lua(avatar, "assert(fp_pre == 2 and fp_post == 2, 'Native part callbacks: '.."
                            + "fp_pre..'/'..fp_post..' avatar=" + role + "')");
                }
                require(basePart.shaderMaterial == baseMaterial && accessoryPart.shaderMaterial == accessoryMaterial,
                        "The first-person pass must retain both shader materials");
                // A normal draw still writes actual custom attributes after the discard passes.
                try (var storage = new ByteBufferBuilder(4096)) {
                    var format = MaterialVertexFormat.FORMAT;
                    var builder = new BufferBuilder(storage, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.QUADS, format);
                    var discard = OutfitFirstPersonScripts.discardBuffers();
                    base.renderMode = EntityRenderMode.RENDER;
                    base.render.reset(Integer.MAX_VALUE);
                    base.render(null, 0, 0.5f, 1, new PoseStack(),
                            type -> type.pipeline().getVertexFormatBinding(0) == format ? builder : discard.getBuffer(type),
                            state.lightCoords, 0, null, PartFilterScheme.MODEL, false, false);
                    try (var mesh = builder.buildOrThrow()) {
                        var vertices = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                        require(vertices.remaining() == 4 * format.getVertexSize(),
                                "The visible material quad must remain");
                        for (int index = 0; index < 4; index++) {
                            int offset = index * format.getVertexSize();
                            require(vertices.getFloat(offset + MaterialVertexFormat.TIME.offset()) == 0.75f,
                                    "Normal drawing must still write authored material time");
                            float uv = vertices.getFloat(offset + MaterialVertexFormat.SECONDARY_UV.offset());
                            require(uv >= 0.049f && uv <= 0.951f, "Normal drawing must retain the authored secondary UV");
                        }
                    }
                }
                require(recorder.pipelines.size() == 2, "Both native material pipeline identities must remain");
            }
            System.out.println("FIRST_PERSON_MATERIAL_OK chain=drawFigura+capture+renderer+writer"
                    + " callbacks=base+accessory pose=refreshed normal_attributes=retained"
                    + " pixels=MANUAL/PENDING");
        } finally {
            deviceField.set(null, recorder.device);
            if (accessory != null) {
                accessory.clean();
            }
            base.clean();
            deviceField.set(null, previousDevice);
            cache.close();
            RenderSystem.fallbackPipelineCache = previousCache;
            RenderSystem.setCurrentPipelineCache(previousCurrentCache);
            rendererField.set(client, previousRenderer);
        }
    }

    private static OutfitConfig.ResourceRef resource() {
        return new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(),
                null, null, null, "a".repeat(64), OutfitConfig.Format.FIGURA);
    }

    private static FiguraModelPart find(FiguraModelPart part, String name) {
        if (part.name.equals(name)) {
            return part;
        }
        for (var child : part.children) {
            var found = find(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void lua(Avatar avatar, String script) {
        avatar.render.reset(Integer.MAX_VALUE);
        require(avatar.luaRuntime.run(avatar.loadScript("first-person material contract", script), avatar.render) != null
                && !avatar.scriptError, "Lua contract failed: " + avatar.errorText);
    }

    private static java.lang.reflect.Field field(Class<?> type, String name) throws Exception {
        var result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Native shaderc compilation and native PipelineCache; GPU execution remains a recorder. */
    private static final class MaterialCompiler implements java.lang.reflect.InvocationHandler {
        final java.util.Set<RenderPipeline> pipelines = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        final GpuDevice device;
        private final GpuDevice textures;

        MaterialCompiler(GpuDevice textures) {
            this.textures = textures;
            device = (GpuDevice) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{GpuDevice.class}, this);
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            if (method.getName().equals("getDeviceInfo")) {
                var info = textures.getDeviceInfo();
                return new com.mojang.renderpearl.api.device.DeviceInfo(info.name(), info.vendorName(),
                        info.driverInfo(), true, info.backendName(), info.timestampPeriod(), info.limits(),
                        info.features(), info.underlyingExtensions(),
                        new com.mojang.renderpearl.api.device.HintsAndWorkarounds(false, false, false, false),
                        info.type());
            }
            if (!method.getName().equals("compilePipeline")) {
                try {
                    return method.invoke(textures, args);
                } catch (java.lang.reflect.InvocationTargetException error) {
                    throw error.getCause();
                }
            }
            var pipeline = (RenderPipeline) args[0];
            var source = (ShaderSource) args[1];
            if (pipelines.isEmpty()) {
                RenderpearlShaderSmokeTest.verify();
            }
            require(pipeline.getVertexFormatBinding(0) == MaterialVertexFormat.FORMAT,
                    "Native material format must remain");
            try (var compiler = new com.mojang.renderpearl.frontend.shaders.GlslCompiler(true, false)) {
                for (var shader : pipeline.getShaders().entrySet()) {
                    String code = source.getShader(shader.getValue(), shader.getKey());
                    try (var module = compiler.compileToSpv(shader.getValue().toString(), code,
                            shader.getKey(), pipeline.getShaderDefines(), source)) {
                        require(module != null, "Actual author shader compiles to SPIR-V");
                        if (shader.getKey() == com.mojang.renderpearl.api.pipeline.ShaderType.VERTEX) {
                            for (var input : module.reflect().inputs()) {
                                require(MaterialVertexFormat.FORMAT.contains(input.name()),
                                        "Author attribute has a matching native buffer element: " + input.name());
                            }
                        }
                    }
                }
            }
            pipelines.add(pipeline);
            com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline compiled =
                    new com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline() {
                        private boolean closed;

                        @Override
                        public boolean isClosed() {
                            return closed;
                        }

                        @Override
                        public void close() {
                            closed = true;
                        }
                    };
            com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline.Pending pending = () -> compiled;
            return java.util.concurrent.CompletableFuture.completedFuture(pending);
        }
    }
}
