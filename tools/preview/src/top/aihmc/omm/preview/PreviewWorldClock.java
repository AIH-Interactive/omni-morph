package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import java.util.function.IntSupplier;
import org.figuramc.figura.lua.ReadOnlyLuaTable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Figura's game/day tick queries driven by the preview's actual 20 Hz event clock. */
final class PreviewWorldClock {
    private PreviewWorldClock() {}

    static LuaValue create(JsonObject states, IntSupplier tick) {
        JsonObject world = states.has("world") ? states.getAsJsonObject("world") : new JsonObject();
        long gameTime = base(world, "time", 0);
        long dayTime = base(world, "dayTime", gameTime);
        LuaTable api = new LuaTable();
        api.set("getTime", query(gameTime, tick));
        api.set("getTimeOfDay", query(dayTime, tick));
        PreviewConfiguredQueries.install(api, world);
        return new ReadOnlyLuaTable(api);
    }

    private static long base(JsonObject world, String name, long fallback) {
        if (!world.has(name))
            return fallback;
        var value = world.get(name);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            try {
                return value.getAsBigDecimal().longValueExact();
            } catch (ArithmeticException invalid) {
                throw new LuaError("states.world." + name + " must be a signed 64-bit integer tick count");
            }
        }
        throw new LuaError("states.world." + name + " must be an integer tick count");
    }

    private static LuaValue query(long base, IntSupplier tick) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                int position = arguments.arg1().istable() ? 2 : 1;
                double delta = arguments.optdouble(position, 0);
                if (!Double.isFinite(delta))
                    throw new LuaError("World time delta must be finite");
                return LuaValue.valueOf((double) base + tick.getAsInt() + delta);
            }
        };
    }
}
