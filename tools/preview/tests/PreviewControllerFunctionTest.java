package top.aihmc.omm.preview;

import static org.lwjgl.openal.AL10.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.joml.Vector3f;

/** Function slots must retain clocks, delay events, reload, fade and own their actual audio. */
public final class PreviewControllerFunctionTest {
    private static final String CONTROLLER = "controller.player.main";

    private static float value(PreviewAnimation.State state, String source) throws Exception {
        return state.molang().evaluate(state.molang().compile(source));
    }

    private static void near(float actual, float expected) {
        if (Math.abs(actual - expected) > 0.001f)
            throw new AssertionError(actual + " != " + expected);
    }

    private static void count(PreviewAnimation animation, int expected) {
        if (((Number) animation.audio.snapshot().get("activeSources")).intValue() != expected) {
            throw new AssertionError(animation.audio.snapshot());
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("ysm-controller-functions");
        Files.createDirectories(folder.resolve("functions"));
        Files.createDirectories(folder.resolve("audio"));
        for (String name : List.of("main.json", "texture.png", "ysm.json")) {
            Files.copy(
                root.resolve("ysm-functions").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        byte[] ogg;
        try (var input = PreviewControllerFunctionTest.class.getResourceAsStream(
                 "/assets/minecraft/sounds/mob/squid/death1.ogg")) {
            ogg = input.readAllBytes();
        }
        Files.write(folder.resolve("audio/beep.ogg"), ogg);
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{"moving":{"loop":true,"animation_length":1,
              "timeline":{"0":"v.zeros = v.zeros + 1; ysm.play_sound('tone','beep',6,0.01);",
                "0.125":"v.mids = v.mids + 1;"},
              "bones":{"root":{"position":["q.anim_time * 16",0,0]}}
            }}}
            """);
        Files.writeString(folder.resolve("controller.animation.json"), """
            {"animation_controllers":{"controller.player.main":{"states":{"default":{}}}}}
            """);
        Files.writeString(folder.resolve("functions/select@player_ctrl_main.molang"), """
            ctrl.set_beginning_transition_length(0.5);
            v.reload ? {ctrl.indicate_reload(); v.reload = 0;};
            v.stop ? ctrl.reset() : ctrl.set_animation('moving', ctrl.loop);
            v.function_clock = ctrl.anim_time;
            """);
        var config = new PreviewConfig();
        config.configFile = root.resolve("ysm-controller-functions.json");
        config.settings.enabled = false;
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.mode = "controller";
        config.animation.fixedTimeSeconds = 0.7f;
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        try (var animation = new PreviewAnimation(PreviewMesh.load(config), config)) {
            animation.audio.modelBuffer(ogg);
            alListenerf(AL_GAIN, 0);
            var state = animation.newState();
            var other = animation.newState();
            state.sample(0, 0);
            var original = state.molang().controllers.getControllerFunctionAnimation(CONTROLLER);
            if (original == null)
                throw new AssertionError("Function command did not create a real playback");
            state.sample(1, 0.49f);
            near(original.time, 0);
            near(value(state, "v.zeros"), 0);
            count(animation, 0);
            state.sample(2, 0.51f);
            near(original.time, 0.01f);
            near(value(state, "v.zeros"), 1);
            count(animation, 1);
            state.sample(3, 0.7f);
            near(original.time, 0.2f);
            near(value(state, "v.mids"), 1);
            if (state.molang().controllers.getControllerFunctionAnimation(CONTROLLER) != original) {
                throw new AssertionError("Repeated set_animation restarted the function slot");
            }
            state.updateBones();
            near(state.palette[0].transformPosition(new Vector3f()).x, -0.2f);
            if (value(state, "v.function_clock") <= 0.15f)
                throw new AssertionError("Controller clock omitted its function slot");

            value(state, "v.reload = 1;");
            state.sample(4, 0.71f);
            var reloaded = state.molang().controllers.getControllerFunctionAnimation(CONTROLLER);
            if (reloaded == original || reloaded == null)
                throw new AssertionError("Reload did not replace the playback");
            near(reloaded.time, 0);
            count(animation, 0);
            state.sample(5, 0.9f);
            near(value(state, "v.zeros"), 1);
            state.updateBones();
            near(state.palette[0].transformPosition(new Vector3f()).x, -0.124f);
            state.sample(6, 1.22f);
            near(value(state, "v.zeros"), 2);
            count(animation, 1);
            value(state, "v.stop = 1;");
            state.sample(7, 1.23f);
            if (state.molang().controllers.getControllerFunctionAnimation(CONTROLLER) != null) {
                throw new AssertionError("Reset retained the selected function slot");
            }
            count(animation, 0);
            state.updateBones();
            near(state.palette[0].transformPosition(new Vector3f()).x, 0);
            state.sample(8, 2);
            state.updateBones();
            near(state.palette[0].transformPosition(new Vector3f()).x, 0);
            near(value(state, "v.mids"), 1);
            other.sample(0, 0);
            near(value(other, "v.zeros"), 0);
            state.sample(9, 0);
            near(value(state, "v.zeros"), 0);
            near(state.molang().controllers.getControllerFunctionAnimation(CONTROLLER).time, 0);
            near(value(state, "ctrl.loop"), 10);
            near(value(state, "ctrl.play_once"), 11);
            near(value(state, "ctrl.hold_on_last_frame"), 12);
            near(value(state, "ctrl.state_continue"), 2);
            near(value(state, "ctrl.state_stop"), 3);
            near(value(state, "ctrl.state_pause"), 4);
            near(value(state, "ctrl.state_bypass"), 5);
            value(state, "v.stop = 1;");

            state.molang().controller.setPath("controller", "boundary");
            value(state, "ctrl.set_animation('moving',ctrl.loop);");
            var boundary = state.molang().controllers.getControllerFunctionAnimation("boundary");
            value(state, "ctrl.set_animation('player.moving',ctrl.loop);");
            if (state.molang().controllers.getControllerFunctionAnimation("boundary") != boundary) {
                throw new AssertionError("Resolved animation aliases created different function slots");
            }
            state.molang().controllers.advanceOnly(1);
            near(boundary.time, 1);
            if (boundary.hasCompletedCycle())
                throw new AssertionError("Completion fired at the exact end boundary");
            state.molang().controllers.advanceOnly(0.01f);
            near(boundary.time, 0.01f);
            if (!boundary.hasCompletedCycle())
                throw new AssertionError("Completion did not persist across wrapping");

            state.molang().controller.setPath("controller", "delayed");
            value(state, "ctrl.set_beginning_transition_length(0.5); ctrl.set_animation('moving',ctrl.loop);");
            var delayed = state.molang().controllers.getControllerFunctionAnimation("delayed");
            state.molang().controllers.advanceOnly(0.25f);
            near(delayed.time, 0);
            value(state, "ctrl.set_animation('moving',ctrl.loop);");
            state.molang().controllers.advanceOnly(0.25f);
            near(delayed.time, 0);
            state.molang().controllers.advanceOnly(0.125f);
            near(delayed.time, 0.125f);

            state.molang().controller.setPath("controller", "other");
            value(state,
                "ctrl.set_beginning_transition_length(0); ctrl.set_animation('moving',ctrl.hold_on_last_frame);");
            var held = state.molang().controllers.getControllerFunctionAnimation("other");
            state.molang().controllers.advanceOnly(2);
            near(held.time, 1);
            state.molang().controller.setPath("controller", "once");
            value(state, "ctrl.set_animation('moving',ctrl.play_once);");
            state.molang().controllers.advanceOnly(2);
            state.molang().controllers.advanceOnly(0.25f);
            if (state.molang().controllers.getControllerFunctionAnimation("once") != null) {
                throw new AssertionError("Once playback did not retire after its completion fade");
            }
            var cancelled = animation.newState();
            value(cancelled, "v.stop = 1;");
            cancelled.sample(0, 0);
            cancelled.molang().controller.setPath("controller", "cancelled");
            value(cancelled, "ctrl.set_beginning_transition_length(0.5); ctrl.set_animation('moving'); ctrl.reset();");
            cancelled.molang().controllers.advanceOnly(1);
            near(value(cancelled, "v.zeros"), 0);
        }
        System.out.println("Controller functions PASS: actual slots/palette, transition delay, event clocks, "
            + "refresh/reload/reset, native voice cleanup, loop modes and instance/seek isolation");
    }
}
