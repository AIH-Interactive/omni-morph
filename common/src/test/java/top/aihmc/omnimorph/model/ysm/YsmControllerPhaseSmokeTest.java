package top.aihmc.omnimorph.model.ysm;

import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationFrameClock;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerSlot;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerSlotBinder;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class YsmControllerPhaseSmokeTest {
    private YsmControllerPhaseSmokeTest() {
    }

    public static void main(String[] args) {
        List<String> trace = new ArrayList<>();
        List<String> owners = new ArrayList<>();
        YsmFunctionEventBus bus = new YsmFunctionEventBus();
        List<String> events = List.of("player_ctrl_pre_parallel_1", "player_ctrl_pre_main", "player_ctrl_main",
                "player_ctrl_post_main_1", "player_ctrl_post_swing", "player_ctrl_parallel_1",
                "player_ctrl_parallel_2", "player_ctrl_parallel", "player_ctrl_fp_arm");
        Function entry = (execution, arguments) -> {
            Avatar.MolangContext context = (Avatar.MolangContext) execution.entity();
            trace.add("entry:" + context.controller.getPath("controller"));
            return 0f;
        };
        MolangEngine engine = MolangEngine.fromCustomBinding(name -> name.equals("entry") ? entry : null);
        YsmAnimationPlayer player = new YsmAnimationPlayer(null) {
            @Override
            public void runFunctionEventsForController(YsmAnimationController controller, ExpressionEvaluator<?> evaluator) {
                Avatar.MolangContext context = (Avatar.MolangContext) evaluator.entity();
                require(context.controller.getPath("controller").equals(controller.name()), "phase function has correct c owner");
                owners.add(controller.name());
                for (String event : bus.eventsForController(controller.name(), controller.slot(), events)) {
                    trace.add("function:" + event);
                    bus.markExecuted(event, controller.name(), 1);
                }
            }
        };
        player.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{
                  "fp.arm.parallel":{"states":{"default":{"on_entry":["entry();"]}}},
                  "fp.arm.misc":{"states":{"default":{"on_entry":["entry();"]}}},
                  "player.parallel_2":{"states":{"default":{"on_entry":["entry();"]}}},
                  "player.parallel_1":{"states":{"default":{"on_entry":["entry();"]}}},
                  "player.post_swing":{"states":{"default":{"on_entry":["entry();"]}}},
                  "player.post_main_1":{"states":{"default":{"on_entry":["entry();"]}}},
                  "player.main":{"states":{"default":{"on_entry":["entry();"]}}},
                  "player.pre_main":{"states":{"default":{"on_entry":["entry();"]}}},
                  "player.pre_parallel_1":{"states":{"default":{"on_entry":["entry();"]}}}
                }}
                """, engine));
        Avatar.MolangContext context = new Avatar.MolangContext(null);
        var evaluator = ExpressionEvaluator.evaluator(context);
        YsmAnimationFrameClock clock = new YsmAnimationFrameClock();
        for (int pass = 0; pass < 3; pass++) {
            var step = clock.step(1, 1f + pass);
            if (step.advance()) {
                bus.beginFrame();
                player.controllerRuntime().update(evaluator, step.deltaSeconds());
            }
        }
        List<String> expectedOwners = List.of("player.pre_parallel_1", "player.pre_main", "player.main",
                "player.post_main_1", "player.post_swing", "player.parallel_1", "player.parallel_2",
                "fp.arm.misc", "fp.arm.parallel");
        require(owners.equals(expectedOwners), "reference body/arm phase order independent of registration: " + owners);
        require(trace.equals(List.of(
                "function:player_ctrl_pre_parallel_1", "entry:player.pre_parallel_1",
                "function:player_ctrl_pre_main", "entry:player.pre_main",
                "function:player_ctrl_main", "entry:player.main",
                "function:player_ctrl_post_main_1", "entry:player.post_main_1",
                "function:player_ctrl_post_swing", "entry:player.post_swing",
                "function:player_ctrl_parallel_1", "function:player_ctrl_parallel", "entry:player.parallel_1",
                "function:player_ctrl_parallel_2", "entry:player.parallel_2",
                "function:player_ctrl_fp_arm", "entry:fp.arm.misc", "entry:fp.arm.parallel")),
                "owned function-before-entry order and once-per-frame shared events: " + trace);
        require(YsmControllerSlot.fromName("fp.arm.parallel_1") == YsmControllerSlot.FIRST_PERSON_ARM,
                "reference fp.arm prefix takes priority over generic parallel");
        require(YsmControllerSlotBinder.eventNamesForController("player.pre_main", YsmControllerSlot.MAIN)
                .equals(Set.of("player_ctrl_pre_main")), "pre-main cannot consume main event");
        require(YsmControllerSlotBinder.eventNamesForController("player.main", YsmControllerSlot.MAIN)
                .equals(Set.of("player_ctrl_main")), "main cannot consume pre-main event");
        require(bus.unboundEvents(events).isEmpty(), "all declared bound events consumed by their owner");
        requireReferenceStageOrder();
        System.out.println("M3 reference phase order, fp.arm classification and function-event ownership checks passed.");
    }

    /** Sparkle PlayerAnimationController.registerControllers registration order for body stages. */
    private static void requireReferenceStageOrder() {
        List<String> referenceOrder = List.of("player.pre_parallel", "player.vehicle", "player.pre_main",
                "player.main", "player.post_main", "player.pre_hold", "player.hold_offhand",
                "player.hold_mainhand", "player.post_hold", "player.fire", "player.pre_swing", "player.swing",
                "player.post_swing", "player.pre_use", "player.use", "player.post_use", "player.passenger",
                "player.carry_on", "player.cap", "player.gui_hover", "player.gui_focus", "player.parallel",
                "player.armor_head");
        for (int i = 1; i < referenceOrder.size(); i++) {
            String previous = referenceOrder.get(i - 1);
            String current = referenceOrder.get(i);
            require(YsmControllerSlot.executionOrder(previous) < YsmControllerSlot.executionOrder(current),
                    "reference stage " + previous + " runs before " + current);
        }
        require(YsmControllerSlot.executionOrder("controller.animation.player.post_use")
                        == YsmControllerSlot.executionOrder("player.post_use"),
                "stage order ignores the controller.animation prefix");
        require(YsmControllerSlot.executionOrder("player.post_main_1")
                        == YsmControllerSlot.executionOrder("player.post_main"),
                "numbered post_main keeps its stage order");
        for (String stage : referenceOrder.subList(referenceOrder.indexOf("player.post_main"), referenceOrder.size())) {
            require(YsmControllerSlot.executionOrder(stage) >= 20, stage + " is applied after main");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
