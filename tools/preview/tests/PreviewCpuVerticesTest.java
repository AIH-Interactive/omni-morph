package top.aihmc.omm.preview;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.List;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

/** Compares real GPU shader output against CPU-prepared vertices in a normal visible GL context. */
public final class PreviewCpuVerticesTest {
    private static void near(float actual, float expected) {
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > 0.00005f)
            throw new AssertionError(actual + " != " + expected);
    }

    static void check(PreviewMesh mesh, FloatBuffer palette, PreviewNativeVertices nativeVertices) {
        int vertices = mesh.owners.length;
        FloatBuffer cpu = MemoryUtil.memAllocFloat(mesh.vertices.length);
        FloatBuffer gpuResult = MemoryUtil.memAllocFloat(vertices * 4);
        FloatBuffer cpuResult = MemoryUtil.memAllocFloat(vertices * 4);
        int program = PreviewMain.program(PreviewMain.MODEL_VERTEX, """
            #version 330 core
            out vec4 pixel; void main() { pixel=vec4(0.3,0.8,0.4,1); }
            """);
        glTransformFeedbackVaryings(program, new CharSequence[] {"gl_Position"}, GL_INTERLEAVED_ATTRIBS);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE)
            throw new AssertionError(glGetProgramInfoLog(program));
        int vao = glGenVertexArrays(), input = glGenBuffers(), owners = glGenBuffers();
        int bind = glGenBuffers(), matrices = glGenBuffers(), texture = glGenTextures(), capture = glGenBuffers();
        try {
            if (nativeVertices == null)
                PreviewCpuVertices.transform(mesh.vertices, mesh.owners, palette, cpu);
            else
                nativeVertices.transform(palette, cpu);
            for (int vertex = 0; vertex < vertices; vertex++) {
                near(cpu.get(vertex * 5 + 3), mesh.vertices[vertex * 5 + 3]);
                near(cpu.get(vertex * 5 + 4), mesh.vertices[vertex * 5 + 4]);
            }
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, input);
            glBufferData(GL_ARRAY_BUFFER, mesh.vertices, GL_STREAM_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 20, 0);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 20, 12);
            glBindBuffer(GL_ARRAY_BUFFER, bind);
            glBufferData(GL_ARRAY_BUFFER, mesh.vertices, GL_STATIC_DRAW);
            glEnableVertexAttribArray(3);
            glVertexAttribPointer(3, 3, GL_FLOAT, false, 20, 0);
            glBindBuffer(GL_ARRAY_BUFFER, owners);
            glBufferData(GL_ARRAY_BUFFER, mesh.owners, GL_STATIC_DRAW);
            glEnableVertexAttribArray(2);
            glVertexAttribIPointer(2, 1, GL_UNSIGNED_INT, 4, 0);
            glBindBuffer(GL_TEXTURE_BUFFER, matrices);
            glBufferData(GL_TEXTURE_BUFFER, palette, GL_DYNAMIC_DRAW);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_BUFFER, texture);
            glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, matrices);
            glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER, capture);
            glBufferData(GL_TRANSFORM_FEEDBACK_BUFFER, (long) vertices * 16, GL_STREAM_READ);
            glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, capture);
            glUseProgram(program);
            glUniform1i(glGetUniformLocation(program, "boneMatrices"), 0);
            float[] identity = new Matrix4f().get(new float[16]);
            glUniformMatrix4fv(glGetUniformLocation(program, "transform"), false, identity);
            for (boolean prepared : new boolean[] {false, true}) {
                if (prepared) {
                    glBindBuffer(GL_ARRAY_BUFFER, input);
                    glBufferSubData(GL_ARRAY_BUFFER, 0, cpu);
                }
                glUniform1i(glGetUniformLocation(program, "cpuPrepared"), prepared ? 1 : 0);
                glClear(GL_COLOR_BUFFER_BIT);
                glBeginTransformFeedback(GL_POINTS);
                glDrawArrays(GL_POINTS, 0, vertices);
                glEndTransformFeedback();
                glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER, capture);
                glGetBufferSubData(GL_TRANSFORM_FEEDBACK_BUFFER, 0, prepared ? cpuResult : gpuResult);
                glfwSwapBuffers(glfwGetCurrentContext());
                glfwPollEvents();
            }
            for (int index = 0; index < vertices * 4; index++) near(cpuResult.get(index), gpuResult.get(index));
            if (glGetError() != GL_NO_ERROR)
                throw new AssertionError("CPU/GPU comparison encountered an OpenGL error");
        } finally {
            glDeleteProgram(program);
            glDeleteVertexArrays(vao);
            glDeleteBuffers(new int[] {input, owners, bind, matrices, capture});
            glDeleteTextures(texture);
            MemoryUtil.memFree(cpu);
            MemoryUtil.memFree(cpuResult);
            MemoryUtil.memFree(gpuResult);
        }
    }

    private static void compareMatrices(Matrix4f[] actual, Matrix4f[] expected) {
        float[] left = new float[16], right = new float[16];
        for (int index = 0; index < actual.length; index++) {
            actual[index].get(left);
            expected[index].get(right);
            for (int value = 0; value < 16; value++) near(left[value], right[value]);
        }
    }

    private static void compareCompose(PreviewMesh mesh, PreviewNativeVertices nativeVertices) {
        int count = mesh.skeleton.names.length;
        Matrix4f[] local = new Matrix4f[count], expectedWorld = new Matrix4f[count];
        Matrix4f[] actualPalette = new Matrix4f[count], expectedPalette = new Matrix4f[count];
        boolean[] visible = new boolean[count], expectedVisible = new boolean[count];
        for (int index = 0; index < count; index++) {
            local[index] = new Matrix4f()
                               .translate(index * 0.01f, 0.2f, -0.3f)
                               .rotateXYZ(0.1f, -0.2f, 0.3f)
                               .scale(index % 2 == 0 ? -1 : 1, 1.1f, 0.9f);
            actualPalette[index] = new Matrix4f();
            visible[index] = true;
        }
        int root = mesh.skeleton.order[0];
        local[root].scaling(0);
        visible[root] = false;
        for (int index : mesh.skeleton.order) {
            int parent = mesh.skeleton.parents[index];
            expectedWorld[index] =
                parent < 0 ? new Matrix4f(local[index]) : new Matrix4f(expectedWorld[parent]).mul(local[index]);
            expectedPalette[index] = new Matrix4f(expectedWorld[index]).mul(mesh.skeleton.inverseBindWorld[index]);
            expectedVisible[index] = visible[index] && (parent < 0 || expectedVisible[parent]);
            if (!expectedVisible[index])
                expectedPalette[index].scaling(0);
        }
        nativeVertices.compose(0, count, local, actualPalette, visible);
        compareMatrices(local, expectedWorld);
        compareMatrices(actualPalette, expectedPalette);
        if (!java.util.Arrays.equals(visible, expectedVisible))
            throw new AssertionError("Native inherited visibility differs from Java hierarchy");
    }

    public static void main(String[] args) throws Exception {
        if (!glfwInit())
            throw new IllegalStateException("GLFW initialization failed");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(640, 480, "CPU/GPU vertex parity", 0, 0);
        if (window == 0)
            throw new IllegalStateException("Visible comparison window failed");
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            for (String name : List.of("animated-ysm.json", "controlled-ysm.json", "figura-keyframes.json",
                     "lua-animated-ysm.json", "figura-scripted-parts.json", "figura-vanilla.json")) {
                var config = PreviewConfig.load(new String[] {"--config", Path.of(args[0]).resolve(name).toString()});
                config.settings.enabled = false;
                var mesh = PreviewMesh.load(config);
                FloatBuffer palette = MemoryUtil.memAllocFloat(mesh.skeleton.names.length * 16);
                try (var animation = new PreviewAnimation(mesh, config)) {
                    var state = animation.newState();
                    int frame = 0;
                    for (float time : new float[] {0, 0.125f, 0.5f}) {
                        state.sample(frame++, time);
                        state.updateBones();
                        state.writePalette(palette);
                        check(mesh, palette, null);
                    }
                    // Zero-scale hiding, reflection and nonuniform transforms use the same affine math.
                    new Matrix4f().scaling(0).get(0, palette);
                    if (mesh.skeleton.names.length > 1)
                        new Matrix4f().translate(1, -2, 3).rotateY(0.7f).scale(-2, 3, 0.5f).get(16, palette);
                    check(mesh, palette, null);
                    if (args.length > 1) {
                        config.execution.nativeLibrary = Path.of(args[1]).toAbsolutePath().toString();
                        for (int threads : new int[] {1, 8, 16}) {
                            config.execution.threads = threads;
                            try (var nativeVertices = new PreviewNativeVertices(mesh, config)) {
                                if (nativeVertices.threads != threads)
                                    throw new AssertionError("Native CPU thread budget differs from requested value");
                                check(mesh, palette, nativeVertices);
                                compareCompose(mesh, nativeVertices);
                                var nativeState = animation.newState();
                                var referenceState = animation.newState();
                                int nativeFrame = 0;
                                for (float time : new float[] {0, 0.125f, 0.5f}) {
                                    referenceState.sample(nativeFrame, time);
                                    referenceState.updateBones();
                                    nativeState.sample(nativeFrame++, time);
                                    nativeState.updateBones(nativeVertices);
                                    compareMatrices(nativeState.palette, referenceState.palette);
                                    nativeState.writePalette(palette);
                                    check(mesh, palette, nativeVertices);
                                }
                            }
                        }
                    }
                } finally {
                    MemoryUtil.memFree(palette);
                }
            }
            if (args.length > 1) {
                int count = 12288; // 48 native blocks: enough work for all 16 requested participants.
                float[] vertices = new float[count * 5];
                int[] indices = new int[count];
                for (int vertex = 0; vertex < count; vertex++) {
                    vertices[vertex * 5] = (vertex % 31) / 32f;
                    vertices[vertex * 5 + 1] = (vertex % 17) / 18f;
                    vertices[vertex * 5 + 2] = (vertex % 7) / 8f;
                    vertices[vertex * 5 + 3] = 0.25f;
                    vertices[vertex * 5 + 4] = 0.75f;
                    indices[vertex] = vertex;
                }
                var mesh = new PreviewMesh(vertices, indices, List.of(),
                    List.of(new PreviewMesh.DrawRange(0, count, -1)), "parallel vertex fixture", true);
                var config = new PreviewConfig();
                config.configFile = Path.of(args[0]).toAbsolutePath().resolve("native-parallel.json");
                config.execution.nativeLibrary = Path.of(args[1]).toAbsolutePath().toString();
                FloatBuffer palette = MemoryUtil.memAllocFloat(16);
                try {
                    new Matrix4f().translate(1, -2, 3).rotateZ(0.3f).scale(-2, 3, 0.5f).get(palette);
                    for (int threads : new int[] {1, 8, 16, 0}) {
                        config.execution.threads = threads;
                        try (var nativeVertices = new PreviewNativeVertices(mesh, config)) {
                            if (nativeVertices.threads < 1 || threads > 0 && nativeVertices.threads != threads)
                                throw new AssertionError("Incorrect explicit/automatic CPU budget");
                            check(mesh, palette, nativeVertices);
                        }
                    }
                } finally {
                    MemoryUtil.memFree(palette);
                }
            }
            System.out.println("CPU vertices PASS: actual GPU shader parity for YSM/Figura, animation/controller, "
                + "zero-scale visibility, reflected/nonuniform matrices, unchanged UVs and optional native 1/8/16 "
                + "kernels");
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
