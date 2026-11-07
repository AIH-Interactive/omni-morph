package top.aihmc.omm.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import org.figuramc.figura.lua.LuaTypeManager;
import org.figuramc.figura.lua.ReadOnlyLuaTable;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Explicit read-only player simulation: no game singleton and no invented missing values. */
final class PreviewSimulatedApi {
    private PreviewSimulatedApi() {}

    static LuaValue player(JsonObject states, LuaTypeManager types) {
        JsonObject configured = states.has("player") ? states.getAsJsonObject("player").deepCopy() : new JsonObject();
        LuaTable api = new LuaTable();
        for (String name : List.of("isLoaded", "isAlive", "isSprinting", "isSneaking", "isCrouching", "isMoving",
                 "isOnGround", "isGliding", "isVisuallySwimming", "isBlocking", "isClimbing", "riptideSpinning",
                 "isUnderwater", "isInWater", "isInLava", "isOnFire", "isUsingItem", "isLeftHanded")) {
            add(api, configured, types, name, Kind.BOOLEAN);
        }
        for (String name : List.of("getHealth", "getMaxHealth", "getAbsorptionAmount", "getExperienceLevel",
                 "getBodyYaw", "getYaw", "getPitch", "getSwingTime", "getFood", "getAir")) {
            add(api, configured, types, name, Kind.NUMBER);
        }
        for (String name : List.of("getPose", "getGamemode", "getActiveHand", "getSwingArm", "getName", "getUUID")) {
            add(api, configured, types, name, Kind.STRING);
        }
        for (String name : List.of("getPos", "getVelocity", "getLookDir")) {
            add(api, configured, types, name, Kind.VECTOR3);
        }
        // Configured host queries do not need a separate Java implementation or game state machine.
        for (var entry : configured.entrySet()) {
            String name = entry.getKey();
            JsonElement value = entry.getValue();
            if (name.isEmpty())
                throw new IllegalArgumentException("states.player query names cannot be empty");
            String method = name;
            if (!name.startsWith("is") && !name.startsWith("get")) {
                String prefix = value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? "is" : "get";
                method = prefix + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
            if (!api.get(method).isnil())
                continue;
            Kind kind = method.startsWith("is")                                    ? Kind.BOOLEAN
                : value.isJsonArray()                                              ? Kind.VECTOR3
                : value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? Kind.NUMBER
                : value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? Kind.STRING
                                                                                   : null;
            if (kind != null)
                add(api, configured, types, method, kind);
        }
        PreviewConfiguredQueries.install(api, configured);
        return new ReadOnlyLuaTable(api);
    }

    private enum Kind { BOOLEAN, NUMBER, STRING, VECTOR3 }

    private static void add(LuaTable api, JsonObject configured, LuaTypeManager types, String method, Kind kind) {
        api.set(method, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                String property = method.startsWith("get") ? method.substring(3)
                    : method.startsWith("is")              ? method.substring(2)
                                                           : method;
                property = Character.toLowerCase(property.charAt(0)) + property.substring(1);
                String key = configured.has(method) ? method : property;
                if (!configured.has(key)) {
                    throw new LuaError(
                        "Missing simulated player query: states.player." + property + " (" + method + ")");
                }
                JsonElement value = configured.get(key);
                String path = "states.player." + key;
                if (kind == Kind.VECTOR3) {
                    if (!value.isJsonArray() || value.getAsJsonArray().size() != 3) {
                        throw new LuaError(path + " must be an array of three numbers");
                    }
                    double[] components = new double[3];
                    for (int index = 0; index < components.length; index++) {
                        JsonElement component = value.getAsJsonArray().get(index);
                        if (!component.isJsonPrimitive() || !component.getAsJsonPrimitive().isNumber()
                            || !Double.isFinite(component.getAsDouble())) {
                            throw new LuaError(path + " must contain finite numbers");
                        }
                        components[index] = component.getAsDouble();
                    }
                    return types.javaToLua(FiguraVec3.of(components[0], components[1], components[2]));
                }
                if (!value.isJsonPrimitive()) {
                    throw new LuaError(path + " must be " + kind.name().toLowerCase(java.util.Locale.ROOT));
                }
                var primitive = value.getAsJsonPrimitive();
                return switch (kind) {
                    case BOOLEAN -> {
                        if (!primitive.isBoolean())
                            throw new LuaError(path + " must be boolean");
                        yield LuaValue.valueOf(primitive.getAsBoolean());
                    }
                    case NUMBER -> {
                        if (!primitive.isNumber() || !Double.isFinite(primitive.getAsDouble())) {
                            throw new LuaError(path + " must be a finite number");
                        }
                        yield LuaValue.valueOf(primitive.getAsDouble());
                    }
                    case STRING -> {
                        if (!primitive.isString())
                            throw new LuaError(path + " must be string");
                        yield LuaValue.valueOf(primitive.getAsString());
                    }
                    case VECTOR3 -> throw new AssertionError("Vector queries handled above");
                };
            }
        });
    }
}
