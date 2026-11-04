package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.device.HintsAndWorkarounds;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.frontend.shaders.GlslCompiler;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Real 26.3 shaderc/SPIR-V frontend and includes; creates no graphics device/window. */
public final class GpuShaderCompilationSmokeTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
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
            }
        } finally {
            activeDevice.set(null, previousDevice);
        }
        System.out.println("PASS five production shader variants through actual 26.3 frontend, OpenGL/Vulkan feature configurations; no GPU execution");
    }

    private static GpuDevice metadataDevice(boolean zeroToOne) {
        var info = new DeviceInfo("compiler fixture", "fixture", "fixture", zeroToOne,
                zeroToOne ? "Vulkan" : "OpenGL", 0, null, null, java.util.Set.of(),
                new HintsAndWorkarounds(false, false, false, false), null);
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
