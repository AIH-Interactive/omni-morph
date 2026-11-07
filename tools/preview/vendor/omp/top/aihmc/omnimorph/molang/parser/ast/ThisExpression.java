package top.aihmc.omnimorph.molang.parser.ast;

/**
 * Bedrock Molang {@code this}: the current value of the bone channel axis being evaluated
 * (used for incremental {@code -this} poses). It is 0 when no channel value was written.
 *
 * Reference: Sparkle ThisExpression
 */
public final class ThisExpression implements Expression {
    public static final ThisExpression INSTANCE = new ThisExpression();

    private ThisExpression() {
    }

    @Override
    public <R> R visit(ExpressionVisitor<R> expressionVisitor) {
        return expressionVisitor.visitThis(this);
    }

    @Override
    public String toString() {
        return "this";
    }
}
