package top.aihmc.omm;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Configured replacement for the player's skin resource supplied by Minecraft. */
final class PreviewSkin {
    static final String IDENTIFIER = "omm-preview:skin";
    private final byte[] bytes;
    private final boolean slim;

    PreviewSkin() throws Exception {
        var configuration = RuntimeConfiguration.json(
            Path.of(System.getProperty("omm.preview.output"), "effective-config.json"));
        Path path = Path.of(configuration.get("skinPath").getAsString()).toRealPath();
        byte[] source = Files.readAllBytes(path);
        var image = ImageIO.read(new ByteArrayInputStream(source));
        if (image == null || image.getWidth() != 64
                || (image.getHeight() != 64 && image.getHeight() != 32)) {
            throw new IllegalArgumentException("Skin must be a 64x64 or legacy 64x32 image: " + path);
        }
        String requested = configuration.get("skinModel").getAsString();
        slim = requested.equals("alex") || requested.equals("auto") && image.getHeight() == 64
            && transparent(image, 54, 20, 2, 12) && transparent(image, 46, 52, 2, 12);
        bytes = image.getHeight() == 32 ? convertLegacy(source, path) : source;
        System.out.println("CONFIGURED SKIN path=" + path + " size="
            + image.getWidth() + "x" + image.getHeight() + " model=" + (slim ? "alex" : "steve")
            + " selection=" + requested);
    }

    byte[] bytes() {
        return bytes;
    }

    boolean slim() {
        return slim;
    }

    private static byte[] convertLegacy(byte[] source, Path path) throws Exception {
        Class<?> type = Class.forName("net.minecraft.client.renderer.texture.SkinTextureDownloader");
        var convert = type.getDeclaredMethod("processLegacySkin",
            com.mojang.blaze3d.platform.NativeImage.class, String.class);
        convert.setAccessible(true);
        try (var input = com.mojang.blaze3d.platform.NativeImage.read(source);
             var output = (com.mojang.blaze3d.platform.NativeImage) convert.invoke(null, input, path.toString())) {
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source));
            Path converted = Path.of(System.getProperty("omm.preview.output"), "skins", hash + ".png");
            Files.createDirectories(converted.getParent());
            output.writeToFile(converted);
            System.out.println("MINECRAFT LEGACY SKIN normalized=" + converted + " size=64x64");
            return Files.readAllBytes(converted);
        }
    }

    private static boolean transparent(java.awt.image.BufferedImage image, int x, int y, int width, int height) {
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                if ((image.getRGB(column, row) >>> 24) != 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
