package org.figuramc.figura.molang.runtime;

/**
 * Interface for a read-only Molang variable.
 * Variables are resolved by name through the binding system.
 *
 * Reference: YSM Variable
 */
public interface Variable {
    /**
     * Evaluates this variable in the given context.
     */
    Object evaluate(ExecutionContext<?> context);
}
