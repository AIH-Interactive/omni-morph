package org.figuramc.figura.molang.runtime;

import org.figuramc.figura.molang.parser.ast.Expression;

/**
 * Execution context interface for Molang evaluation.
 * Provides access to the entity being evaluated and methods
 * to evaluate expressions safely.
 *
 * Reference: YSM ExecutionContext
 */
public interface ExecutionContext<TEntity> {

    /**
     * Returns the entity associated with this context.
     */
    TEntity entity();

    /**
     * Evaluates a single expression.
     */
    Object eval(Expression expression);

    /**
     * Evaluates all expressions in the iterable.
     *
     * @param iterable The expressions to evaluate
     * @param z Whether to track return values (for scope execution)
     * @return The result value
     */
    Object evalAll(Iterable<Expression> iterable, boolean z);

    /**
     * Evaluates a single expression, returning null on error.
     */
    default Object evalSafe(Expression expression) {
        try {
            return eval(expression);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Evaluates all expressions safely.
     */
    default Object evalAllSafe(Iterable<Expression> iterable, boolean z) {
        try {
            return evalAll(iterable, z);
        } catch (Exception e) {
            return null;
        }
    }
}
