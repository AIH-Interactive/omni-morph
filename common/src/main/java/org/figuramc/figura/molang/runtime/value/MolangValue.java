package org.figuramc.figura.molang.runtime.value;

import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.parser.ast.FloatExpression;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;

import java.util.Collections;
import java.util.List;

/**
 * Dynamic Molang expression value wrapper.
 * Wraps parsed {@link Expression} list and evaluates them on demand.
 *
 * <p>Features constant folding: if the expression list contains a single
 * {@link FloatExpression}, evaluation bypasses the evaluator entirely
 * and returns the constant directly (zero-allocation fast path).</p>
 *
 * Reference: YSM MolangValue
 */
public class MolangValue implements IValue {

    private final List<Expression> expressions;
    private final boolean isConstant;
    private final float constantValue;

    public MolangValue(List<Expression> expressions) {
        this.expressions = expressions != null ? expressions : Collections.emptyList();
        // Constant folding: single FloatExpression → fast path
        if (this.expressions.size() == 1) {
            Expression first = this.expressions.get(0);
            if (first instanceof FloatExpression fe) {
                this.isConstant = true;
                this.constantValue = fe.value();
            } else {
                this.isConstant = false;
                this.constantValue = 0.0f;
            }
        } else {
            this.isConstant = false;
            this.constantValue = 0.0f;
        }
    }

    @Override
    public float evalAsFloat(ExpressionEvaluator<?> evaluator) {
        // Constant folding fast path
        if (isConstant) return constantValue;

        // Dynamic evaluation
        if (expressions.isEmpty()) return 0.0f;
        if (expressions.size() == 1) {
            return evaluator.evalAsFloat(expressions.get(0));
        }
        return evaluator.evalAsFloat(expressions.get(expressions.size() - 1));
    }

    @Override
    public float evalSafe(ExpressionEvaluator<?> evaluator, float fallback) {
        try {
            return evalAsFloat(evaluator);
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    public List<Expression> getExpressions() {
        return expressions;
    }

    @Override
    public String toString() {
        return "MolangValue(" + expressions.size() + " exprs" +
                (isConstant ? ", const=" + constantValue : "") + ")";
    }
}
