package top.aihmc.omnimorph.avatar.ysm.binary;

import com.github.ustc_zzzz.imageio.avif.FiguraAvifDecoder;
import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeBudget;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;

/** Bounded in-memory conversion for the PNG texture path consumed by Figura. */
public final class YsmTextureDecoder {
    private YsmTextureDecoder() {
    }

    public static byte[] toPng(byte[] data, int format, int width, int height,
                               YsmDecodeBudget budget) throws IOException {
        budget.length(data.length, budget.limits().singleResourceBytes(), "image/input", 0);
        BufferedImage image = null;
        try {
            image = format == -1 ? rgba(data, width, height, budget) : encoded(data, width, height, budget);
            BoundedOutput output = new BoundedOutput(budget);
            try {
                if (!ImageIO.write(image, "PNG", output)) {
                    throw invalid("image/png-encoder");
                }
                return output.bytes();
            } finally {
                output.close();
            }
        } catch (YsmDecodeException e) {
            throw e;
        } catch (java.io.InterruptedIOException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Third-party diagnostics may contain input data; retain only our stage.
            throw invalid("image/decode");
        } finally {
            if (image != null) {
                clear(image);
            }
        }
    }

    private static BufferedImage rgba(byte[] data, int width, int height,
                                      YsmDecodeBudget budget) throws IOException {
        long pixels = budget.pixels(width, height, "image/rgba-pixels", 0);
        if (data.length != pixels * 4) {
            throw invalid("image/rgba-length");
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        try {
            for (int y = 0; y < height; y++) {
                budget.check("image/rgba", y);
                for (int x = 0; x < width; x++) {
                    int offset = (y * width + x) * 4;
                    int argb = (data[offset + 3] & 255) << 24 | (data[offset] & 255) << 16
                            | (data[offset + 1] & 255) << 8 | (data[offset + 2] & 255);
                    image.setRGB(x, y, argb);
                }
            }
            return image;
        } catch (IOException | RuntimeException e) {
            clear(image);
            throw e;
        }
    }

    private static BufferedImage encoded(byte[] data, int declaredWidth, int declaredHeight,
                                         YsmDecodeBudget budget) throws IOException {
        if (isAvif(data)) {
            return FiguraAvifDecoder.read(data, declaredWidth, declaredHeight, budget);
        }
        try (MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            ImageReader reader;
            if (isWebp(data)) {
                reader = new WebPImageReaderSpi().createReaderInstance();
            } else {
                String name = encoding(data);
                Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(name);
                if (!readers.hasNext()) {
                    throw new YsmDecodeException(YsmDecodeException.Code.UNSUPPORTED_VERSION, "image/codec", 0);
                }
                reader = readers.next();
            }
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                budget.pixels(width, height, "image/encoded-pixels", 0);
                if (declaredWidth > 0 && declaredWidth != width || declaredHeight > 0 && declaredHeight != height) {
                    throw invalid("image/dimension-mismatch");
                }
                budget.check("image/before-decode", 0);
                BufferedImage image = reader.read(0);
                try {
                    budget.check("image/after-decode", 0);
                    if (image == null || image.getWidth() != width || image.getHeight() != height) {
                        throw invalid("image/decoded-dimensions");
                    }
                    return image;
                } catch (IOException | RuntimeException e) {
                    if (image != null) {
                        clear(image);
                    }
                    throw e;
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private static String encoding(byte[] bytes) throws IOException {
        if (bytes.length >= 8 && (bytes[0] & 255) == 137 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G') {
            return "PNG";
        }
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255) {
            return "JPEG";
        }
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "GIF";
        }
        throw new YsmDecodeException(YsmDecodeException.Code.UNSUPPORTED_VERSION, "image/encoding", 0);
    }

    private static boolean isAvif(byte[] bytes) {
        if (bytes.length < 16 || bytes[4] != 'f' || bytes[5] != 't' || bytes[6] != 'y' || bytes[7] != 'p') {
            return false;
        }
        for (int offset = 8; offset <= Math.min(bytes.length - 4, 60); offset += 4) {
            if (bytes[offset] == 'a' && bytes[offset + 1] == 'v' && bytes[offset + 2] == 'i'
                    && (bytes[offset + 3] == 'f' || bytes[offset + 3] == 's')) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWebp(byte[] bytes) {
        return bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private static void clear(BufferedImage image) {
        var storage = image.getRaster().getDataBuffer();
        if (storage instanceof DataBufferByte bytes) {
            for (byte[] bank : bytes.getBankData()) {
                Arrays.fill(bank, (byte) 0);
            }
        } else if (storage instanceof DataBufferInt pixels) {
            for (int[] bank : pixels.getBankData()) {
                Arrays.fill(bank, 0);
            }
        } else if (storage instanceof DataBufferUShort values) {
            for (short[] bank : values.getBankData()) {
                Arrays.fill(bank, (short) 0);
            }
        }
        image.flush();
    }

    private static YsmDecodeException invalid(String stage) {
        return new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, stage, 0);
    }

    private static final class BoundedOutput extends OutputStream {
        private final YsmDecodeBudget budget;
        private final ClearingBuffer bytes = new ClearingBuffer();

        private BoundedOutput(YsmDecodeBudget budget) {
            this.budget = budget;
        }

        @Override
        public void write(int value) throws IOException {
            budget.length((long) bytes.size() + 1, budget.limits().singleResourceBytes(), "image/png-output", 0);
            bytes.write(value);
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            budget.length((long) bytes.size() + length, budget.limits().singleResourceBytes(), "image/png-output", 0);
            bytes.write(values, offset, length);
        }

        private byte[] bytes() {
            return bytes.toByteArray();
        }

        @Override
        public void close() {
            bytes.clear();
        }
    }

    private static final class ClearingBuffer extends ByteArrayOutputStream {
        private void clear() {
            Arrays.fill(buf, (byte) 0);
            reset();
        }
    }
}
