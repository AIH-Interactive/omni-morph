package top.aihmc.omm.preview;

import static org.lwjgl.opengl.GL33C.*;

import java.nio.FloatBuffer;
import java.util.Arrays;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

/** One immutable geometry/texture allocation per model, reused sequentially by its independent instances. */
final class PreviewModelRenderer implements AutoCloseable {
    final PreviewMesh mesh;
    final PreviewNativeVertices nativeVertices;
    final int[] textures;
    private final int vao = glGenVertexArrays();
    private final int vbo = glGenBuffers(), ebo = glGenBuffers(), owners = glGenBuffers();
    private final int bind;
    private final int paletteBuffer = glGenBuffers(), paletteTexture = glGenTextures();
    private final boolean cpu;
    private final FloatBuffer paletteValues, cpuValues;
    private final Matrix4f[] initial;
    private final float[][] bounds;
    private final PreviewNativeDraw nativeDraw;
    private final Matrix4f boundMatrix = new Matrix4f();
    private final Vector3f corner = new Vector3f();
    private final float[] transformValues = new float[16];

    PreviewModelRenderer(PreviewMesh mesh, PreviewConfig config, PreviewNativeVertices sharedRuntime) {
        this.mesh = mesh;
        cpu = config.execution.path.equals("cpu");
        bind = cpu ? glGenBuffers() : 0;
        nativeVertices = config.execution.implementation.equals("native")
            ? new PreviewNativeVertices(mesh, config, sharedRuntime)
            : null;
        nativeDraw = !cpu && nativeVertices != null ? new PreviewNativeDraw(mesh) : null;
        textures = mesh.textures.stream().mapToInt(PreviewMain::uploadTexture).toArray();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, mesh.vertices, cpu ? GL_STREAM_DRAW : GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 20, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 20, 12);
        glBindBuffer(GL_ARRAY_BUFFER, cpu ? bind : vbo);
        if (cpu)
            glBufferData(GL_ARRAY_BUFFER, mesh.vertices, GL_STATIC_DRAW);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 3, GL_FLOAT, false, 20, 0);
        glBindBuffer(GL_ARRAY_BUFFER, owners);
        glBufferData(GL_ARRAY_BUFFER, mesh.owners, GL_STATIC_DRAW);
        glEnableVertexAttribArray(2);
        glVertexAttribIPointer(2, 1, GL_UNSIGNED_INT, 4, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, mesh.indices, GL_STATIC_DRAW);
        int count = mesh.skeleton == null ? 1 : mesh.skeleton.names.length;
        if ((long) count * 4 > glGetInteger(GL_MAX_TEXTURE_BUFFER_SIZE))
            throw new UnsupportedOperationException("Skeleton exceeds GPU palette capacity");
        paletteValues = MemoryUtil.memAllocFloat(count * 16);
        cpuValues = cpu ? MemoryUtil.memAllocFloat(mesh.vertices.length) : null;
        initial = new Matrix4f[count];
        boolean[] visible = mesh.skeleton == null
            ? null
            : YsmVisibilityDefaults.visibility(mesh, new PreviewMolang(config.states, config.scene.seed), true);
        bounds = new float[count][6];
        for (int bone = 0; bone < count; bone++) {
            initial[bone] = new Matrix4f();
            if (visible != null && !visible[bone])
                initial[bone].scaling(0);
            initial[bone].get(bone * 16, paletteValues);
            Arrays.fill(bounds[bone], 0, 3, Float.POSITIVE_INFINITY);
            Arrays.fill(bounds[bone], 3, 6, Float.NEGATIVE_INFINITY);
        }
        for (int vertex = 0; vertex < mesh.owners.length; vertex++) {
            float[] box = bounds[mesh.owners[vertex]];
            for (int axis = 0; axis < 3; axis++) {
                float value = mesh.vertices[vertex * 5 + axis];
                box[axis] = Math.min(box[axis], value);
                box[axis + 3] = Math.max(box[axis + 3], value);
            }
        }
        glBindBuffer(GL_TEXTURE_BUFFER, paletteBuffer);
        glBufferData(GL_TEXTURE_BUFFER, paletteValues, GL_DYNAMIC_DRAW);
        glBindTexture(GL_TEXTURE_BUFFER, paletteTexture);
        glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, paletteBuffer);
    }

    boolean visible(FrustumIntersection frustum, Matrix4f placement, PreviewAnimation.State state) {
        Matrix4f[] palette = state == null ? initial : state.paletteMatrices();
        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int bone = 0; bone < bounds.length; bone++) {
            float[] box = bounds[bone];
            if (!Float.isFinite(box[0]))
                continue;
            Matrix4f matrix = palette[bone];
            if (matrix.m00() == 0 && matrix.m01() == 0 && matrix.m02() == 0 && matrix.m10() == 0 && matrix.m11() == 0
                && matrix.m12() == 0 && matrix.m20() == 0 && matrix.m21() == 0 && matrix.m22() == 0)
                continue;
            boundMatrix.set(placement).mul(matrix);
            for (int mask = 0; mask < 8; mask++) {
                corner.set(box[(mask & 1) == 0 ? 0 : 3], box[(mask & 2) == 0 ? 1 : 4], box[(mask & 4) == 0 ? 2 : 5]);
                boundMatrix.transformPosition(corner);
                minX = Math.min(minX, corner.x);
                minY = Math.min(minY, corner.y);
                minZ = Math.min(minZ, corner.z);
                maxX = Math.max(maxX, corner.x);
                maxY = Math.max(maxY, corner.y);
                maxZ = Math.max(maxZ, corner.z);
            }
        }
        return Float.isFinite(minX) && frustum.testAab(minX, minY, minZ, maxX, maxY, maxZ);
    }

    int draw(int program, Matrix4f transform, PreviewAnimation.State state, FrameLog log, FrameLog.Frame frame) {
        FloatBuffer framePalette = state == null ? paletteValues : state.paletteBuffer(paletteValues);
        if (cpu) {
            long start = System.nanoTime();
            if (nativeVertices == null)
                PreviewCpuVertices.transform(mesh.vertices, mesh.owners, framePalette, cpuValues);
            else
                nativeVertices.transform(framePalette, cpuValues);
            frame.verticesMs = Math.max(0, frame.verticesMs) + (System.nanoTime() - start) / 1e6;
        }
        long start = System.nanoTime();
        glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "cpuPrepared"), cpu ? 1 : 0);
        glUniformMatrix4fv(glGetUniformLocation(program, "transform"), false, transform.get(transformValues));
        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_BUFFER, paletteTexture);
        glUniform1i(glGetUniformLocation(program, "boneMatrices"), 3);
        if (cpu) {
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferSubData(GL_ARRAY_BUFFER, 0, cpuValues);
        } else {
            glBindBuffer(GL_TEXTURE_BUFFER, paletteBuffer);
            glBufferSubData(GL_TEXTURE_BUFFER, 0, framePalette);
        }
        glActiveTexture(GL_TEXTURE0);
        glUniform1i(glGetUniformLocation(program, "modelTexture"), 0);
        glBindVertexArray(vao);
        if (state != null)
            state.recordRenderedPivots();
        int query = log.beginQuery();
        if (query == 0)
            frame.completeModelQueries = false;
        else
            frame.modelQueries.add(query);
        int draws = 0;
        boolean vanillaVisible = state == null || state.lua() == null || state.lua().vanillaVisible();
        if (nativeDraw != null) {
            draws = nativeDraw.draw(vao, glGetUniformLocation(program, "textured"), mesh, textures, vanillaVisible);
            frame.nativeDrawCalls += draws;
        } else {
            for (var range : mesh.draws) {
                if (mesh.vanilla != null && mesh.vanilla.owns(range) && !vanillaVisible)
                    continue;
                boolean textured = range.textureIndex() >= 0;
                glBindTexture(GL_TEXTURE_2D, textured ? textures[range.textureIndex()] : 0);
                glUniform1i(glGetUniformLocation(program, "textured"), textured ? 1 : 0);
                glDrawElements(GL_TRIANGLES, range.indexCount(), GL_UNSIGNED_INT, (long) range.firstIndex() * 4);
                draws++;
            }
        }
        if (query != 0)
            glEndQuery(GL_TIME_ELAPSED);
        frame.submitMs += (System.nanoTime() - start) / 1e6;
        frame.drawCalls += draws;
        return draws;
    }

    @Override
    public void close() {
        if (nativeDraw != null)
            nativeDraw.close();
        if (nativeVertices != null)
            nativeVertices.close();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(new int[] {vbo, ebo, owners, bind, paletteBuffer});
        glDeleteTextures(textures);
        glDeleteTextures(paletteTexture);
        MemoryUtil.memFree(paletteValues);
        if (cpuValues != null)
            MemoryUtil.memFree(cpuValues);
    }
}
