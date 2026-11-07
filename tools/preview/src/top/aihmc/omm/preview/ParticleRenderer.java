package top.aihmc.omm.preview;

import static org.lwjgl.opengl.GL33C.*;

import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

/** Sorted, textured camera-facing quads; all instances can share this immutable sprite atlas. */
final class ParticleRenderer implements AutoCloseable {
    private record Sprite(float u0, float v0, float u1, float v1) {}
    private record Drawable(PreviewParticles.Particle particle, Vector3f position, float depth) {}
    private final Map<String, List<Sprite>> sprites = new LinkedHashMap<>();
    private final int program;
    private final int vao;
    private final int vbo;
    private final int texture;
    private final Matrix4f inverse = new Matrix4f();
    int submitted;

    ParticleRenderer() throws IOException {
        var paths = new LinkedHashMap<String, BufferedImage>();
        var definitions = new LinkedHashMap<String, List<String>>();
        for (String type : PreviewParticles.TYPES.stream().sorted().toList()) {
            try (var input =
                     ParticleRenderer.class.getResourceAsStream("/assets/minecraft/particles/" + type + ".json")) {
                if (input == null)
                    throw new IOException("Missing packaged particle definition: " + type);
                var definition =
                    JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                List<String> names = new ArrayList<>();
                for (var value : definition.getAsJsonArray("textures")) {
                    String name = value.getAsString();
                    names.add(name);
                    if (!paths.containsKey(name)) {
                        String path = "/assets/" + name.replace(":", "/textures/particle/") + ".png";
                        try (var image = ParticleRenderer.class.getResourceAsStream(path)) {
                            if (image == null)
                                throw new IOException("Missing packaged particle texture: " + path);
                            paths.put(name, PreviewTextures.image(image.readAllBytes(), path));
                        }
                    }
                }
                definitions.put(type, names);
            }
        }
        int tile = paths.values()
                       .stream()
                       .mapToInt(image -> Math.max(image.getWidth(), image.getHeight()))
                       .max()
                       .orElseThrow();
        int columns = 8;
        int width = columns * tile;
        int height = ((paths.size() + columns - 1) / columns) * tile;
        var atlas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var coordinates = new LinkedHashMap<String, Sprite>();
        int index = 0;
        for (var entry : paths.entrySet()) {
            var image = entry.getValue();
            int x = index % columns * tile, y = index / columns * tile;
            atlas.setRGB(x, y, image.getWidth(), image.getHeight(),
                image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()), 0,
                image.getWidth());
            coordinates.put(entry.getKey(),
                new Sprite((float) x / width, (float) y / height, (float) (x + image.getWidth()) / width,
                    (float) (y + image.getHeight()) / height));
            index++;
        }
        definitions.forEach((type, names) -> sprites.put(type, names.stream().map(coordinates::get).toList()));
        texture = PreviewMain.uploadTexture(atlas);
        program = PreviewMain.program("""
            #version 330 core
            layout(location=0) in vec3 position;
            layout(location=1) in vec2 uv;
            layout(location=2) in vec4 color;
            uniform mat4 transform;
            out vec2 textureUv;
            out vec4 tint;
            void main() {
                gl_Position = transform * vec4(position, 1);
                textureUv = uv;
                tint = color;
            }
            """, """
            #version 330 core
            uniform sampler2D sprites;
            in vec2 textureUv;
            in vec4 tint;
            out vec4 pixel;
            void main() {
                pixel = texture(sprites, textureUv) * tint;
                if (pixel.a < 0.01) discard;
            }
            """);
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 36, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 36, 12);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, 36, 20);
    }

    int draw(PreviewParticles particles, Matrix4f transform, float partialTick) {
        submitted = 0;
        if (particles == null || particles.active().isEmpty())
            return 0;
        List<Drawable> drawables = new ArrayList<>();
        for (var particle : particles.active()) {
            if (!particle.isAlive())
                continue;
            var position =
                new Vector3f((float) particle.previous.x, (float) particle.previous.y, (float) particle.previous.z)
                    .lerp(new Vector3f(particle.position), partialTick);
            float depth = transform.transformProject(new Vector3f(position)).z;
            drawables.add(new Drawable(particle, position, depth));
        }
        drawables.sort(Comparator.comparingDouble(Drawable::depth).reversed());
        inverse.set(transform).invert();
        var origin = inverse.transformProject(new Vector3f(0, 0, -1));
        var right = inverse.transformProject(new Vector3f(1, 0, -1)).sub(origin).normalize();
        var up = inverse.transformProject(new Vector3f(0, 1, -1)).sub(origin).normalize();
        float[] vertices = new float[Math.multiplyExact(drawables.size(), 54)];
        int cursor = 0;
        int[] corners = {0, 1, 2, 0, 2, 3};
        for (var drawable : drawables) {
            var particle = drawable.particle;
            List<Sprite> frames = sprites.get(particle.type);
            Sprite sprite = frames.get(particle.spriteIndex(frames.size()));
            for (int corner : corners) {
                float sx = corner == 0 || corner == 3 ? -1 : 1;
                float sy = corner < 2 ? -1 : 1;
                float radius = particle.quadSize;
                vertices[cursor++] = drawable.position.x + (right.x * sx + up.x * sy) * radius;
                vertices[cursor++] = drawable.position.y + (right.y * sx + up.y * sy) * radius;
                vertices[cursor++] = drawable.position.z + (right.z * sx + up.z * sy) * radius;
                vertices[cursor++] = sx < 0 ? sprite.u0 : sprite.u1;
                vertices[cursor++] = sy < 0 ? sprite.v1 : sprite.v0;
                vertices[cursor++] = particle.red;
                vertices[cursor++] = particle.green;
                vertices[cursor++] = particle.blue;
                vertices[cursor++] = particle.alpha;
            }
        }
        if (vertices.length == 0)
            return 0;
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);
        glUseProgram(program);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glUniform1i(glGetUniformLocation(program, "sprites"), 0);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(16);
            glUniformMatrix4fv(glGetUniformLocation(program, "transform"), false, transform.get(values));
        }
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STREAM_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, drawables.size() * 6);
        glDepthMask(true);
        glDisable(GL_BLEND);
        submitted = drawables.size();
        return 1;
    }

    @Override
    public void close() {
        glDeleteTextures(texture);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
