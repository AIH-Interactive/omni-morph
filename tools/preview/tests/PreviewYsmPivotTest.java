package top.aihmc.omm.preview;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Tracked pivots must come from the submitted hierarchy, in original model-pixel query coordinates. */
public final class PreviewYsmPivotTest {
    private static float value(PreviewAnimation.State state, String source) throws Exception {
        return state.molang().evaluate(state.molang().compile(source));
    }

    private static void near(float actual, float expected) {
        if (Math.abs(actual - expected) > 0.001f)
            throw new AssertionError(actual + " != " + expected);
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("ysm-pivot-query");
        Files.createDirectories(folder);
        Files.copy(root.resolve("animated-ysm/texture.png"), folder.resolve("texture.png"),
            StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(folder.resolve("ysm.json"), """
            {"files":{"player":{"model":{"main":"main.json"},"texture":"texture.png","animation":"main.animation.json"}}}
            """);
        Files.writeString(folder.resolve("main.json"), """
            {"minecraft:geometry":[{"description":{"texture_width":16,"texture_height":16},"bones":[
              {"name":"root","pivot":[16,0,0],"rotation":[0,0,90],
                "cubes":[{"origin":[16,0,0],"size":[4,4,4],"uv":[0,0]}]},
              {"name":"child","parent":"root","pivot":[16,16,0],
                "cubes":[{"origin":[16,16,0],"size":[4,4,4],"uv":[0,0]}]}
            ]}]}
            """);
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{"pivot":{"loop":true,"animation_length":2,
              "timeline":{"0":"v.move=0; v.parent_scale=2; v.saved=ysm.bone_pivot_abs('child');"},
              "bones":{"root":{"position":["v.move",0,0],"scale":["v.parent_scale",3,1]},
                "child":{"position":[0,8,0],"rotation":[0,0,45],"scale":[4,5,1]}}
            }}}
            """);
        var config = new PreviewConfig();
        config.configFile = root.resolve("ysm-pivot-query.json");
        config.scene.models = List.of(folder.toString());
        config.settings.enabled = false;
        config.animation.enabled = true;
        config.animation.clip = "pivot";
        config.animation.fixedTimeSeconds = 0f;
        config.camera.distanceScale = 8;
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        try (var animation = new PreviewAnimation(PreviewMesh.load(config), config)) {
            var state = animation.newState();
            state.sample(0, 0);
            near(value(state, "v.saved.x"), 0);
            state.updateBones();
            near(value(state, "v.saved.x"), 0);
            state.recordRenderedPivots();
            // Geometry parsing mirrors X: root is [-1,0]. Parent Rz(90)*S(2,3)
            // maps the child's [0,1.5] relative pivot to [-4.5,0], giving query X=88.
            near(value(state, "v.saved.x"), 88);
            near(value(state, "v.saved.y"), 0);
            near(value(state, "ysm.bone_pivot_abs('ROOT').x"), 0);
            state.recordRenderedPivots();
            near(value(state, "ysm.bone_pivot_abs('root').x"), 16);
            value(state, "v.saved.x=999; v.move=16;");
            state.sample(1, 0.1f);
            state.updateBones();
            near(value(state, "v.saved.x"), 88);
            try (var scope = state.molang().bones.beginEvaluation()) {
                state.recordRenderedPivots();
                near(value(state, "v.saved.x"), 104);
            }
            near(value(state, "ysm.bone_pivot_abs('root').x"), 32);
            value(state, "v.parent_scale=0; v.move=32;");
            state.sample(2, 0.2f);
            state.updateBones();
            state.recordRenderedPivots();
            near(value(state, "v.saved.x"), 104);
            near(value(state, "ysm.bone_pivot_abs('root').x"), 32);
            value(state, "v.parent_scale=2;");
            state.sample(3, 0.3f);
            state.updateBones();
            state.recordRenderedPivots();
            near(value(state, "v.saved.x"), 120);
            var other = animation.newState();
            other.sample(0, 0);
            near(value(other, "v.saved.x"), 0);
            state.sample(4, 0);
            near(value(state, "v.saved.x"), 0);
        }
        config.animation.actions = true;
        config.scripts.controls.addProperty("ysm.width_scale", 2);
        config.scripts.controls.addProperty("ysm.height_scale", 3);
        try (var animation = new PreviewAnimation(PreviewMesh.load(config), config)) {
            var state = animation.newState();
            state.sample(0, 0);
            state.updateBones();
            state.recordRenderedPivots();
            near(value(state, "v.saved.x"), 88);
            near(value(state, "v.saved.y"), 0);
        }
        System.out.println("YSM pivot PASS: actual parent rotation/nonuniform scale/position, render boundary, "
            + "live tracking, hidden ancestor retention, model-scale exclusion and instance/seek isolation");
    }
}
