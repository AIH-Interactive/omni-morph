package org.figuramc.figura.molang.parser.ast;

import java.util.Objects;

/**
 * AST node for ternary conditional expressions: {@code condition ? trueVal : falseVal}.
 *
 * Follows Molang short-circuit semantics: only the selected branch is evaluated.
 *
 * Reference: YSM TernaryConditionalExpression
 */
public final class TernaryConditionalExpression implements Expression {

    private final Expression conditional;
    private final Expression trueExpression;
    private final Expression falseExpression;

    public TernaryConditionalExpression(
            Expression conditional,
            Expression trueExpression,
            Expression falseExpression
    ) {
        this.conditional = Objects.requireNonNull(conditional, "conditional");
        this.trueExpression = Objects.requireNonNull(trueExpression, "trueExpression");
        this.falseExpression = Objects.requireNonNull(falseExpression, "falseExpression");
    }

    public Expression condition() {
        return conditional;
    }

    public Expression trueExpression() {
        return trueExpression;
    }

    public Expression falseExpression() {
        return falseExpression;
    }

    @Override
    public <R> R visit(ExpressionVisitor<R> visitor) {
        return visitor.visitTernaryConditional(this);
    }

    @Override
    public String toString() {
        return "TernaryCondition(" + conditional + ", "
                + trueExpression + ", "
                + falseExpression + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TernaryConditionalExpression that = (TernaryConditionalExpression) o;
        return conditional.equals(that.conditional)
                && trueExpression.equals(that.trueExpression)
                && falseExpression.equals(that.falseExpression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conditional, trueExpression, falseExpression);
    }
}
