package top.aihmc.omm.preview.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lossless in-memory host for the reused bbmodel parser's tag output.
 * Numeric widths are retained, including unsigned mesh indices packed into bytes/shorts.
 * This is a data tree, not a Minecraft runtime or an NBT wire-format codec.
 */
public final class PreviewTags {
    private PreviewTags() {}

    public interface Tag {}

    public interface NumericTag extends Tag {
        Number number();
    }

    public record ByteTag(byte value) implements NumericTag {
        public static ByteTag valueOf(byte value) {
            return new ByteTag(value);
        }
        public Number number() {
            return value;
        }
    }

    public record ShortTag(short value) implements NumericTag {
        public static ShortTag valueOf(short value) {
            return new ShortTag(value);
        }
        public Number number() {
            return value;
        }
    }

    public record IntTag(int value) implements NumericTag {
        public static IntTag valueOf(int value) {
            return new IntTag(value);
        }
        public Number number() {
            return value;
        }
    }

    public record FloatTag(float value) implements NumericTag {
        public static FloatTag valueOf(float value) {
            return new FloatTag(value);
        }
        public Number number() {
            return value;
        }
    }

    public record StringTag(String value) implements Tag {
        public static StringTag valueOf(String value) {
            return new StringTag(value);
        }
    }

    public record ByteArrayTag(byte[] value) implements Tag {
        public ByteArrayTag {
            value = value.clone();
        }
        @Override
        public byte[] value() {
            return value.clone();
        }
    }

    public record IntArrayTag(int[] value) implements Tag {
        public IntArrayTag {
            value = value.clone();
        }
        @Override
        public int[] value() {
            return value.clone();
        }
    }

    public static final class ListTag extends ArrayList<Tag> implements Tag {
        public float number(int index) {
            return ((NumericTag) get(index)).number().floatValue();
        }
    }

    public static final class CompoundTag implements Tag {
        private final Map<String, Tag> values = new LinkedHashMap<>();

        public void put(String key, Tag value) {
            values.put(key, value);
        }

        public void putString(String key, String value) {
            put(key, new StringTag(value));
        }

        public void putBoolean(String key, boolean value) {
            put(key, new ByteTag((byte) (value ? 1 : 0)));
        }

        public void putFloat(String key, float value) {
            put(key, new FloatTag(value));
        }

        public void putInt(String key, int value) {
            put(key, new IntTag(value));
        }

        public void putIntArray(String key, int[] value) {
            put(key, new IntArrayTag(value));
        }

        public boolean contains(String key) {
            return values.containsKey(key);
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }

        public void remove(String key) {
            values.remove(key);
        }

        public java.util.Optional<Boolean> getBoolean(String key) {
            Tag tag = values.get(key);
            return tag instanceof NumericTag number ? java.util.Optional.of(number.number().byteValue() != 0)
                                                    : java.util.Optional.empty();
        }

        public Tag get(String key) {
            return values.get(key);
        }

        public CompoundTag compound(String key) {
            return (CompoundTag) values.get(key);
        }

        public ListTag list(String key) {
            return (ListTag) values.get(key);
        }

        public String string(String key, String fallback) {
            return values.containsKey(key) ? ((StringTag) values.get(key)).value() : fallback;
        }

        public float number(String key, float fallback) {
            return values.containsKey(key) ? ((NumericTag) values.get(key)).number().floatValue() : fallback;
        }

        public Map<String, Tag> entries() {
            return Collections.unmodifiableMap(values);
        }
    }
}
