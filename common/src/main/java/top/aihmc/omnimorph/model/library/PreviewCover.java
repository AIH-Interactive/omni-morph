package top.aihmc.omnimorph.model.library;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Automatic upload cover cut from the model preview when the author chose no image: a square around the
 * upper body of the drawn entity, scaled to a fixed size and encoded as PNG.
 */
public final class PreviewCover {
    /** Output edge in pixels, within the server's default image limits. */
    public static final int SIZE = 256;
    /** The preview draws the entity origin about one block above its feet; the head ends 0.9 blocks higher. */
    private static final float HEAD_ABOVE_ORIGIN = 0.9f;
    private static final float SIDE_PER_SCALE = 1.0f;
    private static final int MIN_SIDE = 8;

    /** A square in GUI units. */
    public record Region(int x, int y, int side) {
    }

    private PreviewCover() {
    }

    /**
     * The upper-body square of an entity drawn at {@code originX, originY} with {@code scale} GUI units per
     * block, kept inside the preview frame's inner area. Returns null when too little of it is visible.
     */
    public static Region upperBody(float originX, float originY, float scale,
                                   int frameX, int frameY, int frameWidth, int frameHeight) {
        int side = Math.round(scale * SIDE_PER_SCALE);
        int left = Math.round(originX) - side / 2;
        int top = Math.round(originY - scale * HEAD_ABOVE_ORIGIN);
        int clippedLeft = Math.max(left, frameX + 1);
        int clippedTop = Math.max(top, frameY + 1);
        int clippedRight = Math.min(left + side, frameX + frameWidth - 1);
        int clippedBottom = Math.min(top + side, frameY + frameHeight - 1);
        int square = Math.min(clippedRight - clippedLeft, clippedBottom - clippedTop);
        if (square < MIN_SIDE) {
            return null;
        }
        // Keep the visible part centred on the side that was clipped less.
        int x = clippedLeft + (clippedRight - clippedLeft - square) / 2;
        int y = clippedTop;
        return new Region(x, y, square);
    }

    /**
     * Samples the region of an ARGB screenshot (row-major, top row first) into an {@code size}-square ARGB
     * image. {@code guiScale} converts GUI units to screenshot pixels. Returns null outside the screenshot.
     */
    public static int[] crop(int[] argb, int width, int height, Region region, int guiScale, int size) {
        int left = region.x() * guiScale;
        int top = region.y() * guiScale;
        int side = region.side() * guiScale;
        if (left < 0 || top < 0 || side <= 0 || left + side > width || top + side > height) {
            return null;
        }
        int[] output = new int[size * size];
        for (int row = 0; row < size; row++) {
            int sourceY = top + (row * side + side / 2) / size;
            for (int column = 0; column < size; column++) {
                int sourceX = left + (column * side + side / 2) / size;
                output[row * size + column] = argb[sourceY * width + sourceX] | 0xff000000;
            }
        }
        return output;
    }

    /** Encodes a square ARGB image as PNG bytes. */
    public static byte[] png(int[] argb, int size) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, size, size, argb, 0, size);
        var output = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", output);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return output.toByteArray();
    }
}
