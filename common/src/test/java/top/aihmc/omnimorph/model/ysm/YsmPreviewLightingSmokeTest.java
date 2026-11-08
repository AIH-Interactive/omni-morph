package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import top.aihmc.omnimorph.test.NativeGlWindow;

import java.nio.charset.StandardCharsets;

/** Real-window GLSL compilation/readback plus native render-state contracts; loader startup is separate. */
public final class YsmPreviewLightingSmokeTest {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        RenderSystem.initRenderThread();
        try (var window = NativeGlWindow.open()) {
            var texture = new ResourceLocation("omni-morph", "preview-test");
            var types = new YsmPreviewRenderTypes();
            for (boolean cull : new boolean[] {false, true}) {
                for (boolean translucent : new boolean[] {false, true}) {
                    var material = types.get(texture, cull, translucent);
                    verifyMaterial(material, cull, translucent);
                    require(types.get(texture, cull, translucent) == material, "stable bounded material cache");
                }
            }
            verifyPixels();
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "no GL error");
        }
        System.out.println("PASS native unlit material state, real-window GLSL compile/link, color modulation and alpha discard; loader startup pending");
    }

    public static void verifyMaterial(RenderType material, boolean cull, boolean translucent) throws Exception {
        var state = field(material, "state");
        require(material.format() == DefaultVertexFormat.NEW_ENTITY, "unlit material retains native entity format");
        require(sortsOnUpload(material) == translucent, "only blended materials sort on upload");
        require(field(state, "cullState") == shard(cull ? "CULL" : "NO_CULL"), "native face-culling policy");
        require(field(state, "transparencyState") == shard(translucent ? "TRANSLUCENT_TRANSPARENCY" : "NO_TRANSPARENCY"),
                "native alpha-blending policy");
        require(field(state, "depthTestState") == shard("LEQUAL_DEPTH_TEST")
                        && field(state, "writeMaskState") == shard("COLOR_DEPTH_WRITE"), "ordinary entity depth policy");
        require(field(state, "lightmapState") == shard("NO_LIGHTMAP"), "GUI does not sample lightmap");
        require(field(state, "overlayState") == shard("OVERLAY"), "entity overlays remain available");
        require(field(state, "outputState") == shard("MAIN_TARGET"), "GUI draws to the main target");
        var vanilla = RenderType.entityCutout(new ResourceLocation("preview-test"));
        require(field(state, "shaderState") != field(field(vanilla, "state"), "shaderState"),
                "GUI uses its dedicated unlit shader state");
    }

    private static void verifyPixels() throws Exception {
        String vertex = resource("assets/omni-morph/shaders/core/ysm_gui_unlit.vsh");
        var preprocessor = new com.mojang.blaze3d.preprocessor.GlslPreprocessor() {
            @Override
            public String applyImport(boolean quoted, String name) {
                try {
                    return resource("assets/minecraft/shaders/include/" + name);
                } catch (java.io.IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                }
            }
        };
        String fragment = String.join("", preprocessor.process(resource("assets/omni-morph/shaders/core/ysm_gui_unlit.fsh")));
        int vs = compile(GL20.GL_VERTEX_SHADER, vertex);
        int fs = 0;
        int program = 0;
        int vao = 0;
        int buffer = 0;
        int base = 0;
        int overlay = 0;
        try {
            fs = compile(GL20.GL_FRAGMENT_SHADER, fragment);
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vs);
            GL20.glAttachShader(program, fs);
            GL20.glLinkProgram(program);
            require(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != 0, GL20.glGetProgramInfoLog(program));
            GL20.glUseProgram(program);
            require(GL20.glGetUniformLocation(program, "Light0_Direction") == -1
                            && GL20.glGetUniformLocation(program, "Light1_Direction") == -1
                            && GL20.glGetUniformLocation(program, "Sampler2") == -1
                            && GL20.glGetAttribLocation(program, "Normal") == -1,
                    "linked program has no directional-light, normal or lightmap input");
            var identity = new Matrix4f().get(BufferUtils.createFloatBuffer(16));
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, "ModelViewMat"), false, identity);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, "ProjMat"), false, identity);
            GL20.glUniform4f(GL20.glGetUniformLocation(program, "ColorModulator"), 1, 0.5f, 1, 1);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "FogStart"), 100);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "FogEnd"), 200);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Sampler0"), 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Sampler1"), 1);
            base = texture(0, 200, 120, 80, 255);
            overlay = texture(1, 0, 0, 0, 255);
            vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);
            buffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, new float[] {-1, -1, 0, 3, -1, 0, -1, 3, 0}, GL15.GL_STATIC_DRAW);
            int position = GL20.glGetAttribLocation(program, "Position");
            GL20.glEnableVertexAttribArray(position);
            GL20.glVertexAttribPointer(position, 3, GL11.GL_FLOAT, false, 0, 0L);
            GL20.glVertexAttrib4f(GL20.glGetAttribLocation(program, "Color"), 0.5f, 1, 0.25f, 1);
            GL20.glVertexAttrib2f(GL20.glGetAttribLocation(program, "UV0"), 0, 0);
            GL30.glVertexAttribI2i(GL20.glGetAttribLocation(program, "UV1"), 0, 0);
            GL11.glViewport(0, 0, 32, 32);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DITHER);
            GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            requirePixel(100, 60, 20, 255);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, base);
            var invisible = BufferUtils.createByteBuffer(4);
            invisible.put(new byte[] {(byte) 200, 120, 80, 0}).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, invisible);
            GL11.glClearColor(0.1f, 0.2f, 0.3f, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            requirePixel(26, 51, 77, 255);
            GLFW.glfwSwapBuffers(GLFW.glfwGetCurrentContext());
            GLFW.glfwPollEvents();
        } finally {
            GL20.glUseProgram(0);
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL11.glDeleteTextures(base);
            GL11.glDeleteTextures(overlay);
            GL15.glDeleteBuffers(buffer);
            GL30.glDeleteVertexArrays(vao);
            GL20.glDeleteProgram(program);
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
        }
    }

    private static int texture(int unit, int r, int g, int b, int a) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        var pixels = BufferUtils.createByteBuffer(4);
        pixels.put(new byte[] {(byte) r, (byte) g, (byte) b, (byte) a}).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        return texture;
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new AssertionError(log);
        }
        return shader;
    }

    private static void requirePixel(int... expected) {
        var pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(16, 16, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        for (int channel = 0; channel < 4; channel++) {
            require(Math.abs((pixel.get(channel) & 255) - expected[channel]) <= 1, "rendered unlit pixel channel " + channel);
        }
    }

    private static String resource(String name) throws java.io.IOException {
        try (var input = YsmPreviewLightingSmokeTest.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("Missing shader resource " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Object shard(String name) throws Exception {
        var field = RenderStateShard.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    public static boolean sortsOnUpload(RenderType material) throws Exception {
        var field = RenderType.class.getDeclaredField("sortOnUpload");
        field.setAccessible(true);
        return field.getBoolean(material);
    }

    private static Object field(Object value, String name) throws Exception {
        var field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
