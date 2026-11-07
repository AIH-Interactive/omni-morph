package top.aihmc.omm.preview;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.joml.Vector3f;
import top.aihmc.omnimorph.model.ysm.YsmFilterRuntime;

/** Compares the actual palette path against the unchanged model filter runtime. */
public final class PreviewYsmFilterTest {
    private static void near(float actual, float expected) {
        if (Math.abs(actual - expected) > 0.0002f) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static float second(YsmFilterRuntime filters, String bone, float input) {
        try (var scope = filters.beginBone(bone)) {
            return filters.secondOrder(YsmFilterRuntime.Domain.PREVIEW, "shared", input, 1, 1, 0);
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("ysm-filters");
        Files.createDirectories(folder);
        for (String name : List.of("main.json", "texture.png")) {
            Files.copy(
                root.resolve("animated-ysm").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("ysm.json"), """
            {"files":{"player":{"model":{"main":"main.json"},"texture":"texture.png",
              "animation":"main.animation.json"}}}
            """);
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{"filtered":{"loop":true,"animation_length":0.25,"bones":{
              "root":{"position":["ysm.first_order('move',v.input,0.5)*16",
                "ysm.second_order('shared',v.input,1,1,0)*16",0]},
              "child":{"position":[0,"ysm.second_order('shared',v.input+1,1,1,0)*16",0]}
            }}}}
            """);
        var config = new PreviewConfig();
        config.configFile = root.resolve("ysm-filters.json");
        config.settings.enabled = false;
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.clip = "filtered";
        config.animation.fixedTimeSeconds = 1f;
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        try (var animation = new PreviewAnimation(PreviewMesh.load(config), config)) {
            var instance = animation.newState();
            var other = animation.newState();
            var reference = new YsmFilterRuntime();
            for (int frame = 0; frame < 120; frame++) {
                float time = frame / 60f;
                float input = frame < 5 ? 0 : 10;
                instance.molang().evaluate(instance.molang().compile("v.input = " + input));
                reference.update(YsmFilterRuntime.Domain.PREVIEW, time * 20, frame, null);
                float x = reference.firstOrder(YsmFilterRuntime.Domain.PREVIEW, "move", input, 0.5f);
                float rootY = second(reference, "root", input);
                float childY = second(reference, "child", input + 1);
                instance.sample(frame, time);
                instance.updateBones();
                var position = instance.palette[1].transformPosition(new Vector3f(0, 1, 0));
                near(position.x, -x);
                near(position.y, 1 + rootY + childY);
                instance.sample(frame, time);
                instance.updateBones();
                near(instance.palette[1].transformPosition(new Vector3f(0, 1, 0)).y, position.y);
            }
            other.sample(0, 0);
            other.updateBones();
            near(other.palette[1].transformPosition(new Vector3f(0, 1, 0)).y, 2);
            instance.sample(120, 0);
            instance.updateBones();
            near(instance.palette[1].transformPosition(new Vector3f(0, 1, 0)).y, 2);
        }
        System.out.println("YSM filters PASS: original first/second order -> palette, bone keys, monotonic render "
                           + "clock across clip loops, frame/instance isolation and seek");
    }
}
