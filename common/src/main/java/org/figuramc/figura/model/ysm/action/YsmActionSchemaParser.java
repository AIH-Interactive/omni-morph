package org.figuramc.figura.model.ysm.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.control.AvatarControlDefinition;
import org.figuramc.figura.avatar.control.AvatarControlType;
import org.figuramc.figura.model.ysm.YsmModelRuntime;
import org.figuramc.figura.model.ysm.animation.YsmAnimationClip;

import java.io.StringReader;

public final class YsmActionSchemaParser {
    private YsmActionSchemaParser() {
    }

    public static void apply(YsmModelRuntime runtime, String path, String json) {
        if (runtime == null || json == null || json.isBlank())
            return;
        try {
            JsonElement root = parseLenient(json);
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
            readLegacyExtraInfo(runtime, object);
        } catch (Exception e) {
            FiguraMod.LOGGER.warn("Failed to parse YSM action/control schema {}", path, e);
        }
    }

    private static JsonElement parseLenient(String json) {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        return JsonParser.parseReader(reader);
    }

    private static void readYsmProperties(YsmModelRuntime runtime, JsonObject root) {
        JsonObject properties = object(root, "properties");
        if (properties == null)
            return;
        readScaleProperty(runtime, properties, "width_scale", "YSM Width Scale");
        readScaleProperty(runtime, properties, "height_scale", "YSM Height Scale");
        readExtraAnimations(runtime, object(properties, "extra_animation"));
        readExtraAnimationClassify(runtime, array(properties, "extra_animation_classify"));
        readExtraAnimationClassify(runtime, object(properties, "extra_animation_classify"));
        readExtraAnimationButtons(runtime, array(properties, "extra_animation_buttons"));
    }

    private static void readLegacyExtraInfo(YsmModelRuntime runtime, JsonObject root) {
        JsonArray geometries = array(root, "minecraft:geometry");
        if (geometries == null)
            return;
        for (JsonElement geometryElement : geometries) {
            if (geometryElement == null || !geometryElement.isJsonObject())
                continue;
            JsonObject description = object(geometryElement.getAsJsonObject(), "description");
            JsonObject extra = object(description, "ysm_extra_info");
            readLegacyExtraAnimationNames(runtime, array(extra, "extra_animation_names"));
        }
    }

    private static void readLegacyExtraAnimationNames(YsmModelRuntime runtime, JsonArray names) {
        if (names == null)
            return;
        for (int i = 0; i < names.size(); i++) {
            String title = string(names.get(i), "");
            if (title == null || title.isBlank())
                continue;
            String id = "extra" + i;
            runtime.actions().register(new YsmActionDefinition("extra_animation." + id)
                    .setTitle(title)
                    .setAnimation(id)
                    .setPage("extra_animation")
                    .setLoop(runtime.animations().loopModeFor(id, YsmAnimationClip.LoopMode.ONCE) == YsmAnimationClip.LoopMode.LOOP));
        }
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
        readExtraAnimations(runtime, object, "extra_animation");
    }

    private static void readExtraAnimations(YsmModelRuntime runtime, JsonObject object, String page) {
        if (object == null)
            return;
        for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String id = entry.getKey();
            String label = string(entry.getValue(), id);
            if (id == null || id.isBlank() || label == null || label.isBlank())
                continue;
            if (id.startsWith("#"))
                continue;
            String animation = label.startsWith("#") ? label : id;
            String title = label.startsWith("#") ? id : label;
            runtime.actions().register(new YsmActionDefinition("extra_animation." + id)
                    .setTitle(title)
                    .setAnimation(animation)
                    .setPage(page)
                    .setLoop(!animation.startsWith("#")
                            && runtime.animations().loopModeFor(animation, YsmAnimationClip.LoopMode.ONCE) == YsmAnimationClip.LoopMode.LOOP));
        }
    }

    private static void readExtraAnimationClassify(YsmModelRuntime runtime, JsonArray array) {
        if (array == null)
            return;
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject())
                continue;
            JsonObject object = element.getAsJsonObject();
            String id = string(object, "id", string(object, "name", ""));
            if (id.isBlank())
                continue;
            JsonObject extras = object(object, "extra_animation");
            if (extras == null)
                extras = object(object, "extras");
            readExtraAnimations(runtime, extras, id);
        }
    }

    private static void readExtraAnimationClassify(YsmModelRuntime runtime, JsonObject object) {
        if (object == null)
            return;
        for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String id = entry.getKey();
            JsonElement value = entry.getValue();
            if (id == null || id.isBlank() || value == null)
                continue;
            if (value.isJsonObject()) {
                JsonObject child = value.getAsJsonObject();
                JsonObject extras = object(child, "extra_animation");
                if (extras == null)
                    extras = object(child, "extras");
                readExtraAnimations(runtime, extras == null ? child : extras, id);
            }
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
                forms = array(button, "forms");
            if (forms == null)
                continue;
            int index = 0;
            for (JsonElement formElement : forms) {
                if (formElement != null && formElement.isJsonObject())
                    runtime.owner().controls.register(readConfigForm(page, index, formElement.getAsJsonObject()));
                index++;
            }
        }
    }

    public static AvatarControlDefinition readConfigForm(String page, int index, JsonObject form) {
        String binding = string(form, "value", "");
        String id = !binding.isBlank() ? "ysm." + binding.replace('.', '_') : "ysm." + page + "." + index;
        AvatarControlType type = controlType(string(form, "type", "toggle"));
        AvatarControlDefinition control = new AvatarControlDefinition(id, type)
                .setTitle(string(form, "title", id))
                .setPage(page)
                .setCategory(string(form, "description", ""))
                .setBinding(binding)
                .setPersistent(false);
        if (form.has("min") || form.has("max") || form.has("step"))
            control.setRange(number(form, "min", 0d), number(form, "max", 1d), number(form, "step", 0.05d));

        JsonObject labels = object(form, "labels");
        if (labels != null) {
            for (java.util.Map.Entry<String, JsonElement> label : labels.entrySet()) {
                String option = label.getKey();
                control.addOption(option);
                control.addOptionCommand(option, string(label.getValue(), ""));
            }
        }
        JsonElement defaultValue = first(form, "default", "default_value", "defaultValue");
        if (defaultValue != null)
            control.setDefault(value(defaultValue, type));
        else
            applyImplicitDefault(control, type);
        return control;
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
                    .setCategory(string(object, "category", ""))
                    .setBinding(string(first(object, "binding", "variable", "value"), id));
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
            } else if (object.has("default_value")) {
                control.setDefault(value(object.get("default_value"), type));
            } else if (object.has("defaultValue")) {
                control.setDefault(value(object.get("defaultValue"), type));
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
            String controlsPage = string(first(object, "controls_page", "target_page", "page_ref", "open_page"), "");
            if (!controlsPage.isBlank())
                animation = controlsPage.startsWith("#") ? controlsPage : "#" + controlsPage;
            YsmActionDefinition action = new YsmActionDefinition(id)
                    .setTitle(string(object, "title", string(object, "name", id)))
                    .setAnimation(animation)
                    .setPage(string(object, "page", "root"))
                    .setLoop(bool(object, "loop", bool(object, "repeat", false)))
                    .setMode(string(object, "mode", string(object, "trigger", "press")))
                    .setCooldownTicks((int) number(object, "cooldown", number(object, "cooldown_ticks", 0d)))
                    .setSpeed((float) number(object, "speed", 1d))
                    .setItem(string(first(object, "item", "icon"), ""));
            runtime.actions().register(action);
        }
    }

    private static void applyImplicitDefault(AvatarControlDefinition control, AvatarControlType type) {
        switch (type) {
            case TOGGLE -> control.setDefault(false);
            case TEXT, COLOR, KEYBIND -> control.setDefault("");
            case ENUM -> {
                if (!control.options().isEmpty())
                    control.setDefault(control.options().get(0));
            }
            case SLIDER, NUMBER -> control.setDefault(control.min());
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

    private static JsonElement first(JsonObject object, String... keys) {
        if (object == null)
            return null;
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && !element.isJsonNull())
                return element;
        }
        return null;
    }
}
