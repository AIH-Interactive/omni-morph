package top.aihmc.omm.preview;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.lwjgl.opengl.GL;

/** Real Windows GLFW focus transitions; no external model or game process. */
public final class PreviewWindowFocusTest {
    private static void focus(long target) throws Exception {
        glfwFocusWindow(target);
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (glfwGetWindowAttrib(target, GLFW_FOCUSED) != GLFW_TRUE && System.nanoTime() < deadline) {
            glfwPollEvents();
            Thread.sleep(10);
        }
        if (glfwGetWindowAttrib(target, GLFW_FOCUSED) != GLFW_TRUE) {
            throw new AssertionError("Real test window did not receive focus");
        }
        glfwPollEvents();
        Thread.sleep(250);
    }

    public static void main(String[] args) throws Exception {
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW initialization failed");
        }
        long primary = 0, secondary = 0;
        try {
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            primary = glfwCreateWindow(480, 320, "Preview focus recording test", 0, 0);
            secondary = glfwCreateWindow(320, 240, "Preview focus transfer target", 0, 0);
            if (primary == 0 || secondary == 0) {
                throw new AssertionError("Real test windows were not created");
            }
            glfwMakeContextCurrent(primary);
            GL.createCapabilities();
            var config = new PreviewConfig();
            config.execution.threads = 1;
            var output = Path.of(args[0]);
            try (var log = new FrameLog(output, PreviewMesh.load(config), config.animation, config.execution)) {
                for (int index = 0; index < 2; index++) {
                    focus(index == 0 ? primary : secondary);
                    glClearColor(0.15f, 0.3f, 0.45f, 1);
                    glClear(GL_COLOR_BUFFER_BIT);
                    glfwSwapBuffers(primary);
                    var frame = new FrameLog.Frame();
                    frame.id = index;
                    frame.sample = true;
                    frame.record = true;
                    frame.frameMs = 1;
                    frame.focused = glfwGetWindowAttrib(primary, GLFW_FOCUSED) == GLFW_TRUE;
                    log.enqueue(frame);
                    log.poll();
                }
                log.drain();
            }
            var rows = Files.readAllLines(output.resolve("frames.csv"));
            if (!rows.get(0).endsWith(",window_focused") || !rows.get(1).endsWith(",true")
                || !rows.get(2).endsWith(",false")) {
                throw new AssertionError("Real focused/unfocused frames were not recorded separately");
            }
            System.out.println(
                "Window focus PASS: two visible GLFW windows, actual focus transfer, CSV true/false; no model loads");
        } finally {
            if (secondary != 0)
                glfwDestroyWindow(secondary);
            if (primary != 0)
                glfwDestroyWindow(primary);
            glfwTerminate();
        }
    }
}
