package top.aihmc.omnimorph.avatar.ysm;

import top.aihmc.omnimorph.avatar.ysm.binary.YsmTextureDecoder;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeBudget;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

/** Actual CPU/native image decoding, with no window, GPU or GUI validation claim. */
public final class YsmAvifSmokeTest {
    private YsmAvifSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("figura-avif-test-").toAbsolutePath().normalize();
        String previous = System.getProperty("figura.test.directory");
        System.setProperty("figura.test.directory", directory.toString());
        try {
            runCorpus(Path.of(args[0]));
        } finally {
            if (previous == null) {
                System.clearProperty("figura.test.directory");
            } else {
                System.setProperty("figura.test.directory", previous);
            }
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(directory)) {
                        throw new IllegalStateException("Unexpected AVIF test cleanup path");
                    }
                    Files.delete(path);
                }
            }
        }
    }

    private static void runCorpus(Path root) throws Exception {
        testIndependentVector();
        int images = 0;
        int guiImages = 0;
        int previews = 0;
        var resolveClip = top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer.class
                .getDeclaredMethod("resolveAnimationName", String.class);
        resolveClip.setAccessible(true);
        int models = 0;
        long pixels = 0;
        try (var files = Files.list(root)) {
            for (Path path : files.sorted(Comparator.comparing(Path::toString)).toList()) {
                if (!YsmAvatarDetector.isYsmAvatar(path)) {
                    continue;
                }
                try (YsmModelData data = YsmModelData.read(path, () -> false)) {
                    models++;
                    var tag = YsmAvatarLoader.toRuntimeTag(data, data.manifest().textures().getFirst());
                    guiImages += tag.getListOrEmpty("gui_entries").size();
                    String previewName = tag.getStringOr("preview_animation", "");
                    if (!previewName.isBlank()) {
                        var player = new top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer(null);
                        for (var value : tag.getListOrEmpty("animations")) {
                            var animation = (net.minecraft.nbt.CompoundTag) value;
                            String json = new String(animation.getByteArray("data").orElseThrow(), java.nio.charset.StandardCharsets.UTF_8);
                            player.registerAnimations(top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser.parse(json, false, null,
                                    tag.getBooleanOr("merge_multiline_expr", false)));
                        }
                        String resolved = (String) resolveClip.invoke(player, previewName);
                        require(player.getClips().containsKey(resolved), "declared corpus preview resolves through the production animation aliases");
                        previews++;
                    }
                    try (var gui = new top.aihmc.omnimorph.model.ysm.YsmGuiResources(null)) {
                        gui.load(tag);
                        require(gui.invalidImages() == 0, "every actual selected/named GUI image decodes through the runtime resource container");
                    }
                    if (data.encrypted()) {
                        var portable = YsmAvatarLoader.toAvatarNbt(data, data.manifest().textures().getFirst(),
                                new java.util.UUID(0, 1), path).getCompoundOrEmpty("ysm");
                        require(!portable.contains("gui_entries") && !portable.contains("gui_image_names"),
                                "decoded GUI resources and names never enter the encrypted portable payload");
                    }
                    for (String resource : data.resources().resourcePaths()) {
                        if (!resource.endsWith(".avif")) {
                            continue;
                        }
                        byte[] encoded = data.resources().readBytes(resource);
                        byte[] original = encoded.clone();
                        byte[] png = YsmTextureDecoder.toPng(encoded, 5, 0, 0, new YsmDecodeBudget());
                        var decoded = ImageIO.read(new ByteArrayInputStream(png));
                        require(decoded != null && decoded.getWidth() > 0 && decoded.getHeight() > 0,
                                "actual AVIF resource decodes into a valid PNG");
                        pixels += (long) decoded.getWidth() * decoded.getHeight();
                        byte[] repeated = YsmTextureDecoder.toPng(encoded, 5, decoded.getWidth(), decoded.getHeight(), new YsmDecodeBudget());
                        require(Arrays.equals(png, repeated), "repeated native creation/destruction retains identical pixels");
                        try {
                            YsmTextureDecoder.toPng(encoded, 5, decoded.getWidth() + 1, decoded.getHeight(), new YsmDecodeBudget());
                            throw new AssertionError("mismatched dimensions accepted");
                        } catch (YsmDecodeException expected) {
                            require(expected.getMessage().contains("dimension-mismatch"), "parsed dimensions are checked before frame decoding");
                        }
                        try {
                            YsmTextureDecoder.toPng(Arrays.copyOf(encoded, 32), 5, 0, 0, new YsmDecodeBudget());
                            throw new AssertionError("truncated AVIF accepted");
                        } catch (java.io.IOException expected) {
                            require(!expected.getMessage().contains(resource), "decode failure does not expose model resource names");
                        }
                        require(Arrays.equals(original, encoded), "decoder never mutates the owned source bytes");
                        decoded.flush();
                        images++;
                    }
                }
            }
        }
        require(guiImages > 0, "corpus includes actual GUI resources");
        require(models == 40 && images > 0, "the complete plain/encrypted model corpus includes actual AVIF images");
        System.out.println("M5 AVIF native decoding PASS: models=" + models + " images=" + images + " pixels=" + pixels + " guiImages=" + guiImages + " declaredPreviews=" + previews);
    }

    private static void testIndependentVector() throws Exception {
        byte[] encoded;
        try (var input = YsmAvifSmokeTest.class.getResourceAsStream("/ysm/image-vectors/rgba-quadrants.avif")) {
            require(input != null, "independent AVIF test vector is available");
            encoded = input.readAllBytes();
        }
        byte[] png = YsmTextureDecoder.toPng(encoded, 5, 16, 16, new YsmDecodeBudget());
        var image = ImageIO.read(new ByteArrayInputStream(png));
        try {
            pixel(image.getRGB(3, 3), 0xffff0000, "red channel order");
            pixel(image.getRGB(12, 3), 0x8000ff00, "green with half alpha");
            pixel(image.getRGB(3, 12), 0x400000ff, "blue with quarter alpha");
            require(image.getRGB(12, 12) >>> 24 == 0, "fully transparent alpha survives AVIF decoding");
        } finally {
            image.flush();
        }
        try {
            YsmTextureDecoder.toPng(encoded, 5, 16, 16,
                    new YsmDecodeBudget(YsmResourceLimits.DEFAULT, () -> true, 10000));
            throw new AssertionError("cancelled AVIF decode accepted");
        } catch (java.io.InterruptedIOException expected) {
            require(expected.getMessage().contains("CANCELLED"), "cancellation is checked before native decoding");
        }
    }

    private static void pixel(int actual, int expected, String message) {
        for (int shift : new int[]{0, 8, 16, 24}) {
            int difference = Math.abs((actual >>> shift & 255) - (expected >>> shift & 255));
            require(difference <= 2, message + " (8-bit YUV conversion tolerance)");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
