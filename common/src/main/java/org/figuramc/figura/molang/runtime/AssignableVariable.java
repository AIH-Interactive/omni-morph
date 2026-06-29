package org.figuramc.figura.molang.runtime;

/**
 * Interface for a writable Molang variable.
 * Supports both reading (via Variable interface) and writing.
 *
 * Reference: YSM AssignableVariable
 */
public interface AssignableVariable extends Variable {
    /**
     * Assigns a value to this variable.
     */
    void assign(ExecutionContext<?> context, Object value);
}
