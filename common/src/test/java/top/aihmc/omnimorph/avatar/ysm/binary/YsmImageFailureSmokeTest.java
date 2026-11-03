package top.aihmc.omnimorph.avatar.ysm.binary;

import top.aihmc.omnimorph.avatar.ysm.YsmResourceLimits;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeBudget;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.InterruptedIOException;

/** Local CPU regression for exceptions wrapped by the real PNG writer; never run by Actions. */
public final class YsmImageFailureSmokeTest {
    private static final byte[] RGBA = {0x12, 0x34, 0x56, (byte) 0xff};

    public static void main(String[] args) throws Exception {
        byte[] png = convert(new YsmDecodeBudget());
        var image = ImageIO.read(new ByteArrayInputStream(png));
        if (image.getRGB(0, 0) != 0xff123456) {
            throw new AssertionError("Normal conversion changed pixel values");
        }
        image.flush();

        try {
            convert(new YsmDecodeBudget(YsmResourceLimits.DEFAULT,
                    YsmImageFailureSmokeTest::writingPng, 10000));
            throw new AssertionError("PNG output ignored cancellation");
        } catch (InterruptedIOException expected) {
            // Cancellation must reach Avatar.load without becoming INVALID_DATA.
        }

        var small = new YsmResourceLimits(1024, 32, 1024, 16);
        expectLimit(new YsmDecodeBudget(small, () -> false, 10000), "image/png-output");
        expectLimit(new YsmDecodeBudget(YsmResourceLimits.DEFAULT, () -> {
            if (writingPng()) {
                try {
                    Thread.sleep(1100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return false;
        }, 1000), "image/png-output/deadline");

        try {
            byte[] truncated = {(byte) 137, 'P', 'N', 'G', 13, 10, 26, 10};
            YsmTextureDecoder.toPng(truncated, 0, 1, 1, new YsmDecodeBudget());
            throw new AssertionError("Truncated image was accepted");
        } catch (YsmDecodeException expected) {
            if (expected.code() != YsmDecodeException.Code.INVALID_DATA
                    || !expected.stage().equals("image/decode") || expected.getCause() != null) {
                throw new AssertionError("Untrusted codec diagnostics escaped sanitization", expected);
            }
        }
        System.out.println("PASS: PNG pixels, wrapped cancellation, byte limit, deadline and invalid-image diagnostics");
    }

    private static byte[] convert(YsmDecodeBudget budget) throws Exception {
        return YsmTextureDecoder.toPng(RGBA, -1, 1, 1, budget);
    }

    private static void expectLimit(YsmDecodeBudget budget, String stage) throws Exception {
        try {
            convert(budget);
            throw new AssertionError("PNG output ignored " + stage);
        } catch (YsmDecodeException expected) {
            if (expected.code() != YsmDecodeException.Code.RESOURCE_LIMIT || !expected.stage().equals(stage)) {
                throw new AssertionError("PNG writer discarded the resource-limit diagnostic", expected);
            }
        }
    }

    private static boolean writingPng() {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                frame.getClassName().equals(YsmTextureDecoder.class.getName() + "$BoundedOutput")
                        && frame.getMethodName().equals("write")));
    }
}
