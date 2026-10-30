package top.aihmc.omnimorph.render.acceleration;

import java.nio.ByteBuffer;

/**
 * ABI 2: native-order float vertices (xyzw, normal xyz, uv), column-major padded matrices, plus pose packing
 * that converts native-order doubles to float32 exactly like a Java {@code (float)} cast.
 */
final class NativeBindings {
    static final int ABI = 2;
    static final int STRIDE_BYTES = 9 * Float.BYTES;
    static final int MATRIX_BYTES = 40 * Float.BYTES;
    static final int MAX_VERTICES = 65_536;
    static final int MAX_PACK_VALUES = 4096 * 40;

    private NativeBindings() {
    }

    static native int handshake(int javaAbi);

    static native int capabilities();

    static native int transform(ByteBuffer input, int inputOffset, int inputStride,
                                ByteBuffer matrices, int matrixOffset, ByteBuffer output,
                                int outputOffset, int outputStride, int count, int kernel, int flags);

    /** Converts {@code count} doubles at a byte offset to floats; status codes match {@link #transform}. */
    static native int pack(ByteBuffer input, int inputOffset, ByteBuffer output, int outputOffset,
                           int count, int kernel);
}
