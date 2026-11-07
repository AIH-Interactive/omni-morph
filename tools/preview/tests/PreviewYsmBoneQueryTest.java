package top.aihmc.omm.preview;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.joml.Vector3f;

/** Bone Structs are live outside evaluation and stable previous-frame views during pose sampling. */
public final class PreviewYsmBoneQueryTest {
    private static float value(PreviewAnimation.State state, String source) throws Exception {
        return state.molang().evaluate(state.molang().compile(source));
    }

    private static void near(float actual, float expected) {
        if (Math.abs(actual - expected) > 0.0001f)
            throw new AssertionError(actual + " != " + expected);
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("ysm-bone-queries");
        Files.createDirectories(folder);
        for (String name : List.of("main.json", "texture.png")) {
            Files.copy(
                root.resolve("animated-ysm").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("ysm.json"), """
            {"files":{"player":{"model":{"main":"main.json"},"texture":"texture.png","animation":"main.animation.json"}}}
            """);
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{"queries":{"loop":true,"animation_length":1,
              "timeline":{"0":"v.target = 2; v.saved = ysm.bone_scale('root');"},
              "bones":{"root":{"position":[16,0,0],"rotation":[5,6,7],"scale":["v.target",3,4]},
                "child":{"scale":["ysm.bone_scale('root').x",1,1]}}
            }}}
            """);
        var config = new PreviewConfig();
        config.configFile = root.resolve("ysm-bone-queries.json");
        config.settings.enabled = false;
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.clip = "queries";
        config.animation.fixedTimeSeconds = 0f;
        config.camera.distanceScale = 8;
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        var mesh = PreviewMesh.load(config);
        try (var animation = new PreviewAnimation(mesh, config)) {
            var state = animation.newState();
            var other = animation.newState();
            state.sample(0, 0);
            near(value(state, "ysm.bone_scale('root').x"), 2);
            near(value(state, "ysm.bone_scale('child').x"), 1);
            near(value(state, "ysm.bone_pos('ROOT').x"), 16);
            near(value(state, "ysm.bone_rot('root').y"), 6);
            near(value(state, "v.saved.x"), 2);
            value(state, "v.saved.x = 99;");
            near(value(state, "ysm.bone_scale('root').x"), 2);
            state.updateBones();
            near(state.palette[0].getScale(new Vector3f()).x, 2);
            near(state.palette[0].transformPosition(new Vector3f()).x, -1);
            value(state, "v.target = 5;");
            state.sample(1, 0.1f);
            near(value(state, "ysm.bone_scale('child').x"), 2);
            near(value(state, "v.saved.x"), 5);
            value(state, "v.target = 7;");
            state.sample(1, 0.1f);
            near(value(state, "ysm.bone_scale('child').x"), 2);
            state.sample(2, 0.2f);
            near(value(state, "ysm.bone_scale('child').x"), 7);
            other.sample(0, 0);
            near(value(other, "ysm.bone_scale('child').x"), 1);
            state.sample(3, 0);
            near(value(state, "ysm.bone_scale('root').x"), 2);
            near(value(state, "ysm.bone_scale('child').x"), 1);
        }
        System.out.println("Bone query PASS: actual pos/rot/scale and GPU palette, live read-only Structs, "
            + "previous-complete-frame inputs, duplicate-frame stability and instance/seek isolation");
    }
}
