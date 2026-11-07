package top.aihmc.omm;

import static org.lwjgl.opengl.GL33C.*;

/** GL bindings for configured lightmap/overlay inputs, shared by both CPU drawing hosts. */
final class CpuLightTextures implements AutoCloseable {
    private final int overlay;
    private final int lightmap;

    CpuLightTextures() throws Exception {
        overlay = CpuPreviewWindow.texture(LightingTextureInputs.read("overlayTexture"));
        try {
            lightmap = CpuPreviewWindow.texture(LightingTextureInputs.read("lightmapTexture"));
        } catch (Exception failure) {
            glDeleteTextures(overlay);
            throw failure;
        }
    }

    void bind() {
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, overlay);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, lightmap);
        glActiveTexture(GL_TEXTURE0);
    }

    static void coordinates(int program, int light, int overlay, boolean emissive, boolean noOverlay) {
        glUniform2i(glGetUniformLocation(program, "lightCoordinates"), light & 65535, light >>> 16);
        glUniform2i(glGetUniformLocation(program, "overlayCoordinates"), overlay & 65535, overlay >>> 16);
        glUniform1i(glGetUniformLocation(program, "emissive"), emissive ? 1 : 0);
        glUniform1i(glGetUniformLocation(program, "noOverlay"), noOverlay ? 1 : 0);
    }

    @Override
    public void close() {
        glDeleteTextures(lightmap);
        glDeleteTextures(overlay);
    }
}
