package top.aihmc.omnimorph.lua.api.ping;

import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.lua.LuaNotNil;
import top.aihmc.omnimorph.lua.LuaWhitelist;
import top.aihmc.omnimorph.lua.docs.LuaTypeDoc;
import org.luaj.vm2.LuaFunction;

import java.util.HashMap;

@LuaWhitelist
@LuaTypeDoc(
        name = "PingAPI",
        value = "pings"
)
public class PingAPI {

    private final HashMap<String, PingFunction> map = new HashMap<>();
    private final HashMap<Integer, String> idMap = new HashMap<>();
    private final Avatar owner;

    public PingAPI(Avatar owner) {
        this.owner = owner;
    }

    public PingFunction get(String arg) {
        return map.get(arg);
    }

    public String getName(int id) {
        return idMap.get(id);
    }

    @LuaWhitelist
    public Object __index(String arg) {
        return get(arg);
    }

    @LuaWhitelist
    public void __newindex(@LuaNotNil String key, LuaFunction value) {
        int id = (key.hashCode() + 1) * 31;
        if (value == null) {
            map.remove(key);
            idMap.remove(id);
        } else {
            PingFunction func = new PingFunction(id, owner, value);
            map.put(key, func);
            idMap.put(id, key);
        }
    }

    @Override
    public String toString() {
        return "PingAPI";
    }
}
