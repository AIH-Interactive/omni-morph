package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.joml.Vector3f;

/** Named functions and controller events must affect the actual submitted skeleton. */
public final class PreviewYsmFunctionTest {
    private static void equal(float actual, float expected) {
        if (Math.abs(actual - expected) > 0.0001f) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) throws Exception {
        var host = new PreviewMolang(new JsonObject(), 42);
        var sources = new LinkedHashMap<String, String>();
        sources.put("inner.molang", "t.local = 9; return args[0] * 2;");
        sources.put("outer.molang", "t.local = 3; t.result = fn.inner(args[0]); return t.result + t.local;");
        sources.put("first@manual.molang", "v.result = args[0];");
        sources.put("second@manual.molang", "v.result = v.result + 2;");
        sources.put("recursive.molang", "return fn.recursive();");
        sources.put("broken.molang", "return q.missing_preview_value;");
        host.functions = new PreviewYsmFunctions(host, sources);
        equal(host.evaluate(host.compile("fn.outer(5)")), 13);
        equal(host.evaluate(host.compile("ysm.run_event('manual', 6); return v.result;")), 8);
        for (String name : List.of("recursive", "broken", "unknown")) {
            try {
                host.evaluate(host.compile("fn." + name + "()"));
                throw new AssertionError("Function failure was swallowed: " + name);
            } catch (IllegalStateException | IllegalArgumentException | UnsupportedOperationException expected) {
                // The following invocation proves argument/temp/call-stack cleanup after failure.
            }
            equal(host.evaluate(host.compile("fn.outer(5)")), 13);
        }

        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("ysm-functions");
        Files.createDirectories(folder.resolve("functions"));
        for (String name : List.of("main.json", "texture.png")) {
            Files.copy(
                root.resolve("animated-ysm").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("ysm.json"), """
            {"files":{"player":{"model":{"main":"main.json"},"texture":"texture.png",
              "animation":"main.animation.json","animation_controllers":"controller.animation.json"}}}
            """);
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{"move":{"loop":true,"bones":{"root":{"position":[0,0,0]},
              "child":{"position":[0,"v.stage * 16",0]}}}}}
            """);
        Files.writeString(folder.resolve("controller.animation.json"), """
            {"animation_controllers":{"controller.player.main":{"initial_state":"default","states":{
              "default":{"on_entry":["v.entered = v.stage;"],"animations":["move"]}
            }}}}
            """);
        Files.writeString(folder.resolve("functions/update@player_ctrl_main.molang"), "v.stage = v.stage + 1;");
        Files.writeString(folder.resolve("functions/pre@player_ctrl_pre_main.molang"), "v.pre = v.pre + 1;");
        var config = new PreviewConfig();
        config.configFile = root.resolve("ysm-functions.json");
        config.settings.enabled = false;
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.mode = "controller";
        config.animation.fixedTimeSeconds = 0f;
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        var mesh = PreviewMesh.load(config);
        if (mesh.ysmResources.functions().size() != 2)
            throw new AssertionError("Functions were lost at load");
        try (var animation = new PreviewAnimation(mesh, config)) {
            var first = animation.newState();
            var second = animation.newState();
            first.sample(0, 0);
            first.updateBones();
            equal(first.molang().evaluate(first.molang().compile("v.entered")), 1);
            equal(first.molang().evaluate(first.molang().compile("v.pre")), 1);
            int child = java.util.Arrays.asList(mesh.skeleton.names).indexOf("child");
            var position = first.palette[child].transformPosition(new Vector3f(0, 1, 0));
            equal(position.y, 2);
            first.sample(1, 1f / 60);
            equal(first.molang().evaluate(first.molang().compile("v.stage")), 2);
            second.sample(0, 0);
            equal(second.molang().evaluate(second.molang().compile("v.stage")), 1);
            first.sample(2, 0);
            equal(first.molang().evaluate(first.molang().compile("v.stage")), 1);
        }
        Path zip = root.resolve("ysm-functions.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(zip)); var files = Files.walk(folder)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(
                    new ZipEntry("nested/model/" + folder.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, output);
                output.closeEntry();
            }
        }
        config.scene.models = List.of(zip.toString());
        var zipped = PreviewMesh.load(config);
        if (!zipped.ysmResources.functions().equals(mesh.ysmResources.functions())) {
            throw new AssertionError("Nested ZIP functions differ");
        }
        config.configFile = root.resolve("ysm-functions-zip.json");
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        var raw = new top.aihmc.omnimorph.avatar.ysm.binary.YsmBinaryModel();
        var rawFunction = new top.aihmc.omnimorph.avatar.ysm.binary.YsmBinaryModel.RawDataFile();
        rawFunction.data = "return 4;".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        raw.functionFiles.put("kept.molang", rawFunction);
        var retained = PreviewYsmResources.binary(raw);
        raw.close();
        if (!"return 4;".equals(retained.functions().get("kept.molang"))) {
            throw new AssertionError("Function source was cleared with binary parser input");
        }
        System.out.println(
            "YSM functions PASS: args/nested temps/return/errors, controller stage -> palette, seek/isolation and ZIP");
    }
}
