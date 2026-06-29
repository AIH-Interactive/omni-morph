package org.figuramc.figura.molang.parser.ast;

import java.util.Objects;

/**
 * AST node for control flow statements: break and continue.
 *
 * Reference: YSM StatementExpression
 */
public final class StatementExpression implements Expression {

    private final Op op;

    public StatementExpression(Op op) {
        this.op = Objects.requireNonNull(op, "op");
    }

    public Op op() {
        return op;
    }

    @Override
    public <R> R visit(ExpressionVisitor<R> visitor) {
        return visitor.visitStatement(this);
    }

    public enum Op {
        BREAK,
        CONTINUE
    }
}
