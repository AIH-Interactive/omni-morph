package top.aihmc.omm;

import org.joml.Matrix4f;
import static org.lwjgl.opengl.GL33C.*;

/** Static preview scenery only: a textured level plane, with no game world simulation. */
final class SuperflatBackground implements AutoCloseable {
    private int program;
    private int vao;
    private int buffer;
    private int texture;

    SuperflatBackground() throws Exception {
        int previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            byte[] grass = new MinecraftTextureSources().read("minecraft:textures/block/grass_block_top.png");
            texture = CpuPreviewWindow.texture(grass);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            int vertex = CpuPreviewWindow.shader(GL_VERTEX_SHADER, """
                #version 330 core
                layout(location=0) in vec3 position;
                layout(location=1) in vec2 uv;
                uniform mat4 camera;
                out vec2 textureUv;
                void main() {
                    gl_Position = camera * vec4(position, 1);
                    textureUv = uv;
                }
                """);
            int fragment;
            try {
                fragment = CpuPreviewWindow.shader(GL_FRAGMENT_SHADER, """
                    #version 330 core
                    uniform sampler2D grass;
                    in vec2 textureUv;
                    out vec4 color;
                    void main() {
                        color = texture(grass, textureUv) * vec4(0.56, 0.74, 0.35, 1);
                    }
                    """);
            } catch (Exception failure) {
                glDeleteShader(vertex);
                throw failure;
            }
            program = glCreateProgram();
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glLinkProgram(program);
            glDeleteShader(vertex);
            glDeleteShader(fragment);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                throw new IllegalStateException(glGetProgramInfoLog(program));
            }
            vao = glGenVertexArrays();
            buffer = glGenBuffers();
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, buffer);
            glBufferData(GL_ARRAY_BUFFER, new float[]{
                -128, 0, -128, 0, 0,
                -128, 0, 128, 0, 256,
                128, 0, 128, 256, 256,
                -128, 0, -128, 0, 0,
                128, 0, 128, 256, 256,
                128, 0, -128, 256, 0
            }, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 20, 0L);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 20, 12L);
        } catch (Exception failure) {
            close();
            throw failure;
        } finally {
            glBindVertexArray(previousVao);
            glBindBuffer(GL_ARRAY_BUFFER, previousBuffer);
            glBindTexture(GL_TEXTURE_2D, previousTexture);
            glActiveTexture(previousActiveTexture);
        }
    }

    void draw(Matrix4f camera) {
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int previousSampler = glGetInteger(GL_SAMPLER_BINDING);
        boolean blend = glIsEnabled(GL_BLEND);
        boolean cull = glIsEnabled(GL_CULL_FACE);
        boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
        boolean polygonOffset = glIsEnabled(GL_POLYGON_OFFSET_FILL);
        int[] polygonMode = new int[2];
        glGetIntegerv(GL_POLYGON_MODE, polygonMode);
        var colorMask = org.lwjgl.BufferUtils.createByteBuffer(4);
        glGetBooleanv(GL_COLOR_WRITEMASK, colorMask);
        boolean depth = glIsEnabled(GL_DEPTH_TEST);
        boolean depthWrite = glGetBoolean(GL_DEPTH_WRITEMASK);
        int depthFunction = glGetInteger(GL_DEPTH_FUNC);
        try {
            glDisable(GL_BLEND);
            glDisable(GL_CULL_FACE);
            glDisable(GL_SCISSOR_TEST);
            glDisable(GL_POLYGON_OFFSET_FILL);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            glColorMask(true, true, true, true);
            glBindSampler(0, 0);
            glEnable(GL_DEPTH_TEST);
            glDepthMask(true);
            glDepthFunc(GL_LESS);
            glUseProgram(program);
            glUniformMatrix4fv(glGetUniformLocation(program, "camera"), false, camera.get(new float[16]));
            glUniform1i(glGetUniformLocation(program, "grass"), 0);
            glBindTexture(GL_TEXTURE_2D, texture);
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 6);
        } finally {
            glBindVertexArray(previousVao);
            glUseProgram(previousProgram);
            glBindTexture(GL_TEXTURE_2D, previousTexture);
            glBindSampler(0, previousSampler);
            glActiveTexture(previousActiveTexture);
            enabled(GL_BLEND, blend);
            enabled(GL_CULL_FACE, cull);
            enabled(GL_DEPTH_TEST, depth);
            enabled(GL_SCISSOR_TEST, scissor);
            enabled(GL_POLYGON_OFFSET_FILL, polygonOffset);
            glPolygonMode(GL_FRONT_AND_BACK, polygonMode[0]);
            glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                colorMask.get(2) != 0, colorMask.get(3) != 0);
            glDepthMask(depthWrite);
            glDepthFunc(depthFunction);
        }
    }

    private static void enabled(int capability, boolean enabled) {
        if (enabled) {
            glEnable(capability);
        } else {
            glDisable(capability);
        }
    }

    @Override
    public void close() {
        glDeleteTextures(texture);
        glDeleteBuffers(buffer);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
