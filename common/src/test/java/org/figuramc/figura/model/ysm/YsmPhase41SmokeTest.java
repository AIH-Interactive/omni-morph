package org.figuramc.figura.model.ysm;

import com.google.gson.JsonParser;
import org.figuramc.figura.model.ysm.controller.YsmControllerSlot;
import org.figuramc.figura.model.ysm.controller.YsmControllerSlotBinder;
import org.figuramc.figura.model.ysm.controller.YsmAnimationController;
import org.figuramc.figura.model.ysm.controller.YsmAnimationControllerParser;
import org.figuramc.figura.avatar.control.AvatarControlDefinition;
import org.figuramc.figura.avatar.control.AvatarControlType;
import org.figuramc.figura.molang.MolangEngine;
import org.figuramc.figura.molang.runtime.Function;
import org.figuramc.figura.molang.runtime.Variable;
import org.figuramc.figura.molang.runtime.binding.MolangBindings;
import org.figuramc.figura.model.ysm.action.YsmActionSchemaParser;

import java.util.List;
import java.util.Map;
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
        isType(ysm.getProperty("event"), Function.class, "ysm.event");
        isType(ysm.getProperty("run_event"), Function.class, "ysm.run_event");
        Object missing = ysm.getProperty("missing_optional_function");
        isType(missing, Variable.class, "missing ysm variable fallback");
        isType(missing, Function.class, "missing ysm function fallback");

        MolangBindings.CtrlBinding ctrl = new MolangBindings.CtrlBinding();
        Object missingCtrl = ctrl.getProperty("missing_optional_function");
        isType(missingCtrl, Variable.class, "missing ctrl variable fallback");
        isType(missingCtrl, Function.class, "missing ctrl function fallback");

        equals(YsmBoneRole.LEFT_HAND, YsmBoneMapper.roleOfName("LeftHandLocator2"), "left hand locator role");
        equals(YsmBoneRole.RIGHT_HAND, YsmBoneMapper.roleOfName("RightItem"), "right item role");
        equals(YsmBoneRole.BLADE, YsmBoneMapper.roleOfName("LeftSword"), "left sword role");
        equals(YsmBoneRole.BLADE, YsmBoneMapper.roleOfName("RifleLocator"), "rifle locator role");
        equals(YsmBoneRole.BACKGROUND, YsmBoneMapper.roleOfName("PassengerLocator3"), "passenger locator role");
        equals(YsmBoneRole.BACKGROUND, YsmBoneMapper.roleOfName("LeftShoulderLocator"), "shoulder locator role");
        equals(YsmBoneRole.GUI, YsmBoneMapper.roleOfName("GuiRoot"), "gui role");
        equals(YsmBoneRole.FIRST_PERSON_ARM, YsmBoneMapper.roleOfName("firstPersonRightArm"), "first person arm role");
        equals(true, YsmBoneMapper.isLeftSideName("LeftWaistLocator"), "left side locator");
        equals(false, YsmBoneMapper.isFirstPersonArmCandidate("LeftHand", YsmBoneRole.LEFT_HAND, false), "right hand pass rejects left hand");
        equals(true, YsmBoneMapper.isFirstPersonArmCandidate("RightHand", YsmBoneRole.RIGHT_HAND, false), "right hand pass accepts right hand");

        Map<String, YsmAnimationController> controllers = YsmAnimationControllerParser.parse("""
                {"animation_controllers":{"controller.animation.player.main":{"initial_state":"attack","states":{"attack":{"animations":["sword_attack"],"blend_transition":{"0.0":1,"0.05":0},"blend_via_shortest_path":true}}}}}
                """, MolangEngine.fromCustomBinding(new MolangBindings()));
        YsmAnimationController controller = controllers.get("controller.animation.player.main");
        equals(0.05f, controller.states().get("attack").blendTransition(), "object blend_transition duration");
        equals(true, controller.states().get("attack").blendViaShortestPath(), "blend shortest path flag");

        AvatarControlDefinition face = YsmActionSchemaParser.readConfigForm("face", 0, JsonParser.parseString("""
                {"title":"Expression","type":"radio","value":"v.face","labels":{"normal":"v.face=0","smile":"v.face=1"}}
                """).getAsJsonObject());
        equals(AvatarControlType.ENUM, face.type(), "config enum type");
        equals("normal", face.getDefault(), "config enum implicit default");
        equals("v.face=0", face.optionCommands().get("normal"), "config enum option command");
        AvatarControlDefinition height = YsmActionSchemaParser.readConfigForm("face", 1, JsonParser.parseString("""
                {"title":"Height","type":"slider","value":"v.height","min":0.1,"max":2.0,"step":0.1}
                """).getAsJsonObject());
        equals(AvatarControlType.SLIDER, height.type(), "config slider type");
        equals(0.1d, height.getDefault(), "config slider implicit default");
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
