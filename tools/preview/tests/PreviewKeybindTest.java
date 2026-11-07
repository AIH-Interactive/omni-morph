package top.aihmc.omm.preview;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.figuramc.figura.lua.api.keybind.FiguraKeybind;
import org.joml.Vector3f;

/** Original key callbacks, physical input adapter and replay must reach the actual model palette. */
public final class PreviewKeybindTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("figura-keybinds");
        Files.createDirectories(folder);
        for (String name : List.of("main.bbmodel", "blue.png", "orange.png")) {
            Files.copy(
                root.resolve("figura-v4").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("avatar.json"), "{\"autoScripts\":[\"script\"]}");
        Files.writeString(folder.resolve("script.lua"), """
            presses, releases = 0, 0
            local function pressed(modifiers, binding)
              assert(not binding:isPressed())
              last_modifiers = modifiers
              presses = presses + 1
              return true
            end
            local function released(modifiers, binding)
              assert(binding:isPressed())
              releases = releases + 1
            end
            keyboard = keybinds:newKeybind('Keyboard','key.keyboard.k'):onPress(pressed):onRelease(released)
            mouse = keybinds:of('Mouse','key.mouse.left')
            mouse.press, mouse.release = pressed, released
            scan = keybinds:newKeybind('Scan','scancode.99'):onPress(pressed):onRelease(released)
            events.render = function()
              local offset = keyboard:isPressed() and 16 or mouse:isPressed() and 32 or scan:isPressed() and 48 or 0
              models.main.root:setPos(vec(offset,0,0))
            end
            """);
        var config = new PreviewConfig();
        config.settings.enabled = false;
        config.configFile = root.resolve("figura-keybinds.json");
        config.scene.models = List.of(folder.toString());
        config.window.title = "omm-core Figura keybind input";
        config.animation.enabled = true;
        config.animation.mode = "figura";
        config.scripts.enabled = true;
        config.interaction.actions = List.of(key(0, "key-down", "key.keyboard.k"), key(1, "key-down", "key.keyboard.k"),
            key(2, "key-up", "key.keyboard.k"), key(3, "key-down", "key.mouse.left"), key(4, "key-release-all", null),
            key(5, "key-down", "scancode.99"), key(6, "key-up", "scancode.99"));
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        var mesh = PreviewMesh.load(config);
        var shared = new PreviewAnimation(mesh, config);
        var state = shared.newState();
        config.outputDirectory = root.resolve("keybind-recording");
        Files.createDirectories(config.outputDirectory);
        float[] expected = {1, 1, 0, 2, 0, 3, 0};
        try (var interaction = new PreviewInteraction(config)) {
            for (int frame = 0; frame < expected.length; frame++) {
                interaction.update(state.lua(), frame, frame / 60f);
                state.sample(frame, frame / 60f);
                state.updateBones();
                point(mesh, state, expected[frame]);
            }
        }
        if (state.lua().evaluate("return presses,releases").toint(1) != 3
            || state.lua().evaluate("return releases").toint(1) != 3) {
            throw new AssertionError("Duplicate input or release-all lost original callback edge semantics");
        }
        var finalState =
            JsonParser.parseString(Files.readString(config.outputDirectory.resolve("interaction-state.json")));
        config.interaction.actions = List.of();
        config.interaction.replayFile = "keybind-recording/interaction.jsonl";
        config.outputDirectory = root.resolve("keybind-replay");
        Files.createDirectories(config.outputDirectory);
        var replay = shared.newState();
        try (var interaction = new PreviewInteraction(config)) {
            for (int frame = 0; frame < expected.length; frame++) {
                interaction.update(replay.lua(), frame, frame / 60f);
                replay.sample(frame, frame / 60f);
                replay.updateBones();
                point(mesh, replay, expected[frame]);
            }
        }
        if (!finalState.equals(
                JsonParser.parseString(Files.readString(config.outputDirectory.resolve("interaction-state.json"))))) {
            throw new AssertionError("Key replay did not restore actual final snapshot");
        }
        var keyboard = state.lua().keybinds.getKeybinds().get("Keyboard");
        if (!FiguraKeybind.set(state.lua().keybinds.keyBindings, PreviewKeys.parse("key.keyboard.k"), true, 2)
            || !FiguraKeybind.overridesKey(state.lua().keybinds.keyBindings, PreviewKeys.parse("key.keyboard.k"))) {
            throw new AssertionError("Original callback override result was lost");
        }
        FiguraKeybind.releaseAll(state.lua().keybinds.keyBindings);
        if (state.lua().evaluate("return last_modifiers").toint(1) != 2) {
            throw new AssertionError("Input modifier mask was not passed to original callback");
        }
        keyboard.setEnabled(false);
        FiguraKeybind.set(state.lua().keybinds.keyBindings, PreviewKeys.parse("key.keyboard.k"), true, 0);
        if (keyboard.isPressed())
            throw new AssertionError("Disabled keybind accepted input");
        config.states =
            JsonParser
                .parseString(
                    "{\"keybinds\":{\"Keyboard\":true},\"vanillaKeybinds\":{\"key.jump\":\"key.keyboard.space\"}}")
                .getAsJsonObject();
        var held = new PreviewAnimation(mesh, config).newState();
        held.sample(0, 0);
        held.updateBones();
        point(mesh, held, 1);
        held.lua().evaluate("assert(keybinds:fromVanilla('key.jump'):getID() == 32)");
        config.states = JsonParser.parseString("{\"guiOpen\":true}").getAsJsonObject();
        var gui = new PreviewAnimation(mesh, config).newState();
        FiguraKeybind.set(gui.lua().keybinds.keyBindings, PreviewKeys.parse("key.keyboard.k"), true, 0);
        var guiBinding = gui.lua().keybinds.getKeybinds().get("Keyboard");
        if (guiBinding.isPressed())
            throw new AssertionError("Non-GUI binding accepted simulated GUI input");
        guiBinding.gui(true);
        FiguraKeybind.set(gui.lua().keybinds.keyBindings, PreviewKeys.parse("key.keyboard.k"), true, 0);
        if (!guiBinding.isPressed())
            throw new AssertionError("GUI-enabled binding did not accept input");
        if (!PreviewKeys.parse("key.mouse.1").equals(PreviewKeys.parse("key.mouse.left"))) {
            throw new AssertionError("Equivalent mouse identifiers differ");
        }
        for (int code : new int[] {65, 32, 341, 321, 256, 348}) {
            if (PreviewKeys.parse(PreviewKeys.keyboardName(code, 0)).getValue() != code) {
                throw new AssertionError("GLFW key name roundtrip failed for " + code);
            }
        }
        System.out.println("Keybind PASS: original edge callbacks/override, keyboard/mouse/scancode -> palette, "
            + "recording/replay and configured state");
    }

    private static PreviewInteraction.Operation key(long frame, String type, String id) {
        var operation = new PreviewInteraction.Operation();
        operation.frame = frame;
        operation.type = type;
        operation.id = id;
        operation.modifiers = 2;
        return operation;
    }

    private static void point(PreviewMesh mesh, PreviewAnimation.State state, float x) {
        Vector3f bind = new Vector3f(mesh.vertices[0], mesh.vertices[1], mesh.vertices[2]);
        Vector3f actual = state.palette[mesh.owners[0]].transformPosition(new Vector3f(bind));
        if (actual.distance(bind.add(x, 0, 0)) > 0.0003f)
            throw new AssertionError("Key input did not reach model palette: " + actual);
    }
}
