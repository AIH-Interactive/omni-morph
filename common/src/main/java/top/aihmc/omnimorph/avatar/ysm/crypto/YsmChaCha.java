package top.aihmc.omnimorph.avatar.ysm.crypto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** YSM variable-round XChaCha, adapted from Sparkle-Morpher; see bundled MIT license. */
final class YsmChaCha implements AutoCloseable {
    private static final int[] SIGMA = {0x61707865, 0x3320646e, 0x79622d32, 0x6b206574};
    private final int[] state = new int[16];
    private final int[] working = new int[16];
    private final byte[] stream = new byte[64];
    private int rounds;

    YsmChaCha(byte[] key, byte[] nonce, int rounds) {
        if (key.length != 32 || nonce.length != 24 || (rounds != 10 && rounds != 20 && rounds != 30)) {
            throw new IllegalArgumentException("Invalid YSM ChaCha parameters");
        }
        this.rounds = rounds;
        System.arraycopy(SIGMA, 0, working, 0, 4);
        ByteBuffer keyBuffer = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer nonceBuffer = ByteBuffer.wrap(nonce).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 4; index < 12; index++) {
            working[index] = keyBuffer.getInt();
        }
        for (int index = 12; index < 16; index++) {
            working[index] = nonceBuffer.getInt();
        }
        shuffle(working, rounds);
        System.arraycopy(SIGMA, 0, state, 0, 4);
        System.arraycopy(working, 0, state, 4, 4);
        System.arraycopy(working, 12, state, 8, 4);
        state[14] = nonceBuffer.getInt();
        state[15] = nonceBuffer.getInt();
    }

    void process(byte[] input, int inputOffset, byte[] output, int outputOffset, int length) {
        for (int consumed = 0; consumed < length; consumed += 64) {
            System.arraycopy(state, 0, working, 0, 16);
            shuffle(working, rounds);
            for (int index = 0; index < 16; index++) {
                int value = working[index] + state[index];
                int position = index * 4;
                stream[position] = (byte) value;
                stream[position + 1] = (byte) (value >>> 8);
                stream[position + 2] = (byte) (value >>> 16);
                stream[position + 3] = (byte) (value >>> 24);
            }
            int count = Math.min(64, length - consumed);
            for (int index = 0; index < count; index++) {
                output[outputOffset + consumed + index] = (byte) (input[inputOffset + consumed + index] ^ stream[index]);
            }
            if (++state[12] == 0) {
                state[13]++;
            }
        }
    }

    int update(long hash) {
        rounds = 10 * (int) Long.remainderUnsigned(hash, 3) + 10;
        for (int index = 4; index < 16; index++) {
            state[index] ^= index % 2 == 0 ? (int) hash : (int) (hash >>> 32);
        }
        return (int) (((hash & 63) | 64) << 6);
    }

    private static void shuffle(int[] state, int rounds) {
        for (int round = 0; round < rounds / 2; round++) {
            quarter(state, 0, 4, 8, 12);
            quarter(state, 1, 5, 9, 13);
            quarter(state, 2, 6, 10, 14);
            quarter(state, 3, 7, 11, 15);
            quarter(state, 0, 5, 10, 15);
            quarter(state, 1, 6, 11, 12);
            quarter(state, 2, 7, 8, 13);
            quarter(state, 3, 4, 9, 14);
        }
    }

    private static void quarter(int[] state, int a, int b, int c, int d) {
        state[a] += state[b];
        state[d] = Integer.rotateLeft(state[d] ^ state[a], 16);
        state[c] += state[d];
        state[b] = Integer.rotateLeft(state[b] ^ state[c], 12);
        state[a] += state[b];
        state[d] = Integer.rotateLeft(state[d] ^ state[a], 8);
        state[c] += state[d];
        state[b] = Integer.rotateLeft(state[b] ^ state[c], 7);
    }

    @Override
    public void close() {
        Arrays.fill(state, 0);
        Arrays.fill(working, 0);
        Arrays.fill(stream, (byte) 0);
    }
}
