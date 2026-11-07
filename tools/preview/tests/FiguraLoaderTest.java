package top.aihmc.omm.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/** Owned v4/v5 cube+triangle fixtures exercise the original parser and real upload geometry. */
public final class FiguraLoaderTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        PreviewMesh previous = null;
        for (int version : new int[] {4, 5}) {
            Path folder = root.resolve("figura-v" + version);
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("avatar.json"), "{\"name\":\"Owned preview fixture\"}");
            for (String name : List.of("blue", "orange")) {
                BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                int color = name.equals("blue") ? 0xff33ccff : 0xffffaa33;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        image.setRGB(x, y, (x / 4 + y / 4) % 2 == 0 ? color : 0xffeeeeee);
                    }
                }
                ImageIO.write(image, "PNG", folder.resolve(name + ".png").toFile());
            }
            var model = JsonParser.parseString("""
                {"meta":{"format_version":"4.10","model_format":"free"},"resolution":{"width":16,"height":16},
                 "textures":[{"name":"blue.png","relative_path":"blue.png"},
                             {"name":"orange.png","relative_path":"orange.png"}],
                 "elements":[
                   {"uuid":"00000000-0000-0000-0000-000000000001","name":"cube","type":"cube",
                    "from":[0,0,0],"to":[16,16,16],"origin":[0,0,0],
                    "faces":{"north":{"uv":[0,0,16,16],"texture":0},"south":{"uv":[0,0,16,16],"texture":1}}},
                   {"uuid":"00000000-0000-0000-0000-000000000002","name":"triangle","type":"mesh","origin":[0,0,0],
                    "vertices":{"a":[32,0,0],"b":[48,0,0],"c":[32,16,0]},
                    "faces":{"face":{"vertices":["a","b","c"],"texture":1,
                                     "uv":{"a":[0,0],"b":[16,0],"c":[0,16]}}}}
                 ],
                 "outliner":[{"uuid":"00000000-0000-0000-0000-000000000003","name":"root",
                   "origin":[0,0,0],"rotation":[0,0,90],
                   "children":["00000000-0000-0000-0000-000000000001","00000000-0000-0000-0000-000000000002"]}]}
                """).getAsJsonObject();
            if (version == 5) {
                model.getAsJsonObject("meta").addProperty("format_version", "5.0");
                var group = model.getAsJsonArray("outliner").get(0).getAsJsonObject();
                var groups = new JsonArray();
                var metadata = group.deepCopy();
                metadata.remove("children");
                groups.add(metadata);
                model.add("groups", groups);
            }
            Files.writeString(folder.resolve("main.bbmodel"), model.toString());
            Path archive = root.resolve("figura-v" + version + ".zip");
            try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                for (String name : List.of("avatar.json", "main.bbmodel", "blue.png", "orange.png")) {
                    zip.putNextEntry(new ZipEntry("nested/avatar/" + name));
                    Files.copy(folder.resolve(name), zip);
                    zip.closeEntry();
                }
            }
            PreviewMesh directory = load(root, folder, "figura-v" + version + ".json");
            PreviewMesh zipped = load(root, archive, "figura-v" + version + "-zip.json");
            require(Arrays.equals(directory.vertices, zipped.vertices), "Figura folder/ZIP vertices differ");
            require(Arrays.equals(directory.indices, zipped.indices), "Figura folder/ZIP indices differ");
            require(directory.draws.equals(zipped.draws), "Figura folder/ZIP material draws differ");
            require(directory.textures.size() == 2 && directory.draws.size() == 2, "Two materials were not retained");
            require(
                directory.indices.length == 15 && directory.vertices.length == 55, "Cube/triangle topology differs");
            require(Math.abs(directory.vertices[0]) < 1e-5 && Math.abs(directory.vertices[1] - 1) < 1e-5,
                "Parent rotation or pixel-to-block conversion differs from the expected corner");
            if (previous != null) {
                require(Arrays.equals(previous.indices, directory.indices)
                        && Arrays.equals(previous.vertices, directory.vertices),
                    "v4/v5 fixture geometry differs");
            }
            previous = directory;
        }
        System.out.println(
            "Figura loader PASS: v4/v5 folder/nested ZIP, parent transform, cube+triangle, two textures");
    }

    private static PreviewMesh load(Path root, Path model, String filename) throws Exception {
        Path file = root.resolve(filename);
        var config = new PreviewConfig();
        config.settings.enabled = false;
        config.vanilla.enabled = false;
        config.window.title = "omm-core Figura static fixture";
        config.window.width = 960;
        config.window.height = 600;
        config.scene.models = List.of(model.toString());
        config.recording.outputDirectory = "runs";
        Files.writeString(file, PreviewConfig.JSON.toJson(config));
        return PreviewMesh.load(PreviewConfig.load(new String[] {"--config", file.toString()}));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
