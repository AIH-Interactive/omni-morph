package top.aihmc.omm;

import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL33C.*;

/** Draws already transformed original CPU output inside an existing visible GL window. */
final class CpuVertexPass implements AutoCloseable {
    private final int program;
    private final int vao;
    private final int vertexBuffer;
    private final int indexBuffer;
    private final int colorsBuffer;
    private final int lightingBuffer;
    private final CpuLightTextures lightTextures;

    CpuVertexPass() throws Exception {
        program = CpuPreviewWindow.program();
        try {
            lightTextures = new CpuLightTextures();
        } catch (Exception failure) {
            glDeleteProgram(program);
            throw failure;
        }
        vao = glGenVertexArrays();
        vertexBuffer = glGenBuffers();
        indexBuffer = glGenBuffers();
        colorsBuffer = glGenBuffers();
        lightingBuffer = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 36, 0L);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 36, 28L);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 3, GL_FLOAT, false, 36, 16L);
    }

    int draw(double[] output, float[] colors, int[] lighting, int texture, Matrix4f camera) {
        if (output.length % 36 != 0) {
            throw new IllegalArgumentException("Original CPU output must contain complete quads");
        }
        float[] vertices = new float[output.length];
        for (int index = 0; index < output.length; index++) {
            vertices[index] = (float) output[index];
        }
        int[] indices = ReferenceVertexOutput.quadIndices(output.length / 9);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STREAM_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STREAM_DRAW);
        if (colors.length != output.length / 9 * 4) {
            throw new IllegalArgumentException("Original CPU color count does not match vertices");
        }
        glBindBuffer(GL_ARRAY_BUFFER, colorsBuffer);
        glBufferData(GL_ARRAY_BUFFER, colors, GL_STREAM_DRAW);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, 16, 0L);
        glUseProgram(program);
        if (lighting.length != output.length / 9 * 4) {
            throw new IllegalArgumentException("Original lighting count does not match vertices");
        }
        glBindBuffer(GL_ARRAY_BUFFER, lightingBuffer);
        glBufferData(GL_ARRAY_BUFFER, lighting, GL_STREAM_DRAW);
        glEnableVertexAttribArray(4);
        glVertexAttribIPointer(4, 4, GL_INT, 16, 0L);
        glUniform1i(glGetUniformLocation(program, "vertexLighting"), 1);
        lightTextures.bind();
        glUniformMatrix4fv(glGetUniformLocation(program, "camera"), false, camera.get(new float[16]));
        glUniform1i(glGetUniformLocation(program, "modelTexture"), 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDrawElements(GL_TRIANGLES, indices.length, GL_UNSIGNED_INT, 0L);
        glDisable(GL_BLEND);
        return indices.length;
    }

    @Override
    public void close() {
        lightTextures.close();
        glDeleteBuffers(vertexBuffer);
        glDeleteBuffers(indexBuffer);
        glDeleteBuffers(colorsBuffer);
        glDeleteBuffers(lightingBuffer);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
