package top.aihmc.omm.preview;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/** Owned package fixture: real manifest, parser, texture and nested ZIP load; no Minecraft. */
public final class PreviewMeshTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("avatar");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("main.json"), """
            {"minecraft:geometry":[{"description":{"texture_width":16,"texture_height":16},"bones":[
              {"name":"root","pivot":[0,8,0],"rotation":[0,25,0],"cubes":[
                {"origin":[-8,0,-4],"size":[16,16,8],"uv":[0,0]}]},
              {"name":"child","parent":"root","pivot":[0,16,0],"rotation":[0,0,30],"cubes":[
                {"origin":[-2,16,-2],"size":[4,8,4],"uv":[0,0]}]}
            ]}]}
            """);
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, (x / 4 + y / 4) % 2 == 0 ? 0xff33ccff : 0xffffaa33);
            }
        }
        ImageIO.write(image, "PNG", folder.resolve("texture.png").toFile());
        Path archive = root.resolve("avatar.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String name : new String[] {"main.json", "texture.png"}) {
                zip.putNextEntry(new ZipEntry("nested/avatar/" + name));
                Files.copy(folder.resolve(name), zip);
                zip.closeEntry();
            }
        }
        PreviewMesh directory = load(root, "avatar", "folder.json");
        PreviewMesh zipped = load(root, "avatar.zip", "zip.json");
        require(Arrays.equals(directory.vertices, zipped.vertices), "Folder and nested ZIP vertices differ");
        require(Arrays.equals(directory.indices, zipped.indices), "Folder and nested ZIP indices differ");
        require(directory.indices.length == 72 && directory.vertices.length == 240, "Two-cube topology differs");
        require(directory.texture.getRGB(2, 2) == zipped.texture.getRGB(2, 2), "Texture differs");
        require(directory.radius > 0.5 && Float.isFinite(directory.radius), "Invalid auto-frame bounds");
        System.out.println("Preview mesh PASS: nested ZIP equals folder; two-bone bind geometry and PNG loaded");
        System.out.println("Window fixture config: " + root.resolve("folder.json"));
    }

    private static PreviewMesh load(Path root, String model, String configName) throws Exception {
        Path config = root.resolve(configName);
        Files.writeString(config, """
            {"window":{"title":"omm-core YSM static fixture","width":960,"height":600},
             "scene":{"models":["%s"]},"recording":{"outputDirectory":"runs"}}
            """.formatted(model));
        return PreviewMesh.load(PreviewConfig.load(new String[] {"--config", config.toString()}));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
