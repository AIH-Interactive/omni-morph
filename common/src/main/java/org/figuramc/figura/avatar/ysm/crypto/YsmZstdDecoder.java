package org.figuramc.figura.avatar.ysm.crypto;

import io.airlift.compress.v3.zstd.ZstdInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Restores YSM block headers, then uses the pure Java full Zstd decoder. */
public final class YsmZstdDecoder {
    private YsmZstdDecoder() {
    }

    public static byte[] decompress(byte[] data, int offset, int length, YsmDecodeBudget budget) throws IOException {
        if (offset < 0 || length < 0 || offset > data.length - length) {
            throw new YsmDecodeException(YsmDecodeException.Code.TRUNCATED, "zstd/range", offset);
        }
        byte[] frame = Arrays.copyOfRange(data, offset, offset + length);
        try {
            restoreBlockHeaders(frame, budget);
            try (var input = new ZstdInputStream(new ByteArrayInputStream(frame));
                 var output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                try {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        budget.check("zstd/decode", output.size());
                        budget.length((long) output.size() + count, budget.limits().totalResourceBytes(),
                                "zstd/output", output.size());
                        output.write(buffer, 0, count);
                    }
                    return output.toByteArray();
                } finally {
                    Arrays.fill(buffer, (byte) 0);
                }
            } catch (YsmDecodeException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                throw new YsmDecodeException(YsmDecodeException.Code.DECOMPRESS_FAILED, "zstd/decode", offset);
            }
        } finally {
            Arrays.fill(frame, (byte) 0);
        }
    }

    private static void restoreBlockHeaders(byte[] frame, YsmDecodeBudget budget) throws IOException {
        YsmByteReader reader = new YsmByteReader(frame, budget, "zstd/header");
        if (reader.int32(ByteOrder.LITTLE_ENDIAN) != 0xFD2FB528) {
            throw new YsmDecodeException(YsmDecodeException.Code.BAD_HEADER, "zstd/magic", 0);
        }
        int descriptor = reader.unsignedByte();
        if ((descriptor & 0x18) != 0) {
            throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "zstd/descriptor", 4);
        }
        boolean singleSegment = (descriptor & 32) != 0;
        boolean checksum = (descriptor & 4) != 0;
        if (!singleSegment) {
            int window = reader.unsignedByte();
            long base = 1L << ((window >>> 3) + 10);
            budget.length(base + (base >>> 3) * (window & 7), budget.limits().totalResourceBytes(),
                    "zstd/window", reader.offset());
        }
        int dictionaryFlag = descriptor & 3;
        int dictionaryBytes = dictionaryFlag == 0 ? 0 : 1 << (dictionaryFlag - 1);
        reader.skip(dictionaryBytes);
        // The local YSM format has no external dictionary input.
        if (dictionaryBytes != 0) {
            throw new YsmDecodeException(YsmDecodeException.Code.UNSUPPORTED_VERSION, "zstd/dictionary", reader.offset());
        }
        int sizeFlag = descriptor >>> 6;
        int sizeBytes = sizeFlag == 0 ? (singleSegment ? 1 : 0) : 1 << sizeFlag;
        long contentSize = 0;
        for (int index = 0; index < sizeBytes; index++) {
            contentSize |= (long) reader.unsignedByte() << (index * 8);
        }
        if (sizeFlag == 1) {
            contentSize += 256;
        }
        if (sizeBytes != 0) {
            budget.length(contentSize, budget.limits().totalResourceBytes(), "zstd/content-size", reader.offset());
        }
        boolean last;
        do {
            int blockOffset = reader.offset();
            int first = reader.unsignedByte();
            int second = reader.unsignedByte();
            int third = reader.unsignedByte();
            last = (first & 128) != 0;
            int ysmType = (first >>> 5) & 3;
            int type = switch (ysmType) {
                case 0 -> 2;
                case 1 -> 1;
                case 3 -> 0;
                default -> throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "zstd/block-type", blockOffset);
            };
            int blockSize = (((first & 31) << 16) | second | (third << 8)) ^ 0xD4E9;
            budget.length(blockSize, 128 * 1024, "zstd/block-size", blockOffset);
            int header = (last ? 1 : 0) | (type << 1) | (blockSize << 3);
            frame[blockOffset] = (byte) header;
            frame[blockOffset + 1] = (byte) (header >>> 8);
            frame[blockOffset + 2] = (byte) (header >>> 16);
            reader.skip(type == 1 ? 1 : blockSize);
        } while (!last);
        reader.skip(checksum ? 4 : 0);
        if (reader.remaining() != 0) {
            throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "zstd/trailing-data", reader.offset());
        }
    }
}
