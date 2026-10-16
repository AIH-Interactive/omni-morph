package com.github.ustc_zzzz.imageio.avif;

import org.figuramc.figura.avatar.ysm.crypto.YsmDecodeBudget;
import org.figuramc.figura.avatar.ysm.crypto.YsmDecodeException;

import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Lifecycle adapter for the pinned AVIF reader's package-private JNI interface.
 * The upstream ImageReader decodes in getWidth and releases via GC. Calling its
 * JNI interface directly lets us check parsed dimensions before decoding and
 * destroy each decoder deterministically, without registering a second cleaner.
 * No upstream implementation is copied or modified.
 */
public final class FiguraAvifDecoder {
    private FiguraAvifDecoder() {
    }

    public static BufferedImage read(byte[] data, int declaredWidth, int declaredHeight,
                                     YsmDecodeBudget budget) throws IOException {
        budget.length(data.length, budget.limits().singleResourceBytes(), "image/avif-input", 0);
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            Native.Decoded decoded = Native.create(stream);
            Native.Image frame = null;
            try {
                budget.pixels(decoded.width(), decoded.height(), "image/avif-pixels", 0);
                if (declaredWidth > 0 && decoded.width() != declaredWidth
                        || declaredHeight > 0 && decoded.height() != declaredHeight) {
                    throw invalid("image/dimension-mismatch");
                }
                budget.check("image/avif-before-decode", 0);
                frame = Native.next(decoded.decoderPtr());
                budget.check("image/avif-after-decode", 0);
                if (frame.width() != decoded.width() || frame.height() != decoded.height()
                        || frame.depth() < 8 || frame.depth() > 16) {
                    throw invalid("image/avif-frame");
                }
                BufferedImage image = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_ARGB);
                try {
                    int channels = frame.hasAlpha() ? 4 : 3;
                    int shift = frame.depth() - 8;
                    for (int y = 0; y < frame.height(); y++) {
                        budget.check("image/avif-convert", 0);
                        for (int x = 0; x < frame.width(); x++) {
                            int offset = (y * frame.width() + x) * channels;
                            int blue = frame.pixels().getElem(offset) >>> shift;
                            int green = frame.pixels().getElem(offset + 1) >>> shift;
                            int red = frame.pixels().getElem(offset + 2) >>> shift;
                            int alpha = frame.hasAlpha() ? frame.pixels().getElem(offset + 3) >>> shift : 255;
                            image.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
                        }
                    }
                    return image;
                } catch (IOException | RuntimeException failure) {
                    Arrays.fill(((DataBufferInt) image.getRaster().getDataBuffer()).getData(), 0);
                    image.flush();
                    throw failure;
                }
            } finally {
                if (frame != null) {
                    if (frame.pixels() instanceof DataBufferByte pixels) {
                        for (byte[] bank : pixels.getBankData()) {
                            Arrays.fill(bank, (byte) 0);
                        }
                    } else if (frame.pixels() instanceof DataBufferUShort pixels) {
                        for (short[] bank : pixels.getBankData()) {
                            Arrays.fill(bank, (short) 0);
                        }
                    }
                }
                clear(decoded.icc());
                clear(decoded.exif());
                clear(decoded.xmp());
                Native.destroy(decoded.decoderPtr(), decoded.memoryPtr());
            }
        } catch (LinkageError failure) {
            throw new YsmDecodeException(YsmDecodeException.Code.UNSUPPORTED_VERSION, "image/avif-native", 0);
        }
    }

    private static void clear(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static YsmDecodeException invalid(String stage) {
        return new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, stage, 0);
    }
}
