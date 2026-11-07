package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import org.joml.Vector3f;

/** Original local @sync callbacks must run with actual args and reach the model pose. */
public final class PreviewYsmSyncTest {
    private static float eval(PreviewMolang host, String source) throws Exception {
        return host.evaluate(host.compile(source));
    }

    private static void near(float actual, float expected) {
        if (Math.abs(actual - expected) > 0.0001f)
            throw new AssertionError(actual + " != " + expected);
    }

    public static void main(String[] args) throws Exception {
        var host = new PreviewMolang(new JsonObject(), 42);
        var functions = new LinkedHashMap<String, String>();
        functions.put("double.molang", "return args[0] * 2;");
        functions.put("receive@sync.molang", "v.calls = v.calls + 1; v.value = fn.double(args[0]) + args[1];");
        host.functions = new PreviewYsmFunctions(host, functions);
        eval(host, "ysm.sync(3, 4)");
        near(eval(host, "v.calls"), 0);
        try (var scope = host.functions.beginClientEffects(new Object())) {
            eval(host, "ysm.sync(3, 4)");
            near(eval(host, "v.value"), 10);
            near(eval(host, "v.calls"), 1);
            host.variables.setPath("nonfinite", Float.POSITIVE_INFINITY);
            eval(host, "ysm.sync(v.nonfinite, 4)");
            near(eval(host, "v.calls"), 1);
            for (int i = 0; i < 20; i++) eval(host, "ysm.sync(1, 2)");
            if (host.functions.recentSyncKeys().size() != 16)
                throw new AssertionError("Unbounded local sync history");
        }
        if (!host.functions.currentArguments().isEmpty())
            throw new AssertionError("Sync argument scope leaked");

        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("ysm-equipment-sync");
        Files.createDirectories(folder.resolve("functions"));
        for (String name : List.of("main.json", "texture.png", "ysm.json")) {
            Files.copy(
                root.resolve("ysm-functions").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{"move":{"loop":true,"bones":{
              "root":{"position":[0,0,0]},"child":{"position":[0,"v.pose*16",0]}
            }}}}
            """);
        Files.writeString(folder.resolve("controller.animation.json"), """
            {"animation_controllers":{"controller.player.main":{"states":{
              "default":{"animations":["move"]}
            }}}}
            """);
        Files.writeString(
            folder.resolve("functions/send@player_ctrl_main.molang"), "ysm.sync(ctrl.use('mainhand', ':bow'), 7);");
        Files.writeString(
            folder.resolve("functions/receive@sync.molang"), "v.pose = args[0] + args[1]; v.calls = v.calls + 1;");
        var config = new PreviewConfig();
        config.configFile = root.resolve("ysm-equipment-sync.json");
        config.settings.enabled = false;
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.mode = "controller";
        config.animation.fixedTimeSeconds = 0f;
        config.recording.outputDirectory = "runs";
        config.states = JsonParser.parseString("""
            {"equipment":{"mainhand":{"id":"minecraft:bow","types":["bow"],"using":true}}}
            """).getAsJsonObject();
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        var mesh = PreviewMesh.load(config);
        try (var animation = new PreviewAnimation(mesh, config)) {
            var first = animation.newState();
            var second = animation.newState();
            first.sample(0, 0);
            first.updateBones();
            near(first.palette[1].transformPosition(new Vector3f(0, 1, 0)).y, 9);
            near(eval(first.molang(), "v.calls"), 1);
            near(eval(second.molang(), "v.calls"), 0);
            first.sample(1, 0.1f);
            first.sample(2, 0);
            near(eval(first.molang(), "v.calls"), 1);
        }
        config.states.getAsJsonObject("equipment").getAsJsonObject("mainhand").addProperty("using", false);
        try (var animation = new PreviewAnimation(mesh, config)) {
            var unused = animation.newState();
            unused.sample(0, 0);
            unused.updateBones();
            near(unused.palette[1].transformPosition(new Vector3f(0, 1, 0)).y, 8);
        }
        System.out.println("YSM local sync PASS: effect gate, finite float args, nested functions, bounded history, "
                           + "configured equipment -> controller -> @sync -> palette and isolation/seek");
    }
}
