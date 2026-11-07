package top.aihmc.omm.preview;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Local-only loading coverage; does not claim animation or visual correctness. */
public final class PreviewCorpusTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        var manifest =
            JsonParser.parseString(Files.readString(root.resolve("docs/m0/sample-manifest.json"))).getAsJsonObject();
        int passed = 0;
        for (var element : manifest.getAsJsonArray("entries")) {
            var entry = element.getAsJsonObject();
            String name = entry.get("name").getAsString();
            PreviewConfig config = new PreviewConfig();
            config.settings.enabled = false;
            config.configFile = root.resolve("build/preview/corpus.json");
            config.scene.models = List.of(root.resolve("refs/avatars").resolve(name).toString());
            PreviewMesh mesh = PreviewMesh.load(config);
            if (mesh.indices.length == 0 || !Float.isFinite(mesh.radius)) {
                throw new AssertionError("Empty/non-finite mesh: " + name);
            }
            passed++;
            System.out.println("Loaded " + passed + ": " + name + " (" + mesh.indices.length / 3 + " triangles)");
        }
        System.out.println("Preview corpus load PASS: " + passed + " YSM/Figura assets; bind pose only");
    }
}
