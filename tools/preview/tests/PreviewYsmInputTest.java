package top.aihmc.omm.preview;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.joml.Vector3f;

/** YSM numeric input queries must drive the actual no-Lua model through record and replay. */
public final class PreviewYsmInputTest {
    private static float eval(PreviewMolang host, String source) throws Exception {
        return host.evaluate(host.compile(source));
    }

    private static void near(float actual, float expected) {
        if (Math.abs(actual - expected) > 0.001f)
            throw new AssertionError(actual + " != " + expected);
    }

    public static void main(String[] args) throws Exception {
        var inputs = JsonParser.parseString("""
            {"input":{"keys":[32,75,348],"mouse":[0,7]}}
            """).getAsJsonObject();
        var host = new PreviewMolang(inputs, 42);
        near(eval(host, "ysm.keyboard(31,65,75,349)"), 1);
        near(eval(host, "ysm.keyboard(31,65,349)"), 0);
        near(eval(host, "ysm.mouse(7)"), 1);
        near(eval(host, "ysm.mouse(8)"), 0);
        host.input.event(PreviewKeys.parse("scancode.75"), false);
        near(eval(host, "ysm.keyboard(75)"), 1);
        host.input.setReady(false);
        host.input.event(PreviewKeys.parse("key.keyboard.k"), true);
        host.input.setReady(true);
        near(eval(host, "ysm.keyboard(32,75,348) + ysm.mouse(0) + ysm.mouse(7)"), 0);
        var nonOwner = new PreviewMolang(JsonParser.parseString("""
            {"input":{"owner":false,"keys":[75],"mouse":[0]}}
            """).getAsJsonObject(), 42);
        near(eval(nonOwner, "ysm.keyboard(75) + ysm.mouse(0)"), 0);
        var gui = new PreviewMolang(JsonParser.parseString("""
            {"guiOpen":true,"input":{"keys":[75]}}
            """).getAsJsonObject(), 42);
        near(eval(gui, "ysm.keyboard(75)"), 0);

        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("ysm-input");
        Files.createDirectories(folder);
        for (String name : List.of("main.json", "texture.png", "ysm.json")) {
            Files.copy(
                root.resolve("ysm-functions").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{"input":{"loop":true,"animation_length":1,
              "bones":{"root":{"position":["(ysm.keyboard(65,75) + ysm.mouse(0)*2)*16",0,0]}}
            }}}
            """);
        var config = new PreviewConfig();
        config.configFile = root.resolve("ysm-input.json");
        config.scene.models = List.of(folder.toString());
        config.settings.enabled = false;
        config.animation.enabled = true;
        config.animation.clip = "input";
        config.animation.fixedTimeSeconds = 0f;
        config.recording.outputDirectory = "runs";
        config.interaction.actions = List.of(key(0, "key-down", "key.keyboard.k"), key(1, "key-down", "key.mouse.left"),
            ready(2, false), key(3, "key-down", "key.keyboard.k"), ready(4, true), key(5, "key-down", "key.keyboard.k"),
            key(6, "key-up", "key.keyboard.k"));
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        PreviewConfig.load(new String[] {"--config", config.configFile.toString()});
        try (var animation = new PreviewAnimation(PreviewMesh.load(config), config)) {
            var state = animation.newState();
            if (state.lua() != null)
                throw new AssertionError("Numeric YSM input unexpectedly required a Lua VM");
            config.outputDirectory = root.resolve("ysm-input-recording");
            Files.createDirectories(config.outputDirectory);
            float[] expected = {-1, -3, 0, 0, 0, -1, 0};
            try (var interaction = new PreviewInteraction(config)) {
                run(state, interaction, expected);
            }
            var saved =
                JsonParser.parseString(Files.readString(config.outputDirectory.resolve("interaction-state.json")));
            var other = animation.newState();
            near(eval(other.molang(), "ysm.keyboard(75) + ysm.mouse(0)"), 0);
            config.interaction.actions = List.of();
            config.interaction.replayFile = "ysm-input-recording/interaction.jsonl";
            config.outputDirectory = root.resolve("ysm-input-replay");
            Files.createDirectories(config.outputDirectory);
            try (var interaction = new PreviewInteraction(config)) {
                run(other, interaction, expected);
                interaction.keyInput("key.mouse.left", -1, true, 0);
                interaction.update(null, other.molang(), 7, 0);
                near(eval(other.molang(), "ysm.mouse(0)"), 1);
                interaction.releaseKeys();
                interaction.update(null, other.molang(), 8, 0);
                near(eval(other.molang(), "ysm.mouse(0)"), 0);
                interaction.keyInput("key.keyboard.k", -1, true, 0, true);
                interaction.update(null, other.molang(), 9, 0);
                near(eval(other.molang(), "ysm.keyboard(75)"), 0);
            }
            if (!saved.equals(
                    JsonParser.parseString(Files.readString(config.outputDirectory.resolve("interaction-state.json")))))
                throw new AssertionError("Final input snapshot differed after replay/physical queue cleanup");
        }
        System.out.println("YSM input PASS: numeric ANY keys/mouse, readiness/owner, no-Lua palette, "
            + "record/replay, physical queue, release cleanup and model isolation");
    }

    private static void run(PreviewAnimation.State state, PreviewInteraction interaction, float[] expected)
        throws Exception {
        for (int frame = 0; frame < expected.length; frame++) {
            interaction.update(null, state.molang(), frame, 0);
            state.sample(frame, 0);
            state.updateBones();
            near(state.palette[0].transformPosition(new Vector3f()).x, expected[frame]);
        }
    }

    private static PreviewInteraction.Operation key(long frame, String type, String id) {
        var operation = new PreviewInteraction.Operation();
        operation.frame = frame;
        operation.type = type;
        operation.id = id;
        operation.modifiers = 3;
        return operation;
    }

    private static PreviewInteraction.Operation ready(long frame, boolean value) {
        var operation = new PreviewInteraction.Operation();
        operation.frame = frame;
        operation.type = "input-ready";
        operation.value = new JsonPrimitive(value);
        return operation;
    }
}
