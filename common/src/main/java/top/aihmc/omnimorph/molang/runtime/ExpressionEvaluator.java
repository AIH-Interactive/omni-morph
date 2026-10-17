package top.aihmc.omnimorph.molang.runtime;

import top.aihmc.omnimorph.molang.parser.ast.Expression;
import top.aihmc.omnimorph.molang.runtime.binding.ValueConversions;

/**
 * Interface for Molang expression evaluation.
 * Provides typed evaluation methods and factory methods.
 *
 * Reference: YSM ExpressionEvaluator
 */
public interface ExpressionEvaluator<TEntity> extends ExecutionContext<TEntity> {

    static <TEntity> ExpressionEvaluator<TEntity> evaluator(TEntity entity) {
        return new ExpressionEvaluatorImpl<>(entity);
    }

    default float evalAsFloat(Expression expression) {
        return ValueConversions.asFloat(eval(expression));
    }

    default boolean evalAsBoolean(Expression expression) {
        return ValueConversions.asBoolean(eval(expression));
    }
}
