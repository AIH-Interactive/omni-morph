package top.aihmc.omnimorph.render.acceleration;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Owned, bounded JNI workspace. Construction never loads a library or touches graphics state. */
public final class NativeSimdBackend implements AccelerationBackend {
    private static final double[] IDENTITY_3 = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    private final int kernel;
    private ByteBuffer input = direct(0);
    private ByteBuffer output = direct(0);
    private final ByteBuffer matrices = direct(NativeBindings.MATRIX_BYTES);
    private ByteBuffer packInput = direct(0);
    private long packedValues;
    private long successfulBatches;
    private long transformedVertices;

    public NativeSimdBackend() {
        this(-1);
    }

    NativeSimdBackend(int kernel) {
        this.kernel = kernel;
    }

    @Override
    public boolean supports(Operation operation, DrawBatch batch) {
        return operation == Operation.VERTEX_TRANSFORM
                && batch.mesh().vertexCount() <= NativeBindings.MAX_VERTICES;
    }

    @Override
    public synchronized double[] transform(DrawBatch batch) {
        if (!supports(Operation.VERTEX_TRANSFORM, batch)) {
            throw new IllegalArgumentException("Batch exceeds native ABI limits");
        }
        int count = batch.mesh().vertexCount();
        int size = count * NativeBindings.STRIDE_BYTES;
        if (input.capacity() < size) {
            int capacity = Math.min(NativeBindings.MAX_VERTICES * NativeBindings.STRIDE_BYTES,
                    Math.max(size, Math.max(NativeBindings.STRIDE_BYTES * 256, input.capacity() * 2)));
            input = direct(capacity);
            output = direct(capacity);
        }
        batch.pose().writeNativeVertices(batch.mesh(), input);
        matrices.clear();
        for (double value : batch.pose().positionMatrix()) {
            matrices.putFloat((float) value);
        }
        // Vanilla normal normalization is preserved during packing; Figura uses its separate matrix here.
        writeMatrix3(batch.pose().usesFloatPrecision() ? IDENTITY_3
                : batch.pose().normalMatrix());
        writeMatrix3(batch.pose().uvMatrix());
        int status = NativeBindings.transform(input, 0, NativeBindings.STRIDE_BYTES, matrices, 0,
                output, 0, NativeBindings.STRIDE_BYTES, count, kernel, batch.pose().usesFloatPrecision() ? 2 : 0);
        if (status != 0) {
            throw new NativeBatchException(status);
        }
        double[] result = new double[count * MeshHandle.STRIDE];
        for (int index = 0; index < result.length; index++) {
            result[index] = output.getFloat(index * Float.BYTES);
        }
        successfulBatches++;
        transformedVertices += count;
        return result;
    }

    /**
     * Converts {@code count} pose values to floats at the target's position with the native SIMD kernel,
     * byte-for-byte equal to Java {@code (float)} casts, and advances the target. Throws when native rejects.
     */
    public synchronized void packPoses(double[] values, int count, ByteBuffer target) {
        if (count < 0 || count > NativeBindings.MAX_PACK_VALUES || count > values.length || !target.isDirect()
                || target.remaining() < count * Float.BYTES) {
            throw new IllegalArgumentException("Pose pack outside native ABI limits");
        }
        int bytes = count * Double.BYTES;
        if (packInput.capacity() < bytes) {
            packInput = direct(Math.max(bytes, Math.min(NativeBindings.MAX_PACK_VALUES * Double.BYTES,
                    Math.max(64 * GpuBatchData.PART_VALUES * Double.BYTES, packInput.capacity() * 2))));
        }
        packInput.clear();
        packInput.asDoubleBuffer().put(values, 0, count);
        int status = NativeBindings.pack(packInput, 0, target, target.position(), count, kernel);
        if (status != 0) {
            throw new NativeBatchException(status);
        }
        target.position(target.position() + count * Float.BYTES);
        packedValues += count;
    }

    public synchronized long packedValues() {
        return packedValues;
    }

    public synchronized long successfulBatches() {
        return successfulBatches;
    }

    public synchronized long transformedVertices() {
        return transformedVertices;
    }

    private void writeMatrix3(double[] values) {
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                matrices.putFloat((float) values[column * 3 + row]);
            }
            matrices.putFloat(0);
        }
    }

    private static ByteBuffer direct(int capacity) {
        return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
    }

    public static final class NativeBatchException extends RuntimeException {
        private final int status;

        NativeBatchException(int status) {
            super("Native transform rejected batch: " + status);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
