package org.figuramc.figura.model.ysm;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.ysm.animation.YsmAnimationClip;
import org.figuramc.figura.model.ysm.animation.YsmAnimationPlayer;
import org.figuramc.figura.model.ysm.controller.YsmAnimationController;
import org.figuramc.figura.model.ysm.controller.YsmAnimationControllerParser;
import org.figuramc.figura.molang.MolangEngine;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.molang.runtime.Function;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class YsmFirstPersonControllerSmokeTest {
    private YsmFirstPersonControllerSmokeTest() {
    }

    public static void main(String[] args) {
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
        var shared = new YsmAnimationClip("shared", 1f, true);
        player.registerAnimations(Map.of(shared.name, shared));
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
        require(arm != null && arm != body, "first-person and body own separate same-clip instances");
        arm.time = 0.6f;
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
