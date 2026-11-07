package top.aihmc.omm.preview;

import static org.lwjgl.glfw.GLFW.*;

import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

/** Checks the actual JNI hierarchy and rendered vertex path in a visible Windows window. */
public final class PreviewBoneWindowTest {
    private static final int BONES = 96;

    private static PreviewMesh mesh() {
        var parts = new PreviewFiguraParts();
        for (int bone = 0; bone < BONES; bone++) {
            parts.nodes.add(new PreviewFiguraParts.Node("bone" + bone, "bone" + bone, -1,
                new Vector3f(), new Vector3f(), false, true, new Matrix4f(), new Matrix4f()));
        }
        var skeleton = new PreviewSkeleton(parts);
        // Two independent reversed chains exercise non-index order and nonzero subrange offsets.
        for (int bone = 0; bone < BONES; bone++) {
            skeleton.parents[bone] = bone % 48 == 47 ? -1 : bone + 1;
            skeleton.order[bone] = bone < 48 ? 47 - bone : 143 - bone;
            skeleton.inverseBindWorld[bone].translation(0.01f * bone, 0, 0);
        }
        float[] vertices = new float[BONES * 3 * 5];
        int[] owners = new int[BONES * 3];
        int[] indices = new int[owners.length];
        for (int vertex = 0; vertex < owners.length; vertex++) {
            owners[vertex] = vertex / 3;
            indices[vertex] = vertex;
            vertices[vertex * 5] = vertex % 3 == 1 ? 0.2f : 0;
            vertices[vertex * 5 + 1] = vertex % 3 == 2 ? 0.2f : 0;
        }
        return new PreviewMesh(vertices, indices, List.of(), List.of(), "bone order parity", true,
            skeleton, owners, List.of(), PreviewYsmResources.EMPTY);
    }

    private static void near(Matrix4f actual, Matrix4f expected) {
        float[] left = actual.get(new float[16]);
        float[] right = expected.get(new float[16]);
        for (int value = 0; value < 16; value++) {
            if (!Float.isFinite(left[value]) || Math.abs(left[value] - right[value]) > 0.00005f) {
                throw new AssertionError("Bone matrix differs: " + left[value] + " != " + right[value]);
            }
        }
    }

    private static void compare(PreviewMesh mesh, PreviewNativeVertices nativeVertices, int frame,
        int offset, int count, FloatBuffer output) {
        Matrix4f[] local = new Matrix4f[count];
        Matrix4f[] expected = new Matrix4f[count];
        Matrix4f[] palette = new Matrix4f[count];
        boolean[] visible = new boolean[count];
        boolean[] expectedVisible = new boolean[count];
        for (int index = 0; index < count; index++) {
            local[index] = new Matrix4f().translation(0.001f * index, 0, 0)
                               .rotateZ((frame + index) * 0.001f)
                               .scale(index % 7 == 0 ? -1 : 1, 1.001f, 0.999f);
            if (frame % 3 == 0 && index == 30) {
                local[index].scale(0);
            }
            visible[index] = frame % 2 == 0 || index != 32;
            palette[index] = new Matrix4f();
        }
        for (int bone : mesh.skeleton.order) {
            if (bone < offset || bone >= offset + count) {
                continue;
            }
            int index = bone - offset;
            int parent = mesh.skeleton.parents[bone];
            expected[index] = parent < 0 ? new Matrix4f(local[index])
                : new Matrix4f(expected[parent - offset]).mul(local[index]);
            expectedVisible[index] = visible[index] && (parent < 0 || expectedVisible[parent - offset]);
        }
        boolean directPublication = frame % 2 == 0;
        if (directPublication) {
            nativeVertices.beginPublication(output, false);
        }
        try {
            nativeVertices.compose(offset, count, local, palette, visible);
        } finally {
            if (directPublication && !nativeVertices.endPublication()) {
                throw new AssertionError("Native palette was not published");
            }
        }
        for (int index = 0; index < count; index++) {
            near(local[index], expected[index]);
            Matrix4f expectedPalette = new Matrix4f(expected[index])
                                           .mul(mesh.skeleton.inverseBindWorld[index + offset]);
            if (!expectedVisible[index]) {
                expectedPalette.scaling(0);
            }
            if (directPublication) {
                near(palette[index], new Matrix4f());
                near(new Matrix4f().set((index + offset) * 16, output), expectedPalette);
            } else {
                near(palette[index], expectedPalette);
            }
            if (visible[index] != expectedVisible[index]) {
                throw new AssertionError("Inherited visibility differs");
            }
            if (!directPublication) {
                palette[index].get((index + offset) * 16, output);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW initialization failed");
        }
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
        long window = glfwCreateWindow(640, 480, "Native single-thread bone parity", 0, 0);
        if (window == 0) {
            glfwTerminate();
            throw new IllegalStateException("Real bone window failed");
        }
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            glfwSwapBuffers(window);
            glfwPollEvents();
            Thread.sleep(200);
            var mesh = mesh();
            var config = new PreviewConfig();
            config.configFile = Path.of("build/preview/bone-window.json").toAbsolutePath();
            config.execution.nativeLibrary = Path.of(args[0]).toAbsolutePath().toString();
            config.execution.threads = 1;
            FloatBuffer palette = MemoryUtil.memAllocFloat(BONES * 16);
            try (var nativeVertices = new PreviewNativeVertices(mesh, config)) {
                for (int frame = 0; frame < 60; frame++) {
                    compare(mesh, nativeVertices, frame, 0, BONES, palette);
                    compare(mesh, nativeVertices, frame, 48, 48, palette);
                    PreviewCpuVerticesTest.check(mesh, palette, nativeVertices);
                }
                System.out.println("Bone window PASS: 60 rendered frames, reversed hierarchy, subrange, "
                                   + "reflection/zero scale, world/palette/visibility and GPU vertex parity");
            } finally {
                MemoryUtil.memFree(palette);
            }
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
