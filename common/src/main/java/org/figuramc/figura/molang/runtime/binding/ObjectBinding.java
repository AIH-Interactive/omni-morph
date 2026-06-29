package org.figuramc.figura.molang.runtime.binding;

/**
 * Represents an object-like binding for Molang name resolution.
 * These objects can have properties (or fields) that can be read
 * during parsing and evaluation.
 *
 * The top-level binding resolves names like "math", "query", "v", "c", "t".
 * Nested bindings resolve names like "math.sin", "query.anim_time", etc.
 *
 * Reference: YSM ObjectBinding
 */
public interface ObjectBinding {
    ObjectBinding EMPTY = name -> null;

    /**
     * Gets the property value in this object with the given name.
     *
     * @param name The property name (lowercase)
     * @return The resolved value (can be a Number, String, Function, Variable,
     *         AssignableVariable, or another ObjectBinding), or null if not found
     */
    Object getProperty(String name);
}
