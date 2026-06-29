package org.figuramc.figura.molang.runtime;

import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.binding.ValueConversions;

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
