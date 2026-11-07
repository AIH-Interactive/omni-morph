package top.aihmc.omm.preview;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.joml.Vector3f;

/** Controller decisions must reach the actual palette, including entry/exit and transition snapshots. */
public final class PreviewControllerTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("controlled-ysm");
        Files.createDirectories(folder);
        for (String name : List.of("main.json", "texture.png")) {
            Files.copy(
                root.resolve("animated-ysm").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("ysm.json"), """
            {"metadata":{"name":"Owned controller fixture"},"files":{"player":{
              "model":{"main":"main.json"},"texture":"texture.png",
              "animation":"main.animation.json","animation_controllers":"controller.animation.json"}}}
            """);
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{
              "attachment_default":{"bones":{"accessory":{"scale":0}}},
              "pre_parallel0":{"loop":true,"bones":{"accessory":{"scale":0}}},
              "show":{"loop":true,"bones":{"accessory":{"scale":1}}},
              "idle":{"loop":true,"bones":{"root":{"position":[0,0,0]}}},
              "moving":{"loop":true,"animation_length":1,"bones":{
                "root":{"position":[16,0,0]},
                "child":{"position":[0,"(v.entries + v.exits) * 16",0]}}},
              "turn":{"loop":true,"bones":{"root":{"rotation":[0,0,90]}}},
              "short":{"animation_length":0.05,"bones":{"root":{"position":[0,0,0]}}}
            }}
            """);
        String controller = """
            {"animation_controllers":{"controller.player.main":{
              "initial_state":"idle","states":{
                "idle":{"on_entry":["v.entries = 1;"],"on_exit":["v.exits = ctrl.idle;"],
                  "animations":["attachment_default","idle"],"transitions":[{"moving":"q.is_running"}]},
                "moving":{"blend_transition":0.2,"on_entry":["v.entries = v.entries + ctrl.moving;"],
                  "animations":["moving"]}
              }}}}
            """;
        Files.writeString(folder.resolve("controller.animation.json"), controller);
        PreviewConfig config = new PreviewConfig();
        config.settings.enabled = false;
        config.configFile = root.resolve("controlled-ysm.json");
        config.window.title = "omm-core YSM controller fixture";
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.mode = "controller";
        config.animation.fixedTimeSeconds = 0.3f;
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        PreviewMesh mesh = PreviewMesh.load(config);
        var shared = new PreviewAnimation(mesh, config);
        var first = shared.newState();
        first.sample(0, 0);
        first.updateBones();
        state(first, "idle");
        point(first, 0, 1, 0, "Initial state owns its first frame");
        first.sample(1, 1f / 60);
        first.updateBones();
        state(first, "moving");
        point(first, 0, 1, 0, "Transition begins from the outgoing snapshot");
        first.sample(2, 1f / 60 + 0.1f);
        first.updateBones();
        point(first, -0.5f, 2.5f, 0, "Half transition and actual entry/exit Molang effects");
        first.sample(3, 0.3f);
        first.updateBones();
        point(first, -1, 4, 0, "Completed transition replaces its written channels");
        var second = shared.newState();
        second.sample(0, 0.3f);
        second.updateBones();
        point(second, -1, 4, 0, "First fixed-time capture replays controller steps");
        second.sample(1, 0);
        second.updateBones();
        state(second, "idle");
        point(second, 0, 1, 0, "Seek resets controller, variables and transition snapshots");
        point(first, -1, 4, 0, "Another instance's seek cannot mutate this instance");
        config.states.addProperty("running", false);
        var stationary = new PreviewAnimation(mesh, config).newState();
        stationary.sample(0, 0.3f);
        stationary.updateBones();
        state(stationary, "idle");
        point(stationary, 0, 1, 0, "Simulated running=false prevents the actual transition");
        Files.writeString(folder.resolve("controller.animation.json"), """
            {"animation_controllers":{
              "controller.player.main":{"initial_state":"ysm-entry-motion","states":{
                "ysm-entry-motion":{}}},
              "controller.player.main.motion":{"states":{"default":{
                "on_entry":["v.entries = 2; v.exits = 1;"],"animations":["moving"]}}},
              "controller.player.parallel0":{"states":{"default":{"animations":[{"turn":"0.5"}]}}}
            }}
            """);
        var layered = new PreviewAnimation(PreviewMesh.load(config), config).newState();
        layered.sample(0, 0.3f);
        layered.updateBones();
        point(layered, -1 - 4 * (float) Math.sin(Math.PI / 4), 4 * (float) Math.cos(Math.PI / 4), 0,
            "Child controller, parallel stage order, fractional weight and channel replacement");
        Files.writeString(folder.resolve("controller.animation.json"), """
            {"animation_controllers":{"controller.player.main":{"initial_state":"once","states":{
              "once":{"animations":["short"],"transitions":[{"done":"q.all_animations_finished && ctrl.state_time > 0"}]},
              "done":{"on_entry":["v.entries = ctrl.done; v.exits = 2;"],"animations":["moving"]}
            }}}}
            """);
        var completed = new PreviewAnimation(PreviewMesh.load(config), config).newState();
        completed.sample(0, 0.2f);
        completed.updateBones();
        if (!completed.controllerState("controller.player.main").equals("done")) {
            throw new AssertionError("Once clip completion did not drive the controller transition");
        }
        point(completed, -1, 4, 0, "Completion queries and current state flag execute real effects");
        Files.writeString(folder.resolve("controller.animation.json"), """
            {"animation_controllers":{"controller.player.main":{"states":{"default":{
              "animations":["pre_parallel0","show"]}}}}}
            """);
        var restored = new PreviewAnimation(PreviewMesh.load(config), config).newState();
        restored.sample(0, 0);
        restored.updateBones();
        Vector3f accessory = restored.palette[2].transformPosition(new Vector3f(2, 0, 0));
        Vector3f descendant = restored.palette[3].transformPosition(new Vector3f(2, 0, 0));
        if (accessory.distance(new Vector3f(2, 0, 0)) > 0.0001f
            || descendant.distance(new Vector3f(2, 0, 0)) > 0.0001f) {
            throw new AssertionError("A live base default prevented controller action from restoring attachment/child");
        }
        Files.writeString(folder.resolve("controller.animation.json"), controller);
        Path functions = folder.resolve("functions");
        Files.createDirectories(functions);
        Path function = functions.resolve("test.molang");
        Files.writeString(function, "v.side_effect = 1;");
        try {
            var withFunctions = new PreviewAnimation(PreviewMesh.load(config), config).newState();
            withFunctions.sample(0, 0);
            if (withFunctions.molang().evaluate(withFunctions.molang().compile("fn.test(); return v.side_effect;")) != 1) {
                throw new AssertionError("A declared function resource was silently ignored by controller playback");
            }
        } finally {
            Files.delete(function);
        }
        System.out.println("YSM controller PASS: original state runtime, entry/exit, clocks, pose transition, fixed "
            + "seek and isolation");
    }

    private static void state(PreviewAnimation.State instance, String expected) {
        String actual = instance.controllerState("controller.player.main");
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected controller state " + expected + ", got " + actual);
        }
    }

    private static void point(PreviewAnimation.State instance, float x, float y, float z, String label) {
        Vector3f actual = instance.palette[1].transformPosition(new Vector3f(0, 1, 0));
        if (actual.distance(new Vector3f(x, y, z)) > 0.0001f) {
            throw new AssertionError(label + ": " + actual);
        }
    }
}
