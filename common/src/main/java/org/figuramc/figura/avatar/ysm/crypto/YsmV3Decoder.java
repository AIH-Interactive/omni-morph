package org.figuramc.figura.avatar.ysm.crypto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Local-file decoder only; no cache/network cryptography or writer API. */
public final class YsmV3Decoder {
    private static final long FILE_VERIFICATION = 0x9E5599DB80C67C29L;
    private static final long RESOURCE_VERIFICATION = 0xA62B1A2C43842BC3L;
    private static final long KEY_DERIVATION = 0xD017CBBA7B5D3581L;

    private YsmV3Decoder() {
    }

    public static byte[] decode(byte[] source, YsmDecodeBudget budget) throws IOException {
        budget.length(source.length, budget.limits().fileBytes(), "v3/source", 0);
        if (source.length < 77) {
            throw new YsmDecodeException(YsmDecodeException.Code.TRUNCATED, "v3/source", 0);
        }
        int magicOffset = source[0] == (byte) 0xEF && source[1] == (byte) 0xBB && source[2] == (byte) 0xBF ? 3 : 0;
        if (source[magicOffset] != 'Y' || source[magicOffset + 1] != 'S'
                || source[magicOffset + 2] != 'G' || source[magicOffset + 3] != 'P') {
            throw new YsmDecodeException(YsmDecodeException.Code.BAD_HEADER, "v3/magic", magicOffset);
        }
        int tailOffset = source.length - 64;
        int headerEnd = magicOffset + 4;
        while (headerEnd < tailOffset && source[headerEnd] != 0) {
            budget.length(headerEnd + 1, YsmDecodeBudget.MAX_HEADER_BYTES, "v3/text-header", headerEnd);
            headerEnd++;
        }
        if (headerEnd >= tailOffset || tailOffset - headerEnd < 5) {
            throw new YsmDecodeException(YsmDecodeException.Code.TRUNCATED, "v3/text-header", headerEnd);
        }
        int cryptoOffset = headerEnd + 1;
        int crypto = ByteBuffer.wrap(source, cryptoOffset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (crypto != 3) {
            throw new YsmDecodeException(YsmDecodeException.Code.UNSUPPORTED_VERSION, "v3/crypto", cryptoOffset);
        }
        YsmCityHash hash = new YsmCityHash();
        long expected = ByteBuffer.wrap(source, source.length - 8, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
        long actual = hash.hash64WithSeed(source, 0, source.length - 8, FILE_VERIFICATION);
        budget.check("v3/integrity", tailOffset);
        if (actual != expected) {
            throw new YsmDecodeException(YsmDecodeException.Code.CHECKSUM_FAILED, "v3/file", tailOffset + 56);
        }
        byte[] key = Arrays.copyOfRange(source, tailOffset, tailOffset + 32);
        byte[] nonce = Arrays.copyOfRange(source, tailOffset + 32, tailOffset + 56);
        byte[] keyNonce = Arrays.copyOfRange(source, tailOffset, tailOffset + 56);
        int payloadOffset = cryptoOffset + 4;
        byte[] payload = new byte[tailOffset - payloadOffset];
        try {
            long initial = hash.hash64WithSeed(keyNonce, RESOURCE_VERIFICATION);
            int chunkSize = (int) (((initial & 63) | 64) << 6);
            int rounds = 10 * (int) Long.remainderUnsigned(initial, 3) + 10;
            try (YsmChaCha cipher = new YsmChaCha(key, nonce, rounds)) {
                int pointer = 0;
                while (pointer < payload.length) {
                    budget.check("v3/chacha", pointer);
                    int count = Math.min(chunkSize, payload.length - pointer);
                    cipher.process(source, payloadOffset + pointer, payload, pointer, count);
                    pointer += count;
                    if (pointer < payload.length) {
                        chunkSize = cipher.update(hash.hash64WithSeed(payload, pointer - count, count, RESOURCE_VERIFICATION));
                    }
                }
            }
            try (YsmMt19937 random = new YsmMt19937(hash.hash64WithSeed(keyNonce, KEY_DERIVATION))) {
                for (int pointer = 0; pointer < payload.length;) {
                    if ((pointer & 8191) == 0) {
                        budget.check("v3/xor", pointer);
                    }
                    long stream = random.next();
                    for (int index = 0; index < 8 && pointer < payload.length; index++, pointer++) {
                        payload[pointer] ^= (byte) (stream >>> (index * 8));
                    }
                }
            }
            YsmByteReader reader = new YsmByteReader(payload, budget, "v3/padding");
            int padding = (reader.unsignedByte() | (reader.unsignedByte() << 8)) & 1023;
            reader.skip(padding);
            return YsmZstdDecoder.decompress(payload, reader.offset(), reader.remaining(), budget);
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(keyNonce, (byte) 0);
            Arrays.fill(payload, (byte) 0);
        }
    }
}
