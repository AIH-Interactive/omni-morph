package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.binding.MolangBindings;
import org.figuramc.figura.avatar.Avatar;

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
        String[] aligned = {"math.min_angle(200)", "math.min_angle(-540)", "math.hermite_blend(0.5)",
                "math.hermite_blend(1.5)", "math.pow(-1,0.5)", "math.lerprotate(720,90,0.5)",
                "math.random_integer(3,4)", "math.random_integer(4,3)",
                "math.die_roll(3,2,2)", "math.die_roll_integer(3,2,3)", "math.die_roll_integer(2.6,3,2)"};
        double[] alignedExpected = {-160, -180, 1, -4, 0, 45, 3, 3, 6, 6, 6};
        for (int index = 0; index < aligned.length; index++) {
            var expression = engine.parse(aligned[index]).getFirst();
            equal(alignedExpected[index], evaluator.evalAsFloat(expression), aligned[index]);
        }
        if (!Float.isNaN(evaluator.evalAsFloat(engine.parse("math.asin(2)").getFirst()))
                || !Float.isNaN(evaluator.evalAsFloat(engine.parse("math.acos(-2)").getFirst()))) {
            throw new AssertionError("reference inverse trig preserves NaN outside its domain");
        }
        Avatar.MolangContext first = new Avatar.MolangContext(null);
        Avatar.MolangContext second = new Avatar.MolangContext(null);
        var random = engine.parse("math.random(8,2,123)").getFirst();
        first.random.setSeed(42L);
        second.random.setSeed(42L);
        float firstValue = ExpressionEvaluator.evaluator(first).evalAsFloat(random);
        equal(firstValue, ExpressionEvaluator.evaluator(second).evalAsFloat(random), "independent model random streams");
        if (firstValue < 2f || firstValue >= 8f) {
            throw new AssertionError("reversed random bounds use the reference half-open interval");
        }
        System.out.println("PASS YSM degree sin/cos, radian inverse trig and authored bone angle conversion");
    }

    private static void equal(double expected, double actual, String message) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.0001) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }
}
