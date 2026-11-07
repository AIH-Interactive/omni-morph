package top.aihmc.omm.preview;

import static org.lwjgl.openal.AL10.*;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/** Checks original YSM sound banks against native source counts, rather than fake playback flags. */
public final class PreviewYsmSoundFunctionTest {
    private static long active(PreviewAudio audio) {
        return ((Number) audio.snapshot().get("activeSources")).longValue();
    }

    private static void count(PreviewAudio audio, int expected) {
        if (active(audio) != expected)
            throw new AssertionError("Expected " + expected + " sources: " + audio.snapshot());
    }

    private static void result(PreviewMolang host, String expression, int expected) throws Exception {
        if (host.evaluate(host.compile(expression)) != expected)
            throw new AssertionError(expression);
    }

    public static void main(String[] args) throws Exception {
        byte[] ogg;
        try (var input = PreviewYsmSoundFunctionTest.class.getResourceAsStream(
                 "/assets/minecraft/sounds/mob/squid/death1.ogg")) {
            ogg = input.readAllBytes();
        }
        try (var audio = new PreviewAudio()) {
            audio.modelBuffer(ogg);
            alListenerf(AL_GAIN, 0);
            var host = new PreviewMolang(new JsonObject(), 42);
            host.effects = new PreviewYsmEffects(audio, null, host,
                new PreviewYsmResources(List.of(), Map.of(), Map.of("beep", ogg)), new JsonObject(), 42);
            result(host, "ysm.play_sound('id', 'beep', 4)", 0);
            count(audio, 0);
            Object owner = new Object();
            try (var scope = host.functions.beginClientEffects(owner)) {
                host.controller.setPath("controller", "first");
                result(host, "ysm.play_sound('id', 'beep', 4)", 1);
                result(host, "ysm.play_sound('id', 'beep', 4)", 0);
                count(audio, 1);
                result(host, "ysm.play_sound('id', 'beep', 5)", 1);
                count(audio, 1);
                host.controller.setPath("controller", "second");
                result(host, "ysm.play_sound('id', 'beep', 4)", 1);
                result(host, "ysm.play_sound('id', 'beep', 6)", 1);
                count(audio, 3);
                result(host, "ysm.stop_sound('id')", 1);
                count(audio, 2);
                result(host, "ysm.stop_sound('id', true)", 1);
                count(audio, 1);
                host.effects.stopController("first");
                count(audio, 0);
                result(host, "ysm.play_sound(-2, 'beep', 4)", 0);
                result(host, "ysm.play_sound('bad', 'beep', 8)", 0);
                result(host, "ysm.play_sound('empty', '')", 0);
                result(host, "ysm.play_sound('missing', 'not_a_sound')", 0);
                result(host, "ysm.play_sound(0, 'beep', 4)", 1);
                result(host, "ysm.play_sound(0, 'beep', 4)", 1);
                result(host, "ysm.stop_sound(0)", 0);
                count(audio, 2);
                result(host, "ysm.stop_all_sounds()", 1);
                count(audio, 0);
                host.controller.setPath("controller", "");
                result(host, "ysm.play_sound('outer', 'beep', 6)", 1);
                Object nestedOwner = new Object();
                try (var nested = host.functions.beginClientEffects(nestedOwner)) {
                    result(host, "ysm.play_sound('inner', 'beep', 6)", 1);
                }
                result(host, "ysm.play_sound('restored', 'beep', 6)", 1);
                host.effects.stopPlayback(owner);
                count(audio, 1);
                host.effects.stopPlayback(nestedOwner);
                count(audio, 0);
            }
            result(host, "ysm.play_sound('outside', 'beep', 4)", 0);
            host.effects.close();
            count(audio, 0);
        }

        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("ysm-function-sounds");
        Files.createDirectories(folder.resolve("functions"));
        Files.createDirectories(folder.resolve("audio"));
        for (String name : List.of("main.json", "texture.png", "ysm.json")) {
            Files.copy(root.resolve("ysm-sound-events").resolve(name), folder.resolve(name),
                StandardCopyOption.REPLACE_EXISTING);
        }
        Files.write(folder.resolve("audio/beep.ogg"), ogg);
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{
              "quiet":{"loop":true,"bones":{"root":{"position":[0,0,0]}}},
              "timeline":{"loop":true,"animation_length":0.25,
                "timeline":{"0":"ysm.play_sound('clip','beep',6,0.01);"},
                "bones":{"root":{"position":[0,0,0]}}}
            }}
            """);
        Files.writeString(folder.resolve("controller.animation.json"), """
            {"animation_controllers":{"controller.player.main":{"initial_state":"on","states":{
              "on":{"on_entry":["ysm.play_sound('entry','beep',4,0.01);"],
                "animations":["quiet"],"transitions":[{"off":"q.is_running"}]},
              "off":{"animations":["quiet"]}
            }}}}
            """);
        Files.writeString(folder.resolve("functions/play@player_ctrl_main.molang"), """
            !v.started ? {v.started = 1; ysm.play_sound('stage','beep',4,0.01);};
            """);
        var config = new PreviewConfig();
        config.settings.enabled = false;
        config.configFile = root.resolve("ysm-function-sounds.json");
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.mode = "controller";
        config.animation.fixedTimeSeconds = 0.1f;
        config.recording.outputDirectory = "runs";
        try (var animation = new PreviewAnimation(PreviewMesh.load(config), config)) {
            animation.audio.modelBuffer(ogg);
            alListenerf(AL_GAIN, 0);
            var first = animation.newState();
            var second = animation.newState();
            first.sample(0, 0);
            count(animation.audio, 2);
            second.sample(0, 0);
            count(animation.audio, 4);
            first.sample(1, 1f / 60);
            count(animation.audio, 2);
            second.sample(1, 1f / 60);
            count(animation.audio, 0);
            first.sample(2, 0);
            count(animation.audio, 2);
        }
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        config.animation.mode = "clip";
        config.animation.clip = "timeline";
        try (var animation = new PreviewAnimation(PreviewMesh.load(config), config)) {
            animation.audio.modelBuffer(ogg);
            alListenerf(AL_GAIN, 0);
            var instance = animation.newState();
            instance.sample(0, 0);
            count(animation.audio, 1);
            instance.sample(1, 0.25f);
            count(animation.audio, 1);
            instance.sample(2, 0.251f);
            count(animation.audio, 1);
            instance.sample(3, 0);
            count(animation.audio, 1);
        }
        System.out.println("YSM sound functions PASS: native ID/flag banks, local/global/anonymous scope, nested "
            + "owners, controller exit and seek");
    }
}
