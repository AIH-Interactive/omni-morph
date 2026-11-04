package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.vulkan.glsl.GlslCompiler;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

/** Uses the real native Vulkan GLSL/SPIR-V frontend; creates no Vulkan device or window. */
public final class GpuShaderCompilationSmokeTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var imported = new HashSet<String>();
        GlslPreprocessor preprocessor = new GlslPreprocessor() {
            @Override
            public String applyImport(boolean relative, String path) {
                Identifier location = Identifier.parse(path);
                String resource = "assets/" + location.getNamespace() + "/shaders/"
                        + (relative ? "core/" : "include/") + location.getPath();
                return imported.add(resource) ? resource(resource) : null;
            }
        };
        String original = String.join("", preprocessor.process(resource("assets/minecraft/shaders/core/entity.vsh")));
        String[] sources = {
                GpuShaderProgram.wrapEntitySource(original),
                GpuShaderProgram.wrapRigidEntitySource(original),
                GpuShaderProgram.wrapStorageEntitySource(original),
                GpuShaderProgram.wrapStorageRigidEntitySource(original),
                GpuShaderProgram.wrapStorageModelEntitySource(original)
        };
        try (var compiler = new GlslCompiler()) {
            for (int variant = 0; variant < sources.length; variant++) {
                String source = GlslPreprocessor.injectDefines(sources[variant],
                        RenderPipelines.ENTITY_CUTOUT_CULL.getShaderDefines());
                try (var module = compiler.createIntermediary("OMP shader variant " + variant, source, ShaderType.VERTEX)) {
                    if (variant >= 2) {
                        boolean hasOffsets = false;
                        for (Object buffer : module.uniformBuffers()) {
                            hasOffsets |= "OmniMorphOffsets".equals(recordValue(buffer, "name"));
                        }
                        boolean hasData = false;
                        for (Object sampler : module.samplers()) {
                            hasData |= "OmniMorphData".equals(recordValue(sampler, "name"))
                                    && Integer.valueOf(5).equals(recordValue(sampler, "dimensions"));
                        }
                        require(hasOffsets,
                                "SPIR-V retains the native offset UBO");
                        require(hasData,
                                "SPIR-V retains the data channel as a texel buffer");
                    }
                }
            }
        }
        System.out.println("PASS actual Minecraft entity shader imports/defines and five production variants through native Vulkan GLSL/SPIR-V compiler; no GPU execution");
    }

    /** Reflection only reads the compiler's package-private reflection records; production access stays unchanged. */
    private static Object recordValue(Object record, String field) throws ReflectiveOperationException {
        var accessor = record.getClass().getDeclaredMethod(field);
        accessor.setAccessible(true);
        return accessor.invoke(record);
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
