package top.aihmc.omnimorph.avatar.control;

import com.google.common.hash.Hashing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.utils.IOUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class AvatarControlValueStore {
    private static final Map<String, Map<String, StoredValue>> VALUES = new HashMap<>();
    private static boolean loaded;

    private AvatarControlValueStore() {
    }

    public static void apply(Avatar avatar, AvatarControlDefinition control) {
        applySavedValue(avatar, control);
    }

    static boolean applySavedValue(Avatar avatar, AvatarControlDefinition control) {
        if (avatar == null || control == null || !isPersistent(control))
            return false;
        if (avatar.cosmeticContext != null) {
            Object value = avatar.cosmeticContext.control(control.id());
            if (value == null) {
                return false;
            }
            control.setValue(value);
            return true;
        }
        if (avatar.isLibraryPreview) {
            return false;
        }
        load();
        StoredValue value = VALUES.getOrDefault(key(avatar), Map.of()).get(controlKey(avatar, control));
        if (value != null) {
            control.setValue(value.value());
            return true;
        }
        return false;
    }

    public static void save(Avatar avatar, AvatarControlDefinition control) {
        if (avatar == null || control == null || !isPersistent(control))
            return;
        Object value = control.getValue();
        if (hasModelKey(avatar) && control.type() == AvatarControlType.ENUM) {
            int labelIndex = control.options().indexOf(String.valueOf(value));
            Integer index = labelIndex >= 0 ? labelIndex : AvatarControlRuntime.optionIndex(value);
            if (index == null || index < 0 || index >= control.options().size()) {
                return;
            }
            // Persist the user's choice, not a potentially private model label.
            value = index;
        }
        if (avatar.cosmeticContext != null) {
            avatar.cosmeticContext.saveControl(control.id(), value);
            return;
        }
        if (avatar.isLibraryPreview) {
            return;
        }
        load();
        VALUES.computeIfAbsent(key(avatar), ignored -> new HashMap<>())
                .put(controlKey(avatar, control), StoredValue.from(value));
        save();
    }

    private static boolean isPersistent(AvatarControlDefinition control) {
        if (!control.persistent())
            return false;
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
        String owner = avatar.owner == null ? "" : avatar.owner.toString();
        String model = avatar.controls == null ? null : avatar.controls.modelKey();
        return model == null || model.isBlank() ? owner : owner + ":ysm:" + model;
    }

    private static boolean hasModelKey(Avatar avatar) {
        return avatar.controls != null && avatar.controls.modelKey() != null
                && !avatar.controls.modelKey().isBlank();
    }

    private static String controlKey(Avatar avatar, AvatarControlDefinition control) {
        if (!hasModelKey(avatar)) {
            return control.id();
        }
        // YSM form IDs may contain their read expression; only persist an opaque stable identity.
        return "control:" + Hashing.sha256().hashString(control.id(), StandardCharsets.UTF_8);
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
