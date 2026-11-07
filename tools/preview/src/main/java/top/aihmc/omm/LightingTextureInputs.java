package top.aihmc.omm;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Explicit texture inputs replacing Minecraft's world-dependent lightmap and overlay producers. */
final class LightingTextureInputs {
    private LightingTextureInputs() {
    }

    static byte[] read(String key) throws Exception {
        var configuration = RuntimeConfiguration.json(Path.of(
            System.getProperty("omm.preview.output"), "effective-config.json"));
        var inputs = configuration.getAsJsonObject("renderInputs");
        if (inputs != null && inputs.has(key)) {
            Path path = Path.of(inputs.get(key).getAsString());
            if (!path.isAbsolute()) {
                throw new IllegalArgumentException("renderInputs." + key + " requires an absolute path");
            }
            byte[] bytes = Files.readAllBytes(path);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() != 16 || image.getHeight() != 16) {
                throw new IllegalArgumentException("renderInputs." + key + " must be a 16x16 image: " + path);
            }
            System.out.println("LIGHTING TEXTURE " + key + " " + path);
            return bytes;
        }
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, 0xffffffff);
            }
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
