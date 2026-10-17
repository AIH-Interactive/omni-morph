package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.binding.MolangBindings;

/** Checks the YSM mixed angle convention through normal and float evaluation. */
public final class YsmMolangMathSmokeTest {
    public static void main(String[] args) throws Exception {
        var math = new MolangBindings.MathBinding();
        var engine = MolangEngine.fromCustomBinding(name -> "math".equals(name) ? math : null);
        var evaluator = ExpressionEvaluator.evaluator(new Object());
        String[] expressions = {
                "math.sin(90)", "math.cos(180)", "math.asin(0.5)", "math.acos(0.5)",
                "math.atan(1)", "math.atan2(1,-1)", "math.atan2(1,1)*180/math.pi"
        };
        double[] expected = {1, -1, Math.PI / 6, Math.PI / 3, Math.PI / 4, Math.PI * 3 / 4, 45};
        for (int index = 0; index < expressions.length; index++) {
            var expression = engine.parse(expressions[index]).getFirst();
            equal(expected[index], ((Number) evaluator.eval(expression)).doubleValue(), expressions[index]);
            equal(expected[index], evaluator.evalAsFloat(expression), expressions[index] + " float path");
        }
        System.out.println("PASS YSM degree sin/cos, radian inverse trig and authored bone angle conversion");
    }

    private static void equal(double expected, double actual, String message) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.0001) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }
}
