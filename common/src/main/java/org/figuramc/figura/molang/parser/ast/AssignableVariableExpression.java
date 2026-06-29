package org.figuramc.figura.molang.parser.ast;

import org.figuramc.figura.molang.runtime.AssignableVariable;

import java.util.Objects;

/**
 * AST node representing a writable variable reference.
 * Used for assignment targets like v.name = value.
 *
 * Reference: YSM AssignableVariableExpression
 */
public class AssignableVariableExpression implements Expression {

    private final AssignableVariable target;

    public AssignableVariableExpression(AssignableVariable target) {
        Objects.requireNonNull(target, "target");
        this.target = target;
    }

    public AssignableVariable target() {
        return target;
    }

    @Override
    public <R> R visit(ExpressionVisitor<R> visitor) {
        return visitor.visitAssignableVariable(this);
    }

    @Override
    public String toString() {
        return target.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssignableVariableExpression that = (AssignableVariableExpression) o;
        return target.equals(that.target);
    }

    @Override
    public int hashCode() {
        return target.hashCode();
    }
}
