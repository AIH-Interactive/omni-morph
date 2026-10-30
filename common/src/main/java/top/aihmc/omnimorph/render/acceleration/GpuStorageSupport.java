package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLCapabilities;
import top.aihmc.omnimorph.mixin.render.GlBufferAccessor;

import java.util.Locale;

/**
 * The only raw GL in GPU acceleration: storage block capability queries and binding points, which Blaze3D
 * does not expose. Everything else (programs, vertex arrays, textures, blending) stays with Blaze3D.
 * Render thread only.
 */
final class GpuStorageSupport {
    /** Pose storage blocks stay opt-in until the real-window acceptance comparison decides the default. */
    static final String PROPERTY = "omnimorph.gpu.ssbo";

    /** {@code models}: a third vertex-stage block and binding {@link GpuShaderProgram#BATCH_BINDING} for R4-3. */
    record Limits(int offsetAlignment, long maxBlockBytes, boolean models) {
    }

    private static volatile String unavailableReason = "not probed";

    private GpuStorageSupport() {
    }

    static boolean requested() {
        return Boolean.getBoolean(PROPERTY);
    }

    static String unavailableReason() {
        return unavailableReason;
    }

    /** Returns null with a recorded reason when this device cannot read pose storage blocks in the vertex stage. */
    static Limits probe() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return unavailable("macOS OpenGL lacks shader storage blocks");
        }
        GLCapabilities caps;
        try {
            caps = GL.getCapabilities();
        } catch (IllegalStateException noContext) {
            return unavailable("no current OpenGL context");
        }
        if (!(caps.OpenGL43 || caps.GL_ARB_shader_storage_buffer_object)) {
            return unavailable("shader storage blocks unsupported");
        }
        if (!(caps.OpenGL42 || caps.GL_ARB_shading_language_420pack)) {
            return unavailable("explicit block binding unsupported");
        }
        if (GL11.glGetInteger(GL43.GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS) < 2) {
            return unavailable("vertex stage storage blocks below 2");
        }
        if (GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) <= GpuShaderProgram.REMAP_BINDING) {
            return unavailable("storage binding points below " + (GpuShaderProgram.REMAP_BINDING + 1));
        }
        int alignment = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT);
        long maxBlock = GL32.glGetInteger64(GL43.GL_MAX_SHADER_STORAGE_BLOCK_SIZE);
        if (alignment <= 0 || Integer.bitCount(alignment) != 1 || maxBlock < GpuRigidData.POSE_BYTES) {
            return unavailable("storage limits alignment=" + alignment + " maxBlock=" + maxBlock);
        }
        boolean models = GL11.glGetInteger(GL43.GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS) >= 3
                && GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) > GpuShaderProgram.BATCH_BINDING;
        unavailableReason = null;
        return new Limits(alignment, maxBlock, models);
    }

    static void bind(int binding, GpuBuffer buffer, long offset, long size) {
        if (!(buffer instanceof GlBuffer)) {
            throw new IllegalStateException("Pose storage requires OpenGL buffers, got " + buffer.getClass().getName());
        }
        int handle = ((GlBufferAccessor) (Object) buffer).omnimorph$handle();
        GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, binding, handle, offset, size);
    }

    static void unbind(int binding) {
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, 0);
    }

    private static Limits unavailable(String reason) {
        unavailableReason = reason;
        return null;
    }
}
