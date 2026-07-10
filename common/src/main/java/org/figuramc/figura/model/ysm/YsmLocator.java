package org.figuramc.figura.model.ysm;

import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaTypeDoc;

@LuaWhitelist
@LuaTypeDoc(name = "YsmLocator", value = "ysm_locator")
public record YsmLocator(
        @LuaWhitelist
        @LuaMethodDoc("ysm_locator.get_name")
        String name,
        @LuaWhitelist
        @LuaMethodDoc("ysm_locator.get_bone_name")
        String boneName,
        @LuaWhitelist
        @LuaMethodDoc("ysm_locator.get_role")
        YsmBoneRole role,
        @LuaWhitelist
        @LuaMethodDoc("ysm_locator.is_left_side")
        boolean leftSide,
        @LuaWhitelist
        @LuaMethodDoc("ysm_locator.get_default_item_transform")
        String defaultItemTransform
) {
    @LuaWhitelist
    @LuaMethodDoc("ysm_locator.get_role_name")
    public String getRoleName() {
        return role.name().toLowerCase(java.util.Locale.US);
    }
}
