package org.figuramc.figura.molang.runtime.value;

import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;

import java.util.List;

/**
 * Top-level interface for Molang compiled values.
 * Wraps parsed expressions and provides typed evaluation methods
 * with built-in error handling and caching.
 *
 * <p>Three implementations:</p>
 * <ul>
 *   <li>{@link FloatValue} — constant float, zero allocation</li>
 *   <li>{@link MolangValue} — dynamic expression(s), with constant folding</li>
 *   <li>{@link RotationValue} — degree→radian auto-conversion</li>
 * </ul>
 *
 * Reference: YSM IValue
 */
public interface IValue {

    /**
     * Evaluates this value as a float.
     */
    float evalAsFloat(ExpressionEvaluator<?> evaluator);

    /**
     * Evaluates this value as an integer.
     */
    default int evalAsInt(ExpressionEvaluator<?> evaluator) {
        return Math.round(evalAsFloat(evaluator));
    }

    /**
     * Evaluates this value as a boolean (0.0 = false).
     */
    default boolean evalAsBoolean(ExpressionEvaluator<?> evaluator) {
        return evalAsFloat(evaluator) != 0.0f;
    }

    /**
     * Safely evaluates as float, returning fallback on error.
     */
    default float evalSafe(ExpressionEvaluator<?> evaluator, float fallback) {
        try {
            return evalAsFloat(evaluator);
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Returns the inner expression list (for inspection/debugging).
     */
    List<Expression> getExpressions();
}
