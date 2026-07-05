package org.figuramc.figura.lua.api.ysm_model;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.model.ysm.YsmModelPart;
import org.figuramc.figura.model.ysm.YsmModelRuntime;

import java.util.Collection;
import java.util.LinkedHashSet;

@LuaWhitelist
@LuaTypeDoc(name = "YsmModelAPI", value = "ysm_model")
public class YsmModelAPI {
    private final Avatar owner;

    public YsmModelAPI(Avatar owner) {
        this.owner = owner;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_part")
    public YsmModelPart getPart(String name) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? null : runtime.getPart(name);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_parts")
    public Collection<YsmModelPart> getParts() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? java.util.List.of() : new LinkedHashSet<>(runtime.parts().values());
    }

    @LuaWhitelist
    public Object __index(String key) {
        if (key == null)
            return null;
        if ("ALL".equalsIgnoreCase(key))
            return getParts();
        return getPart(key);
    }

    @Override
    public String toString() {
        return "YsmModelAPI";
    }
}
