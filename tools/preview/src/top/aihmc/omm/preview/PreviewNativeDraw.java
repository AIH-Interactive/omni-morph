package top.aihmc.omm.preview;

import java.nio.IntBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

/** Submits the same model VAO, textures and ranges through native GL entry points on the current context. */
final class PreviewNativeDraw implements AutoCloseable {
    private final long bindVertexArray = GL.getCapabilities().glBindVertexArray;
    private final long bindTexture = GL.getCapabilities().glBindTexture;
    private final long uniform1i = GL.getCapabilities().glUniform1i;
    private final long drawElements = GL.getCapabilities().glDrawElements;
    private final IntBuffer commands;

    PreviewNativeDraw(PreviewMesh mesh) {
        commands = MemoryUtil.memAllocInt(mesh.draws.size() * 4);
    }

    int draw(int vao, int texturedUniform, PreviewMesh mesh, int[] textures, boolean vanillaVisible) {
        commands.clear();
        for (var range : mesh.draws) {
            if (mesh.vanilla != null && mesh.vanilla.owns(range) && !vanillaVisible)
                continue;
            boolean textured = range.textureIndex() >= 0;
            commands.put(range.firstIndex())
                .put(range.indexCount())
                .put(textured ? textures[range.textureIndex()] : 0)
                .put(textured ? 1 : 0);
        }
        commands.flip();
        return submit(bindVertexArray, bindTexture, uniform1i, drawElements, vao, texturedUniform, commands,
            commands.remaining() / 4);
    }

    private static native int submit(long bindVertexArray, long bindTexture, long uniform1i, long drawElements, int vao,
        int texturedUniform, IntBuffer commands, int count);

    @Override
    public void close() {
        MemoryUtil.memFree(commands);
    }
}
