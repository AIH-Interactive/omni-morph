package org.figuramc.figura.model.ysm;

import org.figuramc.figura.model.ysm.animation.YsmAnimationClip;
import org.figuramc.figura.model.ysm.animation.YsmAnimationParser;
import org.figuramc.figura.molang.MolangEngine;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.molang.runtime.Variable;
import org.figuramc.figura.molang.runtime.binding.ObjectBinding;

public final class YsmAnimationWeightSmokeTest {
    private YsmAnimationWeightSmokeTest() {
    }

    public static void main(String[] args) {
        Variable weight = context -> ((float[]) context.entity())[0];
        ObjectBinding query = name -> name.equals("fixture_weight") ? weight : null;
        MolangEngine engine = MolangEngine.fromCustomBinding(name -> name.equals("q") ? query : null);
        float[] firstInstance = {0f};
        float[] secondInstance = {1f};
        var first = ExpressionEvaluator.evaluator(firstInstance);
        var second = ExpressionEvaluator.evaluator(secondInstance);
        YsmAnimationClip dynamic = parse("\"q.fixture_weight\"", engine);
        equal(0f, dynamic.evaluateBlendWeight(first), "zero dynamic weight");
        equal(1f, dynamic.evaluateBlendWeight(second), "same clip with independent evaluator");
        firstInstance[0] = 0.2f;
        equal(0.2f, dynamic.evaluateBlendWeight(first), "weight follows changed query");
        equal(1f, dynamic.evaluateBlendWeight(second), "other instance unchanged");
        firstInstance[0] = 1f;
        equal(1f, dynamic.evaluateBlendWeight(first), "full dynamic weight");
        firstInstance[0] = -1f;
        equal(0f, dynamic.evaluateBlendWeight(first), "negative weight suppressed");
        firstInstance[0] = Float.NaN;
        equal(0f, dynamic.evaluateBlendWeight(first), "nonfinite weight suppressed");
        equal(0f, dynamic.evaluateBlendWeight(null), "missing evaluator cannot invent a weight");
        equal(0.2f, parse("0.2", engine).evaluateBlendWeight(first), "numeric weight");
        equal(2f, parse("2", engine).evaluateBlendWeight(first), "positive weights above one retained");
        equal(0f, parse("\"q.fixture_weight +\"", engine).evaluateBlendWeight(first), "invalid expression fails closed");
        var defaultClip = new YsmAnimationClip("default", 1f, true);
        equal(1f, defaultClip.evaluateBlendWeight(null), "absent weight remains one");
        System.out.println("M3 dynamic clip weight and evaluator isolation checks passed; full pose/controller checks remain separate.");
    }

    private static YsmAnimationClip parse(String weight, MolangEngine engine) {
        return YsmAnimationParser.parse("{\"animations\":{\"fixture\":{\"animation_length\":1,\"blend_weight\":"
                + weight + "}}}", engine).get("fixture");
    }

    private static void equal(float expected, float actual, String message) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > 1e-5f) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
