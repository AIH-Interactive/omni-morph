package top.aihmc.omnimorph.avatar.ysm;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Replays encrypted metadata image conversion on the same asynchronous route as library previews. */
public final class YsmMetadataImageRegression {
    public static void main(String[] args) {
        replay(args);
        java.io.File cache = javax.imageio.ImageIO.getCacheDirectory();
        boolean caching = javax.imageio.ImageIO.getUseCache();
        try {
            Path missing = java.nio.file.Files.createTempDirectory("ysm-image-cache-");
            javax.imageio.ImageIO.setCacheDirectory(missing.toFile());
            javax.imageio.ImageIO.setUseCache(true);
            java.nio.file.Files.delete(missing);
            byte[] png = top.aihmc.omnimorph.avatar.ysm.binary.YsmTextureDecoder.toPng(
                    new byte[] {(byte) 255, 0, 0, (byte) 255}, -1, 1, 1,
                    new top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeBudget(
                            YsmResourceLimits.DEFAULT, () -> false, 10_000));
            try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(
                    new java.io.ByteArrayInputStream(png))) {
                var reader = javax.imageio.ImageIO.getImageReadersByFormatName("PNG").next();
                try {
                    reader.setInput(input);
                    if (reader.read(0).getRGB(0, 0) != 0xffff0000) {
                        throw new AssertionError("PNG output must preserve the decoded pixel");
                    }
                } finally {
                    reader.dispose();
                }
            }
            replay(args);
            System.out.println("PASS metadata conversion does not require a writable ImageIO disk cache");
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        } finally {
            javax.imageio.ImageIO.setCacheDirectory(cache);
            javax.imageio.ImageIO.setUseCache(caching);
        }
    }

    private static void replay(String[] args) {
        for (String argument : args) {
            CompletableFuture.runAsync(() -> {
                try (var data = YsmModelData.read(Path.of(argument), () -> false)) {
                    if (data.mainGeometry() == null) {
                        throw new AssertionError("Model geometry missing after metadata conversion");
                    }
                    System.out.println("PASS encrypted metadata/model load: " + Path.of(argument).getFileName());
                } catch (Exception failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            }).join();
        }
    }
}
