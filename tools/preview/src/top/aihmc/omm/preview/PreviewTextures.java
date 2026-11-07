package top.aihmc.omm.preview;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/** Reads Minecraft-style assets directly from the preview JAR. */
final class PreviewTextures {
    private PreviewTextures() {}

    static byte[] resource(String name) throws IOException {
        try (var input = PreviewTextures.class.getResourceAsStream("/" + name)) {
            if (input == null) {
                throw new IOException("Missing packaged texture resource: " + name);
            }
            return input.readAllBytes();
        }
    }

    static BufferedImage image(byte[] bytes, String source) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IOException("Unsupported texture: " + source);
        }
        return image;
    }

    static void writeSources(Path output) throws IOException {
        Files.write(output.resolve("environment-sources.json"), resource("assets/omm_preview/texture-sources.json"));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("JVM has no SHA-256", impossible);
        }
    }
}
