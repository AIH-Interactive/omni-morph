package top.aihmc.omm.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.figuramc.figura.lua.ReadOnlyLuaTable;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Explicit constant query results for a configured standalone scenario, not Minecraft entity/world behavior. */
final class PreviewConfiguredQueries {
    private PreviewConfiguredQueries() {}

    static LuaValue create(JsonObject states, String section) {
        var api = new LuaTable();
        if (states.has(section)) {
            install(api, states.getAsJsonObject(section));
        }
        return new ReadOnlyLuaTable(api);
    }

    static void install(LuaTable api, JsonObject configured) {
        for (var entry : configured.entrySet()) {
            String name = entry.getKey();
            if ((!name.startsWith("get") && !name.startsWith("is")) || !api.get(name).isnil()) {
                continue;
            }
            JsonElement result = entry.getValue().deepCopy();
            api.set(name, new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs arguments) {
                    return value(result);
                }
            });
        }
    }

    private static LuaValue value(JsonElement configured) {
        if (configured.isJsonNull()) {
            return LuaValue.NIL;
        }
        if (configured.isJsonObject()) {
            var object = new LuaTable();
            var fields = configured.getAsJsonObject();
            for (var entry : fields.entrySet()) {
                if (!entry.getKey().startsWith("get") && !entry.getKey().startsWith("is")) {
                    object.set(entry.getKey(), value(entry.getValue()));
                }
            }
            install(object, fields);
            return new ReadOnlyLuaTable(object);
        }
        if (configured.isJsonArray()) {
            var array = new LuaTable();
            int index = 1;
            for (var element : configured.getAsJsonArray()) {
                array.set(index++, value(element));
            }
            return new ReadOnlyLuaTable(array);
        }
        var primitive = configured.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return LuaValue.valueOf(primitive.getAsBoolean());
        }
        if (primitive.isNumber()) {
            return LuaValue.valueOf(primitive.getAsDouble());
        }
        return LuaValue.valueOf(primitive.getAsString());
    }
}
