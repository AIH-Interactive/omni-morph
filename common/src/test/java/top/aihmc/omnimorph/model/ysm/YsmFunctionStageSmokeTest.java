package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class YsmFunctionStageSmokeTest {
    private YsmFunctionStageSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        List<String> trace = new ArrayList<>();
        Function capture = (execution, arguments) -> {
            Avatar.MolangContext context = (Avatar.MolangContext) execution.entity();
            trace.add(arguments.getAsString(execution, 0) + ":" + context.controller.getPath("controller")
                    + ":" + context.is_first_person);
            return 0f;
        };
        ObjectBinding binding = name -> name.equals("capture") ? capture : null;
        YsmMolangFunctionRuntime functions = new YsmMolangFunctionRuntime(null);
        installParsedEvents(functions, binding, List.of("player_ctrl_fp_arm", "player_ctrl_parallel_2",
                "player_ctrl_post_swing", "player_ctrl_post_main_1", "player_ctrl_main",
                "player_ctrl_pre_main", "player_ctrl_pre_parallel_2", "player_ctrl_custom"));
        YsmAnimationPlayer player = new YsmAnimationPlayer(null) {
            @Override
            public void runFunctionEventsForController(YsmAnimationController controller, ExpressionEvaluator<?> evaluator) {
                functions.runControllerSlotEvents(controller.name(), controller.slot(), evaluator);
            }
        };
        player.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{"player.main":{"initial_state":"active","states":{
                  "active":{"on_entry":["capture('state');"]}
                }}}}
                """, MolangEngine.fromCustomBinding(binding)));
        YsmAnimationController original = player.getControllers().get("player.main");
        player.registerFunctionEventControllers(functions.eventDebugNames().keySet());
        require(player.getControllers().get("player.main") == original, "missing-stage installation preserves real state machine");
        Avatar.MolangContext context = new Avatar.MolangContext(null);
        context.is_first_person = 7f;
        var evaluator = ExpressionEvaluator.evaluator(context);
        functions.beginControllerFrame();
        player.controllerRuntime().update(evaluator, 0.1f, false);
        functions.runUnboundControllerEvents(evaluator, player.getControllers().values());
        require(trace.equals(List.of(
                "player_ctrl_pre_parallel_2:player.pre_parallel_2:0.0",
                "player_ctrl_pre_main:player.pre_main:0.0",
                "player_ctrl_main:player.main:0.0", "state:player.main:0.0",
                "player_ctrl_post_main_1:player.post_main_1:0.0",
                "player_ctrl_post_swing:player.post_swing:0.0",
                "player_ctrl_parallel_2:player.parallel_2:0.0",
                "player_ctrl_custom:player_ctrl_custom:7.0")), "real function stage/owner trace: " + trace);
        require(context.controller.getPath("controller") == null && context.controller.getPath("current_state") == null
                && context.is_first_person == 7f, "implicit stages and fallback restore absent outer fields");
        int firstFrameCount = trace.size();
        player.controllerRuntime().update(evaluator, 0f, false);
        functions.runUnboundControllerEvents(evaluator, player.getControllers().values());
        require(trace.size() == firstFrameCount, "same-frame event bus prevents implicit/fallback repeats");
        functions.beginControllerFrame();
        player.controllerRuntime().update(evaluator, 0.1f, true);
        functions.runUnboundControllerEvents(evaluator, player.getControllers().values());
        require(trace.stream().filter(value -> value.equals("player_ctrl_fp_arm:fp.arm.misc:1.0")).count() == 1,
                "first-person function runs exactly once in active arm domain");
        require(trace.stream().filter(value -> value.startsWith("state:")).count() == 1,
                "real state entry remains once despite implicit function slots");
        System.out.println("M3 real Molang function stages, implicit owners, fallback exclusion and same-frame deduplication passed.");
    }

    @SuppressWarnings("unchecked")
    private static void installParsedEvents(YsmMolangFunctionRuntime runtime, ObjectBinding binding,
                                             List<String> names) throws Exception {
        // Supply parser-produced immutable DTOs without constructing Minecraft textures or an Avatar binding registry.
        Field eventsField = YsmMolangFunctionRuntime.class.getDeclaredField("events");
        eventsField.setAccessible(true);
        Map<String, List<Object>> events = (Map<String, List<Object>>) eventsField.get(runtime);
        Class<?> functionClass = Class.forName(YsmMolangFunctionRuntime.class.getName() + "$YsmFunction");
        Constructor<?> constructor = functionClass.getDeclaredConstructor(String.class, String.class, String.class,
                String.class, String.class, String.class, List.class, int.class);
        constructor.setAccessible(true);
        for (String name : names) {
            String path = "functions/@" + name + ".molang";
            var parsed = YsmMolangFunctionParser.parse(path, "capture('" + name + "');", binding);
            Object function = constructor.newInstance(path, parsed.name().fileName(), "", "", name, name,
                    parsed.expressions(), events.size());
            events.put(name, List.of(function));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
