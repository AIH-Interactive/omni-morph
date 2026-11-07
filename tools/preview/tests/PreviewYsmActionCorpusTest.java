package top.aihmc.omm.preview;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Real local YSM schema/action registration coverage, separate from unverified full model animation. */
public final class PreviewYsmActionCorpusTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        var manifest =
            JsonParser.parseString(Files.readString(root.resolve("docs/m0/sample-manifest.json"))).getAsJsonObject();
        int passed = 0;
        for (var element : manifest.getAsJsonArray("entries")) {
            String name = element.getAsJsonObject().get("name").getAsString();
            PreviewConfig config = new PreviewConfig();
            config.settings.enabled = false;
            config.configFile = root.resolve("build/preview/corpus-actions.json");
            config.scene.models = List.of(root.resolve("refs/avatars").resolve(name).toString());
            PreviewMesh mesh = PreviewMesh.load(config);
            if (mesh.skeleton == null) {
                continue;
            }
            var definition = new PreviewActionPlayer.Definition(mesh, config);
            var owner = new PreviewLua(new PreviewLua.Program(java.util.Map.of(), List.of()), config.states, 42,
                new PreviewMolang(config.states, 42));
            owner.attachYsm(definition, mesh, "zh_cn");
            owner.initialize();
            owner.ysm().installWheel();
            if (owner.actionWheel.currentPage == null || owner.controls.get("ysm.action_speed") == null) {
                throw new AssertionError("Missing actual action/control host: " + name);
            }
            passed++;
            System.out.println("YSM operations loaded " + passed + ": " + name + " (actions="
                + owner.ysm().actions().all().size() + ", controls=" + owner.controls.all().size() + ")");
        }
        System.out.println(
            "YSM operation corpus PASS: " + passed + " models; registration only, not full animation correctness");
    }
}
