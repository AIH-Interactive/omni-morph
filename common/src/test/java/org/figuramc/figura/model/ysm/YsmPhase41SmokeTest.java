package org.figuramc.figura.model.ysm;

import org.figuramc.figura.model.ysm.controller.YsmControllerSlot;
import org.figuramc.figura.model.ysm.controller.YsmControllerSlotBinder;
import org.figuramc.figura.molang.runtime.Function;
import org.figuramc.figura.molang.runtime.Variable;
import org.figuramc.figura.molang.runtime.binding.MolangBindings;

import java.util.List;
import java.util.Set;

public final class YsmPhase41SmokeTest {
    private YsmPhase41SmokeTest() {
    }

    public static void main(String[] args) {
        functionName("functions/calc_flag.molang", "calc_flag.molang", "calc_flag", "", false);
        functionName("functions/main@player_ctrl_parallel_1.molang", "main@player_ctrl_parallel_1.molang", "main", "player_ctrl_parallel_1", false);
        functionName("functions/@player_ctrl_pre_main.molang", "@player_ctrl_pre_main.molang", "", "player_ctrl_pre_main", false);
        functionName("functions/foo@.molang", "foo@.molang", "foo", "", true);
        functionName("functions/foo@bar@baz.molang", "foo@bar@baz.molang", "foo", "bar@baz", true);

        contains(YsmControllerSlotBinder.eventNamesForController("controller.animation.player.parallel_1", YsmControllerSlot.PARALLEL),
                "player_ctrl_parallel_1");
        contains(YsmControllerSlotBinder.eventNamesForController("controller.animation.player.pre_main", YsmControllerSlot.MAIN),
                "player_ctrl_pre_main");
        contains(YsmControllerSlotBinder.eventNamesForController("controller.animation.player.main", YsmControllerSlot.MAIN),
                "player_ctrl_main");

        List<String> unbound = YsmControllerSlotBinder.unboundEvents(
                List.of("player_ctrl_parallel_1", "player_ctrl_pre_main", "custom_event"),
                Set.of("player_ctrl_parallel_1")
        );
        equals(List.of("player_ctrl_pre_main"), unbound, "unbound controller events");

        YsmFunctionEventBus bus = new YsmFunctionEventBus();
        equals(List.of("player_ctrl_parallel_1"), bus.eventsForController(
                "controller.animation.player.parallel_1",
                YsmControllerSlot.PARALLEL,
                List.of("player_ctrl_parallel_1", "player_ctrl_pre_main")
        ), "registered controller events");
        bus.markExecuted("player_ctrl_parallel_1", "controller.animation.player.parallel_1", 1);
        equals(List.of(), bus.eventsForController(
                "controller.animation.player.parallel_1",
                YsmControllerSlot.PARALLEL,
                List.of("player_ctrl_parallel_1")
        ), "executed controller events");

        MolangBindings.YsmBinding ysm = new MolangBindings.YsmBinding();
        isType(ysm.getProperty("ground_speed2"), Variable.class, "ysm.ground_speed2");
        isType(ysm.getProperty("has_any_curios"), Function.class, "ysm.has_any_curios");
        isType(ysm.getProperty("particle"), Function.class, "ysm.particle");
        Object missing = ysm.getProperty("missing_optional_function");
        isType(missing, Variable.class, "missing ysm variable fallback");
        isType(missing, Function.class, "missing ysm function fallback");

        MolangBindings.CtrlBinding ctrl = new MolangBindings.CtrlBinding();
        Object missingCtrl = ctrl.getProperty("missing_optional_function");
        isType(missingCtrl, Variable.class, "missing ctrl variable fallback");
        isType(missingCtrl, Function.class, "missing ctrl function fallback");
    }

    private static void functionName(String path, String fileName, String functionName, String eventName, boolean malformed) {
        YsmMolangFunctionName parsed = YsmMolangFunctionName.parse(path);
        equals(fileName, parsed.fileName(), path + " fileName");
        equals(functionName, parsed.functionName(), path + " functionName");
        equals(eventName, parsed.eventName(), path + " eventName");
        equals(malformed, parsed.malformed(), path + " malformed");
    }

    private static void contains(Set<String> values, String expected) {
        if (!values.contains(expected))
            throw new AssertionError("Expected " + values + " to contain " + expected);
    }

    private static void equals(Object expected, Object actual, String label) {
        if (!expected.equals(actual))
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
    }

    private static void isType(Object value, Class<?> type, String label) {
        if (!type.isInstance(value))
            throw new AssertionError(label + ": expected " + type.getSimpleName() + " but got " + (value == null ? "null" : value.getClass().getName()));
    }
}
