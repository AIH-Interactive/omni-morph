package top.aihmc.omm.preview;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.joml.Vector3f;

/** Script-created model settings must change the palette submitted by the real preview path. */
public final class PreviewLuaAnimationTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("lua-animated-ysm");
        Files.createDirectories(folder);
        for (String name : List.of("main.json", "texture.png")) {
            Files.copy(
                root.resolve("animated-ysm").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("ysm.json"), """
            {"files":{"player":{"model":{"main":"main.json"},"texture":"texture.png",
              "animation":"main.animation.json","animation_controllers":"controller.animation.json"}}}
            """);
        Files.writeString(folder.resolve("avatar.json"), "{\"autoScripts\":[\"script\"]}");
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{"script_pose":{"loop":true,"bones":{"child":{
              "position":["v.offset * 16","v.height * 16","v.phase * 16"]}}}}}
            """);
        Files.writeString(folder.resolve("controller.animation.json"), """
            {"animation_controllers":{"controller.player.main":{"states":{
              "default":{"animations":["script_pose"],"transitions":[{"updated":"v.height >= 4"}]},
              "updated":{"animations":["script_pose"]}
            }}}}
            """);
        Files.writeString(folder.resolve("script.lua"), """
            avatar_controls:newNumber('height'):range(0,10,1):setDefault(1):binding('v.height')
            avatar_controls:newSlider('phase'):range(0,1,0.01):binding('v.phase')
            avatar_controls:newNumber('offset'):title('水平偏移'):range(0,2,0.25):binding('v.offset')
            wheel = action_wheel:newPage('模型动作')
            action_wheel:setPage(wheel)
            wheel:newAction(1):title('向右移动'):toggleTitle('恢复位置'):color(0.2,0.5,0.7)
              :onToggle(function(enabled) avatar_controls:set('offset', enabled and 1 or 0) end)
            wheel:newAction(2):title('模型设置'):controlsPage('root'):color(0.3,0.6,0.4)
            events.entity_init = function() avatar_controls:set('height',3) end
            events.tick = function()
              if preview.running then avatar_controls:set('height', avatar_controls:get('height') + 1) end
            end
            events.render = function(delta) avatar_controls:set('phase',delta) end
            """);
        PreviewConfig config = new PreviewConfig();
        config.settings.enabled = false;
        config.configFile = root.resolve("lua-animated-ysm.json");
        config.window.title = "omm-core Lua driven YSM fixture";
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.clip = "script_pose";
        config.animation.fixedTimeSeconds = 0.125f;
        config.scripts.enabled = true;
        config.scripts.controls.addProperty("height", 2);
        config.states.addProperty("running", true);
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        PreviewMesh mesh = PreviewMesh.load(config);
        var shared = new PreviewAnimation(mesh, config);
        var first = shared.newState();
        first.sample(0, 0);
        first.updateBones();
        point(first, 3, 0, "Configured setting overrides init and reaches actual child transform");
        first.sample(1, 0.125f);
        first.updateBones();
        point(first, 5, 0.5f, "Actual Lua 20Hz ticks and render partial tick drive the palette");
        var second = shared.newState();
        second.sample(0, 0);
        second.updateBones();
        point(second, 3, 0, "Lua model instances own independent control values");
        point(first, 5, 0.5f, "Other model initialization leaves this palette unchanged");
        first.sample(2, 0);
        first.updateBones();
        point(first, 3, 0, "Clock rewind reinitializes actual Lua/control state");
        config.animation.mode = "controller";
        var controlled = new PreviewAnimation(mesh, config).newState();
        controlled.sample(0, 0.125f);
        controlled.updateBones();
        point(controlled, 5, 0.5f, "Lua ticks interleave with controller replay and final render callback");
        if (!controlled.controllerState("controller.player.main").equals("updated")) {
            throw new AssertionError("Actual Lua settings did not trigger the controller transition");
        }
        System.out.println("Lua animation PASS: actual settings/ticks/render -> Molang -> skeleton/GPU palette, "
            + "controller and isolation");
    }

    private static void point(PreviewAnimation.State state, float y, float z, String label) {
        Vector3f point = state.palette[1].transformPosition(new Vector3f(0, 1, 0));
        if (point.distance(new Vector3f(0, y, z)) > 0.0001f) {
            throw new AssertionError(label + ": " + point);
        }
    }
}
