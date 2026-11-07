package top.aihmc.omm.preview;

import static org.lwjgl.openal.AL10.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.joml.Vector3f;

/** YSM controller/clip/action events must own actual native voices even with model Lua disabled. */
public final class PreviewYsmSoundTest {
    private static long count(PreviewAnimation animation, String name) {
        return ((Number) animation.audio.snapshot().get(name)).longValue();
    }

    private static void openMuted(PreviewAnimation animation, byte[] ogg) {
        animation.audio.modelBuffer(ogg);
        alListenerf(AL_GAIN, 0);
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("ysm-sound-events");
        Files.createDirectories(folder.resolve("audio"));
        for (String name : List.of("main.json", "texture.png")) {
            Files.copy(
                root.resolve("animated-ysm").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        byte[] ogg;
        try (var input =
                 PreviewYsmSoundTest.class.getResourceAsStream("/assets/minecraft/sounds/mob/squid/death1.ogg")) {
            ogg = input.readAllBytes();
        }
        Files.write(folder.resolve("audio/beep.ogg"), ogg);
        Files.writeString(folder.resolve("ysm.json"), """
            {"files":{"player":{"model":{"main":"main.json"},"texture":"texture.png",
             "animation":"main.animation.json","animation_controllers":"controller.animation.json"}}}
            """);
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{
              "tone":{"loop":true,"animation_length":0.25,
                "sound_effects":{"0":{"effect":"beep","volume":0.01,"loop":true,"offset_x":1}},
                "timeline":{"0.105":"v.event_time = ctrl.anim_time;"},
                "bones":{"root":{"position":[16,0,0]},"child":{"position":[0,"v.event_time*16",0]}}},
              "quiet":{"loop":true,"animation_length":1,"bones":{"root":{"position":[0,0,0]}}}
            }}
            """);
        Files.writeString(folder.resolve("controller.animation.json"), """
            {"animation_controllers":{"controller.player.main":{"initial_state":"on","states":{
              "on":{"sound_effects":["beep"],"animations":["tone"],"transitions":[{"off":"q.is_running"}]},
              "off":{"animations":["quiet"]}
            }}}}
            """);
        var config = new PreviewConfig();
        config.settings.enabled = false;
        config.configFile = root.resolve("ysm-sound-events.json");
        config.window.title = "omm-core YSM controller sound events";
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.mode = "controller";
        config.recording.outputDirectory = "runs";
        var mesh = PreviewMesh.load(config);
        if (!java.util.Arrays.equals(ogg, mesh.ysmResources.audio().get("beep"))) {
            throw new AssertionError("Model sound stem or original bytes were lost");
        }
        try (var animation = new PreviewAnimation(mesh, config)) {
            openMuted(animation, ogg);
            var a = animation.newState();
            var b = animation.newState();
            a.sample(0, 0);
            a.updateBones();
            if (a.lua() != null || count(animation, "playCalls") != 2 || count(animation, "activeSources") != 2) {
                throw new AssertionError("Controller/state sound was skipped or required a fake Lua script host");
            }
            var point = a.palette[mesh.owners[0]].transformPosition(
                new Vector3f(mesh.vertices[0], mesh.vertices[1], mesh.vertices[2]));
            if (Math.abs(point.x - (mesh.vertices[0] - 1)) > 0.0001) {
                throw new AssertionError("Sound-bearing clip did not actually reach the model palette");
            }
            b.sample(0, 0);
            if (count(animation, "activeSources") != 4)
                throw new AssertionError("Instances shared sound ownership");
            a.sample(1, 1f / 60);
            if (count(animation, "activeSources") != 2)
                throw new AssertionError("State exit did not stop only its own voices");
            b.sample(1, 1f / 60);
            if (count(animation, "activeSources") != 0)
                throw new AssertionError("Second state's voices survived exit");
            a.sample(2, 0);
            a.sample(3, 1f / 60);
            if (count(animation, "playCalls") != 6 || count(animation, "activeSources") != 0) {
                throw new AssertionError("Backward seek did not recreate and retire controller audio correctly");
            }
        }

        config.states.addProperty("running", false);
        config.animation.fixedTimeSeconds = 0f;
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        try (var animation = new PreviewAnimation(mesh, config)) {
            openMuted(animation, ogg);
            var state = animation.newState();
            state.sample(0, 0);
            state.sample(1, 0.25f);
            state.updateBones();
            var eventPoint = state.palette[1].transformPosition(new Vector3f(0, 1, 0));
            if (Math.abs(eventPoint.y - (1 + 7f / 60)) > 0.0001) {
                throw new AssertionError("Timeline ctrl.anim_time did not retain the original playback-step context");
            }
            if (count(animation, "playCalls") != 2)
                throw new AssertionError("Loop wrapped at the exact clip end");
            state.sample(2, 0.251f);
            if (count(animation, "playCalls") != 3)
                throw new AssertionError("Loop did not reset and replay its zero sound once");
        }

        // A named clip uses the same event host without requiring action schemas, controllers or Lua.
        config.animation.mode = "clip";
        config.animation.clip = "tone";
        try (var animation = new PreviewAnimation(mesh, config)) {
            openMuted(animation, ogg);
            var state = animation.newState();
            state.sample(0, 0);
            state.sample(1, 0.251f);
            if (count(animation, "playCalls") != 2 || count(animation, "activeSources") != 1) {
                throw new AssertionError("Named clip loop did not retire its previous playback voice");
            }
        }

        config.animation.actions = true;
        try (var animation = new PreviewAnimation(mesh, config)) {
            openMuted(animation, ogg);
            var state = animation.newState();
            state.sample(0, 0);
            state.lua().ysm().animations().stop("tone");
            if (count(animation, "activeSources") != 0)
                throw new AssertionError("Action stop kept its playback sound alive");
            state.lua().ysm().animations().play("tone", true, 1);
            if (count(animation, "playCalls") != 2 || count(animation, "activeSources") != 1) {
                throw new AssertionError("Action restart did not create actual playback");
            }
        }

        Path zip = root.resolve("ysm-sound-events.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(zip)); var paths = Files.walk(folder)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new ZipEntry("avatar/" + folder.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, output);
                output.closeEntry();
            }
        }
        config.animation.actions = false;
        config.animation.mode = "controller";
        config.scene.models = List.of(zip.toString());
        config.window.title = "omm-core YSM ZIP state and clip sounds";
        Files.writeString(root.resolve("ysm-sound-events-zip.json"), PreviewConfig.JSON.toJson(config));
        var zipMesh = PreviewMesh.load(config);
        if (!java.util.Arrays.equals(ogg, zipMesh.ysmResources.audio().get("beep"))) {
            throw new AssertionError("Nested ZIP audio differs from folder");
        }
        var raw = new top.aihmc.omnimorph.avatar.ysm.binary.YsmBinaryModel();
        var rawSound = new top.aihmc.omnimorph.avatar.ysm.binary.YsmBinaryModel.RawDataFile();
        rawSound.data = ogg.clone();
        raw.soundFiles.put("audio/beep.ogg", rawSound);
        var retained = PreviewYsmResources.binary(raw);
        raw.close();
        if (!java.util.Arrays.equals(ogg, retained.audio().get("beep"))) {
            throw new AssertionError("Encrypted-model audio snapshot was cleared with owned parser input");
        }
        System.out.println("YSM sounds PASS: actual controller/state/clip/action voices, loop/reset/exit/seek "
            + "ownership, no-Lua path and nested ZIP resources");
    }
}
