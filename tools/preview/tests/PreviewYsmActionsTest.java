package top.aihmc.omm.preview;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.joml.Vector3f;
import top.aihmc.omnimorph.model.ysm.action.YsmActionRuntime;

/** Original schemas/actions must execute real clips, controls and automatic pages through the palette. */
public final class PreviewYsmActionsTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("ysm-declared-actions");
        Files.createDirectories(folder.resolve("lang"));
        for (String name : List.of("main.json", "texture.png")) {
            Files.copy(
                root.resolve("animated-ysm").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("ysm.json"), """
            {"files":{"player":{"model":{"main":"main.json"},"texture":"texture.png","animation":"main.animation.json"}},
             "properties":{"width_scale":1,"height_scale":1,"extra_animation":{"extra0":"挥手","outfit":"#outfit"},
               "extra_animation_buttons":[{"id":"outfit","name":"模型设置","config_forms":[
                 {"type":"toggle","title":"显示饰品","value":"v.roaming.hat","default":false},
                 {"type":"range","title":"水平偏移","value":"v.offset","min":0,"max":3,"step":0.25,"default":0}
               ]}]}}
            """);
        Files.writeString(
            folder.resolve("lang/zh_cn.json"), "{\"properties.extra_animation.extra0\":\"挥手（本地化）\"}");
        Files.writeString(folder.resolve("ysm.actions.json"), """
            {"actions":[
              {"id":"extra_animation.wave","title":"Toggle wave","animation":"extra0","mode":"toggle","cooldown":4,"speed":2,"page":"extra_animation"},
              {"id":"extra_animation.hold","title":"Hold pose","animation":"extra1","mode":"hold","page":"extra_animation"},
              {"id":"extra_animation.once","title":"Once pose","animation":"once","mode":"press","page":"extra_animation"}
            ]}
            """);
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{
              "pre_parallel0":{"loop":true,"bones":{"accessory":{"scale":0}}},
              "idle":{"loop":true,"bones":{"accessory":{"scale":"v.roaming.hat ? 1 : 0"},
                "child":{"position":["v.offset * 16",0,0]}}},
              "extra0":{"loop":true,"animation_length":1,"timeline":{"0.25":"v.event = 9;"},
                "bones":{"child":{"position":[16,0,"v.event"]}}},
              "extra1":{"loop":true,"bones":{"child":{"position":[0,16,0]}}},
              "once":{"animation_length":0.1,"bones":{"child":{"position":[0,16,0]}}}
            }}
            """);
        PreviewConfig config = new PreviewConfig();
        config.settings.enabled = false;
        config.configFile = root.resolve("ysm-declared-actions.json");
        config.window.title = "omm-core declared YSM actions fixture";
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.actions = true;
        config.animation.clip = "idle";
        config.interaction.enabled = true;
        config.states.addProperty("language", "zh_cn");
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        PreviewMesh mesh = PreviewMesh.load(config);
        var shared = new PreviewAnimation(mesh, config);
        var instance = shared.newState();
        var owner = instance.lua();
        var runtime = owner.ysm();
        if (runtime.actions().get("idle") != null || runtime.actions().get("pre_parallel0") != null) {
            throw new AssertionError("Engine base/native clips leaked into the user action list");
        }
        if (!runtime.actions().get("extra_animation.extra0").getTitle().equals("挥手（本地化）")) {
            throw new AssertionError("Original model-local translations were not used by generated actions");
        }
        if (!owner.actionWheel.currentPage.getTitle().equals("YSM")) {
            throw new AssertionError("Original default wheel root was not installed");
        }
        owner.wheelState(true, 1);
        owner.executeWheel(1, true);
        if (!owner.actionWheel.currentPage.getTitle().equals("YSM: extra_animation")) {
            throw new AssertionError("Generated page navigation did not execute");
        }
        instance.sample(0, 0);
        instance.updateBones();
        point(instance, 0, 1, 0, "Initial actual idle pose");
        if (instance.palette[2].m00() != 0) {
            throw new AssertionError("Default-hidden attachment was not kept hidden");
        }
        owner.controls.setValue(owner, "ysm.v_roaming_hat", true);
        owner.controls.setValue(owner, "ysm.action_speed", 2d);
        if (!runtime.actions().trigger("extra_animation.wave")) {
            throw new AssertionError("Original declared action did not play");
        }
        instance.sample(1, 0.125f);
        instance.updateBones();
        point(instance, -1, 1, 9f / 16, "Real action channel, timeline and speed");
        if (Math.abs(runtime.actions().time("extra_animation.wave") - 0.5f) > 0.0001f
            || Math.abs(owner.molang.evaluate(owner.molang.compile("return v.extra0;")) - 1) > 0.0001f) {
            throw new AssertionError("Action speed multiplier or original variable pulse was lost");
        }
        Vector3f accessory = instance.palette[2].transformPosition(new Vector3f(2, 0, 0));
        if (accessory.distance(new Vector3f(2, 0, 0)) > 0.0001f) {
            throw new AssertionError("Original form binding did not restore actual attachment geometry");
        }
        runtime.clock(0.05f);
        runtime.actions().trigger("extra_animation.wave");
        runtime.clock(0.1f);
        if (runtime.actions().trigger("extra_animation.wave")) {
            throw new AssertionError("Action cooldown was not enforced in simulated 20Hz ticks");
        }
        runtime.clock(0.25f);
        if (!runtime.actions().trigger("extra_animation.wave")) {
            throw new AssertionError("Action did not become available after cooldown");
        }
        instance.sample(2, 0.25f);
        runtime.actions().trigger("extra_animation.once");
        instance.sample(3, 0.3f);
        instance.updateBones();
        point(instance, 0, 2, 0, "Once endpoint before the original fade");
        instance.sample(4, 0.35f);
        instance.updateBones();
        point(instance, 0, 1.75f, 0, "Original direct once fade at five weight units per second");
        instance.sample(5, 0.6f);
        instance.updateBones();
        point(instance, 0, 1, 0, "Completed action returns to the remaining idle slot");
        YsmActionRuntime.Press held = new YsmActionRuntime.Press();
        if (!held.press(runtime.actions(), "extra_animation.hold")) {
            throw new AssertionError("Original hold ownership did not start");
        }
        var playing = runtime.animations().getActiveAnimation("extra1");
        long generation = playing.playbackGeneration;
        held.press(runtime.actions(), "extra_animation.hold");
        if (playing.playbackGeneration != generation || !held.release()
            || runtime.actions().isActive("extra_animation.hold")) {
            throw new AssertionError("Hold repeat/release changed original playback ownership");
        }
        held.press(runtime.actions(), "extra_animation.hold");
        runtime.actions().trigger("extra_animation.hold");
        if (held.release() || !runtime.actions().isActive("extra_animation.hold")) {
            throw new AssertionError("Old input released a replacement playback generation");
        }
        runtime.actions().stopAll();
        if (!runtime.animations().isActive("idle")) {
            throw new AssertionError("StopAll stopped a native/base idle slot");
        }
        int holdSlot = -1;
        var slots = owner.actionWheel.currentPage.slots();
        for (int index = 0; index < slots.length; index++) {
            if (slots[index] != null && "extra_animation.hold".equals(slots[index].getYsmAction())) {
                holdSlot = index;
            }
        }
        owner.wheelState(true, holdSlot);
        owner.executeWheel(holdSlot, true);
        if (!runtime.actions().isActive("extra_animation.hold")) {
            throw new AssertionError("Actual generated wheel did not acquire its hold playback");
        }
        owner.releaseWheelInput();
        if (runtime.actions().isActive("extra_animation.hold")) {
            throw new AssertionError("Actual wheel release did not release its held playback");
        }
        owner.controls.setValue(owner, "ysm.v_offset", 1.5d);
        owner.controls.setValue(owner, "ysm.height_scale", 2d);
        instance.sample(6, 0.65f);
        instance.updateBones();
        point(instance, -1.5f, 2, 0, "Actual form binding and global height setting");
        var isolated = shared.newState();
        isolated.sample(0, 0);
        isolated.updateBones();
        point(isolated, 0, 1, 0, "Schemas, variables, controls and actions are per instance");
        Path archive = root.resolve("ysm-declared-actions.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive)); var files = Files.walk(folder)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                zip.putNextEntry(
                    new ZipEntry("nested/avatar/" + folder.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
        config.scene.models = List.of(archive.toString());
        var zipped = new PreviewAnimation(PreviewMesh.load(config), config).newState();
        zipped.sample(0, 0);
        zipped.lua().controls.setValue(zipped.lua(), "ysm.v_roaming_hat", true);
        zipped.lua().controls.setValue(zipped.lua(), "ysm.action_speed", 2d);
        zipped.lua().ysm().actions().trigger("extra_animation.wave");
        zipped.sample(1, 0.125f);
        zipped.updateBones();
        point(zipped, -1, 1, 9f / 16, "Nested ZIP schemas, localized actions, forms and actual playback");
        if (!zipped.lua().ysm().actions().get("extra_animation.extra0").getTitle().equals("挥手（本地化）")) {
            throw new AssertionError("Nested ZIP model-local action translations differ from directory");
        }
        System.out.println("Declared YSM actions PASS: original schemas/pages/forms, real clips/timeline, "
            + "speed/cooldown/hold/fade and isolation");
    }

    private static void point(PreviewAnimation.State state, float x, float y, float z, String label) {
        Vector3f point = state.palette[1].transformPosition(new Vector3f(0, 1, 0));
        if (point.distance(new Vector3f(x, y, z)) > 0.0002f) {
            throw new AssertionError(label + ": " + point);
        }
    }
}
