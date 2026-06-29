package org.figuramc.figura.molang.storage;

import org.figuramc.figura.molang.runtime.AssignableVariable;
import org.figuramc.figura.molang.runtime.ExecutionContext;
import org.figuramc.figura.molang.runtime.Variable;
import org.figuramc.figura.molang.runtime.binding.ObjectBinding;
import org.figuramc.figura.molang.runtime.binding.ValueConversions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Molang variable storage for v.* (variable) and c.* (context/controller) namespaces.
 *
 * Implements {@link ObjectBinding} to enable parser dot-access (v.speed, c.count).
 * Each property name is lowercased and interned via {@link StringPool}.
 *
 * <p><b>关键设计</b>：{@link #getProperty(String)} 返回的是 {@link Variable} 对象，
 * 而非原始数值，这样解析器会将其编译为 {@link org.figuramc.figura.molang.parser.ast.VariableExpression}，
 * 在每帧求值时动态读取当前存储值，而非在解析时冻结为常量。</p>
 *
 * Reference: YSM VariableStorage
 */
public class VariableStorage implements AssignableVariable, ObjectBinding {

    private final Map<Integer, Object> scopedMap = new HashMap<>();
    private final Set<Integer> publicVariableNames = new HashSet<>();
    private Object fallbackValue = 0.0f;

    /** Cache of Variable wrappers for dot-access, indexed by StringPool ID */
    private final Map<Integer, Variable> variableViewCache = new HashMap<>();

    public VariableStorage() {
    }

    /**
     * Gets a variable value by its interned string ID.
     */
    public Object getScoped(int name) {
        return scopedMap.get(name);
    }

    /**
     * Sets a variable value by its interned string ID.
     */
    public void setScoped(int name, Object value) {
        scopedMap.put(name, value);
        publicVariableNames.add(name);
    }

    /**
     * Gets a public variable value.
     */
    public Object getPublic(int name) {
        return scopedMap.get(name);
    }

    /**
     * Sets the fallback value returned when a variable is not found.
     */
    public void setFallbackValue(Object fallbackValue) {
        this.fallbackValue = fallbackValue;
    }

    /**
     * ObjectBinding implementation: returns a {@link Variable} that dynamically reads
     * the stored value at evaluation time. This prevents values from being frozen
     * as constants during parsing.
     *
     * <p>The returned Variable wraps a lookup by interned StringPool ID.</p>
     */
    @Override
    public Object getProperty(String name) {
        int id = StringPool.computeIfAbsent(name.toLowerCase());
        return variableViewCache.computeIfAbsent(id, key ->
            (Variable) ctx -> scopedMap.getOrDefault(key, fallbackValue)
        );
    }

    /**
     * Iterates over all property names.
     */
    public void forEachPropertyName(Consumer<String> consumer) {
        for (int key : scopedMap.keySet()) {
            consumer.accept(StringPool.getString(key));
        }
    }

    /**
     * Clears all variables.
     */
    public void clear() {
        scopedMap.clear();
        publicVariableNames.clear();
        variableViewCache.clear();
    }

    @Override
    public Object evaluate(ExecutionContext<?> context) {
        // When accessed as a Variable, return this storage as a struct-like object.
        // The parser will use StructAccessExpression to access individual fields.
        return new VariableStorageStruct();
    }

    @Override
    public void assign(ExecutionContext<?> context, Object value) {
        // Direct assignment to the storage is not meaningful,
        // individual variables should be accessed via struct access (v.name = value)
    }

    /**
     * Struct wrapper that allows property access via StringPool IDs.
     */
    public class VariableStorageStruct implements org.figuramc.figura.molang.runtime.Struct {
        @Override
        public Object getProperty(int name) {
            return scopedMap.getOrDefault(name, fallbackValue);
        }

        @Override
        public void putProperty(int name, Object value) {
            scopedMap.put(name, value);
            publicVariableNames.add(name);
        }

        @Override
        public org.figuramc.figura.molang.runtime.Struct copy() {
            VariableStorage copyStorage = new VariableStorage();
            copyStorage.scopedMap.putAll(VariableStorage.this.scopedMap);
            copyStorage.fallbackValue = VariableStorage.this.fallbackValue;
            return copyStorage.new VariableStorageStruct();
        }
    }
}
