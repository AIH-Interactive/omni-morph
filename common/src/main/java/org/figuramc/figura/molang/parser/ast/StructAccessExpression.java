package org.figuramc.figura.molang.parser.ast;

import org.figuramc.figura.molang.storage.StringPool;

/**
 * AST node for struct field access expressions: {@code expr.field}.
 *
 * Evaluates the left expression, then accesses the named field on the resulting struct.
 *
 * Reference: YSM StructAccessExpression
 */
public class StructAccessExpression implements Expression {

    private final Expression left;
    private final int path;

    public StructAccessExpression(Expression expression, String path) {
        this.left = expression;
        this.path = StringPool.computeIfAbsent(path);
    }

    @Override
    public <R> R visit(ExpressionVisitor<R> visitor) {
        return visitor.visitStruct(this);
    }

    public Expression left() {
        return left;
    }

    public int path() {
        return path;
    }
}
