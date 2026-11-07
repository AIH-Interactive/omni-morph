package top.aihmc.omm.preview;

import static org.lwjgl.opengl.GL33C.*;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

/** Analytic ray/plane intersection: one repeating grass tile per block, with no finite mesh edge. */
final class GrassGround implements AutoCloseable {
    private final int program;
    private final int vao;
    private final int texture;
    private final PreviewConfig.Ground config;
    private final boolean skyEnabled;

    GrassGround(PreviewConfig config) throws Exception {
        this.config = config.ground;
        skyEnabled = config.sky.enabled;
        if (!config.ground.enabled) {
            program = vao = texture = 0;
            return;
        }
        String source = config.ground.texture.isBlank()
            ? "assets/minecraft/textures/block/grass_block_top.png"
            : config.configFile.getParent().resolve(config.ground.texture).normalize().toString();
        byte[] encoded =
            config.ground.texture.isBlank() ? PreviewTextures.resource(source) : Files.readAllBytes(Path.of(source));
        var image = PreviewTextures.image(encoded, source);
        PreviewTextures.writeSources(config.outputDirectory);
        Files.writeString(config.outputDirectory.resolve("ground.txt"),
            "texture=" + source + "\nsha256=" + PreviewTextures.sha256(encoded)
                + "\ntile_size=1 block\ngeometry=infinite analytic plane\n");
        texture = PreviewMain.uploadTexture(image);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glGenerateMipmap(GL_TEXTURE_2D);
        program = PreviewMain.program(PreviewMain.CAMERA_RAY_VERTEX, """
            #version 330 core
            in vec3 nearWorld;
            in vec3 farWorld;
            uniform mat4 camera;
            uniform float floorY;
            uniform sampler2D grass;
            uniform vec3 tint;
            uniform vec3 fogColor;
            out vec4 pixel;
            void main() {
                vec3 ray = farWorld - nearWorld;
                if (abs(ray.y) < 0.000001) discard;
                float hit = (floorY - nearWorld.y) / ray.y;
                if (hit <= 0.0) discard;
                vec3 world = nearWorld + ray * hit;
                vec4 projected = camera * vec4(world, 1.0);
                float depth = projected.z / projected.w * 0.5 + 0.5;
                if (depth < 0.0) discard;
                float fog = smoothstep(0.1, 0.9, hit);
                vec3 color = texture(grass, world.xz).rgb * tint;
                pixel = vec4(mix(color, fogColor, fog), 1.0);
                gl_FragDepth = min(depth, 1.0);
            }
            """);
        vao = glGenVertexArrays();
    }

    int draw(Matrix4f camera, float height) {
        if (program == 0) {
            return 0;
        }
        glUseProgram(program);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glUniform1i(glGetUniformLocation(program, "grass"), 0);
        glUniform1f(glGetUniformLocation(program, "floorY"), height);
        glUniform3f(glGetUniformLocation(program, "tint"), config.red, config.green, config.blue);
        glUniform3f(glGetUniformLocation(program, "fogColor"), skyEnabled ? 0.68f : 0.055f, skyEnabled ? 0.80f : 0.07f,
            skyEnabled ? 1f : 0.1f);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(16);
            glUniformMatrix4fv(glGetUniformLocation(program, "camera"), false, camera.get(values));
            glUniformMatrix4fv(
                glGetUniformLocation(program, "inverseCamera"), false, new Matrix4f(camera).invert().get(values));
        }
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        return 1;
    }

    @Override
    public void close() {
        if (program != 0) {
            glDeleteTextures(texture);
            glDeleteVertexArrays(vao);
            glDeleteProgram(program);
        }
    }
}
