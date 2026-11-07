package top.aihmc.omm.preview;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.joml.Vector3f;

/** Declared controller slots freeze their timeline during the authored beginning transition. */
public final class PreviewDeclaredTransitionTest {
    private static void near(float actual, float expected) {
        if (Math.abs(actual - expected) > 0.001f) {
            throw new AssertionError(actual + " != " + expected);
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("ysm-declared-transition");
        Files.createDirectories(folder);
        for (String name : List.of("main.json", "texture.png", "ysm.json")) {
            Files.copy(
                root.resolve("ysm-functions").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{"moving":{"loop":true,"animation_length":1,
              "timeline":{"0":"v.zeros = v.zeros + 1;", "0.125":"v.mids = v.mids + 1;"},
              "bones":{"root":{"position":["q.anim_time * 16",0,0]}}
            }}}
            """);
        Files.writeString(folder.resolve("controller.animation.json"), """
            {"animation_controllers":{"controller.player.main":{"states":{
              "default":{"animations":["moving"],"blend_transition":0.5}
            }}}}
            """);
        var config = new PreviewConfig();
        config.configFile = root.resolve("ysm-declared-transition.json");
        config.scene.models = List.of(folder.toString());
        config.settings.enabled = false;
        config.animation.enabled = true;
        config.animation.mode = "controller";
        config.animation.fixedTimeSeconds = 0.7f;
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        try (var animation = new PreviewAnimation(PreviewMesh.load(config), config)) {
            var state = animation.newState();
            state.sample(0, 0);
            check(state, 0, 0, 0);
            state.sample(1, 0.49f);
            check(state, 0, 0, 0);
            state.sample(2, 0.51f);
            check(state, 0.01f, 1, 0);
            state.sample(3, 0.7f);
            check(state, 0.2f, 1, 1);
            state.updateBones();
            near(state.palette[0].transformPosition(new Vector3f()).x, -0.2f);
            var other = animation.newState();
            other.sample(0, 0);
            check(other, 0, 0, 0);
            state.sample(4, 0);
            check(state, 0, 0, 0);
        }
        System.out.println("Declared transition PASS: real controller timeline, delayed zero/mid events, palette and "
                           + "instance/seek isolation");
    }

    private static void check(PreviewAnimation.State state, float time, float zeros, float mids) throws Exception {
        var host = state.molang();
        near(host.controllers.getControllerAnimation("controller.player.main", "moving").time, time);
        near(host.evaluate(host.compile("v.zeros")), zeros);
        near(host.evaluate(host.compile("v.mids")), mids);
    }
}
