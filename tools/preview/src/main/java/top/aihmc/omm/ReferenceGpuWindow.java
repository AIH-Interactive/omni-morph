package top.aihmc.omm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43C.*;

/** Visible host for the MOD's original GPU mesh, bone computation and shaders. */
final class ReferenceGpuWindow {
    private ReferenceGpuWindow() {
    }

    static void run(String backend, Object model, Path texturePath) throws Exception {
        int frameLimit = Integer.parseInt(System.getProperty("omm.preview.frames", "120"));
        if (frameLimit <= 0) {
            throw new IllegalArgumentException("Frame limit must be positive");
        }
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW initialization failed");
        }
        long window = 0;
        Object mesh = null;
        int[] textures = new int[3];
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            window = glfwCreateWindow(960, 640, backend + " original JAR GPU", 0, 0);
            if (window == 0) {
                throw new IllegalStateException("OpenGL 4.3 window creation failed");
            }
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            glfwSwapInterval(1);
            String prefix = backend.equals("spm") ? "com.micaftic.morpher.core.gpu" : "rip.ysm.gpu";
            Class<?> shader = Class.forName(prefix + ".BoneSkinShader");
            if (!(boolean) shader.getMethod("ensureCompiled").invoke(null)) {
                throw new IllegalStateException("Original MOD shader compilation failed");
            }
            mesh = Class.forName(prefix + ".GpuMeshBuilder").getMethod("build", model.getClass()).invoke(null, model);
            if (mesh == null) {
                throw new IllegalStateException("Original MOD GPU mesh creation failed");
            }
            textures[0] = texture(texturePath);
            // The MOD shader mixes overlay.rgb -> model.rgb using overlay.a.
            textures[1] = solidTexture(0x000000ff);
            textures[2] = solidTexture(0xffffffff);
            Matrix4f root = new Matrix4f().translate(0, -0.6f, -3.5f).rotateY(0.35f);
            Matrix3f normal = root.normal(new Matrix3f());
            float[] parameters = (float[]) model.getClass().getMethod("getBoneTransformData").invoke(model);
            ByteBuffer bones = (ByteBuffer) mesh.getClass().getField("perFrameBoneBuffer").get(mesh);
            int program = (int) shader.getMethod("program").invoke(null);
            int vao = mesh.getClass().getField("vao").getInt(mesh);
            int count = (int) mesh.getClass().getMethod("indexDrawCount", int.class).invoke(mesh, 0);
            int offset = (int) mesh.getClass().getMethod("indexOffsetBytes", int.class).invoke(mesh, 0);
            if (count <= 0) {
                throw new AssertionError("Original GPU mesh has no drawable indices");
            }
            glEnable(GL_DEPTH_TEST);
            glViewport(0, 0, 960, 640);
            int nonBackground = 0;
            for (int frame = 0; frame < frameLimit && !glfwWindowShouldClose(window); frame++) {
                bones.clear();
                if (backend.equals("spm")) {
                    Class<?> computer = Class.forName(prefix + ".BoneMatrixComputer");
                    boolean computed = (boolean) computer.getMethod("compute", model.getClass(), Matrix4f.class,
                        Matrix3f.class, float[].class, float[].class, int.class, ByteBuffer.class)
                        .invoke(null, model, root, normal, parameters, new float[parameters.length / 3], 0x00F000F0, bones);
                    if (!computed) {
                        throw new IllegalStateException("Original bone computation declined the model");
                    }
                } else {
                    long pointer = mesh.getClass().getField("pointer").getLong(mesh);
                    model.getClass().getMethod("nComputeBoneMatrices", long.class, float[].class, float[].class,
                        float[].class, int.class, ByteBuffer.class)
                        .invoke(null, pointer, root.get(new float[16]), normal.get(new float[9]), parameters, 0x00F000F0, bones);
                }
                bones.position(0);
                bones.limit(mesh.getClass().getField("boneCount").getInt(mesh) * 144);
                int ssbo = backend.equals("spm")
                    ? (int) mesh.getClass().getMethod("nextBoneSsbo").invoke(mesh)
                    : mesh.getClass().getField("boneSsbo").getInt(mesh);
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo);
                if (backend.equals("spm")) {
                    glBufferData(GL_SHADER_STORAGE_BUFFER, bones, GL_STREAM_DRAW);
                } else {
                    glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, bones);
                }
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, ssbo);
                glClearColor(0, 0, 0, 1);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                glUseProgram(program);
                glUniformMatrix4fv(location(shader, "locProj"), false,
                    new Matrix4f().perspective((float) Math.toRadians(50), 1.5f, 0.05f, 100).get(new float[16]));
                glUniform4f(location(shader, "locColor"), 1, 1, 1, 1);
                glUniform1i(location(shader, "locOverlay"), 0);
                glUniform1i(location(shader, "locAlphaMode"), 0);
                glUniform1f(location(shader, "locFogStart"), 100);
                glUniform1f(location(shader, "locFogEnd"), 200);
                glUniform3f(location(shader, "locLight0"), 0.2f, 1, -0.7f);
                glUniform3f(location(shader, "locLight1"), -0.2f, 1, 0.7f);
                for (int unit = 0; unit < textures.length; unit++) {
                    glActiveTexture(GL_TEXTURE0 + unit);
                    glBindTexture(GL_TEXTURE_2D, textures[unit]);
                }
                glBindVertexArray(vao);
                glDrawElements(GL_TRIANGLES, count, GL_UNSIGNED_INT, (long) offset);
                if (frame == frameLimit - 1) {
                    nonBackground = nonBackgroundPixels();
                }
                int error = glGetError();
                if (error != GL_NO_ERROR) {
                    throw new IllegalStateException("OpenGL error " + error);
                }
                glfwSwapBuffers(window);
                glfwPollEvents();
            }
            if (nonBackground < 100) {
                throw new AssertionError("GPU window incomplete or model not visible: pixels=" + nonBackground);
            }
            System.out.println("GPU WINDOW PASS original mesh/shader/bones indices=" + count
                + " pixels=" + nonBackground + " frames=" + frameLimit);
        } finally {
            if (mesh != null) {
                mesh.getClass().getMethod("dispose").invoke(mesh);
            }
            if (window != 0) {
                for (int texture : textures) {
                    glDeleteTextures(texture);
                }
                glfwDestroyWindow(window);
            }
            glfwTerminate();
        }
    }

    private static int location(Class<?> shader, String method) throws Exception {
        return (int) shader.getMethod(method).invoke(null);
    }

    private static int texture(Path path) throws Exception {
        var image = ImageIO.read(path.toFile());
        ByteBuffer pixels = MemoryUtil.memAlloc(image.getWidth() * image.getHeight() * 4);
        try {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    pixels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >> 24));
                }
            }
            pixels.flip();
            return uploadTexture(image.getWidth(), image.getHeight(), pixels);
        } finally {
            MemoryUtil.memFree((java.nio.Buffer) pixels);
        }
    }

    private static int solidTexture(int rgba) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(16 * 16 * 4).order(ByteOrder.BIG_ENDIAN);
        while (pixels.hasRemaining()) {
            pixels.putInt(rgba);
        }
        pixels.flip();
        return uploadTexture(16, 16, pixels);
    }

    private static int uploadTexture(int width, int height, ByteBuffer pixels) {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return texture;
    }

    private static int nonBackgroundPixels() {
        ByteBuffer pixels = MemoryUtil.memAlloc(960 * 640 * 4);
        try {
            glReadPixels(0, 0, 960, 640, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            int count = 0;
            for (int index = 0; index < pixels.capacity(); index += 4) {
                if ((pixels.get(index) & 255) + (pixels.get(index + 1) & 255) + (pixels.get(index + 2) & 255) > 12) {
                    count++;
                }
            }
            return count;
        } finally {
            MemoryUtil.memFree((java.nio.Buffer) pixels);
        }
    }
}
