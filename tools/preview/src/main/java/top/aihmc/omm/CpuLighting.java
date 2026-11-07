package top.aihmc.omm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.lwjgl.opengl.GL33C.*;

/** Uses the configured local Minecraft JAR's lighting functions without bundling them. */
final class CpuLighting {
    private CpuLighting() {
    }

    static String source() throws Exception {
        return source("light.glsl");
    }

    static String source(String name) throws Exception {
        var configuration = RuntimeConfiguration.json(Path.of(
            System.getProperty("omm.preview.output"), "effective-config.json"));
        Path jar = Path.of(configuration.get("textureJar").getAsString());
        String resource = "assets/minecraft/shaders/include/" + name;
        try (var zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry(resource);
            if (entry == null) {
                throw new IllegalArgumentException("Missing Minecraft lighting resource: " + jar + "!" + resource);
            }
            try (var input = zip.getInputStream(entry)) {
                String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("CPU LIGHTING SOURCE " + jar + "!" + resource);
                return source.replaceAll("(?m)^#version[^\\r\\n]*", "");
            }
        }
    }

    static void configure(int program) throws Exception {
        RenderInputs inputs = RenderInputs.read();
        glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "directionalLighting"), 1);
        glUniform1f(glGetUniformLocation(program, "alphaCutout"), 0.01f);
        glUniform1i(glGetUniformLocation(program, "overlayTexture"), 1);
        glUniform1i(glGetUniformLocation(program, "lightmapTexture"), 2);
        glUniform4f(glGetUniformLocation(program, "previewFogColor"),
            inputs.fogColor().x, inputs.fogColor().y, inputs.fogColor().z, inputs.fogColor().w);
        glUniform1f(glGetUniformLocation(program, "previewFogStart"), inputs.fogStart());
        glUniform1f(glGetUniformLocation(program, "previewFogEnd"), inputs.fogEnd());
        CpuLightTextures.coordinates(program, inputs.packedLight(), inputs.packedOverlay(), false, false);
        glUniform3f(glGetUniformLocation(program, "previewLight0"),
            inputs.light0().x, inputs.light0().y, inputs.light0().z);
        glUniform3f(glGetUniformLocation(program, "previewLight1"),
            inputs.light1().x, inputs.light1().y, inputs.light1().z);
    }
}
