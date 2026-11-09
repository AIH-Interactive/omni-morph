package top.aihmc.omnimorph.model.library;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Checks the automatic upload cover: upper-body region, clipping, square crop and PNG encoding. */
public final class PreviewCoverSmokeTest {
    public static void main(String[] args) throws IOException {
        // Entity origin (waist) at (100, 120) with scale 50: the upper body square spans 0.9 scale above it.
        var region = PreviewCover.upperBody(100, 120, 50, 40, 40, 160, 160);
        require(region != null && region.side() == 50 && region.x() == 75 && region.y() == 75,
                "upper body square above the waist: " + region);
        // Clipped by the preview frame, the square shrinks instead of leaving it.
        var clipped = PreviewCover.upperBody(100, 60, 50, 40, 40, 160, 160);
        require(clipped != null && clipped.y() == 41 && clipped.y() + clipped.side() <= 199
                        && clipped.x() >= 41 && clipped.x() + clipped.side() <= 199,
                "the region stays inside the preview frame: " + clipped);
        require(PreviewCover.upperBody(10, 10, 50, 40, 40, 160, 160) == null,
                "an entity moved out of the preview gives no cover");
        require(PreviewCover.upperBody(100, 120, 2, 40, 40, 160, 160) == null, "a tiny region gives no cover");

        // Framing: a drag spans a square from its start corner, in any direction, inside the preview frame.
        var framed = PreviewCover.framed(60, 60, 100, 90, 40, 40, 160, 160);
        require(framed != null && framed.x() == 60 && framed.y() == 60 && framed.side() == 40,
                "a drag right and down frames a square of its longer side: " + framed);
        var backwards = PreviewCover.framed(150, 150, 120, 140, 40, 40, 160, 160);
        require(backwards != null && backwards.x() == 120 && backwards.y() == 120 && backwards.side() == 30,
                "a drag up and left frames toward the start corner: " + backwards);
        var edge = PreviewCover.framed(180, 60, 260, 70, 40, 40, 160, 160);
        require(edge != null && edge.x() + edge.side() <= 199 && edge.side() == 19,
                "a drag past the frame is clipped to a square inside it: " + edge);
        require(PreviewCover.framed(60, 60, 62, 61, 40, 40, 160, 160) == null, "a click without a drag frames nothing");

        // A 200x100 screenshot: red left half, blue right half. GUI scale 2 doubles the region in pixels.
        int width = 200;
        int height = 100;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = x < 100 ? 0xffff0000 : 0xff0000ff;
            }
        }
        var square = new PreviewCover.Region(25, 10, 40);
        int[] crop = PreviewCover.crop(pixels, width, height, square, 2, 16);
        require(crop.length == 16 * 16, "output is the requested square size");
        require(crop[0] == 0xffff0000 && crop[15] == 0xff0000ff, "left edge is red, right edge is blue");
        byte[] png = PreviewCover.png(crop, 16);
        var decoded = ImageIO.read(new ByteArrayInputStream(png));
        require(decoded.getWidth() == 16 && decoded.getHeight() == 16 && decoded.getRGB(0, 0) == 0xffff0000,
                "the PNG decodes to the same square");
        require(PreviewCover.crop(pixels, width, height, new PreviewCover.Region(190, 10, 40), 1, 16) == null,
                "a region outside the screenshot gives no cover");
        System.out.println("PASS preview cover: upper-body region, clipping, square crop and PNG");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
