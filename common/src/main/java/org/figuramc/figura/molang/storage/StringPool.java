package org.figuramc.figura.molang.storage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe string interning pool for Molang identifiers.
 * Maps strings to unique integer IDs to reduce GC pressure and enable fast lookups.
 *
 * Reference: YSM StringPool
 */
public class StringPool {

    public static final int NONE = Integer.MIN_VALUE;
    public static final String EMPTY = "";
    public static final int EMPTY_ID = computeIfAbsent(EMPTY);

    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private static final ConcurrentHashMap<String, Integer> POOL = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, String> MAP = new ConcurrentHashMap<>();

    /**
     * Returns the integer ID for the given string, creating one if it doesn't exist.
     */
    public static int computeIfAbsent(String str) {
        return POOL.computeIfAbsent(str, k -> {
            int name = COUNTER.incrementAndGet();
            MAP.put(name, k);
            return name;
        });
    }

    /**
     * Returns the integer ID for the given string, or NONE if not pooled.
     */
    public static int getName(String str) {
        return POOL.getOrDefault(str, NONE);
    }

    /**
     * Returns the string for the given integer ID, or EMPTY if not found.
     */
    public static String getString(int name) {
        return MAP.getOrDefault(name, EMPTY);
    }
}
