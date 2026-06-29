package org.figuramc.figura.molang.parser.ast;

/**
 * AST node for binary operation/index access expressions.
 * Used for array index access like arr[index].
 *
 * Reference: YSM BinaryOperationExpression
 */
public class BinaryOperationExpression implements Expression {

    private final Expression left;
    private final Expression right;

    public BinaryOperationExpression(Expression left, Expression right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public <R> R visit(ExpressionVisitor<R> visitor) {
        return visitor.visitBinaryOperation(this);
    }

    public Expression getLeft() {
        return this.left;
    }

    public Expression getRight() {
        return this.right;
    }
}
