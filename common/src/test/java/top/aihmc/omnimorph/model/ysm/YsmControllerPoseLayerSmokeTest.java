package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.model.ysm.animation.YsmBonePose;
import top.aihmc.omnimorph.model.ysm.controller.YsmBlendTransition;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerPoseLayer;

import java.util.LinkedHashMap;
import java.util.Map;

public final class YsmControllerPoseLayerSmokeTest {
    private YsmControllerPoseLayerSmokeTest() {
    }

    public static void main(String[] args) {
        var layer = new YsmControllerPoseLayer();
        var independent = new YsmControllerPoseLayer();
        var oldPose = pose(10, 170, 2);
        layer.compose(Map.of("root", oldPose, "old-only", pose(4, 0, 1)));
        independent.compose(Map.of("root", pose(100, 0, 1)));
        var curve = YsmBlendTransition.curve(Map.of(0f, 1f, 0.1f, 0.8f, 0.2f, 0f));
        layer.beginTransition(curve);
        Map<String, YsmBonePose> incoming = new LinkedHashMap<>();
        incoming.put("root", YsmBonePose.accumulate(null, pose(20, -170, 3), 0.2f));
        incoming.put("root", YsmBonePose.accumulate(incoming.get("root"), pose(5, 0, 1), 1f));
        incoming.put("new-only", pose(6, 0, 1));
        var initial = layer.compose(incoming);
        equal(10f, initial.get("root").position()[0], "transition starts at old controller layer");
        layer.advance(0.1f);
        var middle = layer.compose(incoming);
        equal(9.8f, middle.get("root").position()[0], "multi-clip incoming layer blended once");
        YsmPoseSamplingSmokeTest.equalAngle(170f + YsmPoseSamplingSmokeTest.singleAxisNlerpDegrees(156f, 0.2f),
                middle.get("root").rotation()[0], "nlerp rotation of weighted controller layer");
        equal(1.88f, middle.get("root").scale()[0], "controller scale blend");
        equal(3.2f, middle.get("old-only").position()[0], "removed bone fades toward identity");
        equal(1.2f, middle.get("new-only").position()[0], "new bone blends from identity");
        incoming.put("root", pose(50, -170, 4));
        var changedInput = layer.compose(incoming);
        equal(18f, changedInput.get("root").position()[0], "new input remains live while outgoing snapshot stays fixed");
        equal(100f, independent.compose(Map.of("root", pose(100, 0, 1))).get("root").position()[0],
                "another controller layer remains independent");
        layer.beginTransition(YsmBlendTransition.linear(0.2f));
        equal(18f, layer.compose(Map.of("root", pose(0, 0, 1))).get("root").position()[0],
                "interrupted transition starts from last composed output");
        layer.advance(0.1f);
        equal(9f, layer.compose(Map.of("root", pose(0, 0, 1))).get("root").position()[0], "interrupted transition midpoint");
        layer.advance(0.1f);
        require(!layer.compose(Map.of("root", pose(0, 0, 1))).containsKey("old-only"), "completed transition releases obsolete bones");
        layer.beginTransition(YsmBlendTransition.linear(0f));
        equal(7f, layer.compose(Map.of("root", pose(7, 0, 1))).get("root").position()[0], "immediate transition");
        System.out.println("M3 controller layer snapshots, live inputs, curve blending and transition interruption passed.");
    }

    private static YsmBonePose pose(float position, float rotation, float scale) {
        return new YsmBonePose(new float[]{position, 0, 0}, new float[]{rotation, 0, 0}, new float[]{scale, scale, scale});
    }

    private static void equal(float expected, float actual, String message) {
        require(Float.isFinite(actual) && Math.abs(expected - actual) <= 1e-5f, message + ": " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
