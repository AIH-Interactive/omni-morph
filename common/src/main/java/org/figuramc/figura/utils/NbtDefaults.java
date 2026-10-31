package org.figuramc.figura.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;

/**
 * Typed NBT reads with an explicit fallback.
 * <p>
 * MC 1.21.1 getters return zero values for missing or mistyped entries; these
 * helpers keep call sites that need a non-zero fallback readable.
 */
public final class NbtDefaults {
    private NbtDefaults() {
    }

    public static String getStringOr(CompoundTag tag, String key, String fallback) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
    }

    public static int getIntOr(CompoundTag tag, String key, int fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
    }

    public static float getFloatOr(CompoundTag tag, String key, float fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(key) : fallback;
    }

    /**
     * Returns the stored list of any element type, or a new empty list.
     * The stored instance is returned so in-place edits reach the tag.
     */
    public static ListTag getListOrEmpty(CompoundTag tag, String key) {
        if (tag.get(key) instanceof ListTag list) {
            return list;
        }
        return new ListTag();
    }

    /**
     * Returns the stored byte array when the key holds one. The stored array is
     * returned by reference, so callers may wipe sensitive payloads in place.
     */
    public static Optional<byte[]> findByteArray(CompoundTag tag, String key) {
        if (tag.contains(key, Tag.TAG_BYTE_ARRAY)) {
            return Optional.of(tag.getByteArray(key));
        }
        return Optional.empty();
    }

    public static float getFloatOr(ListTag list, int index, float fallback) {
        if (index >= 0 && index < list.size() && list.get(index) instanceof NumericTag numeric) {
            return numeric.getAsFloat();
        }
        return fallback;
    }
}
