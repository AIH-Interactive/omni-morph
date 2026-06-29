package org.figuramc.figura.molang.parser.ast;

import java.util.List;
import java.util.Objects;

/**
 * AST node for execution scope expressions: {@code { stmt1; stmt2; resultExpr }}.
 *
 * Executes a sequence of statements and returns the value of the last expression.
 *
 * Reference: YSM ExecutionScopeExpression
 */
public final class ExecutionScopeExpression implements Expression {

    private final List<Expression> expressions;

    public ExecutionScopeExpression(List<Expression> expressions) {
        this.expressions = Objects.requireNonNull(expressions, "expressions");
    }

    public List<Expression> expressions() {
        return expressions;
    }

    @Override
    public <R> R visit(ExpressionVisitor<R> visitor) {
        return visitor.visitExecutionScope(this);
    }

    @Override
    public String toString() {
        return "ExecutionScope(" + this.expressions + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionScopeExpression that = (ExecutionScopeExpression) o;
        return expressions.equals(that.expressions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expressions);
    }
}
