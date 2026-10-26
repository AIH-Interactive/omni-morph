package top.aihmc.omnimorph.model.ysm;

import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class YsmFirstPersonControllerSmokeTest {
    private YsmFirstPersonControllerSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        testResourceDomains();
        List<String> trace = new ArrayList<>();
        Function mark = (execution, arguments) -> {
            Avatar.MolangContext context = (Avatar.MolangContext) execution.entity();
            trace.add(arguments.getAsString(execution, 0) + ":" + context.is_first_person);
            return 0f;
        };
        MolangEngine engine = MolangEngine.fromCustomBinding(name -> name.equals("mark") ? mark : null);
        YsmAnimationPlayer player = new YsmAnimationPlayer(null) {
            @Override
            public void runFunctionEventsForController(YsmAnimationController controller, ExpressionEvaluator<?> evaluator) {
                Avatar.MolangContext context = (Avatar.MolangContext) evaluator.entity();
                float expected = controller.name().startsWith("fp.arm.") ? 1f : 0f;
                require(context.is_first_person == expected, "stage query matches controller domain");
            }
        };
        var shared = new YsmAnimationClip("shared", 1f, false);
        player.registerAnimations(Map.of(shared.name, shared));
        player.registerArmAnimations(Map.of(shared.name, new YsmAnimationClip("shared", 2f, true)));
        require(player.loopModeFor("fp.arm.misc", "shared", YsmAnimationClip.LoopMode.ONCE) == YsmAnimationClip.LoopMode.LOOP
                        && player.loopModeFor("shared", YsmAnimationClip.LoopMode.LOOP) == YsmAnimationClip.LoopMode.ONCE,
                "function loop defaults are selected from the matching domain");
        player.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{
                  "player.main":{"states":{"default":{"animations":["shared"],
                    "on_entry":["mark('body');"]}}},
                  "fp.arm.misc":{"states":{"default":{"animations":["shared"],
                    "on_entry":["mark('arm');"],"on_exit":["mark('arm.exit');"]}}}
                }}
                """, engine));
        Avatar.MolangContext context = new Avatar.MolangContext(null);
        context.is_first_person = 7f;
        var evaluator = ExpressionEvaluator.evaluator(context);
        player.controllerRuntime().update(evaluator, 0.1f, false);
        var body = player.getControllerAnimation("player.main", "shared");
        require(body != null && player.getControllerAnimation("fp.arm.misc", "shared") == null,
                "third-person phase cannot create first-person playback");
        require(trace.equals(List.of("body:0.0")) && context.is_first_person == 7f, "body query scope restored");
        player.controllerRuntime().update(evaluator, 0.1f, true);
        var arm = player.getControllerAnimation("fp.arm.misc", "shared");
        require(arm != null && arm != body && arm.clip.length == 2f, "first-person and body own separate same-clip instances");
        arm.time = 0.6f;
        var inventory = player.syncClips();
        require(inventory.size() == 1, "same-name domains retain one wire inventory name");
        var controllerNames = player.getControllers().keySet().stream().sorted().toList();
        var captured = player.captureRemotePlaybacks(inventory, controllerNames);
        var restore = YsmAnimationPlayer.class.getDeclaredMethod("buildRemotePlaybacks", List.class, List.class, List.class);
        restore.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, YsmAnimationPlayer.PlayingAnimation> restored =
                (Map<String, YsmAnimationPlayer.PlayingAnimation>) restore.invoke(player, captured, inventory, controllerNames);
        require(restored.values().stream().anyMatch(value -> value.clip.length == 2f && value.time == .6f),
                "wire restore selects arm resource using existing controller identity");
        try (var scope = player.controllerRuntime().openAnimationContext("player.main", evaluator, 0.2f, 1f, true)) {
            require(context.is_first_person == 1f, "shared body animation sampled for actual arm pass");
        }
        require(context.is_first_person == 7f, "sample pass query restored");
        player.controllerRuntime().update(evaluator, 0.1f, false);
        player.controllerRuntime().update(evaluator, 0.1f, false);
        require(player.getControllerAnimation("fp.arm.misc", "shared") == null
                && player.getControllerAnimation("player.main", "shared") == body,
                "camera exit releases arm instance and retains body instance");
        require(trace.equals(List.of("body:0.0", "arm:1.0", "arm.exit:1.0")), "arm exit occurs once in its own view context");
        player.controllerRuntime().update(evaluator, 0.1f, true);
        var restarted = player.getControllerAnimation("fp.arm.misc", "shared");
        require(restarted != null && restarted != arm && restarted.time == 0f, "camera re-entry restarts arm playback");
        require(context.is_first_person == 7f, "all stage query scopes restore caller");
        System.out.println("M3 first-person controller activation, view-query scopes and camera exit/re-entry passed.");
    }

    private static void testResourceDomains() throws Exception {
        var tag = new net.minecraft.nbt.CompoundTag();
        var files = new net.minecraft.nbt.ListTag();
        for (int domain = 0; domain < 2; domain++) {
            var file = new net.minecraft.nbt.CompoundTag();
            String json = domain == 0
                    ? "{\"animations\":{\"shared\":{\"animation_length\":1,\"bones\":{\"root\":{\"rotation\":[1,0,0]}}}}}"
                    : "{\"ysm_animation_type\":11,\"animations\":{\"shared\":{\"animation_length\":2,\"bones\":{\"root\":{\"rotation\":[9,0,0]},\"extra\":{\"rotation\":[9,0,0]}}}}}";
            file.putByteArray("data", json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            files.add(file);
        }
        tag.put("animations", files);
        var read = YsmModelRuntime.class.getDeclaredMethod("readAnimations", net.minecraft.nbt.CompoundTag.class, MolangEngine.class);
        read.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, YsmAnimationClip> body = (Map<String, YsmAnimationClip>) read.invoke(null, tag, Avatar.getMolangEngine());
        require(body.get("shared").length == 1f, "body map excludes heavier same-name first-person resource");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
