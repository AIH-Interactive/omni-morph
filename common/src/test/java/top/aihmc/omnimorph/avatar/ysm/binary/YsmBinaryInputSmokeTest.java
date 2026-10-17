package top.aihmc.omnimorph.avatar.ysm.binary;

import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeBudget;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Distinguishes the documented omitted-duration sentinel from invalid geometry floats. */
public final class YsmBinaryInputSmokeTest {
    private YsmBinaryInputSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        require(input(Float.POSITIVE_INFINITY).readAnimationLength() == Float.POSITIVE_INFINITY,
                "omitted animation length sentinel");
        require(input(40f).readAnimationLength() == 40f, "finite animation length");
        require(input(0f).readAnimationLength() == 0f, "zero animation length");
        for (float value : new float[]{Float.NaN, Float.NEGATIVE_INFINITY, -1f}) {
            expectInvalid(() -> input(value).readAnimationLength());
        }
        for (float value : new float[]{Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
            expectInvalid(() -> input(value).readFloat());
        }
        require(input(-1f).readFloat() == -1f, "negative finite geometry coordinate");
        var faces = new YsmBinaryInput(new byte[]{(byte) 128, (byte) 128, 16, 1}, new YsmDecodeBudget());
        require(faces.readGeometryFacesCount() == 262144, "face ceiling accepted without allocation");
        expectLimit(faces::readGeometryFacesCount);
        var bones = new YsmBinaryInput(new byte[]{(byte) 128, 32, 1}, new YsmDecodeBudget());
        require(bones.readGeometryBonesCount() == 4096, "bone ceiling");
        expectLimit(bones::readGeometryBonesCount);
        var frames = new YsmBinaryInput(new byte[]{(byte) 128, (byte) 128, 64, 1}, new YsmDecodeBudget());
        require(frames.readAnimationKeyframesCount() == 1048576, "separate keyframe ceiling");
        expectLimit(frames::readAnimationKeyframesCount);
        System.out.println("YSM binary input smoke passed; full format mapping checks remain separate.");
    }

    private static YsmBinaryInput input(float value) throws java.io.IOException {
        byte[] bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array();
        return new YsmBinaryInput(bytes, new YsmDecodeBudget());
    }

    private static void expectInvalid(CheckedAction action) throws Exception {
        try {
            action.run();
        } catch (YsmDecodeException expected) {
            require(expected.getMessage().startsWith("INVALID_DATA:"), "explicit invalid-data diagnostic");
            return;
        }
        throw new AssertionError("Invalid floating-point value was accepted");
    }

    private static void expectLimit(CheckedAction action) throws Exception {
        try {
            action.run();
        } catch (YsmDecodeException expected) {
            require(expected.getMessage().startsWith("RESOURCE_LIMIT:"), "cumulative allocation limit");
            return;
        }
        throw new AssertionError("Cumulative budget overflow accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
