package top.aihmc.omnimorph.model.outfit;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.frontend.shaders.GlslCompiler;
import net.minecraft.resources.Identifier;
import top.aihmc.omnimorph.lua.api.particle.CustomParticlePipelines;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Compiles native particle stages and checks their reflected interfaces without a GPU surface. */
final class RenderpearlShaderSmokeTest {
    private RenderpearlShaderSmokeTest() {
    }

    static void verify() throws Exception {
        try (var source = new Resources(); var compiler = new GlslCompiler(true, false)) {
            for (var blend : CustomParticlePipelines.Blend.values()) {
                var pipeline = CustomParticlePipelines.pipeline(0, blend, 0, true, false, 0.25f);
                var varyings = new HashMap<Integer, String>();
                var vertexId = pipeline.getShaders().get(ShaderType.VERTEX);
                try (var vertex = compiler.compileToSpv(vertexId.toString(),
                        source.getShader(vertexId, ShaderType.VERTEX), ShaderType.VERTEX,
                        pipeline.getShaderDefines(), source)) {
                    for (var input : vertex.reflect().inputs()) {
                        require(pipeline.getVertexFormatBinding(0).contains(input.name()),
                                "Native particle attribute has a matching buffer element: " + input.name());
                    }
                    for (var output : vertex.reflect().outputs()) {
                        varyings.put(output.location(), output.name());
                    }
                }
                var fragmentId = pipeline.getShaders().get(ShaderType.FRAGMENT);
                try (var fragment = compiler.compileToSpv(fragmentId.toString(),
                        source.getShader(fragmentId, ShaderType.FRAGMENT), ShaderType.FRAGMENT,
                        pipeline.getShaderDefines(), source)) {
                    for (var input : fragment.reflect().inputs()) {
                        require(input.name().equals(varyings.get(input.location())),
                                "Particle fragment interface matches the native vertex stage: " + input.name());
                    }
                }
            }
        }
        System.out.println("PASS native particle shaderc: all blends, custom alpha cutoff, native attributes and matching stage locations");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Resources implements ShaderSource {
        private final Map<Identifier, CachedIncludeSource> includes = new HashMap<>();

        private String read(String path) {
            try (var input = getClass().getResourceAsStream(path)) {
                if (input == null) {
                    throw new AssertionError("Missing native shader resource: " + path);
                }
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (java.io.IOException error) {
                throw new java.io.UncheckedIOException(error);
            }
        }

        @Override
        public String getShader(Identifier id, ShaderType type) {
            return read("/assets/" + id.getNamespace() + "/shaders/" + id.getPath()
                    + (type == ShaderType.VERTEX ? ".vsh" : ".fsh"));
        }

        @Override
        public CachedIncludeSource getInclude(Identifier id) {
            return includes.computeIfAbsent(id, key -> CachedIncludeSource.create(key,
                    read("/assets/" + key.getNamespace() + "/shaders/include/" + key.getPath())));
        }

        @Override
        public void close() {
            includes.values().forEach(CachedIncludeSource::close);
        }
    }
}
