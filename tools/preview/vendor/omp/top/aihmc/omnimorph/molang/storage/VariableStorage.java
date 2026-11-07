package top.aihmc.omnimorph.molang.storage;

import top.aihmc.omnimorph.molang.runtime.AssignableVariable;
import top.aihmc.omnimorph.molang.runtime.ExecutionContext;
import top.aihmc.omnimorph.molang.runtime.HashMapStruct;
import top.aihmc.omnimorph.molang.runtime.Struct;
import top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding;
import top.aihmc.omnimorph.molang.runtime.binding.ValueConversions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
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
 * 而非原始数值，这样解析器会将其编译为 {@link top.aihmc.omnimorph.molang.parser.ast.VariableExpression}，
 * 在每帧求值时动态读取当前存储值，而非在解析时冻结为常量。</p>
 *
 * Reference: YSM VariableStorage
 */
public class VariableStorage implements AssignableVariable, ObjectBinding {
    private static final int ROAMING_ID = StringPool.computeIfAbsent("roaming");

    private final Map<Integer, Object> scopedMap = new HashMap<>();
    private final Set<Integer> publicVariableNames = new HashSet<>();
    private Object fallbackValue = null;

    /** Cache of scoped variable wrappers for dot-access, indexed by StringPool ID */
    private final Map<Integer, AssignableVariable> variableViewCache = new HashMap<>();

    public VariableStorage() {
        initRoamingStruct();
    }

    /** Imports detached model variables without retaining the source evaluator's mutable storage. */
    public void copyValuesFrom(VariableStorage source) {
        if (source == this) {
            return;
        }
        clear();
        IdentityHashMap<Object, Object> copies = new IdentityHashMap<>();
        copies.put(source, new VariableStorageStruct());
        copyValues(source, copies, 0);
    }

    private void copyValues(VariableStorage source, IdentityHashMap<Object, Object> copies, int depth) {
        source.scopedMap.forEach((name, value) -> scopedMap.put(name, detachedValue(value, copies, depth + 1)));
        publicVariableNames.addAll(source.publicVariableNames);
        fallbackValue = detachedValue(source.fallbackValue, copies, depth + 1);
    }

    private static Object detachedValue(Object value, IdentityHashMap<Object, Object> copies, int depth) {
        if (value == null) {
            return null;
        }
        if (copies.containsKey(value)) {
            return copies.get(value);
        }
        if (depth > 64 || copies.size() > 16384) {
            throw new IllegalArgumentException("Model value copy limit exceeded");
        }
        if (value instanceof HashMapStruct source) {
            HashMapStruct copy = new HashMapStruct(source.isRightValue());
            copies.put(value, copy);
            source.forEachProperty((name, child) -> copy.putProperty(name, detachedValue(child, copies, depth + 1)));
            return copy;
        }
        if (value instanceof VariableStorage.VariableStorageStruct source) {
            VariableStorage storage = source.storage();
            if (copies.containsKey(storage)) {
                return copies.get(storage);
            }
            VariableStorage copy = new VariableStorage();
            Struct view = copy.new VariableStorageStruct();
            copies.put(value, view);
            copies.put(storage, view);
            copy.copyValues(storage, copies, depth + 1);
            return view;
        }
        if (value instanceof RoamingStruct source) {
            RoamingStruct copy = new RoamingStruct();
            copy.values.putAll(source.values);
            copies.put(value, copy);
            return copy;
        }
        if (value instanceof Struct source) {
            Struct copy = source.copy();
            copies.put(value, copy);
            return copy;
        }
        if (value instanceof List<?> source) {
            List<Object> copy = new ArrayList<>();
            copies.put(value, copy);
            source.forEach(child -> copy.add(detachedValue(child, copies, depth + 1)));
            return copy;
        }
        return value;
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

    /** Saves only the supplied engine fields, leaving model variables in the same storage intact. */
    public Scope preserveScopedValues(Set<Integer> names) {
        return new Scope(names);
    }

    public final class Scope implements AutoCloseable {
        private final Map<Integer, Object> values = new HashMap<>();
        private final Set<Integer> presentNames = new HashSet<>();
        private final Set<Integer> publicNames = new HashSet<>();
        private boolean closed;

        private Scope(Set<Integer> names) {
            for (int name : names) {
                values.put(name, scopedMap.get(name));
                if (scopedMap.containsKey(name)) {
                    presentNames.add(name);
                }
                if (publicVariableNames.contains(name)) {
                    publicNames.add(name);
                }
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            values.forEach((name, value) -> {
                if (presentNames.contains(name)) {
                    scopedMap.put(name, value);
                } else {
                    scopedMap.remove(name);
                }
                if (publicNames.contains(name)) {
                    publicVariableNames.add(name);
                } else {
                    publicVariableNames.remove(name);
                }
            });
        }
    }

    public void setPath(String path, Object value) {
        String normalized = normalizePath(path);
        if (normalized.isBlank())
            return;
        String[] parts = normalized.split("\\.");
        if (parts.length == 1) {
            setScoped(StringPool.computeIfAbsent(parts[0]), value);
            return;
        }
        Struct current = struct(StringPool.computeIfAbsent(parts[0]));
        for (int i = 1; i < parts.length - 1; i++) {
            int id = StringPool.computeIfAbsent(parts[i]);
            Object child = current.getProperty(id);
            Struct next;
            if (child instanceof Struct struct) {
                next = struct;
            } else {
                next = new HashMapStruct();
                current.putProperty(id, next);
            }
            current = next;
        }
        current.putProperty(StringPool.computeIfAbsent(parts[parts.length - 1]), value);
    }

    public Object getPath(String path) {
        String normalized = normalizePath(path);
        if (normalized.isBlank())
            return null;
        String[] parts = normalized.split("\\.");
        Object current = scopedMap.get(StringPool.computeIfAbsent(parts[0]));
        for (int i = 1; i < parts.length; i++) {
            if (!(current instanceof Struct struct))
                return null;
            current = struct.getProperty(StringPool.computeIfAbsent(parts[i]));
        }
        return current;
    }

    private static String normalizePath(String path) {
        if (path == null)
            return "";
        String value = path.trim().toLowerCase();
        if (value.startsWith("v."))
            return value.substring(2);
        if (value.startsWith("variable."))
            return value.substring("variable.".length());
        return value;
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
     * ObjectBinding implementation: returns an {@link AssignableVariable} that dynamically reads
     * the stored value at evaluation time. This prevents values from being frozen
     * as constants during parsing.
     *
     * <p>The returned Variable wraps a lookup by interned StringPool ID.</p>
     */
    @Override
    public Object getProperty(String name) {
        int id = StringPool.computeIfAbsent(name.toLowerCase());
        return variableViewCache.computeIfAbsent(id, ScopedVariable::new);
    }

    private final class ScopedVariable implements AssignableVariable {
        private final int name;

        private ScopedVariable(int name) {
            this.name = name;
        }

        @Override
        public Object evaluate(ExecutionContext<?> context) {
            return scopedMap.getOrDefault(name, fallbackValue);
        }

        @Override
        public void assign(ExecutionContext<?> context, Object value) {
            setScoped(name, value);
        }
    }

    private Struct struct(int key) {
        Object value = scopedMap.get(key);
        if (value instanceof Struct struct)
            return struct;
        Struct struct = new HashMapStruct();
        scopedMap.put(key, struct);
        publicVariableNames.add(key);
        return struct;
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
        initRoamingStruct();
    }

    private void initRoamingStruct() {
        scopedMap.put(ROAMING_ID, new RoamingStruct());
        publicVariableNames.add(ROAMING_ID);
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
    public class VariableStorageStruct implements top.aihmc.omnimorph.molang.runtime.Struct {
        private VariableStorage storage() {
            return VariableStorage.this;
        }

        @Override
        public Object getProperty(int name) {
            Object value = scopedMap.get(name);
            if (value != null)
                return value;
            return scopedMap.getOrDefault(name, fallbackValue);
        }

        @Override
        public void putProperty(int name, Object value) {
            scopedMap.put(name, value);
            publicVariableNames.add(name);
        }

        @Override
        public top.aihmc.omnimorph.molang.runtime.Struct copy() {
            VariableStorage copyStorage = new VariableStorage();
            copyStorage.scopedMap.putAll(VariableStorage.this.scopedMap);
            copyStorage.fallbackValue = VariableStorage.this.fallbackValue;
            return copyStorage.new VariableStorageStruct();
        }
    }

    private static final class RoamingStruct implements Struct {
        private final Map<Integer, Float> values = new HashMap<>();

        @Override
        public Object getProperty(int name) {
            return values.getOrDefault(name, 0f);
        }

        @Override
        public void putProperty(int name, Object value) {
            values.put(name, ValueConversions.asFloat(value));
        }

        @Override
        public Struct copy() {
            HashMapStruct copy = new HashMapStruct(true);
            for (Map.Entry<Integer, Float> entry : values.entrySet())
                copy.putProperty(entry.getKey(), entry.getValue());
            return copy;
        }
    }
}
