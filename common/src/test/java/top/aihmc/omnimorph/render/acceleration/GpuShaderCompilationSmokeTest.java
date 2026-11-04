package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.DeviceFeatures;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.device.HintsAndWorkarounds;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import com.mojang.renderpearl.frontend.shaders.GlslCompiler;
import com.mojang.renderpearl.frontend.shaders.PipelineBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import top.aihmc.omnimorph.lua.api.particle.CustomParticlePipelines;
import top.aihmc.omnimorph.model.rendering.texture.FiguraRenderTypes;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Real 26.3 shaderc/SPIR-V frontend and includes; creates no graphics device/window. */
public final class GpuShaderCompilationSmokeTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Include lazy production registrations, rather than only acceleration variants.
        FiguraRenderTypes.FiguraRenderPipelines.FIGURA_SOLID.getLocation();
        CustomParticlePipelines.registered();
        Class.forName("top.aihmc.omnimorph.model.ysm.YsmPreviewRenderTypes");
        Class.forName("top.aihmc.omnimorph.model.library.PreviewBoneHighlight$Lines");
        var activeDevice = RenderSystem.class.getDeclaredField("DEVICE");
        activeDevice.setAccessible(true);
        Object previousDevice = activeDevice.get(null);
        try (var sources = new Resources()) {
            String original = sources.getShader(Identifier.withDefaultNamespace("core/entity"), ShaderType.VERTEX);
            String[] variants = {
                    GpuShaderProgram.wrapEntitySource(original),
                    GpuShaderProgram.wrapRigidEntitySource(original),
                    GpuShaderProgram.wrapStorageEntitySource(original),
                    GpuShaderProgram.wrapStorageRigidEntitySource(original),
                    GpuShaderProgram.wrapStorageModelEntitySource(original)
            };
            // These are frontend feature configurations, not a claim of GPU/driver execution.
            for (boolean zeroToOne : new boolean[]{false, true}) {
                activeDevice.set(null, metadataDevice(zeroToOne));
                try (var compiler = new GlslCompiler(zeroToOne, zeroToOne)) {
                    for (int variant = 0; variant < variants.length; variant++) {
                        try (var module = compiler.compileToSpv("OMP variant " + variant,
                                variants[variant], ShaderType.VERTEX,
                                RenderPipelines.ENTITY_CUTOUT_CULL.getShaderDefines(), sources)) {
                            var reflection = module.reflect();
                            require(reflection.inputs().stream().allMatch(input -> input.name().startsWith("Omni")),
                                    "all original vertex inputs are replaced by the accelerated stream");
                            if (variant >= 2) {
                                require(reflection.descriptors().stream()
                                                .anyMatch(value -> value.name().equals("OmniMorphOffsets")),
                                        "SPIR-V retains the native offset UBO");
                                require(reflection.descriptors().stream()
                                                .anyMatch(value -> value.name().equals("OmniMorphData")
                                                        && value.type().dimensions() == 5),
                                        "SPIR-V retains the data channel as a texel buffer");
                            }
                        }
                    }
                }
                validatePipelines(sources, original, zeroToOne);
            }
        } finally {
            activeDevice.set(null, previousDevice);
        }
        System.out.println("PASS five production shader variants through actual 26.3 frontend, OpenGL/Vulkan feature configurations; no GPU execution");
    }

    private record Variant(String name, String source, boolean uniformPose) {
    }

    private static void validatePipelines(Resources sources, String original, boolean zeroToOne) {
        List<Variant> variants = new ArrayList<>();
        for (int parts : GpuBatchData.PART_TIERS) {
            variants.add(new Variant("entity_" + parts, GpuShaderProgram.wrapEntitySource(original, parts), true));
            variants.add(new Variant("rigid_" + parts, GpuShaderProgram.wrapRigidEntitySource(original, parts), true));
        }
        variants.add(new Variant("storage", GpuShaderProgram.wrapStorageEntitySource(original), false));
        variants.add(new Variant("storage_rigid", GpuShaderProgram.wrapStorageRigidEntitySource(original), false));
        variants.add(new Variant("storage_model", GpuShaderProgram.wrapStorageModelEntitySource(original), false));
        List<RenderPipeline> materials = List.of(RenderPipelines.ENTITY_SOLID,
                RenderPipelines.ENTITY_CUTOUT_CULL, RenderPipelines.ENTITY_TRANSLUCENT,
                RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE);
        var accepted = new AtomicInteger();
        var backend = (GpuDeviceBackend) Proxy.newProxyInstance(GpuDeviceBackend.class.getClassLoader(),
                new Class<?>[]{GpuDeviceBackend.class}, (instance, method, arguments) -> {
                    if (method.getName().equals("getDeviceInfo")) {
                        return deviceInfo(zeroToOne);
                    }
                    if (method.getName().equals("compilePipeline")) {
                        var info = (BackendRenderPipeline.CreateInfo) arguments[0];
                        require(info.shaders().size() == 2, "both complete production stages reach backend creation");
                        accepted.incrementAndGet();
                        // Stop after real frontend validation; this fixture does not compile a driver pipeline.
                        return BackendRenderPipeline.Pending.NULL;
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        try (var builder = new PipelineBuilder(backend)) {
            int registered = 0;
            for (RenderPipeline pipeline : java.util.stream.Stream.concat(
                    RenderPipelines.requiredPipelines().stream(), RenderPipelines.optionalPipelines().stream()).toList()) {
                if (pipeline.getLocation().getNamespace().equals("omni-morph")) {
                    validatePipeline(builder, pipeline, sources, accepted);
                    registered++;
                }
            }
            require(registered == 30, "all 30 registered custom production pipelines are compiled");
            System.out.println("PASS registered custom pipelines: " + registered + " "
                    + (zeroToOne ? "Vulkan" : "OpenGL") + " full frontend configurations");
            accepted.set(0);
            for (Variant variant : variants) {
                var location = Identifier.fromNamespaceAndPath("omni-morph", "test/" + variant.name());
                var shaderSource = new ShaderSource() {
                    @Override
                    public String getShader(Identifier id, ShaderType type) {
                        return id.equals(location) && type == ShaderType.VERTEX
                                ? variant.source() : sources.getShader(id, type);
                    }

                    @Override
                    public CachedIncludeSource getInclude(Identifier id) {
                        return sources.getInclude(id);
                    }

                    @Override
                    public void close() {
                        // Shared sources are owned by the enclosing test scope.
                    }
                };
                for (RenderPipeline material : materials) {
                    var pipeline = GpuShaderProgram.pipeline(material, location, variant.uniformPose());
                    validatePipeline(builder, pipeline, shaderSource, accepted);
                    if (material == RenderPipelines.ENTITY_TRANSLUCENT
                            || material == RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE) {
                        var oit = material == RenderPipelines.ENTITY_TRANSLUCENT
                                ? RenderPipelines.OIT_ENTITY : RenderPipelines.OIT_ENTITY_EMISSIVE;
                        for (var stage : List.of(oit.depthBoundsPipeline(), oit.transmittancePipeline(),
                                oit.accumulatePipeline())) {
                            validatePipeline(builder, GpuShaderProgram.stage(pipeline, stage), shaderSource, accepted);
                        }
                    }
                }
            }
        }
        require(accepted.get() == variants.size() * 10, "every material, palette and OIT stage is validated");
        System.out.println("PASS full pipeline frontend: " + accepted.get() + " "
                + (zeroToOne ? "Vulkan" : "OpenGL") + " configurations, vertex/fragment/layout/format/OIT; no driver execution");
    }

    private static void validatePipeline(PipelineBuilder builder, RenderPipeline pipeline,
                                         ShaderSource sources, AtomicInteger accepted) {
        int before = accepted.get();
        builder.compilePipeline(pipeline, sources, Runnable::run).join().finishCompile();
        require(accepted.get() == before + 1, "frontend rejected " + pipeline.getLocation());
    }

    private static DeviceInfo deviceInfo(boolean zeroToOne) {
        return new DeviceInfo("compiler fixture", "fixture", "fixture", zeroToOne,
                zeroToOne ? "Vulkan" : "OpenGL", 0, null,
                new DeviceFeatures(false, zeroToOne, false, false, false, false, false, false),
                java.util.Set.of(), new HintsAndWorkarounds(false, false, false, false), null);
    }

    private static GpuDevice metadataDevice(boolean zeroToOne) {
        var info = deviceInfo(zeroToOne);
        return (GpuDevice) Proxy.newProxyInstance(GpuDevice.class.getClassLoader(), new Class<?>[]{GpuDevice.class},
                (instance, method, arguments) -> {
                    if (method.getName().equals("getDeviceInfo")) {
                        return info;
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    private static final class Resources implements ShaderSource {
        private final Map<Identifier, CachedIncludeSource> includes = new HashMap<>();

        @Override
        public String getShader(Identifier id, ShaderType type) {
            String suffix = type == ShaderType.VERTEX ? ".vsh" : ".fsh";
            return resource("assets/" + id.getNamespace() + "/shaders/" + id.getPath() + suffix);
        }

        @Override
        public CachedIncludeSource getInclude(Identifier id) {
            return includes.computeIfAbsent(id, key -> CachedIncludeSource.create(key,
                    resource("assets/" + key.getNamespace() + "/shaders/include/" + key.getPath())));
        }

        @Override
        public void close() {
            includes.values().forEach(CachedIncludeSource::close);
            includes.clear();
        }
    }

    private static String resource(String path) {
        try (var stream = GpuShaderCompilationSmokeTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing shader resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read shader resource: " + path, failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
