package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import org.figuramc.figura.lua.ReadOnlyLuaTable;
import org.figuramc.figura.utils.Version;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Read-only client configuration and OMP's pure version comparison; no Minecraft client singleton. */
final class PreviewClient {
    private PreviewClient() {}

    static LuaValue create(JsonObject states) {
        JsonObject configured = states.has("client") ? states.getAsJsonObject("client").deepCopy() : new JsonObject();
        var client = new LuaTable();
        client.set("getFiguraVersion", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                var value = configured.get("getFiguraVersion");
                if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    throw new LuaError("states.client.getFiguraVersion must specify a simulated version string");
                }
                return LuaValue.valueOf(value.getAsString());
            }
        });
        client.set("compareVersions", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue first, LuaValue second) {
                Version left = new Version(first.checkjstring());
                Version right = new Version(second.checkjstring());
                if (left.invalid || right.invalid) {
                    throw new LuaError("Cannot parse version: " + (left.invalid ? first : second));
                }
                return LuaValue.valueOf(left.compareTo(right));
            }
        });
        return new ReadOnlyLuaTable(client);
    }
}
