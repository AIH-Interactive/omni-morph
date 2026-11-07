package top.aihmc.omm.preview;

import java.nio.FloatBuffer;

/** Applies the same column-major rigid-part palette used by the GPU vertex shader. */
final class PreviewCpuVertices {
    private PreviewCpuVertices() {}

    static void transform(float[] bindVertices, int[] owners, FloatBuffer palette, FloatBuffer output) {
        output.clear();
        for (int vertex = 0; vertex < owners.length; vertex++) {
            int offset = vertex * 5;
            int matrix = owners[vertex] * 16;
            float x = bindVertices[offset], y = bindVertices[offset + 1], z = bindVertices[offset + 2];
            output.put(palette.get(matrix) * x + palette.get(matrix + 4) * y + palette.get(matrix + 8) * z
                + palette.get(matrix + 12));
            output.put(palette.get(matrix + 1) * x + palette.get(matrix + 5) * y + palette.get(matrix + 9) * z
                + palette.get(matrix + 13));
            output.put(palette.get(matrix + 2) * x + palette.get(matrix + 6) * y + palette.get(matrix + 10) * z
                + palette.get(matrix + 14));
            output.put(bindVertices[offset + 3]).put(bindVertices[offset + 4]);
        }
        output.flip();
    }
}
