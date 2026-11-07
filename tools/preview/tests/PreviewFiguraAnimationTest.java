package top.aihmc.omm.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.joml.Vector3f;

/** Original Figura keyframe playback must reach the palette used by the real renderer. */
public final class PreviewFiguraAnimationTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("figura-keyframes");
        Files.createDirectories(folder);
        for (String name : List.of("blue.png", "orange.png")) {
            Files.copy(
                root.resolve("figura-v4").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        JsonObject model =
            JsonParser.parseString(Files.readString(root.resolve("figura-v4/main.bbmodel"))).getAsJsonObject();
        JsonArray animations = new JsonArray();
        for (String interpolation : List.of("linear", "step", "catmullrom", "bezier")) {
            JsonObject animation = JsonParser.parseString("""
                {"name":"move","loop":"loop","length":1,"animators":{
                  "00000000-0000-0000-0000-000000000003":{"name":"root","type":"bone","keyframes":[]},
                  "effects":{"type":"effect","keyframes":[{"channel":"timeline","time":0.1,
                    "data_points":[{"script":"timeline_count = timeline_count + 1"}]}]}
                }}
                """).getAsJsonObject();
            animation.addProperty("name", interpolation);
            JsonArray frames = animation.getAsJsonObject("animators")
                                   .getAsJsonObject("00000000-0000-0000-0000-000000000003")
                                   .getAsJsonArray("keyframes");
            int count = interpolation.equals("catmullrom") ? 4 : 2;
            animation.addProperty("length", count - 1);
            for (int index = 0; index < count; index++) {
                JsonObject frame = JsonParser.parseString("""
                    {"channel":"position","time":0,"interpolation":"linear","data_points":[{"x":"0","y":"0","z":"0"}]}
                    """).getAsJsonObject();
                frame.addProperty("time", index);
                frame.addProperty("interpolation", interpolation);
                frame.getAsJsonArray("data_points")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("x", Integer.toString(index * 16));
                frames.add(frame);
            }
            animations.add(animation);
        }
        model.add("animations", animations);
        JsonObject expression = animations.get(0).getAsJsonObject().deepCopy();
        expression.addProperty("name", "expression");
        for (var value : expression.getAsJsonObject("animators")
                 .getAsJsonObject("00000000-0000-0000-0000-000000000003")
                 .getAsJsonArray("keyframes")) {
            value.getAsJsonObject()
                .getAsJsonArray("data_points")
                .get(0)
                .getAsJsonObject()
                .addProperty("x", "return 16");
        }
        animations.add(expression);
        Files.writeString(folder.resolve("main.bbmodel"), model.toString());
        Files.writeString(folder.resolve("avatar.json"), "{\"autoScripts\":[\"script\"]}");
        Files.writeString(folder.resolve("script.lua"), """
            timeline_count = 0
            animations.main.linear:play()
            local page = action_wheel:newPage('Figura 动画')
            action_wheel:setPage(page)
            page:newAction(1):title('播放/暂停'):onLeftClick(function()
              if animations.main.linear:isPlaying() then animations.main.linear:pause()
              else animations.main.linear:play() end
            end)
            """);
        PreviewConfig config = new PreviewConfig();
        config.settings.enabled = false;
        config.configFile = root.resolve("figura-keyframes.json");
        config.scene.models = List.of(folder.toString());
        config.window.title = "omm-core Figura keyframe playback";
        config.animation.enabled = true;
        config.animation.mode = "figura";
        config.scripts.enabled = true;
        config.interaction.enabled = true;
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        PreviewMesh mesh = PreviewMesh.load(config);
        var shared = new PreviewAnimation(mesh, config);
        var state = shared.newState();
        sample(state, 0, 0);
        sample(state, 1, 0.25f);
        displacement(mesh, state, -0.25f, "Linear playback");
        if (state.lua().evaluate("return timeline_count").toint(1) != 1) {
            throw new AssertionError("Actual timeline Lua did not execute exactly once");
        }
        state.lua().wheelState(true, 0);
        state.lua().executeWheel(0, true);
        sample(state, 2, 0.5f);
        displacement(mesh, state, -0.25f, "Wheel pause");
        var paused = PreviewInteraction.snapshot(state.lua()).animations().get("main.linear");
        if (!paused.state().equals("PAUSED") || Math.abs(paused.time() - 0.25f) > 0.0002f) {
            throw new AssertionError("Interaction snapshot lost actual Figura playback state");
        }
        state.lua().executeWheel(0, true);
        sample(state, 3, 0.75f);
        displacement(mesh, state, -0.5f, "Resume excludes pause interval");
        state.lua().evaluate("animations:stopAll(); animations.main.linear:speed(-1):restart()");
        sample(state, 4, 1);
        displacement(mesh, state, -0.75f, "Reverse playback starts at clip length");
        for (String interpolation : List.of("linear", "step", "catmullrom", "bezier")) {
            float time = interpolation.equals("catmullrom") ? 1.5f : 0.5f;
            state.lua().evaluate(
                "animations:stopAll(); animations.main." + interpolation + ":setTime(" + time + "):pause()");
            sample(state, 5, 1);
            displacement(mesh, state, interpolation.equals("step") ? 0 : -time, interpolation);
        }
        state.lua().evaluate("animations:stopAll(); animations.main.linear:blend(0.5):setTime(0.5):pause(); "
            + "animations.main.bezier:blend(0.5):setTime(0.5):pause()");
        sample(state, 6, 1);
        displacement(mesh, state, -0.5f, "Equal-priority clips merge");
        state.lua().evaluate("animations.main.bezier:priority(1):blend(1):setTime(0.75)");
        sample(state, 7, 1);
        // Symmetric zero-valued Bezier handles with default time handles do not give linear time at .75.
        // Use the linear clip as the higher-priority override for an exact, independent expected point.
        state.lua().evaluate("animations.main.linear:priority(2):blend(1):setTime(0.25)");
        sample(state, 8, 1);
        displacement(mesh, state, -0.25f, "Higher priority replaces lower contribution");
        var isolated = shared.newState();
        sample(isolated, 0, 0.25f);
        displacement(mesh, isolated, -0.25f, "Independent animation clocks");
        sample(state, 0, 0.25f);
        displacement(mesh, state, -0.25f, "Seek resets animation and Lua state");
        var sought = shared.newState();
        sample(sought, 0, 2.25f);
        displacement(mesh, sought, -0.25f, "First fixed-time seek must replay multiple loops");
        if (sought.lua().evaluate("return timeline_count").toint(1) != 3) {
            throw new AssertionError("Fixed-time seek lost timeline events across loops");
        }
        var holding = shared.newState();
        holding.lua().evaluate("animations.main.linear:loop('hold')");
        sample(holding, 0, 1.25f);
        displacement(mesh, holding, -1, "Hold retains endpoint");
        if (!holding.lua().evaluate("return animations.main.linear:isHolding()").toboolean(1)) {
            throw new AssertionError("Hold did not report its original play state");
        }
        var once = shared.newState();
        once.lua().evaluate("animations.main.linear:loop('once')");
        sample(once, 0, 1.25f);
        displacement(mesh, once, 0, "Once clears contribution after stop");
        if (!once.lua().evaluate("return animations.main.linear:isStopped()").toboolean(1)) {
            throw new AssertionError("Once did not stop");
        }
        var expressions = shared.newState();
        expressions.lua().evaluate("animations:stopAll(); animations.main.expression:play()");
        sample(expressions, 0, 0.25f);
        displacement(mesh, expressions, -1, "Keyframe Lua block compilation and execution");
        Path archive = root.resolve("figura-keyframes.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String name : List.of("avatar.json", "main.bbmodel", "blue.png", "orange.png", "script.lua")) {
                zip.putNextEntry(new ZipEntry("nested/avatar/" + name));
                Files.copy(folder.resolve(name), zip);
                zip.closeEntry();
            }
        }
        config.scene.models = List.of(archive.toString());
        var zipped = new PreviewAnimation(PreviewMesh.load(config), config).newState();
        sample(zipped, 0, 0.25f);
        displacement(mesh, zipped, -0.25f, "Nested ZIP animation and actual Lua source");
        config.scripts.enabled = false;
        config.animation.clip = "main.linear";
        var selected = new PreviewAnimation(PreviewMesh.load(config), config).newState();
        sample(selected, 0, 0.05f);
        displacement(mesh, selected, -0.05f, "Explicit clip playback without enabling model Lua scripts");
        System.out.println("Figura animation PASS: original clips/interpolations, Lua timeline, wheel pause/reverse, "
            + "blend/priority and isolation");
    }

    private static void sample(PreviewAnimation.State state, long frame, float time) {
        state.sample(frame, time);
        state.updateBones();
    }

    private static void displacement(PreviewMesh mesh, PreviewAnimation.State state, float x, String label) {
        Vector3f original = new Vector3f(mesh.vertices[0], mesh.vertices[1], mesh.vertices[2]);
        Vector3f actual = state.palette[mesh.owners[0]].transformPosition(new Vector3f(original));
        if (actual.distance(original.add(x, 0, 0)) > 0.0003f) {
            throw new AssertionError(label + ": " + actual);
        }
    }
}
