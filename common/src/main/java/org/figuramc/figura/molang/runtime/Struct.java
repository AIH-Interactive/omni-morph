package org.figuramc.figura.molang.runtime;

/**
 * Interface for Molang struct/object types.
 * Supports property access by interned string ID.
 *
 * Reference: YSM Struct
 */
public interface Struct {
    /**
     * Gets a property value by its interned string ID.
     */
    Object getProperty(int name);

    /**
     * Sets a property value by its interned string ID.
     */
    void putProperty(int name, Object value);

    /**
     * Creates a defensive copy of this struct.
     */
    Struct copy();
}
