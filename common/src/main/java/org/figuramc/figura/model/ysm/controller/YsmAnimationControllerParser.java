package org.figuramc.figura.model.ysm.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.molang.MolangEngine;
import org.figuramc.figura.molang.parser.ast.Expression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class YsmAnimationControllerParser {
    private YsmAnimationControllerParser() {
    }

    public static Map<String, YsmAnimationController> parse(String json) {
        return parse(json, Avatar.getMolangEngine());
    }

    public static Map<String, YsmAnimationController> parse(String json, MolangEngine engine) {
        Map<String, YsmAnimationController> controllers = new LinkedHashMap<>();
        if (json == null || json.isBlank())
            return controllers;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("animation_controllers"))
                return controllers;
            JsonElement controllerElement = root.get("animation_controllers");
            if (controllerElement.isJsonObject()) {
                JsonObject object = controllerElement.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    if (!entry.getValue().isJsonObject())
                        continue;
                    YsmAnimationController controller = parseController(entry.getKey(), entry.getValue().getAsJsonObject(), engine);
                    controllers.put(controller.name(), controller);
                }
            } else if (controllerElement.isJsonArray()) {
                for (JsonElement element : controllerElement.getAsJsonArray()) {
                    if (!element.isJsonObject())
                        continue;
                    JsonObject object = element.getAsJsonObject();
                    String name = string(first(object, "name", "id", "controller", "animation"), "");
                    if (name.isBlank())
                        name = "controller_" + controllers.size();
                    YsmAnimationController controller = parseController(name, object, engine);
                    controllers.put(controller.name(), controller);
                }
            }
        } catch (Exception ignored) {
        }
        return controllers;
    }

    private static YsmAnimationController parseController(String name, JsonObject object, MolangEngine engine) {
        String initialState = string(first(object, "initial_state", "initialState"), "default");
        LinkedHashMap<String, YsmControllerState> states = new LinkedHashMap<>();
        if (object.has("states")) {
            JsonElement stateElement = object.get("states");
            if (stateElement.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : stateElement.getAsJsonObject().entrySet()) {
                    if (!entry.getValue().isJsonObject())
                        continue;
                    states.put(entry.getKey(), parseState(entry.getKey(), entry.getValue().getAsJsonObject(), engine));
                }
            } else if (stateElement.isJsonArray()) {
                for (JsonElement element : stateElement.getAsJsonArray()) {
                    if (!element.isJsonObject())
                        continue;
                    JsonObject state = element.getAsJsonObject();
                    String stateName = string(first(state, "name", "id", "state"), "");
                    if (stateName.isBlank())
                        stateName = "state_" + states.size();
                    states.put(stateName, parseState(stateName, state, engine));
                }
            }
        }
        if (!states.containsKey(initialState) && !states.isEmpty())
            initialState = states.keySet().iterator().next();
        return new YsmAnimationController(name, initialState, YsmControllerSlot.fromName(name), states);
    }

    private static YsmControllerState parseState(String name, JsonObject object, MolangEngine engine) {
        return new YsmControllerState(
                name,
                parseAnimations(object.get("animations"), engine),
                parseTransitions(object.get("transitions"), engine),
                parseExpressions(object.get("on_entry"), engine),
                parseExpressions(object.get("on_exit"), engine),
                floatValue(object.get("blend_transition"), 0f),
                object.has("blend_via_shortest_path") && object.get("blend_via_shortest_path").isJsonPrimitive() && object.get("blend_via_shortest_path").getAsBoolean()
        );
    }

    private static List<YsmControllerAnimationRef> parseAnimations(JsonElement element, MolangEngine engine) {
        List<YsmControllerAnimationRef> result = new ArrayList<>();
        if (element == null || element.isJsonNull())
            return result;
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
                result.add(new YsmControllerAnimationRef(entry.getKey(), compile(string(entry.getValue(), ""), engine)));
            return result;
        }
        if (!element.isJsonArray())
            return result;
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonPrimitive()) {
                result.add(new YsmControllerAnimationRef(child.getAsString(), null));
            } else if (child.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : child.getAsJsonObject().entrySet())
                    result.add(new YsmControllerAnimationRef(entry.getKey(), compile(string(entry.getValue(), ""), engine)));
            }
        }
        return result;
    }

    private static List<YsmControllerTransition> parseTransitions(JsonElement element, MolangEngine engine) {
        List<YsmControllerTransition> result = new ArrayList<>();
        if (element == null || element.isJsonNull())
            return result;
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
                result.add(new YsmControllerTransition(entry.getKey(), compile(string(entry.getValue(), ""), engine)));
            return result;
        }
        if (!element.isJsonArray())
            return result;
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonObject())
                continue;
            for (Map.Entry<String, JsonElement> entry : child.getAsJsonObject().entrySet())
                result.add(new YsmControllerTransition(entry.getKey(), compile(string(entry.getValue(), ""), engine)));
        }
        return result;
    }

    private static List<Expression> parseExpressions(JsonElement element, MolangEngine engine) {
        List<Expression> result = new ArrayList<>();
        if (element == null)
            return result;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray())
                addExpression(result, child, engine);
        } else {
            addExpression(result, element, engine);
        }
        return result;
    }

    private static void addExpression(List<Expression> result, JsonElement element, MolangEngine engine) {
        result.addAll(compileAll(string(element, ""), engine));
    }

    private static Expression compile(String value, MolangEngine engine) {
        if (value == null || value.isBlank())
            return null;
        try {
            List<Expression> parsed = (engine == null ? Avatar.getMolangEngine() : engine).parse(value);
            return parsed.isEmpty() ? null : parsed.get(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<Expression> compileAll(String value, MolangEngine engine) {
        if (value == null || value.isBlank())
            return List.of();
        try {
            return (engine == null ? Avatar.getMolangEngine() : engine).parse(value);
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

    private static JsonElement first(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && !element.isJsonNull())
                return element;
        }
        return null;
    }

    private static float floatValue(JsonElement element, float fallback) {
        if (element == null || element.isJsonNull())
            return fallback;
        try {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber())
                return element.getAsFloat();
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString())
                return Float.parseFloat(element.getAsString());
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
