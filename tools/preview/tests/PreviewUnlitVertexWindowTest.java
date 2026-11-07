package top.aihmc.omm.preview;

import static org.lwjgl.glfw.GLFW.*;

import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

/** Real visible-window shader comparison for native unlit output; no model assets or headless mode. */
public final class PreviewUnlitVertexWindowTest {
    public static void main(String[] args) throws Exception {
        if (!glfwInit())
            throw new IllegalStateException("GLFW initialization failed");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(640, 480, "Native unlit CPU/GPU parity", 0, 0);
        if (window == 0)
            throw new IllegalStateException("Real parity window failed");
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            glfwSwapBuffers(window);
            glfwPollEvents();
            Thread.sleep(200);
            int count = 100_000;
            float[] vertices = new float[count * 5];
            int[] owners = new int[count];
            for (int vertex = 0; vertex < count; vertex++) {
                vertices[vertex * 5] = (vertex % 97 - 48) / 16f;
                vertices[vertex * 5 + 1] = (vertex % 71 - 35) / 16f;
                vertices[vertex * 5 + 2] = (vertex % 53 - 26) / 16f;
                vertices[vertex * 5 + 3] = (vertex % 31) / 32f;
                vertices[vertex * 5 + 4] = (vertex % 17) / 32f;
                owners[vertex] = vertex / 3 % 3;
            }
            var parts = new PreviewFiguraParts();
            for (int bone = 0; bone < 3; bone++) {
                parts.nodes.add(new PreviewFiguraParts.Node("bone" + bone, "bone" + bone, -1, new Vector3f(),
                    new Vector3f(), false, true, new Matrix4f(), new Matrix4f()));
            }
            var mesh = new PreviewMesh(vertices, new int[] {0, 1, 2}, List.of(), List.of(), "owned multi-bone parity",
                true, new PreviewSkeleton(parts), owners, List.of(), PreviewYsmResources.EMPTY);
            var config = new PreviewConfig();
            config.configFile = Path.of("build/preview/unlit-window.json").toAbsolutePath();
            config.execution.nativeLibrary = Path.of(args[0]).toAbsolutePath().toString();
            config.execution.threads = 1;
            FloatBuffer palette = MemoryUtil.memAllocFloat(48);
            try (var nativeVertices = new PreviewNativeVertices(mesh, config)) {
                for (boolean zero : new boolean[] {false, true}) {
                    for (int bone = 0; bone < 3; bone++) {
                        Matrix4f pose = zero || bone == 2 ? new Matrix4f().scaling(0)
                            : bone == 0 ? new Matrix4f()
                                              .translation(0.2f, -0.3f, 0.4f)
                                              .rotateXYZ(0.13f, -0.27f, 0.31f)
                                              .scale(-1.2f, 0.8f, 1.7f)
                                        : new Matrix4f().translation(-2, 1, 3).rotateY(-0.4f).scale(0.5f, -2, 1);
                        pose.get(bone * 16, palette);
                    }
                    PreviewCpuVerticesTest.check(mesh, palette, nativeVertices);
                }
                System.out.println("Unlit window PASS: actual GPU output for 100000 mixed-owner vertices, "
                                   + "reflection/nonuniform/zero scale and unchanged UVs; one thread");
            } finally {
                MemoryUtil.memFree(palette);
            }
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
