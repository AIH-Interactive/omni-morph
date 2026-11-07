package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.joml.Vector3f;

/** Relative world-tag queries must use original coordinates and feed real controller/bone output. */
public final class PreviewBlockQueryTest {
    private static float evaluate(PreviewMolang host, String source) throws Exception {
        return host.evaluate(host.compile(source));
    }

    private static void expected(PreviewMolang host, String source, float value) throws Exception {
        if (evaluate(host, source) != value)
            throw new AssertionError(source);
    }

    public static void main(String[] args) throws Exception {
        var states = JsonParser.parseString("""
            {"player":{"pos":[0.25,1.25,-0.25]},"world":{
              "defaultBlockTags":["example:default"],
              "blocks":[{"pos":[0,2,-2],"tags":["minecraft:replaceable"]},{"pos":[-1,1,-1],"tags":[]}]
            }}
            """).getAsJsonObject();
        var host = new PreviewMolang(states, 42);
        String query = "query.relative_block_has_any_tag(0,1.5,-1,'minecraft:replaceable')";
        expected(host, query, 1);
        expected(host, "q.relative_block_has_any_tag(0,1.5,-1,'missing','replaceable')", 1);
        expected(host, "q.relative_block_has_any_tag(0,1.5,-1,'example:default')", 0);
        expected(host, "q.relative_block_has_any_tag(-1,0,0,'example:default')", 0);
        expected(host, "q.relative_block_has_any_tag(5,0,0,'example:default')", 1);
        expected(host, "q.relative_block_has_any_tag(5.01,0,0,'example:default')", 0);
        expected(host, "q.relative_block_has_any_tag(0,1.5,-1,'BAD TAG')", 0);
        expected(host, "q.relative_block_has_any_tag(0,1.5,-1,'replaceable','BAD TAG')", 1);
        try {
            var missing = new PreviewMolang(new JsonObject(), 42);
            evaluate(missing, query);
            throw new AssertionError("Missing block snapshot silently supplied a tag result");
        } catch (UnsupportedOperationException error) {
            if (!error.getMessage().contains("states.world.blocks"))
                throw error;
        }
        states.getAsJsonObject("world").getAsJsonArray("blocks").get(0).getAsJsonObject().getAsJsonArray("tags").remove(
            0);
        expected(host, query, 1);
        expected(new PreviewMolang(states, 42), query, 0);

        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("ysm-block-query");
        Files.createDirectories(folder.resolve("functions"));
        for (String name : List.of("main.json", "texture.png", "ysm.json")) {
            Files.copy(
                root.resolve("ysm-functions").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{"pose":{"loop":true,"bones":{"root":{"position":[0,0,0]},
              "child":{"position":[0,"v.reachable*4",0]}}}}}
            """);
        Files.writeString(folder.resolve("controller.animation.json"), """
            {"animation_controllers":{"controller.player.main":{"states":{"default":{"animations":["pose"]}}}}}
            """);
        Files.writeString(folder.resolve("functions/sample@player_ctrl_main.molang"),
            "v.reachable = query.relative_block_has_any_tag(0,1.5,-1,'minecraft:replaceable');");
        var config = new PreviewConfig();
        config.configFile = root.resolve("ysm-block-query.json");
        config.settings.enabled = false;
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.mode = "controller";
        config.animation.fixedTimeSeconds = 0f;
        config.recording.outputDirectory = "runs";
        states.getAsJsonObject("world").getAsJsonArray("blocks").get(0).getAsJsonObject().getAsJsonArray("tags").add(
            "minecraft:replaceable");
        config.states = states;
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        var mesh = PreviewMesh.load(config);
        try (var animation = new PreviewAnimation(mesh, config)) {
            var state = animation.newState();
            state.sample(0, 0);
            state.updateBones();
            if (Math.abs(state.palette[1].transformPosition(new Vector3f(0, 1, 0)).y - 1.25f) > 0.0001f) {
                throw new AssertionError("Relative block query did not drive the actual controller palette");
            }
            state.sample(1, 0.1f);
            state.sample(2, 0);
            expected(state.molang(), "v.reachable", 1);
        }
        System.out.println("Block query PASS: original rounding/range/ANY-tag rules, explicit world snapshots, "
                           + "missing-state diagnostics and actual controller palette/seek");
    }
}
