package top.aihmc.omm.preview;

import static org.lwjgl.opengl.GL33C.*;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

/** Overworld-style sky gradient with the game's actual cloud, sun and full-moon textures. */
final class MinecraftSky implements AutoCloseable {
    private final int program;
    private final int vao;
    private final int[] textures;
    private final PreviewConfig.Sky config;

    MinecraftSky(PreviewConfig config) throws Exception {
        this.config = config.sky;
        if (!config.sky.enabled) {
            program = vao = 0;
            textures = new int[0];
            return;
        }
        String[] names = {"assets/minecraft/textures/environment/clouds.png",
            "assets/minecraft/textures/environment/celestial/sun.png",
            "assets/minecraft/textures/environment/celestial/moon/full_moon.png"};
        textures = new int[names.length];
        for (int index = 0; index < names.length; index++) {
            byte[] bytes = PreviewTextures.resource(names[index]);
            textures[index] = PreviewMain.uploadTexture(PreviewTextures.image(bytes, names[index]));
            if (index == 0) {
                glBindTexture(GL_TEXTURE_2D, textures[index]);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            }
        }
        PreviewTextures.writeSources(config.outputDirectory);
        Files.writeString(config.outputDirectory.resolve("sky.txt"),
            "mode=overworld-style gradient with packaged Minecraft cloud/sun/full-moon PNGs\n"
                + "sun_yaw=" + config.sky.sunYawDegrees + "\nsun_pitch=" + config.sky.sunPitchDegrees
                + "\ncloud_height=" + config.sky.cloudHeight + "\nclock=fixed configuration\n");
        program = PreviewMain.program(PreviewMain.CAMERA_RAY_VERTEX, """
            #version 330 core
            in vec3 nearWorld;
            in vec3 farWorld;
            uniform sampler2D cloudTexture;
            uniform sampler2D sunTexture;
            uniform sampler2D moonTexture;
            uniform vec3 sunDirection;
            uniform float cloudHeight;
            out vec4 pixel;
            vec3 celestial(sampler2D image, vec3 ray, vec3 direction) {
                float facing = dot(ray, direction);
                if (facing <= 0.0) return vec3(0.0);
                vec3 right = normalize(cross(direction, vec3(0.0, 1.0, 0.0)));
                vec3 up = cross(right, direction);
                vec2 uv = vec2(dot(ray, right), dot(ray, up)) / (facing * 0.24) + 0.5;
                if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return vec3(0.0);
                vec4 texel = texture(image, uv);
                return texel.rgb * texel.a;
            }
            void main() {
                vec3 ray = normalize(farWorld - nearWorld);
                float height = pow(max(ray.y, 0.0), 0.35);
                vec3 color = mix(vec3(0.68, 0.80, 1.0), vec3(0.38, 0.62, 1.0), height);
                color += celestial(sunTexture, ray, sunDirection);
                color += celestial(moonTexture, ray, -sunDirection) * 0.7;
                if (ray.y > 0.001) {
                    float distance = (cloudHeight - nearWorld.y) / ray.y;
                    vec3 cloudPoint = nearWorld + ray * distance;
                    vec4 cloud = texture(cloudTexture, cloudPoint.xz / 3072.0);
                    float alpha = cloud.a * smoothstep(0.015, 0.12, ray.y);
                    color = mix(color, vec3(1.0), alpha * 0.85);
                }
                pixel = vec4(clamp(color, 0.0, 1.0), 1.0);
            }
            """);
        vao = glGenVertexArrays();
    }

    int draw(Matrix4f camera) {
        if (program == 0) {
            return 0;
        }
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glUseProgram(program);
        String[] samplers = {"cloudTexture", "sunTexture", "moonTexture"};
        for (int index = 0; index < textures.length; index++) {
            glActiveTexture(GL_TEXTURE0 + index);
            glBindTexture(GL_TEXTURE_2D, textures[index]);
            glUniform1i(glGetUniformLocation(program, samplers[index]), index);
        }
        float yaw = (float) Math.toRadians(config.sunYawDegrees);
        float pitch = (float) Math.toRadians(config.sunPitchDegrees);
        Vector3f sun = new Vector3f((float) Math.sin(yaw) * (float) Math.cos(pitch), (float) Math.sin(pitch),
            (float) Math.cos(yaw) * (float) Math.cos(pitch));
        glUniform3f(glGetUniformLocation(program, "sunDirection"), sun.x, sun.y, sun.z);
        glUniform1f(glGetUniformLocation(program, "cloudHeight"), config.cloudHeight);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(16);
            glUniformMatrix4fv(
                glGetUniformLocation(program, "inverseCamera"), false, new Matrix4f(camera).invert().get(values));
        }
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        return 1;
    }

    @Override
    public void close() {
        if (program != 0) {
            glDeleteTextures(textures);
            glDeleteProgram(program);
            glDeleteVertexArrays(vao);
        }
    }
}
