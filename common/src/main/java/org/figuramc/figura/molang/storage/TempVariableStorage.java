package org.figuramc.figura.molang.storage;

import org.figuramc.figura.molang.runtime.AssignableVariable;
import org.figuramc.figura.molang.runtime.ExecutionContext;
import org.figuramc.figura.molang.runtime.binding.ObjectBinding;

import java.util.*;

/**
 * Molang temp variable storage for t.* (temp) namespace.
 * Uses a stack-based structure to support scoped variable access.
 *
 * Implements {@link ObjectBinding} to enable parser dot-access (t.x, t.temp_var).
 *
 * Reference: YSM TempVariableStorage
 */
public class TempVariableStorage implements AssignableVariable, ObjectBinding {

    private static final int MAX_DEPTH = 32;
    private static final int INITIAL_CAPACITY = 16;

    private int baseOffset;
    private int currentSize;
    private int scopeStart;
    private int scopeSize;
    private Object[] elements = new Object[INITIAL_CAPACITY];
    private final Deque<Long> scopeStack = new ArrayDeque<>(4);
    private final ElementListView listView = new ElementListView();

    private void ensureCapacity(int capacity) {
        if (elements.length < capacity) {
            int newSize = elements.length;
            while (newSize < capacity) {
                newSize *= 2;
            }
            elements = Arrays.copyOf(elements, newSize);
        }
    }

    public Object getElement(int index) {
        if (index < currentSize) {
            return elements[baseOffset + index];
        }
        return null;
    }

    public void setElement(int index, Object obj) {
        int requiredSize = index + 1;
        if (currentSize < requiredSize) {
            currentSize = requiredSize;
            ensureCapacity(baseOffset + requiredSize);
        }
        elements[baseOffset + index] = obj;
    }

    public boolean pushScope(List<?> list) {
        if (scopeStack.size() >= MAX_DEPTH) return false;

        int start = baseOffset + currentSize;
        int size = list.size();
        int end = start + size;
        ensureCapacity(end);

        for (int i = 0; i < size; i++) {
            elements[start + i] = list.get(i);
        }

        scopeStack.push(((long) scopeSize << 32) | scopeStart);
        scopeStart = start;
        scopeSize = size;
        baseOffset = end;
        currentSize = 0;
        return true;
    }

    public void popScope() {
        if (scopeStack.isEmpty()) return;

        long packed = scopeStack.pop();
        scopeStart = (int) (packed & 0xFFFFFFFFL);
        scopeSize = (int) (packed >> 32);
        baseOffset = scopeStart + scopeSize;
        currentSize = 0;
    }

    public List<Object> asList() {
        return listView;
    }

    /**
     * ObjectBinding implementation: returns a {@link org.figuramc.figura.molang.runtime.Variable}
     * that reads the indexed temp variable at evaluation time.
     * This prevents values from being frozen as constants during parsing.
     */
    @Override
    public Object getProperty(String name) {
        int index = StringPool.computeIfAbsent(name.toLowerCase());
        return (org.figuramc.figura.molang.runtime.Variable) ctx -> getElement(index);
    }

    @Override
    public Object evaluate(ExecutionContext<?> context) {
        // Return this as a struct-like object for property access
        return new TempStructView();
    }

    @Override
    public void assign(ExecutionContext<?> context, Object value) {
        // Direct assignment to temp storage is handled via struct access
    }

    /**
     * Struct view for temp variable access.
     */
    public class TempStructView implements org.figuramc.figura.molang.runtime.Struct {
        @Override
        public Object getProperty(int name) {
            return getElement(name);
        }

        @Override
        public void putProperty(int name, Object value) {
            setElement(name, value);
        }

        @Override
        public org.figuramc.figura.molang.runtime.Struct copy() {
            TempStructView copy = new TempStructView();
            // Temp variables are ephemeral, shallow copy
            return copy;
        }
    }

    private class ElementIterator implements Iterator<Object> {
        private int currentIndex = scopeStart;
        private final int endIndex = baseOffset;

        @Override
        public boolean hasNext() {
            return currentIndex < endIndex;
        }

        @Override
        public Object next() {
            if (currentIndex < endIndex) {
                return elements[currentIndex++];
            }
            return null;
        }
    }

    private class ElementListView implements List<Object> {
        @Override
        public int size() {
            return scopeSize;
        }

        @Override
        public boolean isEmpty() {
            return scopeSize == 0;
        }

        @Override
        public Object get(int index) {
            if (index >= 0 && index < scopeSize) {
                return elements[scopeStart + index];
            }
            return null;
        }

        @Override
        public Iterator<Object> iterator() {
            return new ElementIterator();
        }

        @Override
        public boolean contains(Object o) { throw new UnsupportedOperationException(); }
        @Override
        public Object[] toArray() { throw new UnsupportedOperationException(); }
        @Override
        public <T> T[] toArray(T[] a) { throw new UnsupportedOperationException(); }
        @Override
        public boolean add(Object o) { throw new UnsupportedOperationException(); }
        @Override
        public boolean remove(Object o) { throw new UnsupportedOperationException(); }
        @Override
        public boolean containsAll(Collection<?> c) { throw new UnsupportedOperationException(); }
        @Override
        public boolean addAll(Collection<?> c) { throw new UnsupportedOperationException(); }
        @Override
        public boolean addAll(int index, Collection<?> c) { throw new UnsupportedOperationException(); }
        @Override
        public boolean removeAll(Collection<?> c) { throw new UnsupportedOperationException(); }
        @Override
        public boolean retainAll(Collection<?> c) { throw new UnsupportedOperationException(); }
        @Override
        public void clear() { throw new UnsupportedOperationException(); }
        @Override
        public Object set(int index, Object element) { throw new UnsupportedOperationException(); }
        @Override
        public void add(int index, Object element) { throw new UnsupportedOperationException(); }
        @Override
        public Object remove(int index) { throw new UnsupportedOperationException(); }
        @Override
        public int indexOf(Object o) { throw new UnsupportedOperationException(); }
        @Override
        public int lastIndexOf(Object o) { throw new UnsupportedOperationException(); }
        @Override
        public ListIterator<Object> listIterator() { throw new UnsupportedOperationException(); }
        @Override
        public ListIterator<Object> listIterator(int index) { throw new UnsupportedOperationException(); }
        @Override
        public List<Object> subList(int fromIndex, int toIndex) { throw new UnsupportedOperationException(); }
    }
}
