package org.figuramc.figura.avatar.control;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.utils.IOUtils;

import java.util.HashMap;
import java.util.Map;

public final class AvatarControlValueStore {
    private static final Map<String, Map<String, StoredValue>> VALUES = new HashMap<>();
    private static boolean loaded;

    private AvatarControlValueStore() {
    }

    public static void apply(Avatar avatar, AvatarControlDefinition control) {
        if (avatar == null || control == null || !isPersistent(control))
            return;
        load();
        StoredValue value = VALUES.getOrDefault(key(avatar), Map.of()).get(control.id());
        if (value != null)
            control.setValue(value.value());
    }

    public static void save(Avatar avatar, AvatarControlDefinition control) {
        if (avatar == null || control == null || !isPersistent(control))
            return;
        load();
        VALUES.computeIfAbsent(key(avatar), ignored -> new HashMap<>()).put(control.id(), StoredValue.from(control.getValue()));
        save();
    }

    private static boolean isPersistent(AvatarControlDefinition control) {
        return switch (control.type()) {
            case LABEL, SEPARATOR, BUTTON -> false;
            default -> true;
        };
    }

    private static void load() {
        if (loaded)
            return;
        loaded = true;
        IOUtils.readCacheFile("avatar_controls", nbt -> {
            for (Tag avatarTag : nbt.getListOrEmpty("avatars")) {
                if (!(avatarTag instanceof CompoundTag avatarNbt))
                    continue;
                String avatar = avatarNbt.getStringOr("avatar", "");
                if (avatar.isBlank())
                    continue;
                Map<String, StoredValue> controls = VALUES.computeIfAbsent(avatar, ignored -> new HashMap<>());
                for (Tag controlTag : avatarNbt.getListOrEmpty("controls")) {
                    if (!(controlTag instanceof CompoundTag controlNbt))
                        continue;
                    String id = controlNbt.getStringOr("id", "");
                    if (!id.isBlank())
                        controls.put(id, StoredValue.read(controlNbt));
                }
            }
        });
    }

    private static void save() {
        IOUtils.saveCacheFile("avatar_controls", nbt -> {
            ListTag avatars = new ListTag();
            for (Map.Entry<String, Map<String, StoredValue>> avatarEntry : VALUES.entrySet()) {
                CompoundTag avatarNbt = new CompoundTag();
                avatarNbt.putString("avatar", avatarEntry.getKey());
                ListTag controls = new ListTag();
                for (Map.Entry<String, StoredValue> controlEntry : avatarEntry.getValue().entrySet()) {
                    CompoundTag controlNbt = new CompoundTag();
                    controlNbt.putString("id", controlEntry.getKey());
                    controlEntry.getValue().write(controlNbt);
                    controls.add(controlNbt);
                }
                avatarNbt.put("controls", controls);
                avatars.add(avatarNbt);
            }
            nbt.put("avatars", avatars);
        });
    }

    private static String key(Avatar avatar) {
        return avatar.owner == null ? "" : avatar.owner.toString();
    }

        private record StoredValue(String type, String stringValue, float numberValue, boolean booleanValue) {
        static StoredValue from(Object value) {
            if (value instanceof Boolean bool)
                return new StoredValue("boolean", "", 0f, bool);
            if (value instanceof Number number)
                return new StoredValue("number", "", number.floatValue(), false);
            return new StoredValue("string", value == null ? "" : value.toString(), 0f, false);
        }

        Object value() {
            return switch (type) {
                case "boolean" -> booleanValue;
                case "number" -> numberValue;
                default -> stringValue;
            };
        }

        static StoredValue read(CompoundTag tag) {
            return new StoredValue(
                    tag.getStringOr("type", "string"),
                    tag.getStringOr("string", ""),
                    tag.getFloatOr("number", 0f),
                    tag.getBooleanOr("boolean", false)
            );
        }

        void write(CompoundTag tag) {
            tag.putString("type", type);
            switch (type) {
                case "boolean" -> tag.putBoolean("boolean", booleanValue);
                case "number" -> tag.putFloat("number", numberValue);
                default -> tag.putString("string", stringValue);
            }
        }
    }
}
