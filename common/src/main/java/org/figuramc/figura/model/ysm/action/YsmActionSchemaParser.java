package org.figuramc.figura.model.ysm.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.control.AvatarControlDefinition;
import org.figuramc.figura.avatar.control.AvatarControlType;
import org.figuramc.figura.model.ysm.YsmModelRuntime;

public final class YsmActionSchemaParser {
    private YsmActionSchemaParser() {
    }

    public static void apply(YsmModelRuntime runtime, String path, String json) {
        if (runtime == null || json == null || json.isBlank())
            return;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root == null || root.isJsonNull())
                return;
            if (root.isJsonArray()) {
                readControls(runtime, root.getAsJsonArray());
                return;
            }
            if (!root.isJsonObject())
                return;
            JsonObject object = root.getAsJsonObject();
            readControls(runtime, array(object, "controls"));
            readControls(runtime, array(object, "control"));
            readActions(runtime, array(object, "actions"));
            readActions(runtime, array(object, "action"));
            readYsmProperties(runtime, object);
        } catch (Exception e) {
            FiguraMod.LOGGER.warn("Failed to parse YSM action/control schema {}", path, e);
        }
    }

    private static void readYsmProperties(YsmModelRuntime runtime, JsonObject root) {
        JsonObject properties = object(root, "properties");
        if (properties == null)
            return;
        readScaleProperty(runtime, properties, "width_scale", "YSM Width Scale");
        readScaleProperty(runtime, properties, "height_scale", "YSM Height Scale");
        readExtraAnimations(runtime, object(properties, "extra_animation"));
        readExtraAnimationButtons(runtime, array(properties, "extra_animation_buttons"));
    }

    private static void readScaleProperty(YsmModelRuntime runtime, JsonObject properties, String key, String title) {
        if (properties == null || !properties.has(key))
            return;
        double value = number(properties, key, 1d);
        runtime.owner().controls.register(new AvatarControlDefinition("ysm." + key, AvatarControlType.SLIDER)
                .setTitle(title)
                .setPage("root")
                .setRange(0.1d, 5d, 0.05d)
                .setDefault(value));
    }

    private static void readExtraAnimations(YsmModelRuntime runtime, JsonObject object) {
        if (object == null)
            return;
        for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String id = entry.getKey();
            String target = string(entry.getValue(), id);
            if (id == null || id.isBlank() || target == null || target.isBlank())
                continue;
            runtime.actions().register(new YsmActionDefinition("extra_animation." + id)
                    .setTitle(id)
                    .setAnimation(target)
                    .setPage("extra_animation")
                    .setLoop(false));
        }
    }

    private static void readExtraAnimationButtons(YsmModelRuntime runtime, JsonArray array) {
        if (array == null)
            return;
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject())
                continue;
            JsonObject button = element.getAsJsonObject();
            String page = string(button, "id", "");
            if (page.isBlank())
                continue;
            String name = string(button, "name", page);
            runtime.owner().controls.register(new AvatarControlDefinition("ysm.page_link." + page, AvatarControlType.BUTTON)
                    .setTitle(name)
                    .setPage("root")
                    .setTargetPage(page));
            runtime.owner().controls.register(new AvatarControlDefinition("ysm.page." + page, AvatarControlType.LABEL)
                    .setTitle(name)
                    .setPage(page));
            JsonArray forms = array(button, "config_forms");
            if (forms == null)
                continue;
            int index = 0;
            for (JsonElement formElement : forms) {
                if (formElement != null && formElement.isJsonObject())
                    readConfigForm(runtime, page, index, formElement.getAsJsonObject());
                index++;
            }
        }
    }

    private static void readConfigForm(YsmModelRuntime runtime, String page, int index, JsonObject form) {
        String binding = string(form, "value", "");
        String id = !binding.isBlank() ? "ysm." + binding.replace('.', '_') : "ysm." + page + "." + index;
        AvatarControlType type = controlType(string(form, "type", "toggle"));
        AvatarControlDefinition control = new AvatarControlDefinition(id, type)
                .setTitle(string(form, "title", id))
                .setPage(page)
                .setCategory(string(form, "description", ""))
                .setBinding(binding);
        if (form.has("min") || form.has("max") || form.has("step"))
            control.setRange(number(form, "min", 0d), number(form, "max", 1d), number(form, "step", 0.05d));

        JsonObject labels = object(form, "labels");
        if (labels != null) {
            for (java.util.Map.Entry<String, JsonElement> label : labels.entrySet()) {
                String option = label.getKey();
                control.addOptionCommand(option, string(label.getValue(), ""));
            }
        }
        if (form.has("default"))
            control.setDefault(value(form.get("default"), type));
        runtime.owner().controls.register(control);
    }

    private static void readControls(YsmModelRuntime runtime, JsonArray array) {
        if (array == null)
            return;
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject())
                continue;
            JsonObject object = element.getAsJsonObject();
            String id = string(object, "id", "");
            if (id.isBlank())
                continue;
            AvatarControlType type = controlType(string(object, "type", "toggle"));
            AvatarControlDefinition control = new AvatarControlDefinition(id, type)
                    .setTitle(string(object, "title", string(object, "name", id)))
                    .setPage(string(object, "page", "root"))
                    .setCategory(string(object, "category", ""));
            if (object.has("min") || object.has("max") || object.has("step"))
                control.setRange(number(object, "min", 0d), number(object, "max", 1d), number(object, "step", 0.05d));
            JsonArray options = array(object, "options");
            if (options == null)
                options = array(object, "values");
            if (options != null) {
                for (JsonElement option : options)
                    control.addOption(optionLabel(option));
            }
            if (object.has("default")) {
                control.setDefault(value(object.get("default"), type));
            } else if (object.has("value")) {
                control.setDefault(value(object.get("value"), type));
            } else {
                applyImplicitDefault(control, type);
            }
            runtime.owner().controls.register(control);
        }
    }

    private static void readActions(YsmModelRuntime runtime, JsonArray array) {
        if (array == null)
            return;
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject())
                continue;
            JsonObject object = element.getAsJsonObject();
            String id = string(object, "id", "");
            if (id.isBlank())
                continue;
            String animation = string(object, "animation", string(object, "anim", id));
            YsmActionDefinition action = new YsmActionDefinition(id)
                    .setTitle(string(object, "title", string(object, "name", id)))
                    .setAnimation(animation)
                    .setPage(string(object, "page", "root"))
                    .setLoop(bool(object, "loop", bool(object, "repeat", false)));
            runtime.actions().register(action);
        }
    }

    private static void applyImplicitDefault(AvatarControlDefinition control, AvatarControlType type) {
        switch (type) {
            case TOGGLE -> control.setDefault(false);
            case SLIDER, NUMBER -> control.setDefault(0d);
            case TEXT, COLOR, KEYBIND -> control.setDefault("");
            case ENUM -> {
                if (!control.options().isEmpty())
                    control.setDefault(control.options().get(0));
            }
            default -> {
            }
        }
    }

    private static AvatarControlType controlType(String value) {
        if (value == null)
            return AvatarControlType.TOGGLE;
        return switch (value.toLowerCase(java.util.Locale.US)) {
            case "bool", "boolean", "switch", "toggle", "checkbox" -> AvatarControlType.TOGGLE;
            case "float", "double", "int", "integer", "slider", "range" -> AvatarControlType.SLIDER;
            case "select", "choice", "cycle", "enum", "radio" -> AvatarControlType.ENUM;
            case "colour", "color" -> AvatarControlType.COLOR;
            case "string", "input", "text" -> AvatarControlType.TEXT;
            case "number" -> AvatarControlType.NUMBER;
            case "key", "keybind", "key_binding" -> AvatarControlType.KEYBIND;
            case "press", "button" -> AvatarControlType.BUTTON;
            case "label" -> AvatarControlType.LABEL;
            case "separator", "divider" -> AvatarControlType.SEPARATOR;
            default -> AvatarControlType.TOGGLE;
        };
    }

    private static Object value(JsonElement element, AvatarControlType type) {
        if (element == null || element.isJsonNull())
            return null;
        try {
            return switch (type) {
                case TOGGLE -> element.getAsBoolean();
                case SLIDER, NUMBER -> element.getAsDouble();
                default -> element.getAsString();
            };
        } catch (Exception ignored) {
            return element.toString();
        }
    }

    private static String optionLabel(JsonElement element) {
        if (element == null || element.isJsonNull())
            return "";
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            return string(object, "value", string(object, "id", string(object, "label", string(object, "name", ""))));
        }
        return element.getAsString();
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object == null ? null : object.get(key);
        return string(element, fallback);
    }

    private static String string(JsonElement element, String fallback) {
        try {
            return element != null && !element.isJsonNull() ? element.getAsString() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double number(JsonObject object, String key, double fallback) {
        JsonElement element = object == null ? null : object.get(key);
        try {
            return element != null && !element.isJsonNull() ? element.getAsDouble() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object == null ? null : object.get(key);
        try {
            return element != null && !element.isJsonNull() ? element.getAsBoolean() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
