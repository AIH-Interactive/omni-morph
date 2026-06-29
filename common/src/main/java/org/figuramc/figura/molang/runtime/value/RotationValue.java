package org.figuramc.figura.molang.runtime.value;

import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;

import java.util.List;

/**
 * Rotation value wrapper for Molang.
 * Automatically converts degrees to radians on evaluation,
 * matching Molang's expected behavior where rotation values
 * are in degrees but Math functions expect radians.
 *
 * <p>Supports {@code inverse} flag for reversed rotation direction.</p>
 *
 * Reference: YSM RotationValue
 */
public class RotationValue implements IValue {

    private final IValue wrapped;
    private final boolean inverse;

    public RotationValue(IValue wrapped, boolean inverse) {
        this.wrapped = wrapped;
        this.inverse = inverse;
    }

    public RotationValue(IValue wrapped) {
        this(wrapped, false);
    }

    @Override
    public float evalAsFloat(ExpressionEvaluator<?> evaluator) {
        float degrees = wrapped.evalAsFloat(evaluator);
        float radians = (float) Math.toRadians(degrees);
        return inverse ? -radians : radians;
    }

    @Override
    public List<Expression> getExpressions() {
        return wrapped.getExpressions();
    }

    public boolean isInverse() {
        return inverse;
    }

    @Override
    public String toString() {
        return "RotationValue(" + wrapped + (inverse ? ", inverse" : "") + ")";
    }
}
