package top.aihmc.omm.preview;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.joml.Vector3f;

/** Config replay and recorded real-window inputs share the same action/control handlers and palette path. */
public final class PreviewInteractionTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        PreviewConfig config =
            PreviewConfig.load(new String[] {"--config", root.resolve("lua-animated-ysm.json").toString()});
        config.outputDirectory = root.resolve("interaction-original");
        Files.createDirectories(config.outputDirectory);
        config.interaction.actions = List.of(click(0), set(2, 2), reset(3), click(4));
        var shared = new PreviewAnimation(PreviewMesh.load(config), config);
        var original = shared.newState();
        float[] expected = {-1, -1, -2, 0, 0};
        try (var interaction = new PreviewInteraction(config)) {
            for (int frame = 0; frame < 5; frame++) {
                interaction.update(original.lua(), frame, 0.125f);
                original.sample(frame, 0.125f);
                original.updateBones();
                point(original, expected[frame], "Original operation frame " + frame);
            }
        }
        Path originalState = config.outputDirectory.resolve("interaction-state.json");
        if (!Files.exists(originalState)) {
            throw new AssertionError("Final actual model settings/wheel state was not recorded");
        }
        Path log = config.outputDirectory.resolve("interaction.jsonl");
        if (Files.readAllLines(log).size() != 4 || !Files.readString(log).contains("恢复位置")) {
            throw new AssertionError("Operation log lost requests or actual wheel/control state");
        }
        config.outputDirectory = root.resolve("interaction-replayed");
        Files.createDirectories(config.outputDirectory);
        config.interaction.actions = List.of();
        config.interaction.replayFile = log.toString();
        var replayed = shared.newState();
        try (var interaction = new PreviewInteraction(config)) {
            for (int frame = 0; frame < 5; frame++) {
                interaction.update(replayed.lua(), frame, 0.125f);
                replayed.sample(frame, 0.125f);
                replayed.updateBones();
                point(replayed, expected[frame], "Recorded operation replay frame " + frame);
            }
        }
        var originalFinal = com.google.gson.JsonParser.parseString(Files.readString(originalState));
        var replayFinal = com.google.gson.JsonParser.parseString(
            Files.readString(config.outputDirectory.resolve("interaction-state.json")));
        if (!originalFinal.equals(replayFinal)) {
            throw new AssertionError("Recorded window operation replay did not restore the final actual model state");
        }
        config.interaction.replayFile = "";
        config.interaction.enabled = true;
        config.window.title = "omm-core interactive wheel fixture";
        config.recording.outputDirectory = "runs";
        Files.writeString(root.resolve("interactive-ysm.json"), PreviewConfig.JSON.toJson(config));
        System.out.println(
            "Interaction PASS: action/control/reset -> actual palette, JSONL state and deterministic replay");
    }

    private static PreviewInteraction.Operation click(long frame) {
        var operation = new PreviewInteraction.Operation();
        operation.frame = frame;
        operation.type = "wheel-click";
        operation.slot = 1;
        return operation;
    }

    private static PreviewInteraction.Operation set(long frame, double value) {
        var operation = new PreviewInteraction.Operation();
        operation.frame = frame;
        operation.type = "set-control";
        operation.id = "offset";
        operation.value = new com.google.gson.JsonPrimitive(value);
        return operation;
    }

    private static PreviewInteraction.Operation reset(long frame) {
        var operation = new PreviewInteraction.Operation();
        operation.frame = frame;
        operation.type = "reset-control";
        operation.id = "offset";
        return operation;
    }

    private static void point(PreviewAnimation.State instance, float x, String label) {
        Vector3f point = instance.palette[1].transformPosition(new Vector3f(0, 1, 0));
        if (point.distance(new Vector3f(x, 5, 0.5f)) > 0.0001f) {
            throw new AssertionError(label + ": " + point);
        }
    }
}
