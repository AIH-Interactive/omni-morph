package top.aihmc.omm.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import top.aihmc.omnimorph.molang.runtime.ExecutionContext;
import top.aihmc.omnimorph.molang.runtime.Function;

/** Original ctrl equipment matching with explicit configuration replacing Minecraft item/entity state. */
final class PreviewYsmEquipment {
    private final JsonObject equipment;

    PreviewYsmEquipment(JsonObject states) {
        equipment = states.has("equipment") ? states.getAsJsonObject("equipment").deepCopy() : new JsonObject();
    }

    static Function binding(String mode) {
        return new Function() {
            @Override
            public Object evaluate(ExecutionContext<?> execution, ArgumentCollection args) {
                var host = (PreviewMolang) execution.entity();
                return host.equipment.match(mode, args.getAsString(execution, 0), args.getAsString(execution, 1));
            }

            @Override
            public boolean validateArgumentSize(int size) {
                return size == 2 || size == 3;
            }
        };
    }

    private int match(String mode, String name, String expected) {
        String slot = slot(name);
        boolean armor = slot != null && !slot.equals("mainhand") && !slot.equals("offhand");
        if (slot == null || mode.equals("armor") != armor)
            return 0;
        if (armor && expected != null && expected.startsWith(":"))
            return 0;
        String path = "states.equipment." + slot;
        if (!equipment.has(slot))
            throw new UnsupportedOperationException(path + " needs simulated equipment");
        JsonObject item = equipment.getAsJsonObject(slot);
        if (mode.equals("use") && !flag(item, "using", path))
            return 0;
        if (mode.equals("swing") && !flag(item, "swinging", path))
            return 0;
        if (expected == null || expected.isBlank())
            return 0;
        String id = required(item, "id", path).getAsString();
        String canonical = identifier(id);
        if (canonical == null)
            throw new IllegalArgumentException(path + ".id must be a resource identifier");
        boolean empty = canonical.equals("minecraft:air");
        if (expected.equalsIgnoreCase("empty"))
            return empty ? 1 : 0;
        if (expected.startsWith("$"))
            return canonical.equals(expected.substring(1)) ? 1 : 0;
        if (empty)
            return 0;
        if (expected.startsWith("#")) {
            String tag = identifier(expected.substring(1));
            if (tag == null)
                return 0;
            for (JsonElement value : required(item, "tags", path).getAsJsonArray()) {
                if (tag.equals(identifier(value.getAsString())))
                    return 1;
            }
        } else if (expected.startsWith(":")) {
            for (JsonElement value : required(item, "types", path).getAsJsonArray()) {
                if (expected.substring(1).equals(value.getAsString()))
                    return 1;
            }
        }
        return 0;
    }

    private static JsonElement required(JsonObject item, String field, String path) {
        if (!item.has(field))
            throw new UnsupportedOperationException(path + "." + field + " needs a simulated value");
        return item.get(field);
    }

    private static boolean flag(JsonObject item, String field, String path) {
        JsonElement value = required(item, field, path);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(path + "." + field + " must be boolean");
        }
        return value.getAsBoolean();
    }

    private static String identifier(String value) {
        if (value == null || value.isBlank())
            return null;
        String id = value.contains(":") ? value : "minecraft:" + value;
        return id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") ? id : null;
    }

    // Aliases and hand/armor gating follow MolangBindings.CtrlBinding.parseSlot/handItem/armor.
    private static String slot(String value) {
        if (value == null)
            return null;
        return switch (value.toLowerCase(Locale.US)) {
            case "mainhand", "main_hand", "main", "hand", "right", "0" -> "mainhand";
            case "offhand", "off_hand", "off", "left", "1" -> "offhand";
            case "head", "helmet", "4" -> "head";
            case "chest", "chestplate", "body", "3" -> "chest";
            case "legs", "leggings", "2" -> "legs";
            case "feet", "boots", "5" -> "feet";
            default -> null;
        };
    }
}
