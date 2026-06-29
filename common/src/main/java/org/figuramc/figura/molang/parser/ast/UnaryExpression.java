package org.figuramc.figura.molang.parser.ast;

import java.util.Objects;

/**
 * AST node for unary expressions: logical negation (!),
 * arithmetical negation (-), plus (+), and return.
 *
 * Examples: {@code -hello}, {@code !p}, {@code return 5}
 *
 * Reference: YSM UnaryExpression
 */
public final class UnaryExpression implements Expression {

    private final Op op;
    private final Expression expression;

    public UnaryExpression(Op op, Expression expression) {
        this.op = Objects.requireNonNull(op, "op");
        this.expression = Objects.requireNonNull(expression, "expression");
    }

    public Op op() {
        return op;
    }

    public Expression expression() {
        return this.expression;
    }

    @Override
    public <R> R visit(ExpressionVisitor<R> visitor) {
        return visitor.visitUnary(this);
    }

    @Override
    public String toString() {
        return "Unary(" + op + ")(" + expression + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnaryExpression that = (UnaryExpression) o;
        if (op != that.op) return false;
        return expression.equals(that.expression);
    }

    @Override
    public int hashCode() {
        int result = op.hashCode();
        result = 31 * result + expression.hashCode();
        return result;
    }

    public enum Op {
        LOGICAL_NEGATION(2800),
        ARITHMETICAL_NEGATION(2800),
        PLUS(2800),
        RETURN(-1);

        final int precedence;

        Op(int precedence) {
            this.precedence = precedence;
        }

        public int precedence() {
            return precedence;
        }
    }
}
