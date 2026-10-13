package org.figuramc.figura.model.ysm;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.ysm.animation.YsmAnimationParser;
import org.figuramc.figura.model.ysm.animation.YsmAnimationPlayer;
import org.figuramc.figura.model.ysm.animation.YsmAnimationSampler;
import org.figuramc.figura.model.ysm.controller.YsmAnimationController;
import org.figuramc.figura.model.ysm.controller.YsmAnimationControllerParser;
import org.figuramc.figura.molang.MolangEngine;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.molang.runtime.Function;
import org.figuramc.figura.molang.runtime.Variable;
import org.figuramc.figura.molang.runtime.binding.ObjectBinding;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class YsmControllerContextSmokeTest {
    private YsmControllerContextSmokeTest() {
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        List<String> trace = new ArrayList<>();
        ObjectBinding controller = name -> (Variable) execution ->
                ((Avatar.MolangContext) execution.entity()).controller.getPath(name);
        ObjectBinding query = name -> name.equals("anim_time")
                ? (Variable) execution -> ((Avatar.MolangContext) execution.entity()).anim_time : null;
        Function capture = (execution, arguments) -> {
            Avatar.MolangContext context = (Avatar.MolangContext) execution.entity();
            trace.add(context.controller.getPath("controller") + ":" + context.controller.getPath("current_state")
                    + ":" + context.anim_time);
            return 0f;
        };
        MolangEngine engine = MolangEngine.fromCustomBinding(name -> switch (name) {
            case "c" -> controller;
            case "q" -> query;
            case "capture" -> capture;
            default -> null;
        });
        YsmAnimationPlayer player = new YsmAnimationPlayer(null) {
            @Override
            public void runFunctionEventsForController(YsmAnimationController definition, ExpressionEvaluator<?> evaluator) {
                Avatar.MolangContext context = (Avatar.MolangContext) evaluator.entity();
                require(context.controller.getPath("controller").equals(definition.name()), "function sees its owner");
            }
        };
        var clip = YsmAnimationParser.parse("""
                {"animations":{"shared":{"animation_length":2,"blend_weight":"c.alpha",
                  "bones":{"root":{"position":["c.alpha*10+c.beta*20+q.anim_time",0,0]}},
                  "timeline":{"0":"capture();","0.25":"capture();"}
                }}}
                """, engine).get("shared");
        var timeline = List.copyOf(clip.events);
        clip.events.clear(); // Install clips before dispatch so no Minecraft entity/audio device is needed.
        player.registerAnimations(java.util.Map.of(clip.name, clip));
        player.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{
                  "First":{"initial_state":"alpha","states":{"alpha":{"animations":["shared"]}}},
                  "second":{"initial_state":"beta","states":{"beta":{"animations":["shared"]}}}
                }}
                """, engine));
        Avatar.MolangContext context = new Avatar.MolangContext(null);
        context.anim_time = 7f;
        context.life_time = 9f;
        context.any_animation_finished = 3f;
        context.all_animations_finished = 4f;
        context.controller.setPath("controller", "outer");
        context.controller.setPath("alpha", null);
        context.controller.setPath("user_value", 5f);
        Set<String> originalNames = names(context);
        var evaluator = ExpressionEvaluator.evaluator(context);
        player.controllerRuntime().update(evaluator, 0.1f);
        restored(context, originalNames);
        try (var first = player.controllerRuntime().openAnimationContext("first", evaluator, 0.4f, 2f)) {
            require(context.controller.getPath("controller").equals("First"), "normalized slot resolves actual definition");
            equal(1f, clip.evaluateBlendWeight(evaluator), "first clip dynamic weight");
            equal(10.4f, YsmAnimationSampler.sampleBone(clip.boneAnimations.get("root"), 0.4f, evaluator).position()[0],
                    "first owned pose expression");
            try (var second = player.controllerRuntime().openAnimationContext("second", evaluator, 0.8f, 2f)) {
                equal(0f, clip.evaluateBlendWeight(evaluator), "other state flag cannot leak");
                equal(20.8f, YsmAnimationSampler.sampleBone(clip.boneAnimations.get("root"), 0.8f, evaluator).position()[0],
                        "second owned pose expression");
                context.controller.setPath("user_value", 6f);
            }
            require(context.controller.getPath("controller").equals("First"), "nested scope restores parent owner");
            equal(0.4f, context.anim_time, "nested scope restores parent time");
        }
        restored(context, originalNames);
        require(context.controller.getPath("user_value").equals(6f), "model variable writes survive engine scope");
        try {
            try (var ignored = player.controllerRuntime().openAnimationContext("second", evaluator, 1f, 2f)) {
                throw new IllegalStateException("fixture");
            }
        } catch (IllegalStateException expected) {
            require(expected.getMessage().equals("fixture"), "expected fixture exception");
        }
        restored(context, originalNames);
        clip.events.addAll(timeline);
        var playing = player.getControllerAnimation("First", "shared");
        invokeEventDispatch(player, playing, evaluator, "dispatchInitialEvents");
        playing.previousTime = 0f;
        playing.time = 0.3f;
        Field window = YsmAnimationPlayer.PlayingAnimation.class.getDeclaredField("eventWindowEnd");
        window.setAccessible(true);
        window.setDouble(playing, 0.3);
        invokeEventDispatch(player, playing, evaluator, "dispatchEvents");
        var secondPlaying = player.getControllerAnimation("second", "shared");
        invokeEventDispatch(player, secondPlaying, evaluator, "dispatchInitialEvents");
        secondPlaying.previousTime = 0f;
        secondPlaying.time = 0.5f;
        window.setDouble(secondPlaying, 0.5);
        invokeEventDispatch(player, secondPlaying, evaluator, "dispatchEvents");
        require(trace.equals(List.of("First:alpha:0.0", "First:alpha:0.3", "second:beta:0.0", "second:beta:0.5")),
                "real initial/timeline owner trace: " + trace);
        restored(context, originalNames);
        System.out.println("M3 controller scopes, live expressions, exception cleanup and real event dispatch ownership passed.");
    }

    private static void invokeEventDispatch(YsmAnimationPlayer player, YsmAnimationPlayer.PlayingAnimation playing,
                                            ExpressionEvaluator<?> evaluator, String name) throws ReflectiveOperationException {
        Method dispatch = YsmAnimationPlayer.class.getDeclaredMethod(name,
                YsmAnimationPlayer.PlayingAnimation.class, ExpressionEvaluator.class);
        dispatch.setAccessible(true);
        dispatch.invoke(player, playing, evaluator);
    }

    private static Set<String> names(Avatar.MolangContext context) {
        Set<String> names = new HashSet<>();
        context.controller.forEachPropertyName(names::add);
        return names;
    }

    private static void restored(Avatar.MolangContext context, Set<String> originalNames) {
        require(context.controller.getPath("controller").equals("outer"), "outer controller restored");
        require(context.controller.getPath("alpha") == null && names(context).equals(originalNames),
                "explicit null and absent state fields restored exactly");
        equal(7f, context.anim_time, "outer animation time");
        equal(9f, context.life_time, "outer duration");
        equal(3f, context.any_animation_finished, "outer any-finished");
        equal(4f, context.all_animations_finished, "outer all-finished");
        require(context.controller.getPath("current_state") == null, "state context does not remain after update");
    }

    private static void equal(float expected, float actual, String message) {
        require(Float.isFinite(actual) && Math.abs(expected - actual) < 1e-5f, message + ": " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
