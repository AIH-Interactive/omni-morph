package top.aihmc.omnimorph.avatar.control;

import top.aihmc.omnimorph.lua.LuaWhitelist;
import top.aihmc.omnimorph.lua.docs.LuaTypeDoc;

@LuaWhitelist
@LuaTypeDoc(
        name = "AvatarControlType",
        value = "avatar_control_type"
)
public enum AvatarControlType {
    TOGGLE,
    SLIDER,
    ENUM,
    COLOR,
    TEXT,
    NUMBER,
    KEYBIND,
    BUTTON,
    LABEL,
    SEPARATOR
}
