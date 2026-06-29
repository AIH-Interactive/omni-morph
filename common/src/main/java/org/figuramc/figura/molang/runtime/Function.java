package org.figuramc.figura.molang.runtime;

import org.figuramc.figura.molang.parser.ast.Expression;

import java.util.Collections;
import java.util.List;

/**
 * Interface for Molang callable functions.
 * Functions are first-class values that can be called with arguments.
 *
 * Reference: YSM Function
 */
@FunctionalInterface
public interface Function {

    /**
     * Executes this function with the given arguments.
     *
     * @param context   The execution context
     * @param arguments The function arguments
     * @return The function result
     */
    Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments);

    /**
     * Validates the argument count for this function.
     * Override to enforce specific argument counts.
     */
    default boolean validateArgumentSize(int size) {
        return true;
    }

    ArgumentCollection EMPTY_ARGUMENT = new ArgumentCollection(Collections.emptyList());

    Function NOOP = (executionContext, argumentCollection) -> null;

    /**
     * Wrapper for a list of argument expressions.
     * Provides typed access to evaluated argument values.
     */
    class ArgumentCollection {

        private final List<Expression> arguments;

        public ArgumentCollection(List<Expression> arguments) {
            this.arguments = arguments;
        }

        public int size() {
            return this.arguments.size();
        }

        public String getAsString(ExecutionContext<?> ctx, int index) {
            return org.figuramc.figura.molang.runtime.binding.ValueConversions.asString(
                    ctx.evalSafe(this.arguments.get(index)));
        }

        public double getAsDouble(ExecutionContext<?> ctx, int index) {
            return org.figuramc.figura.molang.runtime.binding.ValueConversions.asDouble(
                    ctx.evalSafe(this.arguments.get(index)));
        }

        public int getAsInt(ExecutionContext<?> ctx, int index) {
            return org.figuramc.figura.molang.runtime.binding.ValueConversions.asInt(
                    ctx.evalSafe(this.arguments.get(index)));
        }

        public float getAsFloat(ExecutionContext<?> ctx, int index) {
            return org.figuramc.figura.molang.runtime.binding.ValueConversions.asFloat(
                    ctx.evalSafe(this.arguments.get(index)));
        }

        public boolean getAsBoolean(ExecutionContext<?> ctx, int index) {
            return org.figuramc.figura.molang.runtime.binding.ValueConversions.asBoolean(
                    ctx.evalSafe(this.arguments.get(index)));
        }

        public Object getValue(ExecutionContext<?> ctx, int index) {
            return ctx.evalSafe(this.arguments.get(index));
        }

        public Expression getExpression(int i) {
            return this.arguments.get(i);
        }
    }
}
