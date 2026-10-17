package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.Variable;
import top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exercises the real parser/runtime hierarchy without requiring a game or audio device. */
public final class YsmChildControllerSmokeTest {
    private YsmChildControllerSmokeTest() {
    }

    public static void main(String[] args) {
        testParentOwnership();
        testEmptyStateCycle();
        testDepthLimit();
        System.out.println("M3 child ownership, exit/re-entry, empty-state cycle and depth-limit checks passed.");
    }

    private static YsmAnimationPlayer player() {
        return new YsmAnimationPlayer(null) {
            @Override
            public void runFunctionEventsForController(YsmAnimationController controller, ExpressionEvaluator<?> evaluator) {
                // Function event dispatch is tested separately; this fixture tests state ownership.
            }
        };
    }

    private static void testParentOwnership() {
        List<String> trace = new ArrayList<>();
        Function mark = (context, arguments) -> {
            trace.add(arguments.getAsString(context, 0));
            return 0f;
        };
        Variable leave = context -> ((float[]) context.entity())[0];
        ObjectBinding query = name -> name.equals("leave") ? leave : null;
        MolangEngine engine = MolangEngine.fromCustomBinding(name -> switch (name) {
            case "mark" -> mark;
            case "q" -> query;
            default -> null;
        });
        YsmAnimationPlayer player = player();
        player.registerAnimations(Map.of("shared", new YsmAnimationClip("shared", 1f, true)));
        player.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{
                  "fixture":{"initial_state":"ysm-entry-child","states":{
                    "ysm-entry-child":{"on_entry":["mark('parent.entry');"],
                      "on_exit":["mark('parent.exit');"],"transitions":[{"idle":"q.leave"}]},
                    "idle":{"animations":["shared"],"transitions":[{"ysm-entry-child":"!q.leave"}]}
                  }},
                  "fixture.child":{"states":{"default":{"animations":["shared"],
                    "on_entry":["mark('child.entry');"],"on_exit":["mark('child.exit');"]}}}
                }}
                """, engine));
        float[] inputs = {1f};
        var evaluator = ExpressionEvaluator.evaluator(inputs);
        player.controllerRuntime().update(evaluator, 0.1f);
        require(player.getControllerAnimation("fixture.child", "shared") == null,
                "inactive child definition must not be independently advanced");
        require(trace.equals(List.of("parent.entry", "parent.exit")), "no inactive child entry event");
        inputs[0] = 0f;
        player.controllerRuntime().update(evaluator, 0.1f);
        var firstChild = player.getControllerAnimation("fixture.child", "shared");
        require(firstChild != null, "parent entry activates child clip");
        firstChild.time = 0.6f;
        player.controllerRuntime().update(evaluator, 0.1f);
        require(trace.stream().filter("child.entry"::equals).count() == 1, "active child enters once");
        inputs[0] = 1f;
        player.controllerRuntime().update(evaluator, 0.1f);
        require(player.getControllerAnimation("fixture.child", "shared") == null,
                "parent exit releases child playback instance");
        require(trace.subList(trace.size() - 2, trace.size()).equals(List.of("child.exit", "parent.exit")),
                "nested exit precedes parent exit");
        inputs[0] = 0f;
        player.controllerRuntime().update(evaluator, 0.1f);
        var secondChild = player.getControllerAnimation("fixture.child", "shared");
        require(secondChild != null && secondChild != firstChild && secondChild.time == 0f,
                "child re-entry restarts its own clock and instance");
        require(trace.stream().filter("child.entry"::equals).count() == 2, "child re-entry emits exactly one entry");
    }

    private static void testEmptyStateCycle() {
        List<String> trace = new ArrayList<>();
        Function mark = (context, arguments) -> {
            trace.add(arguments.getAsString(context, 0));
            return 0f;
        };
        MolangEngine engine = MolangEngine.fromCustomBinding(name -> name.equals("mark") ? mark : null);
        YsmAnimationPlayer player = player();
        player.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{
                "root":{"initial_state":"ysm-entry-cycle","states":{"ysm-entry-cycle":{}}},
                "root.cycle":{"states":{
                  "default":{"on_entry":["mark('default');"],"transitions":[{"a":"1"}]},
                  "a":{"on_entry":["mark('a');"],"transitions":[{"b":"1"}]},
                  "b":{"on_entry":["mark('b');"],"transitions":[{"a":"1"}]}
                }}}}
                """, engine));
        player.controllerRuntime().update(ExpressionEvaluator.evaluator(new Object()), 0.1f);
        require(trace.equals(List.of("default", "a", "b")), "empty states jump in one update with cycle suppression");
        require(player.controllerRuntime().currentState("root.cycle").equals("b"), "child cycle retains last unique state");
    }

    private static void testDepthLimit() {
        StringBuilder json = new StringBuilder("{\"animation_controllers\":{");
        String name = "root";
        for (int depth = 1; depth <= 6; depth++) {
            if (depth > 1) {
                json.append(',');
            }
            json.append('"').append(name).append("\":{\"initial_state\":\"ysm-entry-next\",\"states\":{")
                    .append("\"ysm-entry-next\":{\"sound_effects\":[\"level.").append(depth).append("\"]}}}");
            name += ".next";
        }
        json.append("}}");
        List<String> entered = new ArrayList<>();
        YsmAnimationPlayer player = new YsmAnimationPlayer(null) {
            @Override
            public void runFunctionEventsForController(YsmAnimationController controller, ExpressionEvaluator<?> evaluator) {
            }

            @Override
            public void playControllerSounds(String controllerName,
                                             top.aihmc.omnimorph.model.ysm.controller.YsmControllerState state) {
                entered.addAll(state.soundEffects());
            }
        };
        player.registerControllers(YsmAnimationControllerParser.parse(json.toString(),
                MolangEngine.fromCustomBinding(nameBinding -> null)));
        player.controllerRuntime().update(null, 0.1f);
        player.controllerRuntime().update(null, 0.1f);
        require(entered.equals(List.of("level.1", "level.2", "level.3", "level.4", "level.5")),
                "only five active levels enter, and repeated update does not fire entries again: " + entered);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
