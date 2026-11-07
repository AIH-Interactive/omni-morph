package top.aihmc.omm.preview;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/** Test-only prepared native bridge. One caller and one pose owner per context. */
final class PreviewPreparedPoseCandidate implements AutoCloseable {
    private final ByteBuffer frame;
    private final FloatBuffer local;
    private final FloatBuffer world;
    private final FloatBuffer palette;
    private final int visibilityOffset;
    private long handle;

    PreviewPreparedPoseCandidate(PreviewSkeleton skeleton) {
        int count = skeleton.names.length;
        frame = MemoryUtil.memAlloc(Math.multiplyExact(count, 193));
        local = frame.slice(0, count * 64).order(frame.order()).asFloatBuffer();
        world = frame.slice(count * 64, count * 64).order(frame.order()).asFloatBuffer();
        palette = frame.slice(count * 128, count * 64).order(frame.order()).asFloatBuffer();
        visibilityOffset = count * 192;
        float[] inverse = new float[count * 16];
        for (int bone = 0; bone < count; bone++) {
            skeleton.inverseBindWorld[bone].get(inverse, bone * 16);
        }
        try {
            handle = create(skeleton.parents, skeleton.order, inverse, frame);
        } catch (RuntimeException | Error failure) {
            MemoryUtil.memFree(frame);
            throw failure;
        }
    }

    void compose(Matrix4f[] localWorld, Matrix4f[] outputPalette, boolean[] visible, boolean simd) {
        publish(localWorld, visible, simd);
        for (int bone = 0; bone < localWorld.length; bone++) {
            localWorld[bone].set(bone * 16, world);
            outputPalette[bone].set(bone * 16, palette);
            visible[bone] = frame.get(visibilityOffset + bone) != 0;
        }
    }

    void publish(Matrix4f[] input, boolean[] visible, boolean simd) {
        publish(input, visible, simd ? 1 : 0);
    }

    void publish(Matrix4f[] input, boolean[] visible, int kernel) {
        if (handle == 0) {
            throw new IllegalStateException("Candidate is closed");
        }
        for (int bone = 0; bone < input.length; bone++) {
            input[bone].get(bone * 16, local);
            frame.put(visibilityOffset + bone, (byte) (visible[bone] ? 1 : 0));
        }
        compute(handle, kernel);
    }

    float worldX(int bone) {
        return world.get(bone * 16 + 12);
    }

    private static native long create(int[] parents, int[] order, float[] inverse, ByteBuffer frame);
    private static native void compute(long handle, int kernel);
    private static native void destroy(long handle);

    @Override
    public void close() {
        if (handle != 0) {
            destroy(handle);
            handle = 0;
            MemoryUtil.memFree(frame);
        }
    }
}
