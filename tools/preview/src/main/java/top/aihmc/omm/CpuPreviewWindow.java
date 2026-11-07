package top.aihmc.omm;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.List;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Displays already-transformed MOD vertices; performs no model parsing or skinning. */
final class CpuPreviewWindow {
    @FunctionalInterface
    interface FrameSource {
        List<double[]> sample(int frame) throws Exception;

        default void camera(Matrix4f camera) {
        }

        default List<float[]> colors() {
            return null;
        }

        default boolean directionalLighting(int batch) {
            return true;
        }

        default boolean perFaceLighting(int batch) {
            return false;
        }

        default void beforeDraw(int batch, int program) {
        }

        default List<int[]> lightingCoordinates() {
            return null;
        }

        default boolean visible(int batch) {
            return true;
        }

        default List<OmniMorphDrawOrder.Range> drawRanges() {
            return null;
        }
    }
    private CpuPreviewWindow() {
    }

    static void run(String title, List<double[]> batches, List<int[]> triangles, byte[] textureBytes) throws Exception {
        run(title, batches, triangles, java.util.Collections.nCopies(batches.size(), textureBytes));
    }

    static void run(String title, List<double[]> batches, List<int[]> triangles, List<byte[]> textureBytes) throws Exception {
        run(title, batches, triangles, textureBytes, null);
    }

    static void run(String title, List<double[]> batches, List<int[]> triangles,
                    List<byte[]> textureBytes, FrameSource source) throws Exception {
        run(title, batches, triangles, textureBytes, source, false);
    }

    static void run(String title, List<double[]> batches, List<int[]> triangles,
                    List<byte[]> textureBytes, FrameSource source, boolean dynamicQuads) throws Exception {
        int frames = Integer.parseInt(System.getProperty("omm.preview.frames", "120"));
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW initialization failed");
        }
        long window = 0;
        int program = 0;
        int vao = 0;
        int verticesBuffer = 0;
        int indicesBuffer = 0;
        int colorsBuffer = 0;
        int lightingBuffer = 0;
        CpuLightTextures lightTextures = null;
        SuperflatBackground background = null;
        var textures = new java.util.IdentityHashMap<byte[], Integer>();
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            window = glfwCreateWindow(960, 640, title, 0, 0);
            if (window == 0) {
                throw new IllegalStateException("Could not create visible CPU output window");
            }
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            glfwSwapInterval(1);
            program = program();
            lightTextures = new CpuLightTextures();
            background = new SuperflatBackground();
            vao = glGenVertexArrays();
            verticesBuffer = glGenBuffers();
            indicesBuffer = glGenBuffers();
            colorsBuffer = glGenBuffers();
            lightingBuffer = glGenBuffers();
            glBindVertexArray(vao);
            int vertexValues = batches.stream().mapToInt(batch -> batch.length).sum();
            float[] vertices = new float[vertexValues];
            int[] indices = new int[triangles.stream().mapToInt(batch -> batch.length).sum()];
            int valueOffset = 0;
            int indexOffset = 0;
            float[] low = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
            float[] high = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            for (int batch = 0; batch < batches.size(); batch++) {
                double[] batchVertices = batches.get(batch);
                for (int index : triangles.get(batch)) {
                    indices[indexOffset++] = valueOffset / 9 + index;
                }
                for (int index = 0; index < batchVertices.length; index++) {
                    float value = (float) batchVertices[index];
                    vertices[valueOffset + index] = value;
                    int component = index % 9;
                    if (component < 3 && (source == null || source.visible(batch))) {
                        low[component] = Math.min(low[component], value);
                        high[component] = Math.max(high[component], value);
                    }
                }
                valueOffset += batchVertices.length;
            }
            glBindBuffer(GL_ARRAY_BUFFER, verticesBuffer);
            glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 36, 0L);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 36, 28L);
            glEnableVertexAttribArray(3);
            glVertexAttribPointer(3, 3, GL_FLOAT, false, 36, 16L);
            for (byte[] bytes : textureBytes) {
                if (!textures.containsKey(bytes)) {
                    textures.put(bytes, texture(bytes));
                }
            }
            float centerX = (low[0] + high[0]) / 2;
            float centerY = (low[1] + high[1]) / 2;
            float centerZ = (low[2] + high[2]) / 2;
            float extent = Math.max(high[1] - low[1], Math.max(high[0] - low[0], high[2] - low[2]));
            Matrix4f view = new Matrix4f().lookAt(centerX, centerY, centerZ + extent * 1.7f,
                centerX, centerY, centerZ, 0, 1, 0);
            Matrix4f camera = new Matrix4f().perspective((float) Math.toRadians(45), 1.5f, 0.01f, 1000).mul(view);
            PreviewCamera configuredCamera = PreviewCamera.configured();
            if (configuredCamera != null) {
                view = configuredCamera.view();
                camera = configuredCamera.projection(1.5f).mul(view);
            }
            if (source != null) {
                source.camera(new Matrix4f(camera));
            }
            glUseProgram(program);
            glUniformMatrix4fv(glGetUniformLocation(program, "camera"), false, camera.get(new float[16]));
            glUniform1i(glGetUniformLocation(program, "modelTexture"), 0);
            glEnable(GL_DEPTH_TEST);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            RenderInputs inputs = RenderInputs.read();
            int visiblePixels = 0;
            double maximumPositionChange = 0;
            int minimumVertices = vertices.length / 9;
            int maximumVertices = minimumVertices;
            for (int frame = 0; frame < frames && !glfwWindowShouldClose(window); frame++) {
                if (source != null) {
                    List<double[]> sampled = source.sample(frame);
                    if (sampled.size() != batches.size()) {
                        throw new IllegalStateException("Animated batch topology changed");
                    }
                    if (dynamicQuads) {
                        vertices = new float[sampled.stream().mapToInt(batch -> batch.length).sum()];
                        var updatedTriangles = new java.util.ArrayList<int[]>();
                        for (double[] batch : sampled) {
                            if (batch.length % 36 != 0) {
                                throw new IllegalStateException("Incomplete original quad output");
                            }
                            updatedTriangles.add(ReferenceVertexOutput.quadIndices(batch.length / 9));
                        }
                        triangles = updatedTriangles;
                        indices = new int[triangles.stream().mapToInt(batch -> batch.length).sum()];
                        int vertexOffset = 0;
                        int destination = 0;
                        for (int batch = 0; batch < sampled.size(); batch++) {
                            for (int index : triangles.get(batch)) {
                                indices[destination++] = vertexOffset + index;
                            }
                            vertexOffset += sampled.get(batch).length / 9;
                        }
                        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer);
                        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STREAM_DRAW);
                        minimumVertices = Math.min(minimumVertices, vertices.length / 9);
                        maximumVertices = Math.max(maximumVertices, vertices.length / 9);
                    }
                    int offset = 0;
                    for (int batch = 0; batch < sampled.size(); batch++) {
                        double[] values = sampled.get(batch);
                        if (!dynamicQuads && values.length != batches.get(batch).length) {
                            throw new IllegalStateException("Animated vertex topology changed");
                        }
                        for (int index = 0; index < values.length; index++) {
                            if (!dynamicQuads && index % 9 < 3) {
                                maximumPositionChange = Math.max(maximumPositionChange,
                                    Math.abs(values[index] - batches.get(batch)[index]));
                            }
                            vertices[offset++] = (float) values[index];
                        }
                    }
                    glBindBuffer(GL_ARRAY_BUFFER, verticesBuffer);
                    if (dynamicQuads) {
                        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STREAM_DRAW);
                    } else {
                        glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
                    }
                }
                List<float[]> colors = source == null ? null : source.colors();
                if (colors == null) {
                    glDisableVertexAttribArray(2);
                    glVertexAttrib4f(2, inputs.tint().x, inputs.tint().y, inputs.tint().z, inputs.tint().w);
                } else {
                    float[] rgba = new float[vertices.length / 9 * 4];
                    int destination = 0;
                    for (float[] batch : colors) {
                        System.arraycopy(batch, 0, rgba, destination, batch.length);
                        destination += batch.length;
                    }
                    if (destination != rgba.length) {
                        throw new IllegalStateException("Original color output does not match vertex count");
                    }
                    glBindBuffer(GL_ARRAY_BUFFER, colorsBuffer);
                    glBufferData(GL_ARRAY_BUFFER, rgba, GL_STREAM_DRAW);
                    glEnableVertexAttribArray(2);
                    glVertexAttribPointer(2, 4, GL_FLOAT, false, 16, 0L);
                }
                int[] width = new int[1];
                List<int[]> lighting = source == null ? null : source.lightingCoordinates();
                glUniform1i(glGetUniformLocation(program, "vertexLighting"), lighting == null ? 0 : 1);
                if (lighting != null) {
                    int[] coordinates = new int[vertices.length / 9 * 4];
                    int destination = 0;
                    for (int[] batch : lighting) {
                        System.arraycopy(batch, 0, coordinates, destination, batch.length);
                        destination += batch.length;
                    }
                    if (destination != coordinates.length) {
                        throw new IllegalStateException("Original lighting output does not match vertex count");
                    }
                    glBindBuffer(GL_ARRAY_BUFFER, lightingBuffer);
                    glBufferData(GL_ARRAY_BUFFER, coordinates, GL_STREAM_DRAW);
                    glEnableVertexAttribArray(4);
                    glVertexAttribIPointer(4, 4, GL_INT, 16, 0L);
                }
                int[] height = new int[1];
                glfwGetFramebufferSize(window, width, height);
                glViewport(0, 0, width[0], height[0]);
                glClearColor(0.53f, 0.75f, 1f, 1);
                glDepthMask(true);
                glColorMask(true, true, true, true);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                background.draw(camera);
                byte[] backgroundPixels = frame == frames - 1 ? WindowPixels.capture(width[0], height[0]) : null;
                long[] drawOffsets = new long[batches.size()];
                long drawOffset = 0;
                for (int batch = 0; batch < batches.size(); batch++) {
                    drawOffsets[batch] = drawOffset;
                    drawOffset += (long) triangles.get(batch).length * Integer.BYTES;
                }
                List<OmniMorphDrawOrder.Range> ranges = source == null ? null : source.drawRanges();
                if (ranges == null) {
                    ranges = new java.util.ArrayList<>();
                    for (int batch = 0; batch < batches.size(); batch++) {
                        ranges.add(new OmniMorphDrawOrder.Range(batch, 0, triangles.get(batch).length));
                    }
                }
                lightTextures.bind();
                for (var range : ranges) {
                    int batch = range.batch();
                    if (source != null && !source.visible(batch)) {
                        continue;
                    }
                    if (source != null) {
                        source.beforeDraw(batch, program);
                    }
                    glUniform1i(glGetUniformLocation(program, "directionalLighting"),
                        source == null || source.directionalLighting(batch) ? 1 : 0);
                    glUniform1i(glGetUniformLocation(program, "perFaceLighting"),
                        source != null && source.perFaceLighting(batch) ? 1 : 0);
                    glBindTexture(GL_TEXTURE_2D, textures.get(textureBytes.get(batch)));
                    glDrawElements(GL_TRIANGLES, range.indexCount(), GL_UNSIGNED_INT,
                        drawOffsets[batch] + (long) range.firstIndex() * Integer.BYTES);
                }
                if (frame == frames - 1) {
                    visiblePixels = WindowPixels.changed(backgroundPixels, WindowPixels.capture(width[0], height[0]));
                    System.out.println("CPU MODEL PIXELS versusBackground=" + visiblePixels);
                }
                int error = glGetError();
                if (error != GL_NO_ERROR) {
                    throw new IllegalStateException("CPU output OpenGL error: " + error);
                }
                glfwSwapBuffers(window);
                glfwPollEvents();
            }
            if (visiblePixels < 100) {
                throw new AssertionError("Incomplete window or model not visible: " + visiblePixels);
            }
            System.out.println("CPU WINDOW PASS vertices=" + vertices.length / 9
                + " indices=" + indices.length + " pixels=" + visiblePixels + " frames=" + frames
                + " maximumPositionChange=" + (dynamicQuads ? "NA" : maximumPositionChange)
                + " minimumVertices=" + minimumVertices + " maximumVertices=" + maximumVertices);
        } finally {
            if (window != 0) {
                if (background != null) {
                    background.close();
                }
                if (lightTextures != null) {
                    lightTextures.close();
                }
                for (int texture : textures.values()) {
                    glDeleteTextures(texture);
                }
                glDeleteBuffers(verticesBuffer);
                glDeleteBuffers(indicesBuffer);
                glDeleteBuffers(colorsBuffer);
                glDeleteBuffers(lightingBuffer);
                glDeleteVertexArrays(vao);
                glDeleteProgram(program);
                glfwDestroyWindow(window);
            }
            glfwTerminate();
        }
    }

    static int program() throws Exception {
        int vertex = shader(GL_VERTEX_SHADER, "#version 330 core\n" + CpuLighting.source()
            + CpuLighting.source("sample_lightmap.glsl") + CpuLighting.source("fog.glsl") + """
            layout(location=0) in vec3 position;
            layout(location=1) in vec2 uv;
            layout(location=2) in vec4 vertexColor;
            layout(location=3) in vec3 normal;
            layout(location=4) in ivec4 vertexLightCoordinates;
            uniform bool vertexLighting;
            uniform vec3 previewLight0;
            uniform vec3 previewLight1;
            uniform bool directionalLighting;
            uniform bool perFaceLighting;
            uniform mat4 camera;
            out float sphericalDistance;
            out float cylindricalDistance;
            uniform sampler2D lightmapTexture;
            uniform sampler2D overlayTexture;
            uniform ivec2 lightCoordinates;
            uniform ivec2 overlayCoordinates;
            out vec4 lightmapColor;
            out vec4 overlayColor;
            out vec2 textureUv;
            out vec4 tint;
            out vec4 backTint;
            void main() {
                gl_Position = camera * vec4(position, 1);
                sphericalDistance = fog_spherical_distance(position);
                cylindricalDistance = fog_cylindrical_distance(position);
                textureUv = uv;
                lightmapColor = sample_lightmap(lightmapTexture,
                    vertexLighting ? vertexLightCoordinates.xy : lightCoordinates);
                overlayColor = texelFetch(overlayTexture,
                    vertexLighting ? vertexLightCoordinates.zw : overlayCoordinates, 0);
                tint = directionalLighting
                    ? minecraft_mix_light(previewLight0, previewLight1, normal, vertexColor) : vertexColor;
                backTint = tint;
                if (perFaceLighting) {
                    vec2 light = minecraft_compute_light(previewLight0, previewLight1, normal);
                    tint = minecraft_mix_light_separate(light, vertexColor);
                    backTint = minecraft_mix_light_separate(-light, vertexColor);
                }
            }
            """);
        int fragment = shader(GL_FRAGMENT_SHADER, "#version 330 core\n" + CpuLighting.source("fog.glsl") + """
            in vec2 textureUv;
            in vec4 tint;
            in vec4 backTint;
            uniform bool perFaceLighting;
            uniform sampler2D modelTexture;
            uniform float alphaCutout;
            uniform bool emissive;
            uniform bool noOverlay;
            uniform vec4 previewFogColor;
            uniform float previewFogStart;
            uniform float previewFogEnd;
            in float sphericalDistance;
            in float cylindricalDistance;
            in vec4 lightmapColor;
            in vec4 overlayColor;
            out vec4 color;
            void main() {
                vec4 faceTint = perFaceLighting && !gl_FrontFacing ? backTint : tint;
                color = texture(modelTexture, textureUv);
                if (color.a < alphaCutout) discard;
                color *= faceTint;
                if (!noOverlay) color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
                if (!emissive) color *= lightmapColor;
                color = apply_fog(color, sphericalDistance, cylindricalDistance,
                    previewFogStart, previewFogEnd, previewFogStart, previewFogEnd, previewFogColor);
            }
            """);
        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String error = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException(error);
        }
        CpuLighting.configure(program);
        return program;
    }

    static int shader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String error = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException(error);
        }
        return shader;
    }

    static int texture(byte[] bytes) throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IllegalArgumentException("No image decoder accepted the model texture");
        }
        ByteBuffer pixels = MemoryUtil.memAlloc(image.getWidth() * image.getHeight() * 4);
        try {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int color = image.getRGB(x, y);
                    pixels.put((byte) (color >> 16)).put((byte) (color >> 8)).put((byte) color).put((byte) (color >> 24));
                }
            }
            pixels.flip();
            int texture = glGenTextures();
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, image.getWidth(), image.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            return texture;
        } finally {
            MemoryUtil.memFree((java.nio.Buffer) pixels);
        }
    }

    private static int pixels(int width, int height) {
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
        try {
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            int count = 0;
            long red = 0;
            long green = 0;
            long blue = 0;
            for (int index = 0; index < pixels.capacity(); index += 4) {
                red += pixels.get(index) & 255;
                green += pixels.get(index + 1) & 255;
                blue += pixels.get(index + 2) & 255;
                if ((pixels.get(index) & 255) + (pixels.get(index + 1) & 255) + (pixels.get(index + 2) & 255) > 12) {
                    count++;
                }
            }
            System.out.println("CPU WINDOW RGB SUM red=" + red + " green=" + green + " blue=" + blue);
            return count;
        } finally {
            MemoryUtil.memFree((java.nio.Buffer) pixels);
        }
    }
}
