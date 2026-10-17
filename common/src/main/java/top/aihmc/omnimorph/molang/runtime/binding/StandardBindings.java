package top.aihmc.omnimorph.molang.runtime.binding;

import top.aihmc.omnimorph.molang.parser.ast.AssignableVariableExpression;
import top.aihmc.omnimorph.molang.parser.ast.ExecutionScopeExpression;
import top.aihmc.omnimorph.molang.parser.ast.Expression;
import top.aihmc.omnimorph.molang.runtime.AssignableVariable;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluatorImpl;
import top.aihmc.omnimorph.molang.runtime.Function;

/**
 * Standard built-in Molang functions including loop and for_each.
 *
 * Reference: YSM StandardBindings
 */
public final class StandardBindings {

    private static final int MAX_LOOP_ROUND = 1024;

    private StandardBindings() {
    }

    /**
     * loop(count, { body }) - executes the body `count` times
     */
    public static final Function LOOP_FUNC = (ctx, args) -> {
        if (args.size() < 2) return null;
        int n = Math.min((int) Math.round(args.getAsDouble(ctx, 0)), MAX_LOOP_ROUND);
        Expression expr = args.getExpression(1);
        if (expr instanceof ExecutionScopeExpression) {
            ((ExpressionEvaluatorImpl<?>) ctx).loopFunction((ExecutionScopeExpression) expr, n);
        }
        return null;
    };

    /**
     * for_each(variable, array, { body }) - iterates over array elements
     */
    public static final Function FOR_EACH_FUNC = (ctx, args) -> {
        if (args.size() != 3) return null;
        Expression variableExpr = args.getExpression(0);
        if (!(variableExpr instanceof AssignableVariableExpression)) {
            return null;
        }
        final AssignableVariable variableAccess = ((AssignableVariableExpression) variableExpr).target();
        Expression exper = args.getExpression(2);
        if (exper instanceof ExecutionScopeExpression executionScopeExpression) {
            Object obj = args.getValue(ctx, 1);
            if (obj instanceof Iterable) {
                ((ExpressionEvaluatorImpl<?>) ctx).forEachFunction(
                        executionScopeExpression, variableAccess, (Iterable<?>) obj);
            }
            return null;
        }
        return null;
    };
}
