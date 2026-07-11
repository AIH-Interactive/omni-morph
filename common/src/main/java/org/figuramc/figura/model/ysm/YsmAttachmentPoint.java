package org.figuramc.figura.model.ysm;

import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaTypeDoc;

@LuaWhitelist
@LuaTypeDoc(name = "YsmAttachmentPoint", value = "ysm_attachment_point")
public record YsmAttachmentPoint(
        @LuaWhitelist
        @LuaMethodDoc("ysm_attachment_point.get_name")
        String name,
        @LuaWhitelist
        @LuaMethodDoc("ysm_attachment_point.get_bone_name")
        String boneName,
        @LuaWhitelist
        @LuaMethodDoc("ysm_attachment_point.get_type")
        YsmAttachmentType type,
        @LuaWhitelist
        @LuaMethodDoc("ysm_attachment_point.get_role")
        YsmBoneRole role,
        @LuaWhitelist
        @LuaMethodDoc("ysm_attachment_point.is_left_side")
        boolean leftSide,
        @LuaWhitelist
        @LuaMethodDoc("ysm_attachment_point.get_default_item_transform")
        String defaultItemTransform,
        @LuaWhitelist
        @LuaMethodDoc("ysm_attachment_point.get_locator")
        YsmLocator locator,
        @LuaWhitelist
        @LuaMethodDoc("ysm_attachment_point.get_part")
        YsmModelPart part
) {
    @LuaWhitelist
    @LuaMethodDoc("ysm_attachment_point.get_type_name")
    public String getTypeName() {
        return type.name().toLowerCase(java.util.Locale.US);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_attachment_point.get_role_name")
    public String getRoleName() {
        return role.name().toLowerCase(java.util.Locale.US);
    }
}
