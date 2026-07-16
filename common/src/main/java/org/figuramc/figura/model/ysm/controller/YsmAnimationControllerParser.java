package org.figuramc.figura.model.ysm.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.molang.parser.ast.Expression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class YsmAnimationControllerParser {
    private YsmAnimationControllerParser() {
    }

    public static Map<String, YsmAnimationController> parse(String json) {
        Map<String, YsmAnimationController> controllers = new LinkedHashMap<>();
        if (json == null || json.isBlank())
            return controllers;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("animation_controllers") || !root.get("animation_controllers").isJsonObject())
                return controllers;
            JsonObject object = root.getAsJsonObject("animation_controllers");
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (!entry.getValue().isJsonObject())
                    continue;
                YsmAnimationController controller = parseController(entry.getKey(), entry.getValue().getAsJsonObject());
                controllers.put(controller.name(), controller);
            }
        } catch (Exception ignored) {
        }
        return controllers;
    }

    private static YsmAnimationController parseController(String name, JsonObject object) {
        String initialState = string(object.get("initial_state"), "default");
        LinkedHashMap<String, YsmControllerState> states = new LinkedHashMap<>();
        if (object.has("states") && object.get("states").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("states").entrySet()) {
                if (!entry.getValue().isJsonObject())
                    continue;
                states.put(entry.getKey(), parseState(entry.getKey(), entry.getValue().getAsJsonObject()));
            }
        }
        return new YsmAnimationController(name, initialState, YsmControllerSlot.fromName(name), states);
    }

    private static YsmControllerState parseState(String name, JsonObject object) {
        return new YsmControllerState(
                name,
                parseAnimations(object.get("animations")),
                parseTransitions(object.get("transitions")),
                parseExpressions(object.get("on_entry")),
                parseExpressions(object.get("on_exit")),
                floatValue(object.get("blend_transition"), 0f),
                object.has("blend_via_shortest_path") && object.get("blend_via_shortest_path").isJsonPrimitive() && object.get("blend_via_shortest_path").getAsBoolean()
        );
    }

    private static List<YsmControllerAnimationRef> parseAnimations(JsonElement element) {
        List<YsmControllerAnimationRef> result = new ArrayList<>();
        if (element == null || !element.isJsonArray())
            return result;
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonPrimitive()) {
                result.add(new YsmControllerAnimationRef(child.getAsString(), null));
            } else if (child.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : child.getAsJsonObject().entrySet())
                    result.add(new YsmControllerAnimationRef(entry.getKey(), compile(string(entry.getValue(), ""))));
            }
        }
        return result;
    }

    private static List<YsmControllerTransition> parseTransitions(JsonElement element) {
        List<YsmControllerTransition> result = new ArrayList<>();
        if (element == null || !element.isJsonArray())
            return result;
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonObject())
                continue;
            for (Map.Entry<String, JsonElement> entry : child.getAsJsonObject().entrySet())
                result.add(new YsmControllerTransition(entry.getKey(), compile(string(entry.getValue(), ""))));
        }
        return result;
    }

    private static List<Expression> parseExpressions(JsonElement element) {
        List<Expression> result = new ArrayList<>();
        if (element == null)
            return result;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray())
                addExpression(result, child);
        } else {
            addExpression(result, element);
        }
        return result;
    }

    private static void addExpression(List<Expression> result, JsonElement element) {
        result.addAll(compileAll(string(element, "")));
    }

    private static Expression compile(String value) {
        if (value == null || value.isBlank())
            return null;
        try {
            List<Expression> parsed = Avatar.getMolangEngine().parse(value);
            return parsed.isEmpty() ? null : parsed.get(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<Expression> compileAll(String value) {
        if (value == null || value.isBlank())
            return List.of();
        try {
            return Avatar.getMolangEngine().parse(value);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String string(JsonElement element, String fallback) {
        if (element == null || element.isJsonNull())
            return fallback;
        if (element.isJsonPrimitive())
            return element.getAsString();
        return element.toString();
    }

    private static float floatValue(JsonElement element, float fallback) {
        if (element == null || element.isJsonNull())
            return fallback;
        try {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber())
                return element.getAsFloat();
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                for (String key : List.of("duration", "time", "value", "blend_transition")) {
                    JsonElement child = object.get(key);
                    if (child != null && child.isJsonPrimitive() && child.getAsJsonPrimitive().isNumber())
                        return child.getAsFloat();
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }
}
