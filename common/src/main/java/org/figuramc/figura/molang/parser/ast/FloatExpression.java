package org.figuramc.figura.molang.parser.ast;

import java.util.Objects;

/**
 * AST node for a float literal value.
 * Includes cached ZERO and ONE constants for common values.
 *
 * Reference: YSM FloatExpression
 */
public final class FloatExpression implements Expression {

    public static final FloatExpression ZERO = new FloatExpression(0.0f);
    public static final FloatExpression ONE = new FloatExpression(1.0f);

    private final float value;
    private final Float boxed;

    public FloatExpression(float value) {
        this.value = value;
        this.boxed = value;
    }

    public float value() {
        return this.value;
    }

    public Float boxed() {
        return this.boxed;
    }

    @Override
    public <R> R visit(ExpressionVisitor<R> expressionVisitor) {
        return expressionVisitor.visitFloat(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        return obj != null && getClass() == obj.getClass()
                && Float.compare(((FloatExpression) obj).value, this.value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "Float(" + value + ")";
    }
}
