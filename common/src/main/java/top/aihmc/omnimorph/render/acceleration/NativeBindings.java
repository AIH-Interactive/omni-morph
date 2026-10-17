package top.aihmc.omnimorph.render.acceleration;

import java.nio.ByteBuffer;

/** ABI 1: native-order float vertices (xyzw, normal xyz, uv), column-major padded matrices. */
final class NativeBindings {
    static final int ABI = 1;
    static final int STRIDE_BYTES = 9 * Float.BYTES;
    static final int MATRIX_BYTES = 40 * Float.BYTES;
    static final int MAX_VERTICES = 65_536;

    private NativeBindings() {
    }

    static native int handshake(int javaAbi);

    static native int capabilities();

    static native int transform(ByteBuffer input, int inputOffset, int inputStride,
                                ByteBuffer matrices, int matrixOffset, ByteBuffer output,
                                int outputOffset, int outputStride, int count, int kernel, int flags);
}
