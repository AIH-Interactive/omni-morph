package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.joml.Vector3f;

/** Persisted values must survive fresh runtimes/processes and affect actual YSM/Figura palettes. */
public final class PreviewSettingsTest {
    private static PreviewConfig figura(Path root, Path folder, Path directory, String key) {
        var config = new PreviewConfig();
        config.configFile = root.resolve("settings-figura.json");
        config.settings.directory = directory.toString();
        config.settings.modelKey = key;
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.mode = "figura";
        config.scripts.enabled = true;
        config.vanilla.enabled = false;
        config.camera.distanceScale = 7;
        config.recording.outputDirectory = "runs";
        return config;
    }

    private static PreviewAnimation.State sample(PreviewAnimation animation, int frame) {
        var state = animation.newState();
        state.sample(frame, 0);
        state.updateBones();
        return state;
    }

    private static void offset(PreviewMesh mesh, PreviewAnimation.State state, float pixels) {
        var bind = new Vector3f(mesh.vertices[0], mesh.vertices[1], mesh.vertices[2]);
        var actual = state.palette[mesh.owners[0]].transformPosition(new Vector3f(bind));
        if (actual.distance(bind.add(pixels / 16, 0, 0)) > 0.0002f) {
            throw new AssertionError("Saved setting did not affect actual submitted Figura palette: " + actual);
        }
    }

    private static Path settingsFile(PreviewAnimation animation) {
        return Path.of((String) animation.settings.snapshot().get("file"));
    }

    private static String key(String id) {
        return PreviewTextures.sha256(id.getBytes(StandardCharsets.UTF_8));
    }

    private static PreviewInteraction.Operation set(String id, double value) {
        var operation = new PreviewInteraction.Operation();
        operation.frame = 0;
        operation.type = "set-control";
        operation.id = id;
        operation.value = new JsonPrimitive(value);
        return operation;
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path directory = Files.createTempDirectory(root, "settings-cache-");
        Path folder = root.resolve("figura-settings");
        Files.createDirectories(folder);
        for (String name : List.of("main.bbmodel", "blue.png", "orange.png")) {
            Files.copy(
                root.resolve("figura-v4").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("avatar.json"), "{\"autoScripts\":[\"script\"]}");
        Files.writeString(folder.resolve("script.lua"), """
            local control = avatar_controls:newNumber('offset'):range(0,64,1):setDefault(16)
            local offset = control:getValue()
            control:onChange(function(value) offset = value end)
            avatar_controls:newEnum('choice'):options({'first','second'}):setDefault('first')
            avatar_controls:newNumber('ephemeral'):range(0,64,1):setDefault(3):persistent(false)
            avatar_controls:newNumber('never_saved'):range(0,64,1):setDefault(7):persistent(false)
            avatar_controls:newButton('button'):onPress(function() end)
            local saveKey = keybinds:newKeybind('save on close','key.keyboard.k')
            saveKey.release = function()
              avatar_controls:set('offset',24)
              sounds:playSound('minecraft:entity.squid.death',vec(0,0,0),0.01,1,false)
            end
            events.render = function()
              models.main.root:setPos(offset,0,0)
              if preview.expectedOffset then
                assert(offset == preview.expectedOffset and models.main.root:getPos().x == offset)
              end
            end
            """);
        var config = figura(root, folder, directory, "figura-profile");
        var mesh = PreviewMesh.load(config);
        Path file;
        try (var first = new PreviewAnimation(mesh, config)) {
            var a = sample(first, 0);
            var b = sample(first, 0);
            file = settingsFile(first);
            if (Files.exists(file))
                throw new AssertionError("Loading defaults wrote preferences before any change");
            a.lua().evaluate("""
                avatar_controls:set('offset',32)
                avatar_controls:set('choice','second')
                avatar_controls:getControl('ephemeral'):persistent(true)
                avatar_controls:set('ephemeral',18)
                avatar_controls:set('never_saved',22)
                avatar_controls:set('button',1)
                """);
            a.sample(1, 0);
            a.updateBones();
            offset(mesh, a, 32);
            b.sample(1, 0);
            b.updateBones();
            offset(mesh, b, 16);
            var values = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("controls");
            if (values.has(key("never_saved")) || values.has(key("button")) || values.size() != 3) {
                throw new AssertionError("Non-persistent control/button was written");
            }
            a.lua().evaluate(
                "avatar_controls:onChange(function(id) return id == 'offset' end); avatar_controls:set('offset',40)");
            if (JsonParser.parseString(Files.readString(file))
                    .getAsJsonObject()
                    .getAsJsonObject("controls")
                    .getAsJsonObject(key("offset"))
                    .get("value")
                    .getAsDouble()
                != 32) {
                throw new AssertionError("Intercepted change persisted before model accepted it");
            }
        }
        try (var restarted = new PreviewAnimation(mesh, config)) {
            var state = sample(restarted, 0);
            offset(mesh, state, 32);
            state.lua().evaluate("""
                assert(avatar_controls:get('choice') == 'second')
                assert(avatar_controls:get('ephemeral') == 3 and avatar_controls:get('never_saved') == 7)
                """);
            state.sample(1, 1);
            state.sample(2, 0);
            state.updateBones();
            offset(mesh, state, 32);
        }
        var isolatedConfig = figura(root, folder, directory, "other-model");
        try (var isolated = new PreviewAnimation(mesh, isolatedConfig)) {
            offset(mesh, sample(isolated, 0), 16);
        }
        config.scripts.controls.addProperty("offset", 48);
        try (var overridden = new PreviewAnimation(mesh, config)) {
            var state = sample(overridden, 0);
            offset(mesh, state, 48);
            state.lua().controls.reset(state.lua(), "offset");
        }
        config.scripts.controls = new JsonObject();
        try (var reset = new PreviewAnimation(mesh, config)) {
            var state = sample(reset, 0);
            offset(mesh, state, 16);
            state.lua().controls.setValue(state.lua(), "offset", 32);
        }
        Path zip = root.resolve("figura-settings.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(zip)); var paths = Files.walk(folder)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new ZipEntry("avatar/" + folder.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, output);
                output.closeEntry();
            }
        }
        var zippedConfig = figura(root, zip, directory, "figura-profile");
        try (var zipped = new PreviewAnimation(PreviewMesh.load(zippedConfig), zippedConfig)) {
            offset(mesh, sample(zipped, 0), 32);
        }
        String saved = Files.readString(file);
        config.benchmark.enabled = true;
        try (var benchmark = new PreviewAnimation(mesh, config)) {
            var state = sample(benchmark, 0);
            offset(mesh, state, 16);
            state.lua().controls.setValue(state.lua(), "offset", 60);
            if (!benchmark.settings.snapshot().get("mode").equals("benchmark-disabled")) {
                throw new AssertionError("Benchmark did not explicitly disable persistence");
            }
        }
        if (!Files.readString(file).equals(saved))
            throw new AssertionError("Benchmark changed user preferences");
        config.benchmark.enabled = false;

        var ysm = PreviewConfig.load(new String[] {"--config", root.resolve("ysm-declared-actions.json").toString()});
        ysm.settings.enabled = true;
        ysm.settings.directory = directory.toString();
        ysm.settings.modelKey = "ysm-profile";
        ysm.interaction.enabled = false;
        var ysmMesh = PreviewMesh.load(ysm);
        float determinant;
        try (var animation = new PreviewAnimation(ysmMesh, ysm)) {
            var state = sample(animation, 0);
            int owner = ysmMesh.owners[0];
            float baseline = state.palette[owner].determinant();
            state.lua().controls.setValue(state.lua(), "ysm.width_scale", 2);
            state.sample(1, 0);
            state.updateBones();
            determinant = state.palette[owner].determinant();
            if (Math.abs(baseline) < 0.0001 || Math.abs(determinant - baseline * 4) > 0.0001) {
                throw new AssertionError("YSM persistent setting did not scale real palette");
            }
        }
        try (var restored = new PreviewAnimation(ysmMesh, ysm)) {
            var state = sample(restored, 0);
            if (Math.abs(state.palette[ysmMesh.owners[0]].determinant() - determinant) > 0.0001) {
                throw new AssertionError("Restored YSM width differs from saved actual pose");
            }
            var choice = new top.aihmc.omnimorph.avatar.control.AvatarControlDefinition(
                "ysm.form.private-expression", top.aihmc.omnimorph.avatar.control.AvatarControlType.ENUM);
            choice.addOption("private-first").addOption("private-second");
            restored.settings.save(choice.setValue("private-second"));
            String privateSaved = Files.readString(settingsFile(restored));
            if (privateSaved.contains("private-expression") || privateSaved.contains("private-second")) {
                throw new AssertionError("YSM preference exposed a private form expression/label");
            }
        }

        config.interaction.actions = List.of(set("offset", 40));
        config.states.addProperty("expectedOffset", 40);
        config.window.title = "omm-core saved Figura settings write";
        Files.writeString(root.resolve("settings-figura-write.json"), PreviewConfig.JSON.toJson(config));
        config.interaction.actions = List.of();
        config.scene.models = List.of(zip.toString());
        config.window.title = "omm-core saved Figura settings restore from ZIP";
        Files.writeString(root.resolve("settings-figura-read.json"), PreviewConfig.JSON.toJson(config));
        ysm.animation.fixedTimeSeconds = 0f;
        ysm.interaction.actions = List.of(set("ysm.width_scale", 2.5));
        ysm.window.title = "omm-core saved YSM settings write";
        Files.writeString(root.resolve("settings-ysm-write.json"), PreviewConfig.JSON.toJson(ysm));
        ysm.interaction.actions = List.of();
        ysm.window.title = "omm-core saved YSM settings restore";
        Files.writeString(root.resolve("settings-ysm-read.json"), PreviewConfig.JSON.toJson(ysm));
        var release = figura(root, folder, directory, "release-profile");
        var held = new PreviewInteraction.Operation();
        held.frame = 0;
        held.type = "key-down";
        held.id = "key.keyboard.k";
        release.interaction.actions = List.of(held);
        release.states.addProperty("expectedOffset", 16);
        release.window.title = "omm-core held-key release persists before cleanup";
        Files.writeString(root.resolve("settings-release.json"), PreviewConfig.JSON.toJson(release));
        System.out.println("Settings PASS: disk restart -> actual Figura/YSM palettes, callbacks, ranges, enum "
            + "privacy, owner isolation, reset/override and benchmark suppression");
    }
}
