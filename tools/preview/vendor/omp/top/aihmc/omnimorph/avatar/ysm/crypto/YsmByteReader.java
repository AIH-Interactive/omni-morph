package top.aihmc.omnimorph.avatar.ysm.crypto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Bounds are checked before every slice or allocation. No permissive short reads. */
public final class YsmByteReader {
    private final byte[] data;
    private final YsmDecodeBudget budget;
    private final String stage;
    private int offset;

    public YsmByteReader(byte[] data, YsmDecodeBudget budget, String stage) {
        this.data = data;
        this.budget = budget;
        this.stage = stage;
    }

    public int offset() {
        return offset;
    }

    public int remaining() {
        return data.length - offset;
    }

    public void require(int bytes) throws IOException {
        budget.check(stage, offset);
        if (bytes < 0 || bytes > remaining()) {
            throw new YsmDecodeException(YsmDecodeException.Code.TRUNCATED, stage, offset);
        }
    }

    public int unsignedByte() throws IOException {
        require(1);
        return data[offset++] & 255;
    }

    public int int32(ByteOrder order) throws IOException {
        require(4);
        int value = ByteBuffer.wrap(data, offset, 4).order(order).getInt();
        offset += 4;
        return value;
    }

    public long int64(ByteOrder order) throws IOException {
        require(8);
        long value = ByteBuffer.wrap(data, offset, 8).order(order).getLong();
        offset += 8;
        return value;
    }

    public float float32() throws IOException {
        float value = Float.intBitsToFloat(int32(ByteOrder.LITTLE_ENDIAN));
        if (!Float.isFinite(value)) {
            throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, stage + "/float", offset - 4);
        }
        return value;
    }

    public int varInt() throws IOException {
        int value = 0;
        for (int index = 0; index < 5; index++) {
            int next = unsignedByte();
            if (index == 4 && (next & 0xF0) != 0) {
                throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, stage + "/varint", offset - 1);
            }
            value |= (next & 127) << (index * 7);
            if ((next & 128) == 0) {
                return value;
            }
        }
        throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, stage + "/varint", offset);
    }

    public byte[] bytes(int length) throws IOException {
        require(length);
        byte[] result = Arrays.copyOfRange(data, offset, offset + length);
        offset += length;
        return result;
    }

    public void skip(int length) throws IOException {
        require(length);
        offset += length;
    }

    public String string() throws IOException {
        return utf8(bytes(budget.length(varInt(), YsmDecodeBudget.MAX_STRING_BYTES, stage + "/string", offset)));
    }

    public String utf8(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, stage + "/utf8", offset);
        }
    }
}
