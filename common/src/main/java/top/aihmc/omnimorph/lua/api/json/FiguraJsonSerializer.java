package top.aihmc.omnimorph.lua.api.json;

import com.google.gson.*;
import top.aihmc.omnimorph.lua.LuaWhitelist;
import top.aihmc.omnimorph.lua.docs.LuaMethodDoc;
import top.aihmc.omnimorph.lua.docs.LuaMethodOverload;
import top.aihmc.omnimorph.lua.docs.LuaTypeDoc;
import top.aihmc.omnimorph.utils.LuaUtils;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@LuaWhitelist
@LuaTypeDoc(name = "JsonSerializer", value = "json_serializer")
public class FiguraJsonSerializer {
    private final Gson serializer;
    public FiguraJsonSerializer(Gson serializer) {
        this.serializer = serializer;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            value = "json_serializer.serialize",
            overloads = @LuaMethodOverload(
                    argumentNames = "val",
                    argumentTypes = LuaValue.class,
                    returnType = String.class
            )
    )
    public String serialize(LuaValue val) {
        if (JsonAPI.isSerializable(val)) {
            JsonElement elem = LuaUtils.asJsonValue(val);
            return serializer.toJson(elem);
        }
        throw new IllegalArgumentException("Type of provided value (%s) is not serializable".formatted(val.typename()));
    }

    @LuaWhitelist
    @LuaMethodDoc(
            value = "json_serializer.deserialize",
            overloads = @LuaMethodOverload(
                    argumentNames = "str",
                    argumentTypes = String.class,
                    returnType = LuaValue.class
            )
    )
    public LuaValue deserialize(String s) {
        JsonElement e = JsonParser.parseString(s);
        return luaValueFromElement(e);
    }

    public static LuaValue luaValueFromElement(JsonElement element) {
        if (element.isJsonNull()) return LuaValue.NIL;
        else if (element.isJsonPrimitive()) return luaValueFromPrimitive(element.getAsJsonPrimitive());
        else if (element.isJsonObject()) return luaTableFromObject(element.getAsJsonObject());
        return luaTableFromArray(element.getAsJsonArray());
    }

    private static LuaTable luaTableFromArray(JsonArray array) {
        LuaTable tbl = new LuaTable();
        int i = 1;
        for (JsonElement elem :
                array) {
            tbl.set(i, luaValueFromElement(elem));
            i++;
        }
        return tbl;
    }

    private static LuaTable luaTableFromObject(JsonObject obj) {
        LuaTable tbl = new LuaTable();
        for (var kv:
                obj.entrySet()) {
            tbl.set(kv.getKey(), luaValueFromElement(kv.getValue()));
        }
        return tbl;
    }

    private static LuaValue luaValueFromPrimitive(JsonPrimitive primitive) {
        if (primitive.isBoolean()) return LuaValue.valueOf(primitive.getAsBoolean());
        else if (primitive.isString()) return LuaValue.valueOf(primitive.getAsString());
        return LuaValue.valueOf(primitive.getAsDouble());
    }


    public interface JsonValue {
        JsonElement getElement();
    }

    @Override
    public String toString() {
        return "JsonSerializer";
    }
}
