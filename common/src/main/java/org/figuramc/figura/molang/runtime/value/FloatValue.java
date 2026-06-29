package org.figuramc.figura.molang.runtime.value;

import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.parser.ast.FloatExpression;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;

import java.util.Collections;
import java.util.List;

/**
 * Constant float value wrapper. Zero-allocation evaluation —
 * simply returns the stored float constant.
 *
 * Pre-cached constants: {@link #ZERO}, {@link #ONE}.
 *
 * Reference: YSM FloatValue
 */
public final class FloatValue implements IValue {

    public static final FloatValue ZERO = new FloatValue(0.0f);
    public static final FloatValue ONE = new FloatValue(1.0f);

    private final float value;
    private final Float boxed;
    private final Expression expression;

    public FloatValue(float value) {
        this.value = Float.isNaN(value) ? 0.0f : value;
        this.boxed = this.value;
        this.expression = new FloatExpression(this.value);
    }

    public float value() {
        return value;
    }

    public Float boxed() {
        return boxed;
    }

    @Override
    public float evalAsFloat(ExpressionEvaluator<?> evaluator) {
        return value;
    }

    @Override
    public List<Expression> getExpressions() {
        return Collections.singletonList(expression);
    }

    @Override
    public String toString() {
        return "FloatValue(" + value + ")";
    }
}
