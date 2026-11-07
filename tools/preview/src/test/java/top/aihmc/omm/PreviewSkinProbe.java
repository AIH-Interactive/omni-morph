package top.aihmc.omm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

public final class PreviewSkinProbe {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(System.getProperty("omm.preview.output"));
        Path configuration = output.resolve("effective-config.json");
        String original = Files.readString(configuration);
        try {
            var image = new java.awt.image.BufferedImage(64, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 64; x++) {
                    image.setRGB(x, y, 0xff000000 | x << 16 | y << 8 | 127);
                }
            }
            Path skin = output.resolve("legacy-test.png");
            ImageIO.write(image, "png", skin.toFile());
            byte[] source = Files.readAllBytes(skin);
            var config = RuntimeConfiguration.json(configuration);
            config.addProperty("skinPath", skin.toAbsolutePath().toString());
            config.addProperty("skinModel", "auto");
            Files.writeString(configuration, config.toString());
            var preview = new PreviewSkin();
            var normalized = ImageIO.read(new ByteArrayInputStream(preview.bytes()));
            if (normalized.getWidth() != 64 || normalized.getHeight() != 64 || preview.slim()) {
                throw new AssertionError("Legacy skin was not converted to a regular 64x64 skin");
            }
            if ((normalized.getRGB(20, 52) >>> 24) != 255 || (normalized.getRGB(36, 52) >>> 24) != 255) {
                throw new AssertionError("Original conversion did not populate mirrored leg and arm");
            }
            if (!java.util.Arrays.equals(source, Files.readAllBytes(skin))) {
                throw new AssertionError("Legacy source skin was modified");
            }
            config.addProperty("skinModel", "alex");
            Files.writeString(configuration, config.toString());
            if (!new PreviewSkin().slim()) {
                throw new AssertionError("Explicit Alex selection was ignored");
            }
            config.addProperty("skinModel", "steve");
            Files.writeString(configuration, config.toString());
            if (new PreviewSkin().slim()) {
                throw new AssertionError("Explicit Steve selection was ignored");
            }
            System.out.println("LEGACY SKIN PASS mirroredLimbs=true sourcePreserved=true explicitModels=true");
        } finally {
            Files.writeString(configuration, original);
        }
    }
}
