package top.aihmc.omnimorph.test;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

/** A visible desktop context for native texture tests; an existing current context is borrowed. */
public final class NativeGlWindow implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private long window;

    private NativeGlWindow(long window) {
        this.window = window;
    }

    public static NativeGlWindow open() {
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Cannot initialize GLFW for native texture validation");
        }
        if (GLFW.glfwGetCurrentContext() != 0L) {
            return new NativeGlWindow(0L);
        }

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(360, 160, "OmniMorph native texture validation", 0L, 0L);
        if (window == 0L) {
            GLFW.glfwTerminate();
            throw new IllegalStateException("Cannot create the native texture validation window");
        }

        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            GL11.glClearColor(0f, 0f, 0f, 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
            return new NativeGlWindow(window);
        } catch (RuntimeException | LinkageError failure) {
            GLFW.glfwMakeContextCurrent(0L);
            GL.setCapabilities(null);
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
            throw failure;
        }
    }

    @Override
    public void close() {
        if (window == 0L) {
            return;
        }
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Native texture window must close on its owning thread");
        }
        GLFW.glfwMakeContextCurrent(0L);
        GL.setCapabilities(null);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        window = 0L;
    }
}
