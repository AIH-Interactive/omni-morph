package top.aihmc.omnimorph.lua.api.data;

import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.lua.LuaWhitelist;
import top.aihmc.omnimorph.lua.docs.LuaMethodDoc;
import top.aihmc.omnimorph.lua.docs.LuaMethodOverload;
import top.aihmc.omnimorph.lua.docs.LuaTypeDoc;

@LuaWhitelist
@LuaTypeDoc(name = "DataAPI", value = "data")
public class DataAPI {

    private final Avatar parent;

    public DataAPI(Avatar parent) {
        this.parent = parent;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            value = "data.create_buffer",
            overloads = {
                    @LuaMethodOverload(
                            returnType = FiguraBuffer.class
                    ),
                    @LuaMethodOverload(
                            returnType = FiguraBuffer.class,
                            argumentNames = "capacity",
                            argumentTypes = Integer.class
                    )
            }
    )
    public FiguraBuffer createBuffer(Integer len) {
        return len == null ? new FiguraBuffer(parent) : new FiguraBuffer(parent, len);
    }

    @Override
    public String toString() {
        return "DataAPI";
    }
}
