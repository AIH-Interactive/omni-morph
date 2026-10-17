package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.model.ysm.controller.YsmBlendTransition;
import top.aihmc.omnimorph.molang.MolangEngine;

import java.util.LinkedHashMap;
import java.util.Map;

public final class YsmBlendTransitionSmokeTest {
    private YsmBlendTransitionSmokeTest() {
    }

    public static void main(String[] args) {
        var controller = YsmAnimationControllerParser.parse("""
                {"animation_controllers":{"fixture":{"states":{"default":{
                  "blend_transition":{"0.05":0,"0.02":0.648,"0.0":1,"0.01":0.896,"0.03":0.352,"0.04":0.104}
                }}}}}
                """, MolangEngine.createEmpty()).get("fixture");
        var curve = controller.states().get("default").blendCurve();
        equal(0.05f, curve.duration(), "curve duration");
        equal(0f, curve.progress(-1f), "before first point");
        equal(0.104f, curve.progress(0.01f), "outgoing value converted to incoming progress");
        equal(0.228f, curve.progress(0.015f), "independent piecewise interpolation expectation");
        equal(0.352f, curve.progress(0.02f), "point value");
        equal(1f, curve.progress(0.05f), "last point");
        equal(1f, curve.progress(1f), "after duration");
        equal(0.5f, YsmBlendTransition.linear(0.2f).progress(0.1f), "numeric transition");
        equal(1f, YsmBlendTransition.linear(0f).progress(0f), "immediate transition");
        Map<Float, Float> points = new LinkedHashMap<>(Map.of(0f, 1f, 1f, 0f));
        var independent = YsmBlendTransition.curve(points);
        points.put(1f, 1f);
        equal(1f, independent.progress(1f), "caller mutation does not alter curve");
        reject(() -> YsmBlendTransition.linear(Float.NaN));
        reject(() -> YsmBlendTransition.curve(Map.of(-1f, 1f)));
        reject(() -> independent.progress(Float.POSITIVE_INFINITY));
        System.out.println("M3 reference-shaped blend curve parsing and interpolation passed; pose integration remains separate.");
    }

    private static void reject(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Invalid transition accepted");
        } catch (IllegalArgumentException expected) {
            // Invalid transition data must not become a nonfinite pose weight.
        }
    }

    private static void equal(float expected, float actual, String message) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > 1e-5f) {
            throw new AssertionError(message);
        }
    }
}
