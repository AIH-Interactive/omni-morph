package top.aihmc.omm.preview;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.joml.Vector3f;

/** Exact known poses exercise original parsing/sampling, host state, parent composition and palette ownership. */
public final class PreviewAnimationTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("animated-ysm");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("ysm.json"), """
            {"metadata":{"name":"Owned animated preview fixture"},"files":{"player":{
              "model":{"main":"main.json"},"texture":"texture.png",
              "animation":{"main":"main.animation.json","fp_arm":"fp-arm.animation.json"},
              "animation_controllers":"controller.animation.json"}}}
            """);
        Files.writeString(folder.resolve("fp-arm.animation.json"),
            "{\"animations\":{\"pre_parallel1\":{\"bones\":{\"root\":{\"scale\":0}}}}}");
        Files.writeString(folder.resolve("main.json"), """
            {"minecraft:geometry":[{"description":{"texture_width":16,"texture_height":16},"bones":[
              {"name":"root","cubes":[{"origin":[-4,0,-4],"size":[8,8,8],"uv":[0,0]}]},
              {"name":"child","parent":"root","pivot":[0,8,0],"cubes":[
                {"origin":[0,8,0],"size":[4,8,4],"uv":[0,0]}]},
              {"name":"accessory","parent":"root","cubes":[{"origin":[20,0,0],"size":[4,4,4],"uv":[0,0]}]},
              {"name":"accessory_child","parent":"accessory","cubes":[{"origin":[20,4,0],"size":[2,2,2],"uv":[0,0]}]}
            ]}]}
            """);
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{"attachment_default":{"bones":{"accessory":{"scale":[0,0,0]}}},
              "hide_child":{"bones":{"child":{"scale":0}}},
              "preview.show":{"loop":true,"bones":{"accessory":{"scale":[1,1,1]}}},
              "preview.wave":{"loop":true,"animation_length":2,
              "timeline":{"0.75":"v.event = 12;", "0.25":"v.event = 1;"},
              "bones":{"root":{"position":["q.is_running ? 16 : 0",0,0]},
                "child":{"rotation":[0,0,"q.is_running ? math.sin(q.anim_time * 90) * 90 : 0"],
                         "position":[0,0,"v.event * 0.01"]}}}}}
            """);
        Files.writeString(folder.resolve("controller.animation.json"), """
            {"animation_controllers":{"controller.player.main":{
              "initial_state":"default","states":{"default":{
                "animations":["attachment_default",{"hide_child":"q.hide_child"}]}}}}}
            """);
        BufferedImage texture = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                texture.setRGB(x, y, (x / 4 + y / 4) % 2 == 0 ? 0xff33ccff : 0xffffaa33);
            }
        }
        ImageIO.write(texture, "PNG", folder.resolve("texture.png").toFile());
        PreviewConfig config = new PreviewConfig();
        config.settings.enabled = false;
        config.configFile = root.resolve("animated-ysm.json");
        config.window.title = "omm-core animated YSM fixture";
        config.window.width = 960;
        config.window.height = 600;
        config.camera.distanceScale = 5;
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.clip = "preview.wave";
        var queries = new com.google.gson.JsonObject();
        queries.addProperty("hide_child", false);
        config.states.add("query", queries);
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        PreviewMesh mesh = PreviewMesh.load(config);
        if (!mesh.skeleton.defaultVisible[0] || mesh.skeleton.defaultVisible[2] || mesh.skeleton.restVisible[3]) {
            throw new AssertionError("Base hidden attachment/descendant defaults were not applied");
        }
        var animation = new PreviewAnimation(mesh, config);
        var first = animation.newState();
        var second = animation.newState();
        first.sample(0, 1);
        first.updateBones();
        close(first.palette[1].transformPosition(new Vector3f(0, 1, 0)), -1.5f, 0.5f, 0.0075f,
            "Child rotation, parent X sign and chronological timeline");
        second.sample(0, 0);
        second.updateBones();
        close(second.palette[1].transformPosition(new Vector3f(0, 1, 0)), -1, 1, 0,
            "Independent playback clock and event state");
        close(first.palette[1].transformPosition(new Vector3f(0, 1, 0)), -1.5f, 0.5f, 0.0075f,
            "Other instance did not mutate first palette");
        first.sample(1, 0);
        first.updateBones();
        close(first.palette[1].transformPosition(new Vector3f(0, 1, 0)), -1, 1, 0, "Seek/reset replay");
        config.states.addProperty("running", false);
        var idle = new PreviewAnimation(mesh, config).newState();
        idle.sample(0, 1);
        idle.updateBones();
        close(idle.palette[1].transformPosition(new Vector3f(0, 1, 0)), 0, 1, 0.0075f,
            "running=false reaches actual channel expressions");
        FloatBuffer palette = FloatBuffer.allocate(mesh.skeleton.names.length * 16);
        idle.writePalette(palette);
        if (palette.remaining() != mesh.skeleton.names.length * 16
            || Math.abs(palette.get(16 + 14) - 0.0075f) > 1e-5f) {
            throw new AssertionError("GPU palette layout differs from column-major matrices");
        }
        close(idle.palette[2].transformPosition(new Vector3f(2, 0, 0)), 0, 0, 0, "Attachment remains hidden");
        close(idle.palette[3].transformPosition(new Vector3f(2, 0, 0)), 0, 0, 0, "Hidden parent suppresses descendant");
        config.animation.clip = "preview.show";
        var shown = new PreviewAnimation(mesh, config).newState();
        shown.sample(0, 0);
        shown.updateBones();
        close(shown.palette[2].transformPosition(new Vector3f(2, 0, 0)), 2, 0, 0,
            "Explicit non-base scale restores attachment");
        close(
            shown.palette[3].transformPosition(new Vector3f(2, 0, 0)), 2, 0, 0, "Restored parent restores descendant");
        config.animation.clip = "preview.wave";
        queries.addProperty("hide_child", true);
        var controlled = new PreviewAnimation(mesh, config).newState();
        controlled.sample(0, 0);
        controlled.updateBones();
        close(controlled.palette[1].transformPosition(new Vector3f(0, 1, 0)), 0, 0, 0,
            "Initial controller condition hides the actual child palette");
        close(idle.palette[1].transformPosition(new Vector3f(0, 1, 0)), 0, 1, 0.0075f,
            "Controller condition remains isolated from another instance");
        queries.addProperty("hide_child", false);
        config.states.addProperty("running", true);
        config.animation.fixedTimeSeconds = 1f;
        Files.writeString(root.resolve("animated-ysm-fixed.json"), PreviewConfig.JSON.toJson(config));
        System.out.println("YSM animation PASS: exact poses, original Molang channels, timeline order, instance "
            + "isolation and GPU palette layout");
    }

    private static void close(Vector3f actual, float x, float y, float z, String label) {
        if (actual.distance(new Vector3f(x, y, z)) > 1e-5f) {
            throw new AssertionError(label + ": " + actual);
        }
    }
}
