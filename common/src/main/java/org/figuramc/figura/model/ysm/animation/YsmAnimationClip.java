package org.figuramc.figura.model.ysm.animation;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.molang.runtime.MolangExecutionBudget;

public class YsmAnimationClip {
    public final String name;
    public final float length;
    public final boolean loop;
    public final LoopMode loopMode;
    public final Map<String, YsmBoneAnimation> boneAnimations = new HashMap<>();
    public final List<YsmAnimationEvent> events = new ArrayList<>();
    public float blendWeight = 1f;
    public List<Expression> blendWeightExpressions = List.of();

    public YsmAnimationClip(String name, float length, boolean loop) {
        this(name, length, loop ? LoopMode.LOOP : LoopMode.ONCE);
    }

    public YsmAnimationClip(String name, float length, LoopMode loopMode) {
        this.name = name;
        this.length = length;
        this.loopMode = loopMode == null ? LoopMode.ONCE : loopMode;
        this.loop = this.loopMode == LoopMode.LOOP;
    }

    public enum LoopMode {
        ONCE,
        LOOP,
        HOLD_ON_LAST_FRAME
    }

    public float evaluateBlendWeight(ExpressionEvaluator<?> evaluator) {
        float value = blendWeight;
        try {
            if (!blendWeightExpressions.isEmpty()) {
                if (evaluator == null) {
                    return 0f;
                }
                for (Expression expression : blendWeightExpressions) {
                    value = evaluator.evalAsFloat(expression);
                }
            }
        } catch (MolangExecutionBudget.Exceeded exhausted) {
            throw exhausted;
        } catch (RuntimeException e) {
            return 0f;
        }
        return Float.isFinite(value) && value > 0f ? value : 0f;
    }
}
