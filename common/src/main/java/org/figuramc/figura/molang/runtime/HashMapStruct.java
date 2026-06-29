package org.figuramc.figura.molang.runtime;

import org.figuramc.figura.molang.storage.StringPool;

import java.util.HashMap;
import java.util.Map;

/**
 * Default HashMap-based implementation of Molang Struct.
 * Stores properties in a standard HashMap keyed by interned string IDs.
 *
 * Reference: YSM HashMapStruct
 */
public class HashMapStruct implements Struct {
    private final Map<Integer, Object> properties;
    private final boolean isRightValue;

    public HashMapStruct() {
        this(false);
    }

    public HashMapStruct(boolean isRightValue) {
        this.properties = new HashMap<>();
        this.isRightValue = isRightValue;
    }

    private HashMapStruct(Map<Integer, Object> properties) {
        this.properties = properties;
        this.isRightValue = false;
    }

    @Override
    public Object getProperty(int name) {
        return properties.get(name);
    }

    @Override
    public void putProperty(int name, Object value) {
        properties.put(name, value);
    }

    @Override
    public Struct copy() {
        if (isRightValue) {
            return new HashMapStruct(properties);
        } else {
            return new HashMapStruct(new HashMap<>(properties));
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("struct{");
        boolean first = true;
        for (Map.Entry<Integer, Object> entry : properties.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(StringPool.getString(entry.getKey()))
                    .append("=")
                    .append(entry.getValue() == null ? "null" : entry.getValue().toString());
        }
        builder.append("}");
        return builder.toString();
    }
}
