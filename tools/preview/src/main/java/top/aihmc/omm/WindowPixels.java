package top.aihmc.omm;

import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL33C.*;

/** Readback evidence from the currently displayed render target, excluding scenery-only pixels. */
final class WindowPixels {
    private WindowPixels() {}

    static byte[] capture(int width, int height) {
        var buffer = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4));
        int rowLength = glGetInteger(GL_PACK_ROW_LENGTH);
        try {
            glPixelStorei(GL_PACK_ROW_LENGTH, 0);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            byte[] pixels = new byte[buffer.capacity()];
            buffer.get(pixels);
            return pixels;
        } finally {
            glPixelStorei(GL_PACK_ROW_LENGTH, rowLength);
            MemoryUtil.memFree((java.nio.Buffer) buffer);
        }
    }

    static int changed(byte[] background, byte[] scene) {
        if (background.length != scene.length) {
            throw new IllegalArgumentException("Window size changed between background and scene readback");
        }
        int count = 0;
        for (int index = 0; index < scene.length; index += 4) {
            int difference = 0;
            for (int channel = 0; channel < 3; channel++) {
                difference += Math.abs((background[index + channel] & 255) - (scene[index + channel] & 255));
            }
            if (difference > 12) {
                count++;
            }
        }
        return count;
    }
}
