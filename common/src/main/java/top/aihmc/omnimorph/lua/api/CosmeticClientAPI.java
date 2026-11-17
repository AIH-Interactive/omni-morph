package top.aihmc.omnimorph.lua.api;

import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.api.ClientAPI;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.math.vector.FiguraVec3;

/** Read-only camera queries for an accessory's view-dependent geometry. */
@LuaWhitelist
@LuaTypeDoc(name = "CosmeticClientAPI", value = "cosmetic_client")
public final class CosmeticClientAPI {
    @LuaWhitelist
    public FiguraVec3 getCameraPos() {
        return ClientAPI.getCameraPos();
    }

    @LuaWhitelist
    public FiguraVec3 getCameraRot() {
        return ClientAPI.getCameraRot();
    }
}
