package org.figuramc.figura.molang.parser.ast;

import org.figuramc.figura.molang.runtime.Variable;

import java.util.Objects;

/**
 * AST node representing a read-only variable reference.
 * The variable is resolved at parse time.
 *
 * Reference: YSM VariableExpression
 */
public class VariableExpression implements Expression {

    private final Variable target;

    public VariableExpression(Variable target) {
        Objects.requireNonNull(target, "target");
        this.target = target;
    }

    public Variable target() {
        return target;
    }

    @Override
    public <R> R visit(ExpressionVisitor<R> visitor) {
        return visitor.visitVariable(this);
    }

    @Override
    public String toString() {
        return target.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VariableExpression that = (VariableExpression) o;
        return target.equals(that.target);
    }

    @Override
    public int hashCode() {
        return target.hashCode();
    }
}
