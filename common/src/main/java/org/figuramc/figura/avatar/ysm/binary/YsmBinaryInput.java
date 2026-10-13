package org.figuramc.figura.avatar.ysm.binary;

import org.figuramc.figura.avatar.ysm.crypto.YsmByteReader;
import org.figuramc.figura.avatar.ysm.crypto.YsmDecodeBudget;
import org.figuramc.figura.avatar.ysm.crypto.YsmDecodeException;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.function.Consumer;

/** Strict primitives used by the versioned binary parser. */
final class YsmBinaryInput {
    private final YsmByteReader reader;
    private final YsmDecodeBudget budget;
    private final Consumer<byte[]> ownBuffer;
    private int geometryBones;
    private int geometryFaces;
    private int animationKeyframes;

    YsmBinaryInput(byte[] data, YsmDecodeBudget budget) throws IOException {
        this(data, budget, bytes -> {});
    }

    YsmBinaryInput(byte[] data, YsmDecodeBudget budget, Consumer<byte[]> ownBuffer) throws IOException {
        budget.length(data.length, budget.limits().totalResourceBytes(), "binary/input", 0);
        this.budget = budget;
        this.ownBuffer = ownBuffer;
        reader = new YsmByteReader(data, budget, "binary");
    }

    int getOffset() {
        return reader.offset();
    }

    int remaining() {
        return reader.remaining();
    }

    int readDword() throws IOException {
        return reader.int32(ByteOrder.LITTLE_ENDIAN);
    }

    int readVarInt() throws IOException {
        return reader.varInt();
    }

    int readCount(int max) throws IOException {
        return budget.length(reader.varInt(), max, "binary/count", reader.offset());
    }

    int readGeometryBonesCount() throws IOException {
        int count = readCount(4096 - geometryBones);
        geometryBones += count;
        return count;
    }

    int readGeometryFacesCount() throws IOException {
        int count = readCount(262144 - geometryFaces);
        geometryFaces += count;
        return count;
    }

    int readAnimationKeyframesCount() throws IOException {
        int count = readCount(1048576 - animationKeyframes);
        animationKeyframes += count;
        return count;
    }

    void checkImageDimensions(int width, int height) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "binary/image-dimensions", reader.offset());
        }
        // Pixel totals are charged when decoded; check declared dimensions before any pixel allocation.
        budget.length((long) width * height, YsmDecodeBudget.MAX_TEXTURE_PIXELS,
                "binary/image-pixels", reader.offset());
    }

    long readVarLong() throws IOException {
        long result = 0;
        for (int index = 0; index < 10; index++) {
            int next = reader.unsignedByte();
            if (index == 9 && (next & 254) != 0) {
                throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "binary/varlong", reader.offset());
            }
            result |= (long) (next & 127) << (index * 7);
            if ((next & 128) == 0) {
                return result;
            }
        }
        throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "binary/varlong", reader.offset());
    }

    float readFloat() throws IOException {
        return reader.float32();
    }

    float readAnimationLength() throws IOException {
        float value = Float.intBitsToFloat(reader.int32(ByteOrder.LITTLE_ENDIAN));
        // The reference packer uses positive infinity for an omitted animation_length.
        if (Float.isNaN(value) || value < 0) {
            throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA,
                    "binary/animation-length", reader.offset() - 4);
        }
        return value;
    }

    byte readByte() throws IOException {
        return (byte) reader.unsignedByte();
    }

    String readString() throws IOException {
        return reader.string();
    }

    byte[] readByteArray() throws IOException {
        budget.beginResource("binary/resource-count", reader.offset());
        int length = budget.length(reader.varInt(), budget.remainingResourceBytes(), "binary/resource", reader.offset());
        reader.require(length);
        budget.resource(length, "binary/resource", reader.offset());
        byte[] bytes = reader.bytes(length);
        ownBuffer.accept(bytes);
        return bytes;
    }

    void skipBytes(int count) throws IOException {
        reader.skip(count);
    }
}
