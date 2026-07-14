package org.figuramc.figura.avatar.control;

import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaTypeDoc;

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
