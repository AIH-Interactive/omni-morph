package org.figuramc.figura.avatar.ysm.crypto;

import java.util.Arrays;

/** MT19937-64, not the unrelated 32-bit MT variant. */
final class YsmMt19937 implements AutoCloseable {
    private final long[] state = new long[312];
    private int index = 312;

    YsmMt19937(long seed) {
        state[0] = seed;
        for (int position = 1; position < state.length; position++) {
            state[position] = 6364136223846793005L * (state[position - 1] ^ (state[position - 1] >>> 62)) + position;
        }
    }

    long next() {
        if (index == state.length) {
            for (int position = 0; position < state.length; position++) {
                long combined = (state[position] & 0xFFFFFFFF80000000L)
                        | (state[(position + 1) % state.length] & 0x7FFFFFFFL);
                long mixed = combined >>> 1;
                if ((combined & 1) != 0) {
                    mixed ^= 0xB5026F5AA96619E9L;
                }
                state[position] = state[(position + 156) % state.length] ^ mixed;
            }
            index = 0;
        }
        long value = state[index++];
        value ^= (value >>> 29) & 0x5555555555555555L;
        value ^= (value << 17) & 0x71D67FFFEDA60000L;
        value ^= (value << 37) & 0xFFF7EEE000000000L;
        value ^= value >>> 43;
        return value;
    }

    @Override
    public void close() {
        Arrays.fill(state, 0);
    }
}
