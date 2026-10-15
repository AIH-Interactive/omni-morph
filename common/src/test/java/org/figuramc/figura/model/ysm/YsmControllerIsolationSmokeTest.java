package org.figuramc.figura.model.ysm;

import org.figuramc.figura.model.ysm.animation.YsmAnimationClip;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.ysm.animation.YsmAnimationPlayer;
import org.figuramc.figura.model.ysm.controller.YsmAnimationController;
import org.figuramc.figura.model.ysm.controller.YsmAnimationControllerParser;
import org.figuramc.figura.molang.MolangEngine;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.molang.runtime.Variable;
import org.figuramc.figura.molang.runtime.binding.ObjectBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exercises real controller/slot management without constructing a renderer or a game window. */
public final class YsmControllerIsolationSmokeTest {
    private YsmControllerIsolationSmokeTest() {
    }

    public static void main(String[] args) {
        List<String> callbacks = new ArrayList<>();
        YsmAnimationPlayer player = new YsmAnimationPlayer(null) {
            @Override
            public void runFunctionEventsForController(YsmAnimationController controller, ExpressionEvaluator<?> evaluator) {
                callbacks.add(controller.name());
            }
        };
        var clip = new YsmAnimationClip("animation.fixture", 1f, YsmAnimationClip.LoopMode.ONCE);
        player.registerAnimations(Map.of(clip.name, clip));
        Variable weight = context -> ((float[]) context.entity())[0];
        ObjectBinding query = name -> name.equals("weight") ? weight : null;
        MolangEngine engine = MolangEngine.fromCustomBinding(name -> name.equals("q") ? query : null);
        player.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{
                  "first":{"states":{"default":{"animations":[{"animation.fixture":"q.weight"}]}}},
                  "second":{"states":{"default":{"animations":[{"animation.fixture":"1"}]}}}
                }}
                """, engine));
        float[] variables = {0.2f};
        var evaluator = ExpressionEvaluator.evaluator(variables);
        player.controllerRuntime().update(evaluator, 0.1f);
        var first = player.getControllerAnimation("first", clip.name);
        var second = player.getControllerAnimation("second", clip.name);
        require(first != null && second != null && first != second, "same clip has distinct controller instances");
        equal(0.2f, first.weight, "fractional reference weight remains fractional");
        equal(1f, second.weight, "other controller weight");
        first.time = 0.25f;
        second.time = 0.75f;
        variables[0] = 0.7f;
        player.controllerRuntime().update(evaluator, 0.1f);
        equal(0.7f, first.weight, "reference weight reevaluates each controller update");
        equal(0.25f, first.time, "controller update does not restart its active instance");
        equal(0.75f, second.time, "independent timeline remains intact");
        var ordinary = player.play(clip.name, false, 1f);
        require(ordinary != first && ordinary != second, "ordinary action playback has its own slot");
        var functionFirst = player.playControllerFunctionAnimation("first", clip.name, clip.loopMode, 1f, 0f);
        var functionSecond = player.playControllerFunctionAnimation("second", clip.name, clip.loopMode, 1f, 0f);
        require(functionFirst != functionSecond && functionFirst != first && functionFirst != ordinary,
                "controller function playback is also isolated");
        player.clearControllerFunctionAnimation("first", 0f);
        require(player.getActiveAnimations().containsValue(functionSecond), "clearing one function slot keeps the other");
        variables[0] = 0f;
        player.controllerRuntime().update(evaluator, 0.1f);
        require(player.getControllerAnimation("first", clip.name) == null, "zero reference contribution stops only its slot");
        require(player.getControllerAnimation("second", clip.name) == second, "zero weight preserves other controller");
        require(player.getActiveAnimations().containsValue(ordinary), "controller changes preserve ordinary playback");
        second.time = 1f;
        require(!player.isControllerAnimationFinished("second", clip.name), "reference exact-length boundary is not finished yet");
        second.time = 1.001f;
        require(player.isControllerAnimationFinished("second", clip.name), "completion belongs to the controller slot");
        require(!player.isFinishedOnce(clip.name), "ordinary completion remains independent");
        require(callbacks.size() == 6, "each controller callback runs once per explicit update");
        testFunctionClock();
        testStateRestart();
        System.out.println("M3 real controller numeric weights, playback slots, function slots and completion isolation passed.");
    }

    private static void testFunctionClock() {
        Map<String, float[]> clocks = new java.util.HashMap<>();
        YsmAnimationPlayer player = new YsmAnimationPlayer(null) {
            @Override
            public void runFunctionEventsForController(YsmAnimationController controller, ExpressionEvaluator<?> evaluator) {
                Avatar.MolangContext context = (Avatar.MolangContext) evaluator.entity();
                clocks.put(controller.name(), new float[]{context.anim_time, context.life_time,
                        context.any_animation_finished, context.all_animations_finished});
            }
        };
        var clip = new YsmAnimationClip("animation.clock", 1f, YsmAnimationClip.LoopMode.ONCE);
        player.registerAnimations(Map.of(clip.name, clip));
        player.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{
                  "first":{"states":{"default":{}}},
                  "second":{"states":{"default":{}}}
                }}
                """, MolangEngine.createEmpty()));
        var first = player.playControllerFunctionAnimation("first", clip.name, clip.loopMode, 1f, 0f);
        var second = player.playControllerFunctionAnimation("second", clip.name, clip.loopMode, 1f, 0f);
        first.time = 0.4f;
        second.time = 1.001f;
        Avatar.MolangContext context = new Avatar.MolangContext(null);
        context.anim_time = 7f;
        context.life_time = 9f;
        context.any_animation_finished = 3f;
        context.all_animations_finished = 4f;
        player.controllerRuntime().update(ExpressionEvaluator.evaluator(context), 0.1f);
        equal(0.4f, clocks.get("first")[0], "first function clock");
        equal(1f, clocks.get("first")[1], "function duration clock");
        equal(0f, clocks.get("first")[2], "first function not finished");
        equal(0f, clocks.get("first")[3], "first function all-finished false");
        equal(1.001f, clocks.get("second")[0], "independent second function clock");
        equal(1f, clocks.get("second")[2], "second function finished");
        equal(1f, clocks.get("second")[3], "second function all-finished true");
        equal(7f, context.anim_time, "outer animation time restored");
        equal(9f, context.life_time, "outer lifetime restored");
        equal(3f, context.any_animation_finished, "outer any-finished restored");
        equal(4f, context.all_animations_finished, "outer all-finished restored");
        player.clearControllerFunctionAnimation("first", 0f);
        require(player.getControllerFunctionAnimation("first") == null
                && player.getControllerFunctionAnimation("second") == second, "function lookup and clear agree on ownership");
    }

    private static void testStateRestart() {
        YsmAnimationPlayer player = new YsmAnimationPlayer(null) {
            @Override
            public void runFunctionEventsForController(YsmAnimationController controller, ExpressionEvaluator<?> evaluator) {
                // This fixture isolates state playback from function execution.
            }
        };
        var clip = new YsmAnimationClip("animation.restart", 2f, YsmAnimationClip.LoopMode.ONCE);
        player.registerAnimations(Map.of(clip.name, clip));
        Variable transition = context -> ((float[]) context.entity())[0];
        ObjectBinding query = name -> name.equals("switch") ? transition : null;
        MolangEngine engine = MolangEngine.fromCustomBinding(name -> name.equals("q") ? query : null);
        player.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{"fixture":{"states":{
                  "default":{"animations":["animation.restart"],"blend_transition":0.2,
                    "transitions":[{"next":"q.switch"}]},
                  "next":{"animations":["animation.restart"],"blend_transition":0.2}
                }}}}
                """, engine));
        float[] variables = {0f};
        var evaluator = ExpressionEvaluator.evaluator(variables);
        player.controllerRuntime().update(evaluator, 0f);
        var outgoing = player.getControllerAnimation("fixture", clip.name);
        outgoing.time = 0.75f;
        variables[0] = 1f;
        player.controllerRuntime().update(evaluator, 0.1f);
        var incoming = player.getControllerAnimation("fixture", clip.name);
        require(incoming != null && incoming != outgoing, "same clip in a new state gets a new playback instance");
        equal(0f, incoming.time, "new state animation starts at zero");
        equal(0.75f, outgoing.time, "outgoing timeline retained for transition");
        equal(0f, outgoing.targetWeight, "outgoing instance fades out");
        require(player.getActiveAnimations().containsValue(outgoing), "outgoing contribution retained during fade");
        require(player.controllerRuntime().currentState("fixture").equals("next"), "state transition occurred");
    }

    private static void equal(float expected, float actual, String message) {
        require(Math.abs(expected - actual) <= 1e-5f, message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
