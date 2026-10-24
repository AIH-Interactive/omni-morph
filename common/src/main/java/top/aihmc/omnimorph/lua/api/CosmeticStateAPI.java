package top.aihmc.omnimorph.lua.api;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import top.aihmc.omnimorph.lua.LuaWhitelist;
import top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext;

/** Explicit scalar state declarations; this API never exposes another instance or a Java object graph. */
@LuaWhitelist
public final class CosmeticStateAPI {
    private final CosmeticInstanceContext context;

    public CosmeticStateAPI(CosmeticInstanceContext context) {
        this.context = context;
    }

    @LuaWhitelist
    public Object get(String id) {
        return context.luaState().get(id);
    }

    @LuaWhitelist
    public CosmeticStateAPI set(String id, LuaValue value) {
        Object scalar;
        if (value.isboolean()) {
            scalar = value.toboolean();
        } else if (value.type() == LuaValue.TNUMBER) {
            scalar = value.todouble();
        } else if (value.type() == LuaValue.TSTRING) {
            scalar = value.tojstring();
        } else {
            throw new LuaError("COSMETIC_STATE_VALUE");
        }
        context.saveLuaState(id, scalar);
        return this;
    }
}
