package top.aihmc.omm.preview;

import static org.lwjgl.glfw.GLFW.*;

import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

/** Test-only pose publication experiment; never selected by the production preview. */
public final class PreviewPoseCandidateWindowTest {
    private static final int BONES = 2;

    private static final class Pose implements AutoCloseable {
        final Matrix4f[] world = {new Matrix4f(), new Matrix4f()};
        final Matrix4f[] palette = {new Matrix4f(), new Matrix4f()};
        final boolean[] visible = new boolean[BONES];
        final FloatBuffer published = MemoryUtil.memAllocFloat(BONES * 16);

        @Override
        public void close() {
            MemoryUtil.memFree(published);
        }
    }

    private static final class Candidate implements AutoCloseable {
        private final PreviewNativeVertices bridge;

        Candidate(PreviewMesh mesh, PreviewConfig config) {
            bridge = new PreviewNativeVertices(mesh, config);
        }

        void update(Pose pose, float x, boolean visible) {
            pose.world[0].translation(x, 0, 0);
            pose.world[1].translation(0, 0.25f, 0).scale(-2, 3, 0.5f);
            pose.visible[0] = visible;
            pose.visible[1] = true;
            bridge.compose(0, BONES, pose.world, pose.palette, pose.visible);
            for (int bone = 0; bone < BONES; bone++) {
                pose.palette[bone].get(bone * 16, pose.published);
            }
        }

        @Override
        public void close() {
            bridge.close();
        }
    }

    private static PreviewMesh mesh() {
        var parts = new PreviewFiguraParts();
        for (int bone = 0; bone < BONES; bone++) {
            parts.nodes.add(new PreviewFiguraParts.Node("bone" + bone, "bone" + bone, bone - 1,
                new Vector3f(), new Vector3f(), false, true, new Matrix4f(), new Matrix4f()));
        }
        return new PreviewMesh(
            new float[] {0, 0, 0, 0, 0, 0.1f, 0, 0, 1, 0, 0, 0.1f, 0, 0, 1},
            new int[] {0, 1, 2}, List.of(), List.of(), "pose publication", true,
            new PreviewSkeleton(parts), new int[] {1, 1, 1}, List.of(), PreviewYsmResources.EMPTY);
    }

    private static void near(float actual, float expected, String label) {
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > 0.00005f) {
            throw new AssertionError(label + ": " + actual + " != " + expected);
        }
    }

    private static void verify(Pose pose, float x, boolean visible) {
        // Hand-worked result: parent translates X, child translates Y then scales XYZ.
        float[] child = visible
            ? new float[] {-2, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0.5f, 0, x, 0.25f, 0, 1}
            : new float[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        for (int value = 0; value < 16; value++) {
            near(pose.published.get(16 + value), child[value], "published child " + value);
        }
        near(pose.world[1].m30(), x, "script world X");
        near(pose.world[1].m31(), 0.25f, "script world Y");
        if (pose.visible[1] != visible) {
            throw new AssertionError("Child visibility must inherit parent visibility");
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
        long window = glfwCreateWindow(640, 480, "Pose candidate isolation", 0, 0);
        if (window == 0) {
            glfwTerminate();
            throw new IllegalStateException("Visible candidate window failed");
        }
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            glfwSwapBuffers(window);
            glfwPollEvents();
            Thread.sleep(200);
            var mesh = mesh();
            var config = new PreviewConfig();
            config.configFile = Path.of("build/preview/pose-candidate.json").toAbsolutePath();
            config.execution.nativeLibrary = Path.of(args[0]).toAbsolutePath().toString();
            config.execution.threads = 1;
            try (var candidate = new Candidate(mesh, config); var a = new Pose(); var b = new Pose()) {
                for (int frame = 0; frame < 30; frame++) {
                    boolean visible = frame % 2 == 0;
                    candidate.update(a, -0.25f, visible);
                    candidate.update(b, 0.5f, true);
                    // Both updates precede either draw, matching the production multi-instance ordering.
                    verify(a, -0.25f, visible);
                    verify(b, 0.5f, true);
                    PreviewCpuVerticesTest.check(mesh, a.published, candidate.bridge);
                    PreviewCpuVerticesTest.check(mesh, b.published, candidate.bridge);
                }
                System.out.println("Pose candidate PASS: 30 paired updates, delayed publication, "
                    + "world query values, hide/restore, reflection/nonuniform scale and CPU/GPU parity");
            }
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
